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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wfs.request.GetFeatureRequest;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;

/**
 * Request-shape helpers shared by the WFS GetFeature output formats: pulling the single requested collection out of a
 * multi-query response, reading the raw {@code format_options} map off the request, and looking up one option by name
 * without regard to case.
 */
final class WfsOutputFormats {

    private WfsOutputFormats() {}

    /**
     * Returns the single {@link SimpleFeatureCollection} a GetFeature response holds, for output formats that serve one
     * query per request. Rejects a multi-query request and a complex-feature result with a {@link ServiceException}
     * naming {@code formatName}.
     */
    static SimpleFeatureCollection singleCollection(FeatureCollectionResponse response, String formatName) {
        List<FeatureCollection> collections = response.getFeatures();
        if (collections.size() != 1) {
            throw new ServiceException(
                    formatName + " serves a single query per request; this request produced " + collections.size()
                            + " result collections",
                    ServiceException.INVALID_PARAMETER_VALUE,
                    "typeName");
        }
        if (!(collections.get(0) instanceof SimpleFeatureCollection simple)) {
            throw new ServiceException(formatName + " does not support complex features");
        }
        return simple;
    }

    /** Returns the request's {@code format_options} map, or an empty map when the request has none. */
    static Map<String, ?> formatOptions(Operation getFeature) {
        GetFeatureRequest request = GetFeatureRequest.adapt(getFeature.getParameters()[0]);
        Map<String, ?> options = request == null ? null : request.getFormatOptions();
        return options == null ? Map.of() : options;
    }

    /**
     * Looks up {@code name} in {@code options} matching the key case-insensitively. A null value is treated as absent,
     * the same as the key not being present at all.
     */
    static Optional<String> option(Map<String, ?> options, String name) {
        return options.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(String.valueOf(entry.getKey())))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .findFirst();
    }
}
