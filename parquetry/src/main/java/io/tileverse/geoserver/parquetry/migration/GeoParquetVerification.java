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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geotools.api.data.Parameter;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.vfny.geoserver.util.DataStoreUtils;

import io.tileverse.geoserver.parquetry.migration.GroupingAttempts.Candidate;
import io.tileverse.geoserver.parquetry.migration.GroupingAttempts.Counterpart;
import io.tileverse.geoserver.parquetry.migration.GroupingAttempts.Match;
import io.tileverse.geoserver.web.storage.migration.CheckResult;
import io.tileverse.geoserver.web.storage.migration.LayerComparison;
import io.tileverse.geoserver.web.storage.migration.MigrationNote;
import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

/**
 * Opens the translated store through GeoServer, without saving it, and compares its layers with the published ones. A
 * folder or a glob can map to layers in several ways: the verification tries the layer groupings of the Parquetry store
 * in order of preference, and keeps the first one with a layer for each published feature type.
 */
final class GeoParquetVerification {

    private static final String LOCATION = GeoParquetDataStoreFactory.GEOPARQUET_URI.key;

    private static final String LAYER_GROUPING = GeoParquetDataStoreFactory.LAYER_GROUPING.key;

    /** The parameter of the community store holding its location and query string. */
    private static final String COMMUNITY_URI = "uri";

    /** The number of Hive partition levels read by the partition grouping, absent for all levels. */
    private static final String PARTITION_DEPTH = "partition-depth";

    private static final String BY_FILE = "file";

    private static final String BY_PARTITION = "partition";

    private static final String MERGED = "merged";

    /** The community setting kept by the translation; it maps one to one to the partition depth. */
    private static final String MAX_HIVE_DEPTH = "max_hive_depth";

    /** The groupings in order of preference. The community store publishes a layer per file or per Hive partition. */
    private static final List<String> PREFERRED_GROUPINGS = List.of(BY_FILE, BY_PARTITION, MERGED);

    /** Opens the candidate stores through its resource pool. */
    private final Catalog catalog;

