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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.impl.AttributeTypeInfoImpl;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.DataUtilities;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.geoserver.web.storage.migration.MigrationNote;

class SchemaDifferencesTest {

    private static final String POINT_WITH_NAME = "geom:Point,name:String";

    private static final Set<String> BOX_COLUMNS = Set.of("geom_bbox");

    @ParameterizedTest
    @MethodSource("attributeChanges")
    void comparesAttributes(String before, String after, List<MigrationNote> expected) throws Exception {
        SimpleFeatureType beforeType = DataUtilities.createType("before", before);
        SimpleFeatureType afterType = DataUtilities.createType("after", after);

        List<MigrationNote> notes = SchemaDifferences.attributes(beforeType, afterType, BOX_COLUMNS);

        assertThat(notes).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> attributeChanges() {
        return Stream.of(
                Arguments.of(POINT_WITH_NAME, POINT_WITH_NAME, List.of()),
                Arguments.of(
                        POINT_WITH_NAME,
                        "geom:Point",
                        List.of(MigrationNote.difference("GeoParquetMigration.attributeMissing", "name"))),
                Arguments.of(
                        POINT_WITH_NAME,
                        "geom:Point,name:Integer",
                        List.of(MigrationNote.difference(
                                "GeoParquetMigration.attributeRetyped", "name", "String", "Integer"))),
                Arguments.of(
                        "geom:Point,geom_bbox:String",
                        "geom:Point",
                        List.of(MigrationNote.difference("GeoParquetMigration.boxColumnHidden", "geom_bbox"))),
                Arguments.of("geom:Point,name:java.lang.Object", POINT_WITH_NAME, List.of()),
                Arguments.of(
                        POINT_WITH_NAME,
                        "geom:Point,name:String,extra:Double",
                        List.of(MigrationNote.info("GeoParquetMigration.attributeAdded", "extra"))));
    }

    // a source expression reads the properties named by it
    @ParameterizedTest
    @MethodSource("savedSources")
    void savedSourcesMustExist(String name, String source, List<MigrationNote> expected) throws Exception {
        AttributeTypeInfo attribute = new AttributeTypeInfoImpl();
        attribute.setName(name);
        attribute.setSource(source);
        SimpleFeatureType after = DataUtilities.createType("after", POINT_WITH_NAME);

        List<MigrationNote> notes = SchemaDifferences.missingSources(List.of(attribute), after, BOX_COLUMNS);

        assertThat(notes).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> savedSources() {
        MigrationNote bboxMissing = MigrationNote.blocker("GeoParquetMigration.savedAttributeMissing", "bbox");
        MigrationNote labelMissing = MigrationNote.blocker("GeoParquetMigration.savedAttributeMissing", "label");
        MigrationNote boxColumn = MigrationNote.blocker("GeoParquetMigration.savedBoxColumn", "geom_bbox");
        MigrationNote labelOfBox = MigrationNote.blocker("GeoParquetMigration.savedBoxColumn", "label");
        return Stream.of(
                Arguments.of("name", null, List.of()),
                Arguments.of("bbox", null, List.of(bboxMissing)),
                Arguments.of("label", "name", List.of()),
                Arguments.of("label", "strToUpperCase(name)", List.of()),
                Arguments.of("label", "strConcat(name, ghost)", List.of(labelMissing)),
                Arguments.of("label", "ghost", List.of(labelMissing)),
                Arguments.of("label", "\"bbox\"", List.of(labelMissing)),
                Arguments.of("geom_bbox", null, List.of(boxColumn)),
                Arguments.of("label", "strConcat(name, geom_bbox)", List.of(labelOfBox)),
                Arguments.of("label", "strConcat(geom_bbox, ghost)", List.of(labelMissing)));
    }

    @Test
    void crsDifferenceIsNoted() throws Exception {
        CoordinateReferenceSystem saved = CRS.decode("EPSG:4326");
        CoordinateReferenceSystem found = CRS.decode("EPSG:3857");

        List<MigrationNote> notes = SchemaDifferences.crs(saved, found);

        assertThat(notes)
                .containsExactly(MigrationNote.difference("GeoParquetMigration.crsDiffers", "EPSG:4326", "EPSG:3857"));
    }

    @Test
    void nullSavedCrsIsSkipped() throws Exception {
        CoordinateReferenceSystem found = CRS.decode("EPSG:3857");

        List<MigrationNote> notes = SchemaDifferences.crs(null, found);

        assertThat(notes).isEmpty();
    }
}
