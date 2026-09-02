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

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import org.geoserver.config.GeoServer;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.data.simple.SimpleFeatureCollection;

import io.tileverse.parquetry.arrow.ipc.ArrowIpcWriter;
import io.tileverse.parquetry.columnar.ParquetRecordBatch;
import io.tileverse.parquetry.geotools.export.FeatureRecordBatches;

/**
 * Serves a WFS GetFeature response as an Arrow IPC stream, under the {@code arrow-ipc} format name and its
 * {@code application/vnd.apache.arrow.stream} MIME type alias. Serves one query per request: {@link WfsOutputFormats}
 * rejects a multi-typeName request and a complex-feature result. Write behavior comes entirely from the engine's fixed
 * defaults; the format takes no {@code format_options}.
 */
public class ArrowIpcOutputFormat extends WFSGetFeatureOutputFormat {

    static final String FORMAT_NAME = "arrow-ipc";
    static final String MIME_TYPE = "application/vnd.apache.arrow.stream";

    public ArrowIpcOutputFormat(GeoServer gs) {
        super(gs, new LinkedHashSet<>(List.of(FORMAT_NAME, MIME_TYPE)));
    }

    @Override
    public String getMimeType(Object value, Operation operation) {
        return MIME_TYPE;
    }

    @Override
    public String getPreferredDisposition(Object value, Operation operation) {
        return DISPOSITION_ATTACH;
    }

    @Override
    protected String getExtension(FeatureCollectionResponse response) {
        return "arrows";
    }

    @Override
    public String getCapabilitiesElementName() {
        return FORMAT_NAME;
    }

    @Override
    protected void write(FeatureCollectionResponse featureCollection, OutputStream output, Operation getFeature)
            throws IOException, ServiceException {
        SimpleFeatureCollection features = WfsOutputFormats.singleCollection(featureCollection, FORMAT_NAME);
        FeatureRecordBatches bridge = FeatureRecordBatches.forType(features.getSchema());
        try (Stream<ParquetRecordBatch> batches = bridge.batches(features, FeatureRecordBatches.DEFAULT_BATCH_ROWS)) {
            ArrowIpcWriter.write(bridge.parquetSchema(), bridge.geoMetadata(), batches, output);
        }
    }
}
