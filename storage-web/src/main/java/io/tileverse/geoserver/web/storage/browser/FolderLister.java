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

import java.util.List;
import java.util.stream.Stream;

import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageCapabilities;
import io.tileverse.storage.StorageEntry;

/**
 * Lists one level of a folder, up to a number of entries: the listing of a large folder stops early, and the dialog
 * tells the user. Over a truncated listing, a search lists the folder again for the names starting with the typed text.
 */
final class FolderLister {

    private static final String FILE_SCHEME = "file";

    private final int limit;

    FolderLister(int limit) {
        this.limit = limit;
    }

    FolderListing list(Storage storage, String folderKey) {
        try (Stream<StorageEntry> entries = storage.list(folderKey)) {
            Stream<StorageEntry> children = entries.filter(entry -> isChild(entry, folderKey));
            return collect(folderKey, children);
        }
    }

    /**
     * The children of {@code folderKey} with a name starting with {@code prefix}, in the same letter case. The text is
     * literal: a glob character in it matches itself.
     *
     * <p>A backend listing by key prefix (S3, Azure Blob, GCS) lists the keys starting with the first segment of the
     * text, up to a glob character or a slash. A glob would list the whole folder, or everything below it with
     * {@code **}, and a slash would list a deeper level. Local files and ADLS Gen2 read a pattern as a directory name,
     * and a search prefix is not one: it would list another folder, or fail. GCS with a hierarchical namespace reports
     * real directories as well, although it lists by prefix. This class lists the folder itself for the backends
     * reporting real directories. Either way, it matches the names against the whole text.
     *
     * <p>A remote backend is read up to {@code limit + 1} entries, and a name past them is not found. The local backend
     * is read in full.
     */
    FolderListing search(Storage storage, String folderKey, String prefix) {
        String pattern = listingPattern(storage, folderKey, prefix);
        try (Stream<StorageEntry> entries = storage.list(pattern)) {
            Stream<StorageEntry> bounded = limitedOnRemoteStorage(storage, entries);
            Stream<StorageEntry> named = bounded.filter(entry -> isNamedChild(entry, folderKey, prefix));
            return collect(folderKey, named);
        }
    }

    private static String listingPattern(Storage storage, String folderKey, String prefix) {
        if (listsByDirectory(storage)) {
            return folderKey;
        }
        return folderKey + safeSegment(prefix);
    }

    /**
     * Whether the backend reports real directories: local files, ADLS Gen2, and GCS with a hierarchical namespace.
     * Local files and ADLS Gen2 read a pattern as a directory name, and a key prefix is not one. GCS lists by prefix
     * even then.
     */
    private static boolean listsByDirectory(Storage storage) {
        StorageCapabilities capabilities = storage.capabilities();
        return capabilities.realDirectories();
    }

    /**
     * The part of the text usable as a listing prefix: its first segment, up to a glob character or a slash, and
     * nothing for a "." or ".." segment. The engine refuses those in a pattern, and typing a name such as .gitkeep
     * starts with one.
     */
    private static String safeSegment(String text) {
        String beforeGlob = textBeforeGlob(text);
        int slash = beforeGlob.indexOf('/');
        String segment = slash < 0 ? beforeGlob : beforeGlob.substring(0, slash);
        return isDotSegment(segment) ? "" : segment;
    }

    private static String textBeforeGlob(String text) {
        int glob = GlobCharacters.firstIn(text);
        return glob < 0 ? text : text.substring(0, glob);
    }

    private static boolean isDotSegment(String segment) {
        return segment.equals(".") || segment.equals("..");
    }

    /**
     * On remote storage, reads at most {@code limit + 1} entries of the listing. A listing by directory narrows nothing
     * on the server, and neither does a text without a usable prefix, such as one starting with a glob character.
     * Matching them over a large folder would page through the whole folder. The local backend reads the whole folder:
     * a local listing is cheap, and the names past the limit must be found.
     */
    private Stream<StorageEntry> limitedOnRemoteStorage(Storage storage, Stream<StorageEntry> entries) {
        if (isFileBackend(storage)) {
            return entries;
        }
        return entries.limit(limit + 1L);
    }

    private FolderListing collect(String folderKey, Stream<StorageEntry> children) {
        List<StorageEntry> entries = children.limit(limit + 1L).toList();
        boolean truncated = entries.size() > limit;
        List<StorageEntry> kept = truncated ? entries.subList(0, limit) : entries;
        return new FolderListing(folderKey, kept, truncated);
    }

    /**
     * Whether {@code entry} lies in the folder and is not the folder itself: S3 lists the object marking a folder
     * created by its console under the folder's own key.
     */
    private static boolean isChild(StorageEntry entry, String folderKey) {
        String key = entry.key();
        return key.startsWith(folderKey) && key.length() > folderKey.length();
    }

    private static boolean isNamedChild(StorageEntry entry, String folderKey, String prefix) {
        if (!isChild(entry, folderKey)) {
            return false;
        }
        String name = entry.key().substring(folderKey.length());
        return name.startsWith(prefix);
    }

    /** Whether the storage is the local file backend, the only one read in full. */
    private static boolean isFileBackend(Storage storage) {
        return FILE_SCHEME.equalsIgnoreCase(storage.baseUri().getScheme());
    }
}
