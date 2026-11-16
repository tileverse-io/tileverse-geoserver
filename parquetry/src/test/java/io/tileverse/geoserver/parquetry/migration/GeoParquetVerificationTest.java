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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.impl.AttributeTypeInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Point;

import io.tileverse.geoserver.parquetry.migration.GroupingAttempts.Candidate;
import io.tileverse.geoserver.web.storage.migration.MigrationNote;

class GeoParquetVerificationTest {

    private static final String SINGLE_FILE = "s3://bucket/data/roads.parquet";
    private static final String FOLDER = "s3://bucket/data/";
    private static final String GLOB = "file:/data/*.parquet";
    private static final String NESTED_GLOB = "file:/data/*/*.parquet";
    private static final String HIVE_GLOB = "s3://bucket/hive/**/*.parquet";
    private static final String HIVE_ROOT = "s3://bucket/hive/";

    private static final List<String> PINNED_OPTIONS = List.of("merged", "file");
    private static final List<String> PARTITION_OPTIONS = List.of("merged", "file", "partition");

    private static final Map<String, String> NO_SETTINGS = Map.of();
    private static final Map<String, String> HIVE_DEPTH = Map.of("max_hive_depth", "2");

    private static final Map<String, String> DEFAULT_GROUPING = Map.of();
    private static final Map<String, String> BY_FILE = Map.of("layer-grouping", "file");
    private static final Map<String, String> BY_PARTITION = Map.of("layer-grouping", "partition");
    private static final Map<String, String> BY_PARTITION_TO_DEPTH =
            Map.of("layer-grouping", "partition", "partition-depth", "2");
    private static final Map<String, String> MERGED = Map.of("layer-grouping", "merged");

    @ParameterizedTest
    @MethodSource("groupings")
    void triesGroupingsInOrder(
            String location, List<String> options, Map<String, String> settings, List<Candidate> expected) {
        List<Candidate> candidates = GeoParquetVerification.candidates(location, options, settings);

        assertThat(candidates).isEqualTo(expected);
    }

    static Stream<Arguments> groupings() {
        List<Map<String, String>> pinned = List.of(BY_FILE, MERGED);
        List<Map<String, String>> byPartition = List.of(BY_FILE, BY_PARTITION_TO_DEPTH, MERGED);
        return Stream.of(
                Arguments.of(SINGLE_FILE, PINNED_OPTIONS, NO_SETTINGS, at(SINGLE_FILE, List.of(DEFAULT_GROUPING))),
                Arguments.of(SINGLE_FILE, PARTITION_OPTIONS, HIVE_DEPTH, at(SINGLE_FILE, List.of(DEFAULT_GROUPING))),
                Arguments.of(FOLDER, PINNED_OPTIONS, NO_SETTINGS, at(FOLDER, pinned)),
                Arguments.of(FOLDER, PINNED_OPTIONS, HIVE_DEPTH, at(FOLDER, pinned)),
                Arguments.of(GLOB, PINNED_OPTIONS, NO_SETTINGS, at(GLOB, pinned)),
                Arguments.of(NESTED_GLOB, PINNED_OPTIONS, NO_SETTINGS, at(NESTED_GLOB, pinned)),
                Arguments.of(
                        FOLDER, PARTITION_OPTIONS, NO_SETTINGS, at(FOLDER, List.of(BY_FILE, BY_PARTITION, MERGED))),
                Arguments.of(FOLDER, PARTITION_OPTIONS, HIVE_DEPTH, at(FOLDER, byPartition)),
                Arguments.of(FOLDER, List.of(), NO_SETTINGS, at(FOLDER, List.of(DEFAULT_GROUPING))),
                Arguments.of(HIVE_GLOB, PINNED_OPTIONS, HIVE_DEPTH, both(at(HIVE_GLOB, pinned), at(HIVE_ROOT, pinned))),
                Arguments.of(
                        HIVE_GLOB,
                        PARTITION_OPTIONS,
                        HIVE_DEPTH,
                        both(at(HIVE_GLOB, byPartition), at(HIVE_ROOT, byPartition))));
    }

