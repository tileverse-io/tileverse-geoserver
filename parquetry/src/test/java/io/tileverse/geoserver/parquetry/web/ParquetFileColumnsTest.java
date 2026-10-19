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
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.RangeReader;
import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageFactory;

import io.tileverse.geoserver.web.storage.browser.ColumnSort;
import io.tileverse.geoserver.web.storage.browser.ColumnValue;
import io.tileverse.geoserver.web.storage.browser.FileColumn;

import io.tileverse.parquetry.format.LogicalType;
import io.tileverse.parquetry.schema.ParquetSchema;
import io.tileverse.parquetry.schema.PrimitiveKind;
import io.tileverse.parquetry.schema.Repetition;
import io.tileverse.parquetry.schema.SchemaNode;
import io.tileverse.parquetry.schema.geo.ParquetCrs;
import io.tileverse.parquetry.schema.geo.geoparquet.GeoParquetMetadata;
import io.tileverse.parquetry.schema.geo.projjson.CoordinateReferenceSystem;

class ParquetFileColumnsTest {

    private final ParquetFileColumns columns = new ParquetFileColumns();

    @Test
    void readsParquetFilesOnly() {
        assertThat(columns.reads("points.parquet")).isTrue();
        assertThat(columns.reads("POINTS.PARQUET")).isTrue();
        assertThat(columns.reads("catalog.json")).isFalse();
    }

    @Test
    void readsRowsAndRowGroups() throws Exception {
        Map<String, ColumnValue> values = read("points_3857.parquet");

        assertThat(values.get(ParquetFileColumns.ROWS)).isEqualTo(ColumnValue.count(3));
        assertThat(values.get(ParquetFileColumns.ROW_GROUPS)).isEqualTo(ColumnValue.count(2));
    }

    @Test
    void readsTheGeoParquetMetadata() throws Exception {
        Map<String, ColumnValue> values = read("points_3857.parquet");

        assertThat(values.get(ParquetFileColumns.GEOMETRY)).isEqualTo(ColumnValue.text("geometry (WKB)"));
        assertThat(values.get(ParquetFileColumns.CRS)).isEqualTo(ColumnValue.text("EPSG:3857"));
        assertThat(values.get(ParquetFileColumns.GEOPARQUET)).isEqualTo(ColumnValue.text("1.1.0"));
    }

    @Test
    void anUndeclaredCrsIsCrs84() throws Exception {
        Map<String, ColumnValue> values = read("points_4326.parquet");

        assertThat(values.get(ParquetFileColumns.CRS)).isEqualTo(ColumnValue.text("OGC:CRS84"));
    }

    @Test
    void aPlainTableHasNoGeometry() throws Exception {
        Map<String, ColumnValue> values = read("table.parquet");

        assertThat(values.get(ParquetFileColumns.ROWS)).isEqualTo(ColumnValue.count(2));
        assertThat(values.get(ParquetFileColumns.GEOMETRY)).isEqualTo(ColumnValue.text("none"));
        assertThat(values.get(ParquetFileColumns.GEOPARQUET)).isEqualTo(ColumnValue.text("none"));
        assertThat(values).doesNotContainKey(ParquetFileColumns.CRS);
    }

    @Test
    void readsANativeOnlyFile() throws Exception {
        Map<String, ColumnValue> values = read("points_3857_native.parquet");

        assertThat(values.get(ParquetFileColumns.GEOMETRY)).isEqualTo(ColumnValue.text("geometry (Geometry)"));
        assertThat(values.get(ParquetFileColumns.CRS)).isEqualTo(ColumnValue.text("EPSG:3857"));
        assertThat(values.get(ParquetFileColumns.GEOPARQUET)).isEqualTo(ColumnValue.text("none"));
    }

    // parquetry's dual mode writes the native type with a 1.1 geo value: the cell names the type, the version stays
    @Test
    void nativeTypeShowsInADualFile() throws Exception {
        Map<String, ColumnValue> values = read("points_3857_both.parquet");

        assertThat(values.get(ParquetFileColumns.GEOMETRY)).isEqualTo(ColumnValue.text("geometry (Geometry)"));
        assertThat(values.get(ParquetFileColumns.GEOPARQUET)).isEqualTo(ColumnValue.text("1.1.0"));
    }

    // a GeoParquet 2.0 writer may leave the CRS out of the geo value: the native geometry type holds it
    @Test
    void aGeoParquet2FileShowsItsCrs(@TempDir Path folder) throws Exception {
        byte[] both = Files.readAllBytes(pathOf("points_3857_both.parquet"));
        byte[] withoutCrs = replaced(both, "\"crs\"", "\"crx\"");
        byte[] geoParquet2 = replaced(withoutCrs, "\"version\":\"1.1.0\"", "\"version\":\"2.0.0\"");

        Map<String, ColumnValue> values = readCopy(folder, geoParquet2);

        assertThat(values.get(ParquetFileColumns.CRS)).isEqualTo(ColumnValue.text("EPSG:3857"));
        assertThat(values.get(ParquetFileColumns.GEOPARQUET)).isEqualTo(ColumnValue.text("2.0.0"));
    }

