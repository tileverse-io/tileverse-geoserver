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
package io.tileverse.geoserver.parquetry.migration;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;

import io.tileverse.geoserver.web.storage.migration.CheckResult;
import io.tileverse.geoserver.web.storage.migration.MigrationMarker;
import io.tileverse.geoserver.web.storage.migration.MigrationPlan;
import io.tileverse.geoserver.web.storage.migration.StoreMigration;

/**
 * Moves the stores of the GeoServer community GeoParquet module to the Parquetry GeoParquet store. The community store
 * reads its files through DuckDB; the Parquetry store reads them through tileverse storage.
 */
public final class CommunityGeoParquetMigration implements StoreMigration {

    public static final String ID = "community-geoparquet";

    /** The store type saved by the community module, the display name of its factory. */
    static final String COMMUNITY_TYPE = "GeoParquet";

    private static final String DBTYPE = "dbtype";

    private static final String COMMUNITY_DBTYPE = "geoparquet";

    /** The parameter of the community store holding its location. */
    private static final String COMMUNITY_LOCATION = "uri";

    /** The parameter of the Parquetry store holding its location. */
    private static final String PARQUETRY_LOCATION = "geoparquet";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String kindLabelKey() {
        return "GeoParquetMigration.kind";
    }

    @Override
    public List<StoreInfo> recognize(Catalog catalog) {
        List<DataStoreInfo> stores = catalog.getDataStores();
        return stores.stream()
                .filter(store -> !MigrationMarker.isOn(store))
                .filter(CommunityGeoParquetMigration::isCommunityStore)
                .map(StoreInfo.class::cast)
                .toList();
    }

    @Override
    public MigrationPlan translate(StoreInfo store, List<ResourceInfo> resources) {
        return GeoParquetTranslation.translate(store.getConnectionParameters());
    }

    @Override
    public CheckResult verify(StoreInfo store, List<ResourceInfo> resources, MigrationPlan plan, Catalog catalog) {
        GeoParquetVerification verification = new GeoParquetVerification(catalog);
        return verification.verify(store, resources, plan);
    }

    /** The uri of a community store, the geoparquet parameter of a migrated one. */
    @Override
    public Optional<String> location(StoreInfo store) {
        String key = MigrationMarker.isOn(store) ? PARQUETRY_LOCATION : COMMUNITY_LOCATION;
        Serializable location = store.getConnectionParameters().get(key);
        return Optional.ofNullable(location).map(String::valueOf);
    }

    /** A store saved without its type is recognized by the dbtype parameter of the community factory. */
    private static boolean isCommunityStore(DataStoreInfo store) {
        Serializable dbtype = store.getConnectionParameters().get(DBTYPE);
        boolean communityType = COMMUNITY_TYPE.equalsIgnoreCase(store.getType());
        boolean communityDbtype = dbtype != null && COMMUNITY_DBTYPE.equalsIgnoreCase(String.valueOf(dbtype));
        return communityType || communityDbtype;
    }
}
