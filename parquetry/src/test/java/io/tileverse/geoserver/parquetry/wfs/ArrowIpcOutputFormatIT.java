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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.wfs.WFS1XTestSupport;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Exercises the {@code arrow-ipc} WFS GetFeature output format end to end: request handling, the Arrow schema produced
 * from the source feature type, and error reporting. The format takes no {@code format_options}; every write knob comes
 * from the engine's fixed defaults.
 */
public class ArrowIpcOutputFormatIT extends WFS1XTestSupport {

    @Test
    public void getFeatureProducesAReadableArrowStream() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "&outputFormat=arrow-ipc");
        assertEquals("application/vnd.apache.arrow.stream", response.getContentType());
        assertEquals("attachment; filename=Polygons.arrows", response.getHeader("Content-Disposition"));

        SimpleFeatureSource sourceFeatures = getFeatureSource(SystemTestData.POLYGONS);
        SimpleFeatureType sourceType = sourceFeatures.getSchema();
        long expectedCount = sourceFeatures.getCount(Query.ALL);

        try (BufferAllocator allocator = new RootAllocator();
                ArrowStreamReader reader =
                        new ArrowStreamReader(new ByteArrayInputStream(response.getContentAsByteArray()), allocator)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            Schema arrowSchema = root.getSchema();

            assertEquals(sourceAttributeNames(sourceType), arrowFieldNames(arrowSchema));
            assertGeometryFieldIsGeoArrowWkb(
                    arrowSchema, sourceType.getGeometryDescriptor().getLocalName());

            long rows = 0;
            while (reader.loadNextBatch()) {
                rows += root.getRowCount();
            }
            assertEquals(expectedCount, rows);
        }
    }

    @Test
    public void emptyResultProducesAValidEmptyStream() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "&outputFormat=arrow-ipc&maxFeatures=0");
        assertEquals("application/vnd.apache.arrow.stream", response.getContentType());

        try (BufferAllocator allocator = new RootAllocator();
                ArrowStreamReader reader =
                        new ArrowStreamReader(new ByteArrayInputStream(response.getContentAsByteArray()), allocator)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertTrue(
                    "an empty result still exposes the source schema",
                    arrowFieldNames(root.getSchema()).contains("polygonProperty"));

            long rows = 0;
            while (reader.loadNextBatch()) {
                rows += root.getRowCount();
            }
            assertEquals(0, rows);
        }
    }

    @Test
    public void multiTypeNameRequestIsRejected() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse("wfs?service=WFS&version=1.1.0&request=GetFeature&typeName="
                        + getLayerId(SystemTestData.POLYGONS) + "," + getLayerId(SystemTestData.LINES)
                        + "&outputFormat=arrow-ipc");
        String body = response.getContentAsString();
        assertTrue(body, body.contains("single query"));
    }

    @Test
    public void capabilitiesAdvertiseTheFormat() throws Exception {
        String caps = getAsString("wfs?service=WFS&version=1.1.0&request=GetCapabilities");
        assertTrue(caps, caps.contains("<ows:Value>arrow-ipc</ows:Value>"));
    }

    /** Asserts the field named {@code geometryFieldName} has the GeoArrow WKB extension metadata. */
    private static void assertGeometryFieldIsGeoArrowWkb(Schema arrowSchema, String geometryFieldName) {
        Field geometryField = arrowSchema.findField(geometryFieldName);
        assertEquals("geoarrow.wkb", geometryField.getMetadata().get("ARROW:extension:name"));
    }

    /** The source feature type's attribute names, in declaration order. */
    private static List<String> sourceAttributeNames(SimpleFeatureType featureType) {
        List<String> names = new ArrayList<>();
        for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
            names.add(descriptor.getLocalName());
        }
        return names;
    }

    /** The Arrow schema's field names, in declaration order. */
    private static List<String> arrowFieldNames(Schema arrowSchema) {
        List<String> names = new ArrayList<>();
        for (Field field : arrowSchema.getFields()) {
            names.add(field.getName());
        }
        return names;
    }
}
