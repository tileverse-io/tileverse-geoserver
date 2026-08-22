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
package io.tileverse.geoserver.parquetry.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.geotools.api.data.DataAccessFactory.Param;
import org.junit.jupiter.api.Test;

import io.tileverse.geoserver.web.storage.StorageParamVisibility;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

/**
 * Pins the GeoParquet factory's storage parameters to the shared panel contract: every backend group the factory
 * reports is one the visibility rules know, and every titled group resolves a section label. The labels merge from
 * every {@code GeoServerApplication.properties} on the classpath, exactly as GeoServer's resource loader does - the
 * storage labels ship in the storage-web jar, the store-specific ones in this plugin's.
 */
class GeoParquetStorageGroupLabelsTest {

    @Test
    void titlesEveryBackendGroupTheGeoParquetFactoryReports() {
        Set<String> firstKeys = StorageParamVisibility.firstParamKeysPerGroup(orderedFactoryKeys());
        assertThat(firstKeys.stream().map(StorageParamVisibility::groupOf))
                .containsExactlyInAnyOrder("s3", "azure", "gcs", "http", "caching");
    }

    @Test
    void everyTitledGroupHasASectionLabel() throws IOException {
        Properties labels = mergedSectionLabels();
        Set<String> firstKeys = StorageParamVisibility.firstParamKeysPerGroup(orderedFactoryKeys());
        for (String key : firstKeys) {
            String labelKey = "storage.group." + StorageParamVisibility.groupOf(key);
            assertThat(labels.getProperty(labelKey))
                    .as("missing section label %s", labelKey)
                    .isNotBlank();
        }
    }

    private static List<String> orderedFactoryKeys() {
        Param[] parameters = new GeoParquetDataStoreFactory().getParametersInfo();
        return Arrays.stream(parameters).map(parameter -> parameter.key).toList();
    }

    private static Properties mergedSectionLabels() throws IOException {
        Properties labels = new Properties();
        List<URL> resources = Collections.list(GeoParquetStorageGroupLabelsTest.class
                .getClassLoader()
                .getResources("GeoServerApplication.properties"));
        for (URL resource : resources) {
            try (InputStream in = resource.openStream()) {
                labels.load(in);
            }
        }
        return labels;
    }
}
