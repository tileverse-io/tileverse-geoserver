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
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.tileverse.storage.StorageParameter;

import io.tileverse.geoserver.web.storage.StorageParams;

/**
 * Pins that every backend group rendered by the shared storage section has a section label on the parquetry classpath,
 * where storage-web's bundle and this module's bundle merge.
 */
class GeoParquetStorageGroupLabelsTest {

    @Test
    void ordersCachingFirstThenEveryBackendGroup() {
        assertThat(renderedGroups()).containsExactly("caching", "http", "s3", "gcs", "azure", "file");
    }

    @Test
    void everyRenderedGroupHasASectionLabel() throws IOException {
        Properties labels = mergedSectionLabels();
        for (String group : renderedGroups()) {
            String labelKey = "storage.group." + group;
            assertThat(labels.getProperty(labelKey))
                    .as("missing section label %s", labelKey)
                    .isNotBlank();
        }
    }

    private static List<String> renderedGroups() {
        return StorageParams.orderedParameters(true).stream()
                .map(StorageParameter::group)
                .distinct()
                .toList();
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