    @ParameterizedTest
    @MethodSource("counterparts")
    void findsTheCounterpartName(String nativeName, String writtenQuery, Optional<String> expected) {
        List<String> typeNames = List.of("coastlines", "countries", "my-points");

        Optional<String> typeName = GroupingAttempts.counterpartName(nativeName, typeNames, writtenQuery);

        assertThat(typeName).isEqualTo(expected);
    }

    static Stream<Arguments> counterparts() {
        String sslOff = "s3_use_ssl=false";
        String sslOffInRegion = "s3_use_ssl=false&s3_region=us-east-1";
        return Stream.of(
                Arguments.of("countries", "", Optional.of("countries")),
                Arguments.of("countries", sslOff, Optional.of("countries")),
                Arguments.of("my_points", "", Optional.of("my-points")),
                Arguments.of("countries.parquet_s3_use_ssl_false", sslOff, Optional.of("countries")),
                Arguments.of("coastlines.parquet_s3_use_ssl_false", sslOff, Optional.of("coastlines")),
                Arguments.of(
                        "countries.parquet_s3_use_ssl_false_s3_region_us_east_1",
                        sslOffInRegion,
                        Optional.of("countries")),
                Arguments.of("countries.parquet_s3_use_ssl_false", "", Optional.empty()),
                Arguments.of("nowhere", sslOff, Optional.empty()));
    }

    @Test
    void savedAttributeMissingBlocks() throws Exception {
        FeatureTypeInfo featureType = featureType();
        List<AttributeTypeInfo> attributes = featureType.getAttributes();
        attributes.add(attribute("geom", Point.class));
        attributes.add(attribute("name", null));
        attributes.add(attribute("ghost", String.class));
        Optional<SimpleFeatureType> saved = GeoParquetVerification.schemaOfSavedAttributes(featureType);
        SimpleFeatureType after = DataUtilities.createType("roads", "geom:Point,name:Integer,extra:String");

        List<MigrationNote> notes = GeoParquetVerification.attributeNotes(featureType, saved, after, Set.of());

        MigrationNote missing = MigrationNote.blocker("GeoParquetMigration.savedAttributeMissing", "ghost");
        assertThat(notes).containsExactly(missing);
    }

    // a binding missing from the catalog compares the name only
    @Test
    void savedAttributeRetyped() throws Exception {
        FeatureTypeInfo featureType = featureType();
        List<AttributeTypeInfo> attributes = featureType.getAttributes();
        attributes.add(attribute("geom", Point.class));
        attributes.add(attribute("name", null));
        attributes.add(attribute("count", Long.class));
        Optional<SimpleFeatureType> saved = GeoParquetVerification.schemaOfSavedAttributes(featureType);
        SimpleFeatureType after = DataUtilities.createType("roads", "geom:Point,name:Integer,count:Integer");

        List<MigrationNote> notes = GeoParquetVerification.attributeNotes(featureType, saved, after, Set.of());

        MigrationNote retyped =
                MigrationNote.difference("GeoParquetMigration.attributeRetyped", "count", "Long", "Integer");
        assertThat(notes).containsExactly(retyped);
    }

    @Test
    void unsavedMissingIsADifference() throws Exception {
        SimpleFeatureType before = DataUtilities.createType("roads", "geom:Point,ghost:String");
        SimpleFeatureType after = DataUtilities.createType("roads", "geom:Point");

        List<MigrationNote> notes =
                GeoParquetVerification.attributeNotes(featureType(), Optional.of(before), after, Set.of());

        MigrationNote missing = MigrationNote.difference("GeoParquetMigration.attributeMissing", "ghost");
        assertThat(notes).containsExactly(missing);
    }

    private static List<Candidate> at(String location, List<Map<String, String>> groupings) {
        return groupings.stream()
                .map(grouping -> new Candidate(location, grouping))
                .toList();
    }

    private static List<Candidate> both(List<Candidate> first, List<Candidate> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static FeatureTypeInfo featureType() {
        FeatureTypeInfo featureType = new FeatureTypeInfoImpl(null);
        featureType.setNativeName("roads");
        return featureType;
    }

    private static AttributeTypeInfo attribute(String name, Class<?> binding) {
        AttributeTypeInfo attribute = new AttributeTypeInfoImpl();
        attribute.setName(name);
        attribute.setBinding(binding);
        return attribute;
    }
}