    // the browser owns the reader and closes it after the read
    @Test
    void leavesTheReaderOpen() throws Exception {
        String name = "points_3857.parquet";
        Path folder = pathOf(name).getParent();
        try (Storage storage = StorageFactory.open(folder.toUri());
                RangeReader reader = storage.openRangeReader(name)) {
            columns.read(reader);

            ByteBuffer start = ByteBuffer.allocate(4);
            int bytesRead = reader.readRange(0, 4, start);

            assertThat(bytesRead).isEqualTo(4);
        }
    }

    // a NUMBER column sorts by its numbers, the other columns by their text
    @Test
    void sortsMatchTheValues() throws Exception {
        Map<String, ColumnValue> values = read("points_3857.parquet");

        for (FileColumn column : columns.columns()) {
            assertThat(values).containsKey(column.key());
            boolean hasNumber = values.get(column.key()).number().isPresent();
            boolean sortedAsNumber = column.sort() == ColumnSort.NUMBER;
            assertThat(hasNumber).as(column.key()).isEqualTo(sortedAsNumber);
        }
    }

    // a missing label shows the raw column key as the header
    @Test
    void everyColumnHasATitle() throws IOException {
        Properties labels = moduleLabels();

        for (FileColumn column : columns.columns()) {
            String title = labels.getProperty(column.titleKey());
            assertThat(title).as(column.titleKey()).isNotBlank();
        }
    }

    // a value that cannot be used gives no cell, not even "none"
    @ParameterizedTest
    @MethodSource("geoValues")
    void readsTheGeoValue(String geoJson, ParquetSchema schema, Map<String, ColumnValue> expected) {
        Map<String, ColumnValue> values =
                ParquetFileColumns.geoValuesOf(geoJson, new ParquetFileColumns.FileSchemas(schema, schema));

        assertThat(values).isEqualTo(expected);
    }

    static Stream<Arguments> geoValues() {
        String noPrimaryColumn = """
                {"version": "1.1.0", "columns": {"geometry": {"encoding": "WKB"}}}""";
        String blankPrimaryColumn = """
                {"version": "1.1.0", "primary_column": " ", "columns": {"geometry": {"encoding": "WKB"}}}""";
        String valid = """
                {"version": "1.1.0", "primary_column": "geometry", "columns": {"geometry": {"encoding": "WKB"}}}""";
        String withoutVersion = """
                {"primary_column": "geometry", "columns": {"geometry": {"encoding": "WKB"}}}""";
        String unknownPrimaryColumn = """
                {"version": "1.1.0", "primary_column": "geom", "columns": {"geometry": {"encoding": "WKB"}}}""";
        String withCrs = """
                {"version": "1.1.0", "primary_column": "geometry", "columns": {"geometry": {"encoding": "WKB",
                "crs": {"type": "GeographicCRS", "name": "My CRS"}}}}""";
        Map<String, ColumnValue> noCrs = Map.of(
                ParquetFileColumns.GEOMETRY, ColumnValue.text("geom (WKB)"),
                ParquetFileColumns.GEOPARQUET, ColumnValue.text("1.1.0"));
        ParquetSchema noColumns = plainColumns();
        ParquetSchema plainGeometry = plainColumns("geometry");
        ParquetSchema nativeGeography = geographyColumn("geometry");
        return Stream.of(
                Arguments.argumentSet("no primary column", noPrimaryColumn, noColumns, Map.of()),
                Arguments.argumentSet("blank primary column", blankPrimaryColumn, noColumns, Map.of()),
                Arguments.argumentSet("not an object", "[1, 2, 3]", noColumns, Map.of()),
                Arguments.argumentSet("not JSON", "this is not json", noColumns, Map.of()),
                Arguments.argumentSet("a valid value", valid, noColumns, cells("geometry (WKB)", "OGC:CRS84", "1.1.0")),
                Arguments.argumentSet(
                        "no version", withoutVersion, noColumns, cells("geometry (WKB)", "OGC:CRS84", "none")),
                Arguments.argumentSet("unknown primary column", unknownPrimaryColumn, noColumns, noCrs),
                Arguments.argumentSet(
                        "a CRS, column missing from the schema",
                        withCrs,
                        noColumns,
                        cells("geometry (WKB)", "My CRS", "1.1.0")),
                Arguments.argumentSet(
                        "a CRS, column without a geometry type",
                        withCrs,
                        plainGeometry,
                        cells("geometry (WKB)", "My CRS", "1.1.0")),
                Arguments.argumentSet(
                        "a native geography column",
                        valid,
                        nativeGeography,
                        cells("geometry (Geography)", "OGC:CRS84", "1.1.0")));
    }

    private static ParquetSchema plainColumns(String... names) {
        List<SchemaNode> columns = new ArrayList<>();
        for (String name : names) {
            columns.add(byteArrayColumn(name, Optional.empty()));
        }
        return schemaOf(columns);
    }

