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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import io.tileverse.storage.RangeReader;

import io.tileverse.geoserver.web.storage.browser.ColumnSort;
import io.tileverse.geoserver.web.storage.browser.ColumnValue;
import io.tileverse.geoserver.web.storage.browser.FileColumn;
import io.tileverse.geoserver.web.storage.browser.FileColumns;

import io.tileverse.parquetry.data.ParquetFileReader;
import io.tileverse.parquetry.data.RowGroupSummary;
import io.tileverse.parquetry.format.LogicalType;
import io.tileverse.parquetry.io.ByteRangeSource;
import io.tileverse.parquetry.schema.ParquetSchema;
import io.tileverse.parquetry.schema.SchemaNode;
import io.tileverse.parquetry.schema.geo.ParquetCrs;
import io.tileverse.parquetry.schema.geo.geoparquet.GeoColumn;
import io.tileverse.parquetry.schema.geo.geoparquet.GeoParquetMetadata;
import io.tileverse.parquetry.schema.geo.projjson.CoordinateReferenceSystem;
import io.tileverse.parquetry.schema.geo.projjson.Identifier;

/**
 * The Parquet columns of the storage browser, read from the footer of a {@code .parquet} file: its rows and row groups,
 * its primary geometry column, the CRS of that column as the engine reads it, and the GeoParquet version.
 */
public final class ParquetFileColumns implements FileColumns {

    private static final long serialVersionUID = 1L;

    static final String ROWS = "rows";
    static final String ROW_GROUPS = "rowGroups";
    static final String GEOMETRY = "geometry";
    static final String CRS = "crs";
    static final String GEOPARQUET = "geoparquet";

    private static final String GEO_KEY = "geo";
    private static final String NONE = "none";
    private static final String DEFAULT_CRS = "OGC:CRS84";
    private static final String DEFAULT_ENCODING = "WKB";

    private static final List<FileColumn> COLUMNS = List.of(
            new FileColumn(ROWS, "ParquetFileColumns.rows", ColumnSort.NUMBER),
            new FileColumn(ROW_GROUPS, "ParquetFileColumns.rowGroups", ColumnSort.NUMBER),
            new FileColumn(GEOMETRY, "ParquetFileColumns.geometry", ColumnSort.TEXT),
            new FileColumn(CRS, "ParquetFileColumns.crs", ColumnSort.TEXT),
            new FileColumn(GEOPARQUET, "ParquetFileColumns.geoparquet", ColumnSort.TEXT));

    @Override
    public List<FileColumn> columns() {
        return COLUMNS;
    }

