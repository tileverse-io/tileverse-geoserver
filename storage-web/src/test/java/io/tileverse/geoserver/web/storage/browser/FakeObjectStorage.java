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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.tileverse.storage.CopyOptions;
import io.tileverse.storage.DeleteResult;
import io.tileverse.storage.ListOptions;
import io.tileverse.storage.NotFoundException;
import io.tileverse.storage.PresignWriteOptions;
import io.tileverse.storage.RangeReader;
import io.tileverse.storage.ReadHandle;
import io.tileverse.storage.ReadOptions;
import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageCapabilities;
import io.tileverse.storage.StorageEntry;
import io.tileverse.storage.StorageOutputStream;
import io.tileverse.storage.StoragePattern;
import io.tileverse.storage.WriteOptions;

/**
 * An object storage held in memory, listing by S3's prefix and delimiter rules without a server. It records the
 * patterns listed and whether it was closed. Like the real backends, it refuses an unsafe pattern. Writes are not
 * supported.
 */
final class FakeObjectStorage implements Storage {

    private final URI baseUri;
    private final NavigableMap<String, byte[]> objects = new TreeMap<>();
    private final List<String> listedPatterns = new CopyOnWriteArrayList<>();
    private StorageCapabilities capabilities = listingCapabilities().build();
    private RuntimeException listFailure;
    private volatile boolean closed;

    FakeObjectStorage(String baseUri) {
        this.baseUri = URI.create(baseUri);
    }

    FakeObjectStorage with(String key, String content) {
        objects.put(key, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Reports real directories, as local files and ADLS Gen2 do. Only the capabilities change: the listing stays
     * S3-like.
     */
    FakeObjectStorage withRealDirectories() {
        capabilities = listingCapabilities().realDirectories(true).build();
        return this;
    }

    private static StorageCapabilities.Builder listingCapabilities() {
        return StorageCapabilities.builder().list(true).rangeReads(true);
    }

    FakeObjectStorage unableToList() {
        capabilities = StorageCapabilities.builder().list(false).build();
        return this;
    }

    FakeObjectStorage failingToList(RuntimeException failure) {
        listFailure = failure;
        return this;
    }

    List<String> listedPatterns() {
        return List.copyOf(listedPatterns);
    }

    boolean closed() {
        return closed;
    }

    @Override
    public URI baseUri() {
        return baseUri;
    }

    @Override
    public StorageCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public Optional<StorageEntry.File> stat(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(fileEntry(key, content));
    }

    /** Files directly under the prefix of the pattern, and one common prefix per deeper folder. */
    @Override
    public Stream<StorageEntry> list(String pattern, ListOptions options) {
        Storage.requireSafePattern(pattern);
        listedPatterns.add(pattern);
        if (listFailure != null) {
            throw listFailure;
        }
        StoragePattern parsed = StoragePattern.parse(pattern);
        String prefix = parsed.prefix();
        Predicate<String> matcher = parsed.matcher().orElse(key -> true);
        List<StorageEntry> files = new ArrayList<>();
        Set<String> folders = new TreeSet<>();
        for (Map.Entry<String, byte[]> object : objects.tailMap(prefix, true).entrySet()) {
            String key = object.getKey();
            if (!key.startsWith(prefix)) {
                break;
            }
            String rest = key.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                folders.add(prefix + rest.substring(0, slash + 1));
            } else if (matcher.test(key)) {
                files.add(fileEntry(key, object.getValue()));
            }
        }
        Stream<StorageEntry> prefixes = folders.stream().map(StorageEntry.Prefix::new);
        return Stream.concat(files.stream(), prefixes);
    }

    private static StorageEntry.File fileEntry(String key, byte[] content) {
        return new StorageEntry.File(
                key,
                content.length,
                Instant.EPOCH,
                Optional.of("etag-" + key.hashCode()),
                Optional.empty(),
                Optional.empty(),
                Map.of());
    }

    @Override
    public RangeReader openRangeReader(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new NotFoundException(key);
        }
        return new BytesRangeReader(baseUri + key, content);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public ReadHandle read(String key, ReadOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageEntry.File put(String key, byte[] data, WriteOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageEntry.File put(String key, Path source, WriteOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageOutputStream openOutputStream(String key, WriteOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteAll(Collection<String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageEntry.File copy(String srcKey, String dstKey, CopyOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageEntry.File copy(String srcKey, Storage dst, String dstKey, CopyOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageEntry.File move(String srcKey, String dstKey, CopyOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI presignPut(String key, Duration ttl, PresignWriteOptions options) {
        throw new UnsupportedOperationException();
    }
}
