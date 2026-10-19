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

/** What a store picks in the browser. */
public enum PickMode {
    /** One file, such as a STAC catalog. */
    FILE,
    /** One folder, such as an Iceberg warehouse. */
    FOLDER,
    /** A file or a folder, such as a GeoParquet dataset. */
    FILE_OR_FOLDER;

    public boolean allowsFiles() {
        return this != FOLDER;
    }

    public boolean allowsFolders() {
        return this != FILE;
    }
}
