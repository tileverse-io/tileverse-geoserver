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

import java.util.Map;

/** The format values of one file, or why they could not be read. */
sealed interface FileDetails {

    /** The values read, by column key. */
    record Values(Map<String, ColumnValue> byColumn) implements FileDetails {

        public Values {
            byColumn = Map.copyOf(byColumn);
        }
    }

    /** A failed read, such as a 403 or a truncated file: its cells show "unavailable", with the reason as tooltip. */
    record Unavailable(String reason) implements FileDetails {}
}
