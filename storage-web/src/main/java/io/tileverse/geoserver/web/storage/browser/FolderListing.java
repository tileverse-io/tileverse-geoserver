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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.tileverse.storage.StorageEntry;

/**
 * One listing of a folder: the rows shown, the listed files by key for reading their formats, and whether the listing
 * stopped at its limit.
 */
final class FolderListing {

    private final String folderKey;
    private final List<BrowserEntry> rows;
    private final Map<String, StorageEntry.File> files;
    private final Set<String> folderKeys;
    private final boolean truncated;

    FolderListing(String folderKey, List<StorageEntry> entries, boolean truncated) {
        this.folderKey = folderKey;
        this.truncated = truncated;
        List<BrowserEntry> listedRows = new ArrayList<>(entries.size());
        Map<String, StorageEntry.File> listedFiles = new HashMap<>();
        Set<String> listedFolders = new HashSet<>();
        for (StorageEntry entry : entries) {
            BrowserEntry row = BrowserEntry.of(entry);
            listedRows.add(row);
            if (entry instanceof StorageEntry.File file) {
                listedFiles.put(file.key(), file);
            } else {
                listedFolders.add(row.key());
            }
        }
        this.rows = List.copyOf(listedRows);
        this.files = Map.copyOf(listedFiles);
        this.folderKeys = Set.copyOf(listedFolders);
    }

    static FolderListing empty(String folderKey) {
        return new FolderListing(folderKey, List.of(), false);
    }

    String folderKey() {
        return folderKey;
    }

    List<BrowserEntry> rows() {
        return rows;
    }

    boolean truncated() {
        return truncated;
    }

    int size() {
        return rows.size();
    }

    Optional<StorageEntry.File> file(String key) {
        return Optional.ofNullable(files.get(key));
    }

    boolean hasFolder(String key) {
        return folderKeys.contains(key);
    }
}
