/*
 * (c) Copyright 2026 Multiversio LLC. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package io.tileverse.geoserver.web.storage.browser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import io.tileverse.storage.RangeReader;
import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageEntry;

/**
 * The format reads of one browser. It keeps the values read for its current listing apart from the shared cache: a sort
 * never loses a value to the cache's evictions. The reads of the rows shown come first, the reads of a sort after them,
 * and a few at most run at once on the shared pool.
 */
final class FormatReads {

    /** Recorded when the columns of a file throw an error: its cell stops waiting, and the read slot frees. */
    private static final FileDetails READ_ERROR = new FileDetails.Unavailable("unexpected error");

    private final FileColumns columns;
    private final FileDetailsLoader loader;
    private final FileDetailsCache cache;
    private final Properties storageParams;
    private final String parametersDigest;
    private final int maxRunning;

    /** Guarded by this, as are the fields below: reads finish on the threads of the pool. */
    private OpenStorage storage;

    private final Map<String, FileDetails> values = new HashMap<>();
    private final LinkedHashMap<String, QueuedRead> queued = new LinkedHashMap<>();
    private final Set<RunningRead> running = new HashSet<>();

    /** Counts the listings: a read finishing for an older one fills the shared cache only. */
    private int generation;

    private boolean closed;

    FormatReads(
            FileColumns columns,
            FileDetailsLoader loader,
            FileDetailsCache cache,
            Properties storageParams,
            int maxRunning) {
        this.columns = columns;
        this.loader = loader;
        this.cache = cache;
        this.storageParams = storageParams;
        this.parametersDigest = FileDetailsCache.digest(storageParams);
        this.maxRunning = maxRunning;
    }

    /** Starts over for a new listing of {@code listedStorage}: the values go, and the queued reads are cancelled. */
    synchronized void reset(OpenStorage listedStorage) {
        storage = listedStorage;
        values.clear();
        queued.clear();
        generation++;
    }

    /**
     * Queues the reads of the files shown and missing a value, ahead of the others, and cancels the queued reads of the
     * rows no longer shown. The reads of a sort stay queued, shown or not: paging does not stop a sort.
     */
    synchronized void show(List<StorageEntry.File> shown) {
        Set<String> shownKeys = keysOf(shown);
        LinkedHashMap<String, QueuedRead> reordered = new LinkedHashMap<>();
        for (StorageEntry.File file : missing(shown)) {
            reordered.put(file.key(), shownRead(file));
        }
        for (QueuedRead waiting : queued.values()) {
            boolean hiddenSortRead =
                    waiting.forSort() && !shownKeys.contains(waiting.file().key());
            if (hiddenSortRead) {
                reordered.put(waiting.file().key(), waiting.hidden());
            }
        }
        queued.clear();
        queued.putAll(reordered);
        startReads();
    }

    /** Queues the reads of {@code files} missing a value, behind the reads of the rows shown. */
    synchronized void queueForSort(List<StorageEntry.File> files) {
        for (StorageEntry.File file : missing(files)) {
            queued.put(file.key(), sortRead(file));
        }
        startReads();
    }

    /** Cancels the queued reads of a sort. The files shown keep their reads. */
    synchronized void cancelSortReads() {
        queued.values().removeIf(read -> !read.shown());
        queued.replaceAll((key, read) -> read.withoutSort());
    }

    /** The files of {@code files} read by the columns and still without a value. */
    synchronized int missingCount(List<StorageEntry.File> files) {
        return missing(files).size();
    }

    synchronized Optional<FileDetails> details(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Cancels the queued reads; the running ones finish without updating the values. */
    synchronized void close() {
        closed = true;
        queued.clear();
    }

    /** The read of a file shown. A read queued for a sort keeps its mark: paging away must not cancel it. */
    private QueuedRead shownRead(StorageEntry.File file) {
        QueuedRead waiting = queued.get(file.key());
        boolean forSort = waiting != null && waiting.forSort();
        return new QueuedRead(file, true, forSort);
    }

    /** The read of a file needed by a sort. The read of a file shown stays a read of the rows shown as well. */
    private QueuedRead sortRead(StorageEntry.File file) {
        QueuedRead waiting = queued.get(file.key());
        boolean shown = waiting != null && waiting.shown();
        return new QueuedRead(file, shown, true);
    }

    /** The files read by the columns and missing a value, after taking the values found in the shared cache. */
    private List<StorageEntry.File> missing(List<StorageEntry.File> files) {
        List<StorageEntry.File> missing = new ArrayList<>();
        for (StorageEntry.File file : files) {
            if (needsRead(file)) {
                missing.add(file);
            }
        }
        return missing;
    }

    private boolean needsRead(StorageEntry.File file) {
        String key = file.key();
        boolean known = values.containsKey(key) || running.contains(new RunningRead(key, generation));
        if (known || !columns.reads(BrowserEntry.nameOf(key))) {
            return false;
        }
        Optional<FileDetails.Values> cached = cache.get(cacheKey(file, storage));
        cached.ifPresent(found -> values.put(key, found));
        return cached.isEmpty();
    }

    private void startReads() {
        Iterator<QueuedRead> next = queued.values().iterator();
        while (!closed && running.size() < maxRunning && next.hasNext()) {
            QueuedRead read = next.next();
            next.remove();
            start(read.file());
        }
    }

    private void start(StorageEntry.File file) {
        OpenStorage readStorage = storage;
        if (!readStorage.acquire()) {
            return;
        }
        int readGeneration = generation;
        running.add(new RunningRead(file.key(), readGeneration));
        loader.submit(() -> run(file, readStorage, readGeneration));
    }

    /** Runs on the pool. An error thrown by the columns goes on to the pool, after freeing the read slot. */
    private void run(StorageEntry.File file, OpenStorage readStorage, int readGeneration) {
        FileDetails details = READ_ERROR;
        try {
            details = read(file, readStorage.storage());
        } finally {
            readStorage.release();
            finished(file, readStorage, readGeneration, details);
        }
    }

    private FileDetails read(StorageEntry.File file, Storage readStorage) {
        try (RangeReader reader = readStorage.openRangeReader(file)) {
            return new FileDetails.Values(columns.read(reader));
        } catch (IOException | RuntimeException e) {
            return new FileDetails.Unavailable(FailureMessages.of(e, storageParams));
        }
    }

    private synchronized void finished(
            StorageEntry.File file, OpenStorage readStorage, int readGeneration, FileDetails details) {
        running.remove(new RunningRead(file.key(), readGeneration));
        if (details instanceof FileDetails.Values read) {
            cache.put(cacheKey(file, readStorage), read);
        }
        if (readGeneration == generation && !closed) {
            values.put(file.key(), details);
        }
        startReads();
    }

    private String cacheKey(StorageEntry.File file, OpenStorage readStorage) {
        return FileDetailsCache.key(parametersDigest, columns, readStorage.root(), file);
    }

    private static Set<String> keysOf(List<StorageEntry.File> files) {
        Set<String> keys = new HashSet<>();
        for (StorageEntry.File file : files) {
            keys.add(file.key());
        }
        return keys;
    }

    /** A read waiting for a slot, wanted by the rows shown, by a sort, or by both. */
    private record QueuedRead(StorageEntry.File file, boolean shown, boolean forSort) {

        QueuedRead hidden() {
            return new QueuedRead(file, false, forSort);
        }

        QueuedRead withoutSort() {
            return new QueuedRead(file, shown, false);
        }
    }

    private record RunningRead(String key, int generation) {}
}
