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
package io.tileverse.geoserver.web.storage.migration;

import java.util.Map;

import org.geoserver.catalog.StoreInfo;

/** The metadata entry of a migrated store. Its value is the encoded backup of the original store. */
public final class MigrationMarker {

    public static final String KEY = "io.tileverse.migration";

    private MigrationMarker() {}

    /** Whether the store was migrated and still keeps its backup. */
    public static boolean isOn(StoreInfo store) {
        Map<String, ?> metadata = store.getMetadata();
        return metadata != null && metadata.containsKey(KEY);
    }
}