    private static ParquetSchema geographyColumn(String name) {
        LogicalType geography = new LogicalType.Geography(Optional.empty(), Optional.empty());
        SchemaNode column = byteArrayColumn(name, Optional.of(geography));
        return schemaOf(List.of(column));
    }

    private static SchemaNode byteArrayColumn(String name, Optional<LogicalType> logicalType) {
        return new SchemaNode.Primitive(
                name, Repetition.OPTIONAL, PrimitiveKind.BYTE_ARRAY, OptionalInt.empty(), logicalType, 1);
    }

    private static ParquetSchema schemaOf(List<SchemaNode> columns) {
        SchemaNode.Group root = new SchemaNode.Group("schema", Repetition.REQUIRED, columns, Optional.empty(), 0);
        return new ParquetSchema(root);
    }

    private static Map<String, ColumnValue> cells(String geometry, String crs, String version) {
        return Map.of(
                ParquetFileColumns.GEOMETRY, ColumnValue.text(geometry),
                ParquetFileColumns.CRS, ColumnValue.text(crs),
                ParquetFileColumns.GEOPARQUET, ColumnValue.text(version));
    }

    @ParameterizedTest
    @MethodSource("nativeCrsForms")
    void namesTheNativeCrs(ParquetCrs crs, String expected) {
        assertThat(ParquetFileColumns.nameOf(crs)).isEqualTo(expected);
    }

    static Stream<Arguments> nativeCrsForms() {
        CoordinateReferenceSystem withId = projjson("""
                {"type": "GeographicCRS", "name": "WGS 84", "id": {"authority": "EPSG", "code": 4326}}""");
        CoordinateReferenceSystem withName = projjson("""
                {"type": "GeographicCRS", "name": "My CRS"}""");
        CoordinateReferenceSystem withNeither = projjson("""
                {"type": "GeographicCRS"}""");
        return Stream.of(
                Arguments.argumentSet("inline with an identifier", ParquetCrs.inline(withId), "EPSG:4326"),
                Arguments.argumentSet("inline with a name", ParquetCrs.inline(withName), "My CRS"),
                Arguments.argumentSet("inline with neither", ParquetCrs.inline(withNeither), "PROJJSON"),
                Arguments.argumentSet("authority and code", new ParquetCrs.AuthorityCode("OGC", "CRS84"), "OGC:CRS84"),
                Arguments.argumentSet("an SRID", new ParquetCrs.Srid(3857), "EPSG:3857"),
                Arguments.argumentSet("an undefined SRID", new ParquetCrs.Srid(0), "srid:0"),
                Arguments.argumentSet("a PROJJSON key", new ParquetCrs.ProjjsonKey("mercator"), "projjson:mercator"));
    }

    private static CoordinateReferenceSystem projjson(String json) {
        return GeoParquetMetadata.DEFAULT_MAPPER.readValue(json, CoordinateReferenceSystem.class);
    }

    // GDAL cannot write these geo values: the test patches a copy of its output
    @ParameterizedTest
    @MethodSource("oddGeoKeys")
    void anOddGeoValueKeepsTheRows(String oddKey, @TempDir Path folder) throws Exception {
        String primaryColumnKey = "\"primary_column\"";
        byte[] original = Files.readAllBytes(pathOf("points_4326.parquet"));
        byte[] odd = replaced(original, primaryColumnKey, oddKey);

        Map<String, ColumnValue> values = readCopy(folder, odd);

        assertThat(values).containsOnlyKeys(ParquetFileColumns.ROWS, ParquetFileColumns.ROW_GROUPS);
    }

    static Stream<Arguments> oddGeoKeys() {
        String renamed = "\"primary_columX\"";
        String unquoted = " primary_column ";
        return Stream.of(
                Arguments.argumentSet("no primary column", renamed), Arguments.argumentSet("not JSON", unquoted));
    }

    private Map<String, ColumnValue> read(String fixture) throws IOException, URISyntaxException {
        return read(pathOf(fixture).getParent(), fixture);
    }

    private Map<String, ColumnValue> read(Path folder, String name) throws IOException {
        try (Storage storage = StorageFactory.open(folder.toUri());
                RangeReader reader = storage.openRangeReader(name)) {
            return columns.read(reader);
        }
    }

    private Map<String, ColumnValue> readCopy(Path folder, byte[] contents) throws IOException {
        Files.write(folder.resolve("copy.parquet"), contents);
        return read(folder, "copy.parquet");
    }

    private Path pathOf(String fixture) throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/" + fixture).toURI());
    }

    private static Properties moduleLabels() throws IOException {
        Properties labels = new Properties();
        try (InputStream in = ParquetFileColumns.class.getResourceAsStream("/GeoServerApplication.properties")) {
            labels.load(in);
        }
        return labels;
    }

    /** The texts have the same length because the file ends with the length of its footer. */
    private static byte[] replaced(byte[] bytes, String from, String to) {
        assertThat(to).hasSameSizeAs(from);
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(text).contains(from);
        return text.replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
    }
}
