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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.StoreInfo;
import org.geotools.api.data.DataAccess;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.FeatureType;

import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

/**
 * Opens the translated store with one candidate location and layer grouping after the other, until one has a layer for
 * each published feature type. It remembers what the failed candidates found, to explain a blocked check.
 */
final class GroupingAttempts {

    private static final String LOCATION = GeoParquetDataStoreFactory.GEOPARQUET_URI.key;

    /** Opens the candidate stores through its resource pool. */
    private final Catalog catalog;

    /** The store to migrate. */
    private final StoreInfo store;

    /** The published feature types of the store. */
    private final List<FeatureTypeInfo> featureTypes;

    /** The translated parameters, and the secrets to scrub from failure messages. */
    private final MigrationPlan plan;

    /** The query string of the community uri, as written; empty without one. */
    private final String writtenQuery;

    /** The scrubbed message of the first candidate failing to open, null while none failed. */
    private String firstFailure;

    /** The native names without a layer in the first candidate opened, null while none opened. */
    private List<String> missingNames;

    GroupingAttempts(
            Catalog catalog,
            StoreInfo store,
            List<FeatureTypeInfo> featureTypes,
            MigrationPlan plan,
            String writtenQuery) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.store = store;
        this.featureTypes = featureTypes;
        this.plan = plan;
        this.writtenQuery = Objects.requireNonNull(writtenQuery, "writtenQuery");
    }

    /** The first candidate with a layer for each published feature type, empty when none has. */
    Optional<Match> firstMatch(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            Optional<Match> match = attempt(candidate);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    boolean anyOpened() {
        return missingNames != null;
    }

    String firstFailure() {
        return firstFailure;
    }

    List<String> missingNames() {
        return missingNames;
    }

    /** An unsaved store is not cached by the resource pool: it is disposed here. */
    private Optional<Match> attempt(Candidate candidate) {
        DataStoreInfo candidateStore = candidateStore(candidate);
        DataAccess<? extends FeatureType, ? extends Feature> access = null;
        try {
            access = catalog.getResourcePool().getDataStore(candidateStore);
            return matchIn(asDataStore(access), candidate);
        } catch (IOException | RuntimeException e) {
            rememberFailure(e);
            return Optional.empty();
        } finally {
            dispose(access);
        }
    }

    private DataStoreInfo candidateStore(Candidate candidate) {
        DataStoreInfo candidateStore = catalog.getFactory().createDataStore();
        candidateStore.setName(store.getName());
        candidateStore.setWorkspace(store.getWorkspace());
        candidateStore.setType(plan.targetType());
        candidateStore.setEnabled(true);
        Map<String, Serializable> parameters = candidateStore.getConnectionParameters();
        parameters.putAll(plan.targetParameters());
        parameters.put(LOCATION, candidate.location());
        parameters.putAll(candidate.grouping());
        return candidateStore;
    }

    private Optional<Match> matchIn(DataStore dataStore, Candidate candidate) throws IOException {
        List<String> typeNames = List.of(dataStore.getTypeNames());
        Map<String, Counterpart> counterparts = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (FeatureTypeInfo featureType : featureTypes) {
            String nativeName = featureType.getNativeName();
            Optional<String> typeName = counterpartName(nativeName, typeNames, writtenQuery);
            if (typeName.isPresent()) {
                counterparts.put(featureType.getId(), counterpartIn(dataStore, typeName.get()));
            } else {
                missing.add(String.valueOf(nativeName));
            }
        }
        if (!missing.isEmpty()) {
            rememberMissing(missing);
            return Optional.empty();
        }
        return Optional.of(new Match(candidate, counterparts));
    }

    private static DataStore asDataStore(DataAccess<? extends FeatureType, ? extends Feature> access)
            throws IOException {
        if (access instanceof DataStore dataStore) {
            return dataStore;
        }
        throw new IOException("The Parquetry store did not open as a vector store");
    }

    private void rememberFailure(Exception failure) {
        if (firstFailure == null) {
            firstFailure = plan.scrub(messageOf(failure));
        }
    }

    private static void dispose(DataAccess<? extends FeatureType, ? extends Feature> access) {
        if (access != null) {
            access.dispose();
        }
    }

    /**
     * The type name of the Parquetry store matching a published feature type: its native name, else the type name
     * turned into that native name by the naming rule of the community store. A community store reading a URI with a
     * query string appends it to its layer names: such a native name matches the type name of its file last.
     */
    static Optional<String> counterpartName(String nativeName, List<String> typeNames, String writtenQuery) {
        if (typeNames.contains(nativeName)) {
            return Optional.of(nativeName);
        }
        Optional<String> renamed = renamedByCommunity(nativeName, typeNames);
        return renamed.or(() -> renamedWithQuery(nativeName, typeNames, writtenQuery));
    }

    private static Optional<String> renamedByCommunity(String nativeName, List<String> typeNames) {
        return typeNames.stream()
                .filter(typeName -> CommunityNames.layerName(typeName).equals(nativeName))
                .findFirst();
    }

    private static Optional<String> renamedWithQuery(String nativeName, List<String> typeNames, String writtenQuery) {
        if (writtenQuery.isEmpty()) {
            return Optional.empty();
        }
        return typeNames.stream()
                .filter(typeName -> CommunityNames.layerNameWithQuery(typeName, writtenQuery)
                        .equals(nativeName))
                .findFirst();
    }

    private static Counterpart counterpartIn(DataStore dataStore, String typeName) throws IOException {
        SimpleFeatureType schema = dataStore.getSchema(typeName);
        Set<String> boxColumns = BoxColumns.of(dataStore, typeName);
        return new Counterpart(typeName, schema, boxColumns);
    }

    private void rememberMissing(List<String> missing) {
        if (missingNames == null) {
            missingNames = List.copyOf(missing);
        }
    }

    /** The first message in the chain of causes: GeoServer wraps a failure to open a store without a message. */
    private static String messageOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                return cause.getMessage();
            }
        }
        return failure.getClass().getName();
    }

    /**
     * A location and the layer grouping parameters to open it with.
     *
     * @param location the value of the location parameter of the Parquetry store
     * @param grouping the layer grouping parameters, empty for the default grouping of the store
     */
    record Candidate(String location, Map<String, String> grouping) {}

    /** The candidate with a layer for each published feature type. */
    record Match(Candidate candidate, Map<String, Counterpart> counterparts) {

        Counterpart counterpartOf(FeatureTypeInfo featureType) {
            return counterparts.get(featureType.getId());
        }
    }

    /**
     * The layer of the Parquetry store matching a published feature type.
     *
     * @param boxColumns the bounding box columns of the layer, read by the Parquetry store and not published
     */
    record Counterpart(String typeName, SimpleFeatureType schema, Set<String> boxColumns) {}
}
