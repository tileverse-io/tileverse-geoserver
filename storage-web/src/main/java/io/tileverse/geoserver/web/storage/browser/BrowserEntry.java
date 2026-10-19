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

import java.io.Serializable;
import java.time.Instant;

import io.tileverse.storage.StorageEntry;

/**
 * One row of the browser: a file or a folder of the folder shown. Serializable because the page keeps its rows.
 *
 * @param name the last segment of the key, without the trailing {@code /} of a folder
 * @param key the key relative to the storage root; a folder key ends with {@code /}
 * @param folder whether the row is a folder
 * @param size the file size in bytes, 0 for a folder
 * @param lastModified the last-modified date, or {@code null} when the storage gives none, as for an S3 prefix
 */
public record BrowserEntry(String name, String key, boolean folder, long size, Instant lastModified)
        implements Serializable {

    /** The row of a listed entry: a file, or a folder for a prefix or a directory. */
    static BrowserEntry of(StorageEntry entry) {
        if (entry instanceof StorageEntry.File file) {
            return new BrowserEntry(nameOf(file.key()), file.key(), false, file.size(), file.lastModified());
        }
        if (entry instanceof StorageEntry.Directory directory) {
            return folder(directory.key(), directory.lastModified().orElse(null));
        }
        return folder(entry.key(), null);
    }

    private static BrowserEntry folder(String key, Instant lastModified) {
        String folderKey = key.endsWith("/") ? key : key + "/";
        return new BrowserEntry(nameOf(folderKey), folderKey, true, 0, lastModified);
    }

    /** The last segment of {@code key}, without its trailing {@code /}. */
    static String nameOf(String key) {
        String trimmed = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
