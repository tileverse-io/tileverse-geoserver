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
package io.tileverse.geoserver.parquetry.migration;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.geotools.api.data.DataStore;

import io.tileverse.parquetry.dataset.GeoParquetDataset;
import io.tileverse.parquetry.dataset.ParquetDataset;
import io.tileverse.parquetry.geotools.data.CatalogDataStore;
import io.tileverse.parquetry.schema.ColumnPath;
import io.tileverse.parquetry.schema.ParquetSchema;
import io.tileverse.parquetry.schema.geo.geoparquet.BboxCovering;
import io.tileverse.parquetry.schema.geo.geoparquet.BboxCoverings;
import io.tileverse.parquetry.schema.geo.geoparquet.GeoParquetMetadata;

/**
 * The bounding box columns of a layer of the Parquetry store. A GeoParquet file may declare a struct of four numbers as
 * the covering of a geometry column. The Parquetry store reads that struct to filter rows and does not publish it as an
 * attribute, although the community store does.
 */
final class BoxColumns {

    private BoxColumns() {}

    /** The names of the box columns of a type, empty when the store does not read GeoParquet metadata. */
    static Set<String> of(DataStore dataStore, String typeName) {
        if (!(dataStore instanceof CatalogDataStore catalogStore)) {
            return Set.of();
        }
        ParquetDataset dataset = catalogStore.catalog().dataset(typeName);
        if (!(dataset instanceof GeoParquetDataset geoDataset)) {
            return Set.of();
        }
        Optional<GeoParquetMetadata> metadata = geoDataset.geoMetadata();
        if (metadata.isEmpty()) {
            return Set.of();
        }
        return namesIn(dataset.schema(), metadata.orElseThrow());
    }

    /** A covering of top-level columns is published as attributes: only a covering struct is hidden. */
    private static Set<String> namesIn(ParquetSchema schema, GeoParquetMetadata metadata) {
        Set<String> names = new LinkedHashSet<>();
        for (String geometryColumn : metadata.columns().keySet()) {
            ColumnPath column = ColumnPath.of(geometryColumn);
            Optional<BboxCovering> covering = BboxCoverings.coveringOf(column, schema, metadata);
            covering.map(BboxCovering::xmin)
                    .filter(leaf -> leaf.numParts() > 1)
                    .ifPresent(leaf -> names.add(leaf.part(0)));
        }
        return names;
    }
}
