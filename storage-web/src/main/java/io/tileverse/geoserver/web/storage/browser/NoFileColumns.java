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
import java.util.Map;

import io.tileverse.storage.RangeReader;

/** The columns of {@link FileColumns#NONE}: one instance, kept as one through serialization. */
enum NoFileColumns implements FileColumns {
    INSTANCE;

    @Override
    public List<FileColumn> columns() {
        return List.of();
    }

    @Override
    public boolean reads(String fileName) {
        return false;
    }

    @Override
    public Map<String, ColumnValue> read(RangeReader file) {
        return Map.of();
    }
}
