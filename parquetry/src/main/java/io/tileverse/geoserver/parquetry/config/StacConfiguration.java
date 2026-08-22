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

import io.tileverse.geoserver.parquetry.web.StacDataStoreEditPanel;

import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * Registers the STAC GeoParquet vector store in the GeoServer UI. A STAC catalog can read from more than one storage
 * backend at once (its catalog and its data assets may live on different backends). It therefore uses the multi-select
 * {@link StacDataStoreEditPanel} and has no single {@code storage.provider} parameter.
 *
 * <p>GeoServer Cloud imports this class directly; vanilla GeoServer reaches the same bean through the plugin's
 * {@code applicationContext.xml}.
 */
@Configuration(proxyBeanMethods = false)
public class StacConfiguration {

    @Bean
    DataStorePanelInfo stacDataStorePanel() {
        return DataStorePanels.panel(
                "stac-geoparquet",
                StacDataStoreFactory.class,
                StacDataStoreEditPanel.class,
                "img/icons/stac-geoparquet.svg");
    }
}
