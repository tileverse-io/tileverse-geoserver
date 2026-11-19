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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The original definition of a migrated store, kept in the store metadata to revert the migration. The url is null when
 * the store had none. The native names map resource ids to the names before the migration; an empty name means the
 * resource had none.
 */
public record StoreBackup(
        String migrationId,
        Instant migratedAt,
        String type,
        String url,
        Map<String, String> connectionParameters,
        Map<String, String> nativeNames,
        Map<String, String> settings) {

    public StoreBackup {
        Objects.requireNonNull(migrationId, "migrationId");
        Objects.requireNonNull(migratedAt, "migratedAt");
        Objects.requireNonNull(type, "type");
        connectionParameters = Map.copyOf(connectionParameters);
        nativeNames = Map.copyOf(nativeNames);
        settings = Map.copyOf(settings);
    }
}
