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

import static io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory.ICEBERG_URI;
import static io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory.GEOPARQUET_URI;
import static io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory.STAC_URI;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFactorySpi;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * The parquetry stores open the locations written by the storage browser for a local choice: the {@code file:} URI of
 * the path, percent-encoded, and ending with {@code /} for a folder.
 */
class BrowsedLocationsTest {

    /** A folder name with characters to percent-encode: a space, {@code #}, {@code %} and a letter beyond ASCII. */
    private static final String ODD_FOLDER_NAME = "my dir#1%-caf\u00e9";

    /** A file name with the same characters to percent-encode. */
    private static final String ODD_FILE_NAME = "my file#1%-caf\u00e9.parquet";

    /** Relative to the module directory, the working directory of the test JVM. */
    private static final Path DEMO_DATA = Path.of("..", "demo", "data");

    @TempDir
    Path folder;

    @Test
    void geoParquetOpensAFile() throws Exception {
        Path file = copyFixture("points_4326.parquet", ODD_FOLDER_NAME + "/" + ODD_FILE_NAME);

        DataStore store = open(new GeoParquetDataStoreFactory(), GEOPARQUET_URI.key, file.toUri());
        try {
            String[] typeNames = store.getTypeNames();
            assertThat(typeNames).hasSize(1);
            SimpleFeatureSource features = store.getFeatureSource(typeNames[0]);
            int count = features.getCount(Query.ALL);
            assertThat(count).isEqualTo(3);
        } finally {
            store.dispose();
        }
    }

    @Test
    void geoParquetOpensAFolder() throws Exception {
        Path file = copyFixture("points_4326.parquet", ODD_FOLDER_NAME + "/points.parquet");
        URI folderUri = file.getParent().toUri();
        assertThat(folderUri.toString()).endsWith("/");

        DataStore store = open(new GeoParquetDataStoreFactory(), GEOPARQUET_URI.key, folderUri);
        try {
            assertThat(store.getTypeNames()).isNotEmpty();
        } finally {
            store.dispose();
        }
    }

    @Test
    void icebergOpensAWarehouse() throws IOException {
        Path warehouse = folder.resolve(ODD_FOLDER_NAME).resolve("warehouse");
        Path demoTable = DEMO_DATA.resolve("iceberg-warehouse/ne/disputed_areas");
        Path copiedTable = warehouse.resolve("ne/disputed_areas");
        copyTree(demoTable, copiedTable);

        DataStore store = open(new IcebergDataStoreFactory(), ICEBERG_URI.key, warehouse.toUri());
        try {
            String typeName = "ne.disputed_areas";
            assertThat(store.getTypeNames()).contains(typeName);
            SimpleFeatureSource features = store.getFeatureSource(typeName);
            int count = features.getCount(Query.ALL);
            assertThat(count).isEqualTo(23);
        } finally {
            store.dispose();
        }
    }

    @Test
    void stacOpensAnItemsTable() throws IOException {
        Path items = folder.resolve(ODD_FOLDER_NAME).resolve("items.parquet");
        Files.createDirectories(items.getParent());
        Path demoItems = DEMO_DATA.resolve("ne/index/items.parquet");
        Files.copy(demoItems, items);

        DataStore store = open(new StacDataStoreFactory(), STAC_URI.key, items.toUri());
        try {
            assertThat(store.getTypeNames())
                    .containsExactlyInAnyOrder(
                            "boundary_lines_land", "coastlines", "countries", "disputed_areas", "populated_places");
        } finally {
            store.dispose();
        }
    }

    private static DataStore open(DataStoreFactorySpi factory, String key, URI location) throws IOException {
        Map<String, Serializable> params = Map.of(key, location.toString());
        return factory.createDataStore(params);
    }

    private Path copyFixture(String fixture, String relativeTarget) throws IOException, URISyntaxException {
        URL resource = getClass().getResource("/fixtures/" + fixture);
        Path source = Path.of(resource.toURI());
        Path target = folder.resolve(relativeTarget);
        Files.createDirectories(target.getParent());
        return Files.copy(source, target);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path copied = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copied);
                } else {
                    Files.copy(path, copied);
                }
            }
        }
    }
}