    GeoParquetVerification(Catalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    CheckResult verify(StoreInfo store, List<ResourceInfo> resources, MigrationPlan plan) {
        List<FeatureTypeInfo> featureTypes = featureTypesOf(resources);
        String location = locationOf(plan);
        List<String> options = groupingOptions();
        List<Candidate> candidates = candidates(location, options, plan.savedSettings());
        String communityUri = communityUriOf(store);
        String writtenQuery = CommunityUri.writtenQuery(communityUri);
        List<String> writtenSecrets = GeoParquetTranslation.writtenSecrets(communityUri);
        GroupingAttempts attempts = new GroupingAttempts(catalog, store, featureTypes, plan, writtenQuery);
        Optional<Match> match = attempts.firstMatch(candidates);
        if (match.isEmpty()) {
            return blocked(plan, attempts, location, options, writtenSecrets);
        }
        return compare(plan, location, featureTypes, match.get(), writtenSecrets);
    }

    /**
     * The locations and layer groupings to try, in order of preference: the location as written first. A location with
     * a recursive glob segment ({@code **}) then tries the folder above its first glob segment. The community store
     * needs such a glob to read a Hive tree, and a Parquetry store may find no files through it.
     */
    static List<Candidate> candidates(String location, List<String> options, Map<String, String> savedSettings) {
        List<Candidate> candidates = new ArrayList<>(candidatesAt(location, options, savedSettings));
        Optional<String> rootFolder = CommunityUri.parse(location).recursiveGlobRoot();
        rootFolder.ifPresent(folder -> candidates.addAll(candidatesAt(folder, options, savedSettings)));
        return candidates;
    }

    private static List<Candidate> candidatesAt(
            String location, List<String> options, Map<String, String> savedSettings) {
        List<Map<String, String>> groupings = groupings(location, options, savedSettings);
        return groupings.stream()
                .map(grouping -> new Candidate(location, grouping))
                .toList();
    }

    /**
     * The layer grouping parameters to try at a location, in order of preference. A single file is one layer whatever
     * the grouping. Another location tries the groupings offered by the factory: by file, by Hive partition (to the
     * saved {@code max_hive_depth}), then merged. A location without any of them opens with the default of the factory.
     */
    private static List<Map<String, String>> groupings(
            String location, List<String> options, Map<String, String> savedSettings) {
        if (isSingleFile(location)) {
            return List.of(Map.of());
        }
        List<Map<String, String>> candidates = new ArrayList<>();
        for (String grouping : PREFERRED_GROUPINGS) {
            if (options.contains(grouping)) {
                candidates.add(groupingParameters(grouping, savedSettings));
            }
        }
        if (candidates.isEmpty()) {
            return List.of(Map.of());
        }
        return candidates;
    }

    private static List<FeatureTypeInfo> featureTypesOf(List<ResourceInfo> resources) {
        return resources.stream()
                .filter(FeatureTypeInfo.class::isInstance)
                .map(FeatureTypeInfo.class::cast)
                .toList();
    }

    /** The community store appends the query string of its uri, secrets included, to its layer names. */
    private static String communityUriOf(StoreInfo store) {
        Serializable uri = store.getConnectionParameters().get(COMMUNITY_URI);
        if (uri == null) {
            return "";
        }
        return String.valueOf(uri);
    }

    private static String locationOf(MigrationPlan plan) {
        Serializable uri = plan.targetParameters().get(LOCATION);
        return String.valueOf(uri);
    }

    /** The values of the layer grouping parameter of the factory: a newer Parquetry offers more of them. */
    private static List<String> groupingOptions() {
        Object options = GeoParquetDataStoreFactory.LAYER_GROUPING.metadata.get(Parameter.OPTIONS);
        if (options instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static CheckResult blocked(
            MigrationPlan plan,
            GroupingAttempts attempts,
            String location,
            List<String> options,
            List<String> writtenSecrets) {
        List<MigrationNote> notes;
        if (attempts.anyOpened()) {
            notes = layersNotFound(attempts, location, options, writtenSecrets);
        } else {
            notes = List.of(cannotOpen(attempts));
        }
        return new CheckResult(plan, List.of(), notes);
    }

    /**
     * Compares the published feature types with their counterparts. The attributes are compared when the old schema can
     * be read: from the community store when its module is installed, else from the attributes saved in the catalog.
     * The feature ids of the layers change: the community store prefixes them with the layer name, the Parquetry store
     * does not.
     */
    private static CheckResult compare(
            MigrationPlan plan,
            String location,
            List<FeatureTypeInfo> featureTypes,
            Match match,
            List<String> writtenSecrets) {
        boolean communityInstalled = DataStoreUtils.aquireFactory(CommunityGeoParquetMigration.COMMUNITY_TYPE) != null;
        List<LayerComparison> layers = new ArrayList<>();
        boolean attributesCompared = true;
        for (FeatureTypeInfo featureType : featureTypes) {
            Optional<SimpleFeatureType> before = schemaBefore(featureType, communityInstalled);
            Counterpart counterpart = match.counterpartOf(featureType);
            layers.add(compareLayer(featureType, counterpart, before, writtenSecrets));
            attributesCompared = attributesCompared && before.isPresent();
        }
        List<MigrationNote> notes = new ArrayList<>();
        if (!attributesCompared) {
            notes.add(MigrationNote.difference("GeoParquetMigration.attributesNotCompared"));
        }
        if (!featureTypes.isEmpty()) {
            notes.add(MigrationNote.difference("GeoParquetMigration.featureIdsChange"));
        }
        MigrationPlan refined = refinedPlan(plan, location, featureTypes, match);
        return new CheckResult(refined, layers, notes);
    }

    private static List<MigrationNote> layersNotFound(
            GroupingAttempts attempts, String location, List<String> options, List<String> writtenSecrets) {
        List<MigrationNote> notes = new ArrayList<>();
        List<String> shownNames = attempts.missingNames().stream()
                .map(nativeName -> CommunityNames.shownName(nativeName, writtenSecrets))
                .toList();
        String missingNames = String.join(", ", shownNames);
        notes.add(MigrationNote.blocker("GeoParquetMigration.layersNotFound", missingNames));
        if (!isSingleFile(location) && !options.contains(BY_PARTITION)) {
            notes.add(MigrationNote.info("GeoParquetMigration.needsNewerParquetry"));
        }
        return notes;
    }

    private static MigrationNote cannotOpen(GroupingAttempts attempts) {
        return MigrationNote.blocker("GeoParquetMigration.cannotOpen", attempts.firstFailure());
    }

    private static Map<String, String> groupingParameters(String grouping, Map<String, String> savedSettings) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(LAYER_GROUPING, grouping);
        String hiveDepth = savedSettings.get(MAX_HIVE_DEPTH);
        if (BY_PARTITION.equals(grouping) && hiveDepth != null) {
            parameters.put(PARTITION_DEPTH, hiveDepth);
        }
        return Collections.unmodifiableMap(parameters);
    }

    private static boolean isSingleFile(String location) {
        return CommunityUri.parse(location).isSingleFile();
    }

    private static Optional<SimpleFeatureType> schemaBefore(FeatureTypeInfo featureType, boolean communityInstalled) {
        if (communityInstalled) {
            Optional<SimpleFeatureType> read = readSchema(featureType);
            if (read.isPresent()) {
                return read;
            }
        }
        return schemaOfSavedAttributes(featureType);
    }

    /** The native name before the migration is shown with the secrets of the community uri masked. */
    private static LayerComparison compareLayer(
            FeatureTypeInfo featureType,
            Counterpart counterpart,
            Optional<SimpleFeatureType> before,
            List<String> writtenSecrets) {
        SimpleFeatureType after = counterpart.schema();
        CoordinateReferenceSystem foundCrs = after.getCoordinateReferenceSystem();
        List<MigrationNote> notes = new ArrayList<>(SchemaDifferences.crs(featureType.getNativeCRS(), foundCrs));
        notes.addAll(attributeNotes(featureType, before, after, counterpart.boxColumns()));
        String shownNameBefore = CommunityNames.shownName(featureType.getNativeName(), writtenSecrets);
        return new LayerComparison(
                featureType.getId(), featureType.getName(), shownNameBefore, counterpart.typeName(), notes);
    }

    /**
     * The plan with the location and grouping of the match, and the native names of the layers renamed by the Parquetry
     * store.
     */
    private static MigrationPlan refinedPlan(
            MigrationPlan plan, String location, List<FeatureTypeInfo> featureTypes, Match match) {
        MigrationPlan.Builder refined = plan.toBuilder();
        Candidate candidate = match.candidate();
        candidate.grouping().forEach(refined::parameter);
        rewriteLocation(refined, location, candidate.location());
        for (FeatureTypeInfo featureType : featureTypes) {
            String typeName = match.counterpartOf(featureType).typeName();
            if (!typeName.equals(featureType.getNativeName())) {
                refined.nativeName(featureType.getId(), typeName);
            }
        }
        return refined.build();
    }

    /**
     * The attribute notes of a layer. A layer with saved attributes reads them from the store by their source: a
     * missing one blocks the migration. Such a layer publishes its saved attributes only, and the attributes new in the
     * Parquetry store stay unpublished.
     */
    static List<MigrationNote> attributeNotes(
            FeatureTypeInfo featureType,
            Optional<SimpleFeatureType> before,
            SimpleFeatureType after,
            Set<String> boxColumns) {
        List<AttributeTypeInfo> savedAttributes = savedAttributesOf(featureType);
        if (savedAttributes.isEmpty()) {
            return before.map(schema -> SchemaDifferences.attributes(schema, after, boxColumns))
                    .orElse(List.of());
        }
        List<MigrationNote> notes =
                new ArrayList<>(SchemaDifferences.missingSources(savedAttributes, after, boxColumns));
        before.ifPresent(schema -> notes.addAll(SchemaDifferences.retypedAttributes(schema, after)));
        return notes;
    }

    private static void rewriteLocation(MigrationPlan.Builder refined, String location, String matchedLocation) {
        if (!matchedLocation.equals(location)) {
            refined.parameter(LOCATION, matchedLocation);
            refined.note(MigrationNote.info("GeoParquetMigration.locationRewritten", matchedLocation));
        }
    }

    /** A failure leaves the comparison to the attributes saved in the catalog. */
    private static Optional<SimpleFeatureType> readSchema(FeatureTypeInfo featureType) {
        try {
            FeatureType schema = featureType.getFeatureType();
            if (schema instanceof SimpleFeatureType simpleSchema) {
                return Optional.of(simpleSchema);
            }
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The schema of the attributes saved in the catalog. An attribute saved without binding reads as {@link Object},
     * and its type is not compared.
     */
    static Optional<SimpleFeatureType> schemaOfSavedAttributes(FeatureTypeInfo featureType) {
        List<AttributeTypeInfo> attributes = savedAttributesOf(featureType);
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(featureType.getNativeName());
        for (AttributeTypeInfo attribute : attributes) {
            builder.add(attribute.getName(), bindingOf(attribute));
        }
        return Optional.of(builder.buildFeatureType());
    }

    private static List<AttributeTypeInfo> savedAttributesOf(FeatureTypeInfo featureType) {
        List<AttributeTypeInfo> attributes = featureType.getAttributes();
        return attributes == null ? List.of() : attributes;
    }

    private static Class<?> bindingOf(AttributeTypeInfo attribute) {
        Class<?> binding = attribute.getBinding();
        return binding == null ? Object.class : binding;
    }
}
