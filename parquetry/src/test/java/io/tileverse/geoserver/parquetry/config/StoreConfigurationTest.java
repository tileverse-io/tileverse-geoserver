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

import static org.assertj.core.api.Assertions.assertThat;

import org.geoserver.web.data.resource.DataStorePanelInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.tileverse.geoserver.parquetry.web.GeoParquetDataStoreEditPanel;
import io.tileverse.geoserver.parquetry.web.StacDataStoreEditPanel;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * Loads the per-store {@code @Configuration} classes through an {@link AnnotationConfigApplicationContext} - the way a
 * GeoServer Cloud autoconfiguration imports them - and asserts they produce the same store-panel beans, by id, as the
 * plugin's {@code applicationContext.xml}.
 */
class StoreConfigurationTest {

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void loadContext() {
        context = new AnnotationConfigApplicationContext(
                GeoParquetConfiguration.class, IcebergConfiguration.class, StacConfiguration.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void geoParquetConfigurationBindsTheGeoParquetFactory() {
        DataStorePanelInfo panel = context.getBean("parquetryGeoParquetDataStorePanel", DataStorePanelInfo.class);

        assertThat(panel.getId()).isEqualTo("parquetry-parquet");
        assertThat(panel.getFactoryClass()).isEqualTo(GeoParquetDataStoreFactory.class);
        assertThat(panel.getComponentClass()).isEqualTo(GeoParquetDataStoreEditPanel.class);
        assertThat(panel.getIcon()).isEqualTo("img/icons/parquetry.svg");
    }

    @Test
    void icebergConfigurationBindsTheIcebergFactory() {
        DataStorePanelInfo panel = context.getBean("icebergDataStorePanel", DataStorePanelInfo.class);

        assertThat(panel.getId()).isEqualTo("iceberg");
        assertThat(panel.getFactoryClass()).isEqualTo(IcebergDataStoreFactory.class);
        assertThat(panel.getComponentClass()).isEqualTo(GeoParquetDataStoreEditPanel.class);
        assertThat(panel.getIcon()).isEqualTo("img/icons/apache-iceberg.svg");
    }

    @Test
    void stacConfigurationBindsTheStacFactory() {
        DataStorePanelInfo panel = context.getBean("stacDataStorePanel", DataStorePanelInfo.class);

        assertThat(panel.getId()).isEqualTo("stac-geoparquet");
        assertThat(panel.getFactoryClass()).isEqualTo(StacDataStoreFactory.class);
        assertThat(panel.getComponentClass()).isEqualTo(StacDataStoreEditPanel.class);
        assertThat(panel.getIcon()).isEqualTo("img/icons/stac-geoparquet.svg");
    }
}
