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
package io.tileverse.geoserver.parquetry.wfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.geoserver.data.test.SystemTestData;
import org.geoserver.wfs.WFS1XTestSupport;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.mock.web.MockHttpServletResponse;

import io.tileverse.parquetry.catalog.CatalogOptions;
import io.tileverse.parquetry.catalog.FilesetCatalog;
import io.tileverse.parquetry.geotools.data.CatalogDataStore;
import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStore;
import io.tileverse.parquetry.io.LocalFileSource;

/**
 * Exercises the {@code geoparquet} WFS GetFeature output format end to end: request handling, format_options plumbing,
 * and error reporting. The dataset name given to the read-back catalog is arbitrary; a single-file
 * {@link FilesetCatalog} exposes exactly one dataset regardless of the name chosen.
 */
public class GeoParquetOutputFormatIT extends WFS1XTestSupport {

    private static final String READBACK_DATASET_NAME = "readback";

    @Test
    public void getFeatureProducesAReadableGeoParquetFile() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "&outputFormat=geoparquet");
        assertEquals("application/vnd.apache.parquet", response.getContentType());
        assertEquals("attachment; filename=Polygons.parquet", response.getHeader("Content-Disposition"));

        Path file = writeToTempFile(response);
        assertReadableGeoParquetMatchesSource(file, SystemTestData.POLYGONS);
    }

    @Test
    public void mimeTypeAliasWorksAsOutputFormat() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS)
                        + "&outputFormat=application/vnd.apache.parquet");
        assertEquals("application/vnd.apache.parquet", response.getContentType());
    }

    @Test
    public void formatOptionsFlowIntoTheFile() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS)
                        + "&outputFormat=geoparquet&format_options=compression:snappy;rowGroupSize:1000");
        assertEquals("application/vnd.apache.parquet", response.getContentType());

        // Codec-level assertions live in the engine's own tests; this pins the option plumbing end to end by
        // confirming the file the chosen codec produced still reads back correctly.
        Path file = writeToTempFile(response);
        assertReadableGeoParquetMatchesSource(file, SystemTestData.POLYGONS);
    }

    @Test
    public void invalidFormatOptionReportsInvalidParameterValue() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS)
                        + "&outputFormat=geoparquet&format_options=compression:bogus");
        String body = response.getContentAsString();
        assertTrue(body, body.contains("InvalidParameterValue"));
    }

    @Test
    public void multiTypeNameRequestIsRejected() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "," + getLayerId(SystemTestData.LINES)
                        + "&outputFormat=geoparquet");
        String body = response.getContentAsString();
        assertTrue(body, body.contains("single query"));
    }

    @Test
    public void emptyResultProducesAValidEmptyFile() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "&outputFormat=geoparquet&maxFeatures=0");
        assertEquals("application/vnd.apache.parquet", response.getContentType());

        Path file = writeToTempFile(response);
        try (CatalogDataStore store = openReadBackStore(file)) {
            SimpleFeatureSource features = store.getFeatureSource(READBACK_DATASET_NAME);
            assertEquals(0, features.getCount(Query.ALL));
            assertTrue(
                    "an empty result still exposes the source schema",
                    attributeNames(features.getSchema()).contains("polygonProperty"));
        }
    }

    @Test
    public void capabilitiesAdvertiseTheFormat() throws Exception {
        String caps = getAsString("wfs?service=WFS&version=1.1.0&request=GetCapabilities");
        assertTrue(caps, caps.contains("<ows:Value>geoparquet</ows:Value>"));
    }

    /**
     * Reads the GeoParquet file back through the engine's own DataStore and checks it against the GeoServer layer it
     * was written from: same feature count, same attribute names, and the one feature's geometry matches exactly. The
     * {@code Polygons} CITE fixture holds exactly one feature, which keeps this comparison unambiguous.
     */
    private void assertReadableGeoParquetMatchesSource(Path file, QName layer) throws Exception {
        SimpleFeatureSource sourceFeatures = getFeatureSource(layer);

        try (CatalogDataStore store = openReadBackStore(file)) {
            SimpleFeatureSource readBackFeatures = store.getFeatureSource(READBACK_DATASET_NAME);

            assertEquals(sourceFeatures.getCount(Query.ALL), readBackFeatures.getCount(Query.ALL));
            assertEquals(attributeNames(sourceFeatures.getSchema()), attributeNames(readBackFeatures.getSchema()));

            Geometry sourceGeometry = (Geometry) readOnlyFeature(sourceFeatures).getDefaultGeometry();
            Geometry readBackGeometry =
                    (Geometry) readOnlyFeature(readBackFeatures).getDefaultGeometry();
            assertTrue(
                    "the read-back geometry matches the source geometry exactly",
                    readBackGeometry.equalsExact(sourceGeometry));
        }
    }

    private static CatalogDataStore openReadBackStore(Path file) {
        FilesetCatalog catalog = FilesetCatalog.open(
                LocalFileSource.file(file),
                CatalogOptions.builder().datasetName(READBACK_DATASET_NAME).build());
        return new GeoParquetDataStore(catalog);
    }

    /**
     * Writes the response body to a file in a directory of its own, rather than directly under the shared system temp
     * root: {@link io.tileverse.parquetry.io.LocalFileSource#file(Path)} lists its parent directory to open the
     * dataset, and the system temp root also holds unrelated directories other processes own, some unreadable to this
     * JVM.
     */
    private static Path writeToTempFile(MockHttpServletResponse response) throws IOException {
        Path directory = Files.createTempDirectory("geoparquet-output-format-it-");
        directory.toFile().deleteOnExit();
        Path file = directory.resolve("output.parquet");
        Files.write(file, response.getContentAsByteArray());
        file.toFile().deleteOnExit();
        return file;
    }

    private static SimpleFeature readOnlyFeature(SimpleFeatureSource source) throws IOException {
        try (SimpleFeatureIterator features = source.getFeatures(Query.ALL).features()) {
            return features.next();
        }
    }

    private static Set<String> attributeNames(SimpleFeatureType type) {
        Set<String> names = new LinkedHashSet<>();
        for (AttributeDescriptor descriptor : type.getAttributeDescriptors()) {
            names.add(descriptor.getLocalName());
        }
        return names;
    }
}
