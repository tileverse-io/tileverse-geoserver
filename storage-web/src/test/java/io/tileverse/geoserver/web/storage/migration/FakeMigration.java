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
package io.tileverse.geoserver.web.storage.migration;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A migration of the data stores of type "Old" to the type "New", for the tests. Its verification returns the answers
 * queued by a test, in order, then READY results without notes. Its translation throws the exception set by a test, if
 * any. It records the authentication seen by the last verification.
 */
public final class FakeMigration implements StoreMigration {

    public static final String ID = "fake-migration";

    public static final String KIND = "FakeMigration.kind";

    /** The parameter of a migrated store holding its location. */
    public static final String PATH = "path";

    /** The answers of the next verifications, in order. Verifications may run on another thread. */
    private final Queue<Answer> answers = new ConcurrentLinkedQueue<>();

    /** Null until a verification runs. */
    private volatile Authentication seenAuthentication;

    /** Thrown by the translations when not null. */
    private volatile RuntimeException translationFailure;

    /** Whether the recognized stores include the migrated ones, against the contract. */
    private volatile boolean recognizesMigratedStores;

    /** The next verification returns a result with these notes. */
    public void nextNotes(MigrationNote... notes) {
        answers.add(new Answer(List.of(notes), null));
    }

    /** The next verification throws this exception. */
    public void nextFailure(RuntimeException failure) {
        answers.add(new Answer(List.of(), failure));
    }

    /** The next verification throws this error, as a broken library on the classpath would. */
    public void nextError(Error error) {
        answers.add(new Answer(List.of(), error));
    }

    /** The next translations throw this exception. */
    public void failTranslations(RuntimeException failure) {
        translationFailure = failure;
    }

    /** From now on, the recognized stores include the migrated ones, as a careless migration would. */
    public void recognizeMigratedStores() {
        recognizesMigratedStores = true;
    }

    /** The authentication seen by the last verification, null before any. */
    public Authentication seenAuthentication() {
        return seenAuthentication;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String kindLabelKey() {
        return KIND;
    }

    @Override
    public List<StoreInfo> recognize(Catalog catalog) {
        List<DataStoreInfo> stores = catalog.getDataStores();
        return stores.stream()
                .filter(this::isRecognized)
                .map(StoreInfo.class::cast)
                .toList();
    }

    private boolean isRecognized(DataStoreInfo store) {
        if (MigrationMarker.isOn(store)) {
            return recognizesMigratedStores;
        }
        return "Old".equals(store.getType());
    }

    /** The path of a migrated store; the page finds the location of the other stores by itself. */
    @Override
    public Optional<String> location(StoreInfo store) {
        if (!MigrationMarker.isOn(store)) {
            return Optional.empty();
        }
        Serializable path = store.getConnectionParameters().get(PATH);
        return Optional.ofNullable(path).map(String::valueOf);
    }

    /** The plan of the catalog tests, with the original parameters for display. */
    @Override
    public MigrationPlan translate(StoreInfo store, List<ResourceInfo> resources) {
        if (translationFailure != null) {
            throw translationFailure;
        }
        Object uri = store.getConnectionParameters().get("uri");
        MigrationPlan.Builder plan = MigrationPlan.to("New")
                .parameter(PATH, String.valueOf(uri))
                .secret("pass", "SK")
                .source("uri", String.valueOf(uri))
                .source("secret", MigrationPlan.MASK)
                .removeMetadata("legacy");
        for (ResourceInfo resource : resources) {
            plan.nativeName(resource.getId(), resource.getName());
        }
        return plan.build();
    }

    @Override
    public CheckResult verify(StoreInfo store, List<ResourceInfo> resources, MigrationPlan plan, Catalog catalog) {
        seenAuthentication = SecurityContextHolder.getContext().getAuthentication();
        Answer answer = answers.poll();
        if (answer == null) {
            return new CheckResult(plan, layersOf(resources), List.of());
        }
        if (answer.failure() != null) {
            throwUnchecked(answer.failure());
        }
        return new CheckResult(plan, layersOf(resources), answer.notes());
    }

    /** The answers hold runtime exceptions and errors only. */
    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }

    private static List<LayerComparison> layersOf(List<ResourceInfo> resources) {
        return resources.stream().map(FakeMigration::layerOf).toList();
    }

    private static LayerComparison layerOf(ResourceInfo resource) {
        return new LayerComparison(
                resource.getId(), resource.getName(), resource.getNativeName(), resource.getName(), List.of());
    }

    /**
     * What a verification returns: the notes of its result, or the runtime exception or error it throws when not null.
     */
    private record Answer(List<MigrationNote> notes, Throwable failure) {}
}
