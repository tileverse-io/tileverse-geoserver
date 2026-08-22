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

import org.geoserver.web.data.resource.DataStorePanelInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.tileverse.geoserver.parquetry.web.GeoParquetDataStoreEditPanel;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

/**
 * Registers the GeoParquet vector store in the GeoServer UI. The {@link DataStorePanelInfo} binds
 * {@link GeoParquetDataStoreFactory} to {@link GeoParquetDataStoreEditPanel}, whose form reacts to the chosen storage
 * provider and exposes the provider-specific connection parameters.
 *
 * <p>GeoServer Cloud imports this class directly; vanilla GeoServer reaches the same bean through the plugin's
 * {@code applicationContext.xml}.
 */
@Configuration(proxyBeanMethods = false)
public class GeoParquetConfiguration {

    @Bean
    DataStorePanelInfo parquetryGeoParquetDataStorePanel() {
        return DataStorePanels.panel(
                "parquetry-parquet",
                GeoParquetDataStoreFactory.class,
                GeoParquetDataStoreEditPanel.class,
                "img/icons/parquetry.svg");
    }
}
