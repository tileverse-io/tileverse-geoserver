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

import org.geoserver.config.GeoServer;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.data.simple.SimpleFeatureCollection;

import io.tileverse.parquetry.data.WriteOptions;
import io.tileverse.parquetry.geotools.export.GeoParquetExporter;

/**
 * Serves a WFS GetFeature response as a single GeoParquet file, under the {@code geoparquet} format name and its
 * {@code application/vnd.apache.parquet} MIME type alias. Serves one query per request: {@link WfsOutputFormats}
 * rejects a multi-typeName request and a complex-feature result. Write knobs (Parquet page format version, row group
 * size, compression codec) come from the request's {@code format_options}, parsed by {@link GeoParquetFormatOptions}.
 */
public class GeoParquetOutputFormat extends WFSGetFeatureOutputFormat {

    static final String FORMAT_NAME = "geoparquet";
    static final String MIME_TYPE = "application/vnd.apache.parquet";

    public GeoParquetOutputFormat(GeoServer gs) {
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
        return "parquet";
    }

    @Override
    public String getCapabilitiesElementName() {
        return FORMAT_NAME;
    }

    @Override
    protected void write(FeatureCollectionResponse featureCollection, OutputStream output, Operation getFeature)
            throws IOException, ServiceException {
        SimpleFeatureCollection features = WfsOutputFormats.singleCollection(featureCollection, FORMAT_NAME);
        WriteOptions options = GeoParquetFormatOptions.parse(WfsOutputFormats.formatOptions(getFeature));
        GeoParquetExporter.export(features, output, options);
    }
}
