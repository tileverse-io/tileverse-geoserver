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

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * Where a browser opens.
 *
 * @param root the root of the storage to open: a bucket or container, or a local root directory
 * @param folderKey the folder to list, relative to the root: empty for the root itself, ending with {@code /} otherwise
 * @param localRoots the local roots to choose from, empty on object storage
 */
record StartingPoint(URI root, String folderKey, List<File> localRoots) {

    StartingPoint {
        localRoots = List.copyOf(localRoots);
    }

    /** Whether the browser lists local files. */
    boolean local() {
        return !localRoots.isEmpty();
    }
}
