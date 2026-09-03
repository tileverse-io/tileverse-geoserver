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

import org.geoserver.platform.ModuleStatusImpl;
import org.geoserver.web.data.resource.DataStorePanelInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.tileverse.geoserver.parquetry.web.GeoParquetDataStoreEditPanel;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;

/**
 * Registers the Iceberg vector store in the GeoServer UI. Being single-backend (one {@code storage.provider} parameter,
 * like GeoParquet), the Iceberg store reuses {@link GeoParquetDataStoreEditPanel}, the single-select panel that renders
 * the grouped, provider-driven storage parameters.
 *
 * <p>GeoServer Cloud imports this class directly; vanilla GeoServer reaches the same bean through the plugin's
 * {@code applicationContext.xml}.
 */
@Configuration(proxyBeanMethods = false)
public class IcebergConfiguration {

    @Bean
    DataStorePanelInfo icebergDataStorePanel() {
        return DataStorePanels.panel(
                "iceberg",
                IcebergDataStoreFactory.class,
                GeoParquetDataStoreEditPanel.class,
                "img/icons/apache-iceberg.svg");
    }

    @Bean
    ModuleStatusImpl icebergModuleStatus() {
        return ModuleStatuses.community("gs-parquetry-iceberg", "Parquetry Iceberg Store", "Iceberg DataStore");
    }
}
