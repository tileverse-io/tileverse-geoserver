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
package io.tileverse.geoserver.parquetry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.tileverse.geoserver.parquetry.migration.CommunityGeoParquetMigration;
import io.tileverse.geoserver.web.storage.migration.StoreMigration;
import io.tileverse.geoserver.web.storage.migration.StoreMigrationWebConfiguration;

/**
 * Offers the migration of the community GeoParquet stores to the Parquetry store, on the store migration page of the
 * Tools menu.
 *
 * <p>GeoServer Cloud imports this class directly; vanilla GeoServer reaches the same beans through the plugin's
 * {@code applicationContext.xml}.
 */
@Configuration(proxyBeanMethods = false)
@Import(StoreMigrationWebConfiguration.class)
public class GeoParquetMigrationConfiguration {

    @Bean
    StoreMigration communityGeoParquetMigration() {
        return new CommunityGeoParquetMigration();
    }
}
