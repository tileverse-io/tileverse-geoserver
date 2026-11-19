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

/**
 * Protects the backup text of a migrated store. The backup holds the original connection parameters, secrets included,
 * and GeoServer writes the store metadata in clear text to the catalog.
 */
public interface BackupCipher {

    /** Keeps the text as it is, for tests. */
    BackupCipher NONE = new BackupCipher() {
        @Override
        public String encode(String plain) {
            return plain;
        }

        @Override
        public String decode(String stored) {
            return stored;
        }
    };

    String encode(String plain);

    String decode(String stored);
}