    @Override
    public boolean reads(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".parquet");
    }

    /** Reads the footer only, through the reader of the browser: the source borrows it and leaves it open. */
    @Override
    public Map<String, ColumnValue> read(RangeReader file) throws IOException {
        try (ByteRangeSource source = ByteRangeSource.of(file)) {
            ParquetFileReader reader = ParquetFileReader.open(source);
            return valuesOf(reader);
        }
    }

    private static Map<String, ColumnValue> valuesOf(ParquetFileReader reader) {
        Map<String, ColumnValue> values = new LinkedHashMap<>();
        List<RowGroupSummary> rowGroups = reader.rowGroups();
        long rows = rowGroups.stream().mapToLong(RowGroupSummary::rowCount).sum();
        values.put(ROWS, ColumnValue.count(rows));
        values.put(ROW_GROUPS, ColumnValue.count(rowGroups.size()));
        values.putAll(geometryValuesOf(reader));
        return values;
    }

    /** The geometry, CRS and GeoParquet cells, from the {@code geo} value or else from the schema. */
    private static Map<String, ColumnValue> geometryValuesOf(ParquetFileReader reader) {
        ParquetSchema schema = reader.schema();
        String geoJson = reader.keyValueMetadata().get(GEO_KEY);
        if (geoJson == null) {
            return schemaValuesOf(schema);
        }
        return geoValuesOf(geoJson, schema);
    }

    /**
     * A file without a {@code geo} value is no GeoParquet file, though a column may have a native geometry type. That
     * type gives the geometry and the CRS.
     */
    private static Map<String, ColumnValue> schemaValuesOf(ParquetSchema schema) {
        Optional<SchemaNode.Primitive> column = firstGeometryColumn(schema);
        String geometry = column.map(ParquetFileColumns::nativeGeometryOf).orElse(NONE);
        Optional<String> crs = column.flatMap(ParquetFileColumns::nativeCrsOf);
        Map<String, ColumnValue> values = new LinkedHashMap<>();
        values.put(GEOMETRY, ColumnValue.text(geometry));
        crs.ifPresent(name -> values.put(CRS, ColumnValue.text(name)));
        values.put(GEOPARQUET, ColumnValue.text(NONE));
        return values;
    }

    private static Optional<SchemaNode.Primitive> firstGeometryColumn(ParquetSchema schema) {
        for (SchemaNode node : schema.root().children()) {
            if (node instanceof SchemaNode.Primitive primitive && isGeometry(primitive.logicalType())) {
                return Optional.of(primitive);
            }
        }
        return Optional.empty();
    }

    private static boolean isGeometry(Optional<LogicalType> logicalType) {
        if (logicalType.isEmpty()) {
            return false;
        }
        LogicalType type = logicalType.get();
        return type instanceof LogicalType.Geometry || type instanceof LogicalType.Geography;
    }

    private static String nativeGeometryOf(SchemaNode.Primitive column) {
        return column.name() + " (" + DEFAULT_ENCODING + ")";
    }

    /** The CRS of a column with a geometry type, or empty when the column has another type. */
    private static Optional<String> nativeCrsOf(SchemaNode column) {
        if (column instanceof SchemaNode.Primitive primitive) {
            return primitive.logicalType().flatMap(ParquetFileColumns::crsOfType);
        }
        return Optional.empty();
    }

    /** The CRS of a Geometry or Geography type: OGC:CRS84 when the type declares none. */
    private static Optional<String> crsOfType(LogicalType type) {
        Optional<ParquetCrs> declared;
        if (type instanceof LogicalType.Geometry geometry) {
            declared = geometry.crs();
        } else if (type instanceof LogicalType.Geography geography) {
            declared = geography.crs();
        } else {
            return Optional.empty();
        }
        return Optional.of(declared.map(ParquetFileColumns::nameOf).orElse(DEFAULT_CRS));
    }

    /**
     * The name of a native CRS, by form:
     *
     * <ul>
     *   <li>inline PROJJSON: its identifier as {@code authority:code}, else its name, else {@code PROJJSON};
     *   <li>authority and code: {@code authority:code};
     *   <li>SRID: {@code EPSG:n} when the engine derives an EPSG code from it, else {@code srid:n};
     *   <li>PROJJSON key: {@code projjson:key}.
     * </ul>
     *
     * Package-private for the tests.
     */
    static String nameOf(ParquetCrs crs) {
        return switch (crs) {
            case ParquetCrs.Inline inline -> projjsonNameOf(inline.projjson());
            case ParquetCrs.AuthorityCode authority -> authority.authority() + ":" + authority.code();
            case ParquetCrs.Srid srid -> sridNameOf(srid);
            case ParquetCrs.ProjjsonKey key -> ParquetCrs.PROJJSON_PREFIX + key.key();
        };
    }

    private static String projjsonNameOf(CoordinateReferenceSystem projjson) {
        Optional<Identifier> id = projjson.id();
        if (id.isPresent()) {
            Identifier identifier = id.get();
            return identifier.authority() + ":" + identifier.code();
        }
        return projjson.name().orElse("PROJJSON");
    }

    private static String sridNameOf(ParquetCrs.Srid srid) {
        OptionalInt epsgCode = srid.epsgCode();
        if (epsgCode.isPresent()) {
            return "EPSG:" + epsgCode.getAsInt();
        }
        return ParquetCrs.SRID_PREFIX + srid.id();
    }

    /**
     * The geometry, CRS and GeoParquet cells of a {@code geo} value, given as its JSON text, for a file with the given
     * schema. Returns an empty map when the text is not GeoParquet metadata or names no primary column. The file then
     * still shows its row counts: the columns describe a file, they do not validate it. Package-private to let the
     * tests pass explicit texts instead of Parquet files.
     */
    static Map<String, ColumnValue> geoValuesOf(String geoJson, ParquetSchema schema) {
        Optional<GeoParquetMetadata> usable = usableMetadataOf(geoJson);
        if (usable.isEmpty()) {
            return Map.of();
        }
        GeoParquetMetadata metadata = usable.get();
        String geometry = geometryOf(metadata);
        Optional<String> crs = crsOf(metadata, schema);
        String version = Objects.requireNonNullElse(metadata.version(), NONE);
        Map<String, ColumnValue> values = new LinkedHashMap<>();
        values.put(GEOMETRY, ColumnValue.text(geometry));
        crs.ifPresent(name -> values.put(CRS, ColumnValue.text(name)));
        values.put(GEOPARQUET, ColumnValue.text(version));
        return values;
    }

    private static Optional<GeoParquetMetadata> usableMetadataOf(String geoJson) {
        Optional<GeoParquetMetadata> parsed = parsedMetadataOf(geoJson);
        return parsed.filter(ParquetFileColumns::hasPrimaryColumn);
    }

    private static Optional<GeoParquetMetadata> parsedMetadataOf(String geoJson) {
        try {
            return Optional.of(GeoParquetMetadata.parse(geoJson));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean hasPrimaryColumn(GeoParquetMetadata metadata) {
        String primary = metadata.primaryColumn();
        return primary != null && !primary.isBlank();
    }

    private static String geometryOf(GeoParquetMetadata metadata) {
        Optional<GeoColumn> primary = primaryColumnOf(metadata);
        String encoding = primary.flatMap(GeoColumn::encoding).orElse(DEFAULT_ENCODING);
        return metadata.primaryColumn() + " (" + encoding + ")";
    }

    private static Optional<GeoColumn> primaryColumnOf(GeoParquetMetadata metadata) {
        return Optional.ofNullable(metadata.columns().get(metadata.primaryColumn()));
    }

    /**
     * The CRS of the primary column as the engine uses it: the CRS of the geometry type of the column in the schema.
     * The engine builds that type from the {@code geo} value in a GeoParquet 1.x file. A native type wins over the
     * {@code geo} value, whatever the version of the file. A column that is missing from the schema, or has no geometry
     * type, keeps the CRS of the {@code geo} value.
     */
    private static Optional<String> crsOf(GeoParquetMetadata metadata, ParquetSchema schema) {
        Optional<SchemaNode> column = columnNamed(schema, metadata.primaryColumn());
        Optional<String> engineCrs = column.flatMap(ParquetFileColumns::nativeCrsOf);
        if (engineCrs.isPresent()) {
            return engineCrs;
        }
        return geoCrsOf(metadata);
    }

    private static Optional<SchemaNode> columnNamed(ParquetSchema schema, String name) {
        for (SchemaNode node : schema.root().children()) {
            if (node.name().equals(name)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> geoCrsOf(GeoParquetMetadata metadata) {
        Optional<GeoColumn> primary = primaryColumnOf(metadata);
        return primary.map(ParquetFileColumns::geoCrsNameOf);
    }

    /** The CRS of a column in the {@code geo} value: OGC:CRS84 when the column declares none. */
    private static String geoCrsNameOf(GeoColumn column) {
        Optional<CoordinateReferenceSystem> crs = column.crs();
        return crs.map(ParquetFileColumns::projjsonNameOf).orElse(DEFAULT_CRS);
    }
}
