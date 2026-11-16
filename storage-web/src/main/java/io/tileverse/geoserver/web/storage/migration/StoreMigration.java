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

import java.util.List;
import java.util.Optional;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;

/**
 * Moves the stores of one kind, read by a GeoServer community module, to a tileverse store type. The migration
 * recognizes its stores, translates their parameters without I/O, and verifies the translated store before it is saved.
 */
public interface StoreMigration {

    /** Stable id kept in the backup, for example "community-geoparquet". */
    String id();

    /** Label key of the Origin column: the kind of store migrated by this migration. */
    String kindLabelKey();

    /** The stores of this kind, migrated ones excluded. The page also leaves out the migrated stores returned here. */
    List<StoreInfo> recognize(Catalog catalog);

    /** No I/O. A plan with a BLOCKER note is not verified. */
    MigrationPlan translate(StoreInfo store, List<ResourceInfo> resources);

    /** Opens the translated store without saving it. May return a refined plan in the result. */
    CheckResult verify(StoreInfo store, List<ResourceInfo> resources, MigrationPlan plan, Catalog catalog);

    /**
     * The location shown for a store of this migration, before or after it is migrated. Empty to let the page use the
     * store URL or its "uri" or "url" parameter. The page masks the secrets of the location and leaves out its query.
     */
    default Optional<String> location(StoreInfo store) {
        return Optional.empty();
    }
}
