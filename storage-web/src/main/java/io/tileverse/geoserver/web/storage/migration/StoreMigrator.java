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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;

/**
 * Writes migrations to the catalog. A migration rewrites a store in place: the store keeps its id, its resources and
 * their layers, and gets the type, url and parameters of the plan. The original definition stays in the store metadata,
 * encoded by a {@link BackupCipher}, to revert the migration later.
 *
 * <p>The store and its resources are read from the catalog by id on each call: the objects given by the caller may be
 * stale.
 */
public final class StoreMigrator {

    /** Holds the stores to migrate. */
    private final Catalog catalog;

    /** Protects the backup text. The backup holds the original secrets. */
    private final BackupCipher cipher;

    /** Gives the time of the migration kept in the backup. */
    private final Clock clock;

    public StoreMigrator(Catalog catalog, BackupCipher cipher, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Rewrites the store as the plan says and keeps its original definition in the metadata. The resources are saved
     * before the store. If a save fails, the renamed resources get their names back and the store stays unsaved. An
     * error is thrown again as it is, after the names are back.
     *
     * @throws IllegalStateException when the store is already migrated and keeps its backup
     * @throws MigrationFailedException when a save fails
     */
    public void apply(StoreInfo store, String migrationId, MigrationPlan plan) {
        StoreInfo current = fresh(store);
        requireNoMarker(current);
        List<ResourceInfo> resources = resourcesOf(current);
        StoreBackup backup = newBackup(current, resources, migrationId, plan);
        Renaming renaming = new Renaming();
        try {
            renaming.rename(resources, targetNames(plan));
            rewrite(current, plan, backup);
            catalog.save(current);
        } catch (RuntimeException e) {
            renaming.undo(e);
            throw new MigrationFailedException("Could not migrate store " + current.getName(), e);
        } catch (Error e) {
            renaming.undo(e);
            throw e;
        }
    }

    /**
     * Gives the store and its resources their definition from before the migration, and drops the backup. Resources
     * added after the migration keep their names.
     *
     * @throws IllegalStateException when the store has no backup
     * @throws MigrationFailedException when a save fails
     */
    public void revert(StoreInfo store) {
        StoreInfo current = fresh(store);
        StoreBackup backup = requireBackup(current);
        List<ResourceInfo> resources = resourcesOf(current);
        Renaming renaming = new Renaming();
        try {
            renaming.rename(resources, backup.nativeNames());
            restore(current, backup);
            catalog.save(current);
        } catch (RuntimeException e) {
            renaming.undo(e);
            throw new MigrationFailedException("Could not revert store " + current.getName(), e);
        } catch (Error e) {
            renaming.undo(e);
            throw e;
        }
    }

    /**
     * Drops the backup of a migrated store. The store keeps its migrated definition.
     *
     * @throws IllegalStateException when the store has no backup
     */
    public void discardBackup(StoreInfo store) {
        StoreInfo current = fresh(store);
        requireMarker(current);
        current.getMetadata().remove(MigrationMarker.KEY);
        catalog.save(current);
    }

    /**
     * The original definition of a migrated store, empty for a store without a backup.
     *
     * @throws IllegalArgumentException when the kept backup cannot be read
     */
    public Optional<StoreBackup> backupOf(StoreInfo store) {
        StoreInfo current = fresh(store);
        if (!MigrationMarker.isOn(current)) {
            return Optional.empty();
        }
        Serializable stored = current.getMetadata().get(MigrationMarker.KEY);
        String text = cipher.decode(String.valueOf(stored));
        return Optional.of(StoreBackupCodec.decode(text));
    }

    private StoreInfo fresh(StoreInfo store) {
        StoreInfo current = catalog.getStore(store.getId(), StoreInfo.class);
        if (current == null) {
            throw new IllegalArgumentException("No store with id " + store.getId() + " in the catalog");
        }
        return current;
    }

    private List<ResourceInfo> resourcesOf(StoreInfo store) {
        return catalog.getResourcesByStore(store, ResourceInfo.class);
    }

    private StoreBackup newBackup(
            StoreInfo store, List<ResourceInfo> resources, String migrationId, MigrationPlan plan) {
        Instant migratedAt = clock.instant();
        Map<String, String> parameters = parameterTexts(store);
        Map<String, String> nativeNames = nativeNamesOf(resources);
        return new StoreBackup(
                migrationId, migratedAt, store.getType(), urlOf(store), parameters, nativeNames, plan.savedSettings());
    }

    private static Map<String, String> parameterTexts(StoreInfo store) {
        Map<String, String> texts = new LinkedHashMap<>();
        store.getConnectionParameters().forEach((key, value) -> putText(texts, key, value));
        return texts;
    }

    /** A null parameter is left out: restoring it as the text "null" would give it a value. */
    private static void putText(Map<String, String> texts, String key, Serializable value) {
        if (value != null) {
            texts.put(key, String.valueOf(value));
        }
    }

    private static Map<String, String> nativeNamesOf(List<ResourceInfo> resources) {
        Map<String, String> names = new LinkedHashMap<>();
        for (ResourceInfo resource : resources) {
            names.put(resource.getId(), nativeNameOf(resource));
        }
        return names;
    }

    private static String urlOf(StoreInfo store) {
        if (store instanceof CoverageStoreInfo coverageStore) {
            return coverageStore.getURL();
        }
        return null;
    }

    /** The plan's native names as text, an empty text for a blanked name, as kept in a backup. */
    private static Map<String, String> targetNames(MigrationPlan plan) {
        Map<String, String> names = new LinkedHashMap<>();
        plan.nativeNames().forEach((id, name) -> names.put(id, name.orElse("")));
        return names;
    }

    private void rewrite(StoreInfo store, MigrationPlan plan, StoreBackup backup) {
        store.setType(plan.targetType());
        Optional<String> targetUrl = plan.targetUrl();
        if (store instanceof CoverageStoreInfo coverageStore && targetUrl.isPresent()) {
            coverageStore.setURL(targetUrl.get());
        }
        replaceParameters(store, plan.targetParameters());
        store.getMetadata().keySet().removeAll(plan.removedMetadataKeys());
        String backupText = StoreBackupCodec.encode(backup);
        store.getMetadata().put(MigrationMarker.KEY, cipher.encode(backupText));
    }

    private static void restore(StoreInfo store, StoreBackup backup) {
        store.setType(backup.type());
        if (store instanceof CoverageStoreInfo coverageStore) {
            coverageStore.setURL(backup.url());
        }
        replaceParameters(store, backup.connectionParameters());
        store.getMetadata().remove(MigrationMarker.KEY);
    }

    private static void replaceParameters(StoreInfo store, Map<String, ? extends Serializable> parameters) {
        Map<String, Serializable> current = store.getConnectionParameters();
        current.clear();
        current.putAll(parameters);
    }

    private StoreBackup requireBackup(StoreInfo store) {
        requireMarker(store);
        return backupOf(store).orElseThrow();
    }

    /** A second backup would replace the first one, and the original definition would be lost. */
    private static void requireNoMarker(StoreInfo store) {
        if (MigrationMarker.isOn(store)) {
            throw new IllegalStateException("Store " + store.getName() + " is already migrated");
        }
    }

    private static void requireMarker(StoreInfo store) {
        if (!MigrationMarker.isOn(store)) {
            throw new IllegalStateException("Store " + store.getName() + " was not migrated or has no backup");
        }
    }

    /** The native name of a resource, an empty text when it has none. */
    private static String nativeNameOf(ResourceInfo resource) {
        String name;
        if (resource instanceof CoverageInfo coverage) {
            name = coverage.getNativeCoverageName();
        } else {
            name = resource.getNativeName();
        }
        return name == null ? "" : name;
    }

    /** Sets the native name of a resource; an empty text clears it. */
    private static void setNativeName(ResourceInfo resource, String name) {
        String nameOrNull = name.isEmpty() ? null : name;
        if (resource instanceof CoverageInfo coverage) {
            coverage.setNativeCoverageName(nameOrNull);
        } else {
            resource.setNativeName(nameOrNull);
        }
    }

    /**
     * Renames resources and saves them one by one. It remembers the saved ones with their previous names, to put the
     * names back when a later save fails.
     */
    private final class Renaming {

        /** The saved resources with their native names before the renaming, in save order. */
        private final List<SavedName> savedNames = new ArrayList<>();

        /** Renames the resources found in the map, by resource id. An empty name clears the native name. */
        void rename(List<ResourceInfo> resources, Map<String, String> namesById) {
            for (ResourceInfo resource : resources) {
                String name = namesById.get(resource.getId());
                String previous = nativeNameOf(resource);
                if (name != null && !name.equals(previous)) {
                    setNativeName(resource, name);
                    catalog.save(resource);
                    savedNames.add(new SavedName(resource, previous));
                }
            }
        }

        /** Puts the previous names back. A failed save here is added to the original failure. */
        void undo(Throwable failure) {
            for (SavedName saved : savedNames) {
                restoreName(saved, failure);
            }
        }

        private void restoreName(SavedName saved, Throwable failure) {
            try {
                setNativeName(saved.resource(), saved.previousName());
                catalog.save(saved.resource());
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
    }

    /** A resource saved under a new native name, and its name before. */
    private record SavedName(ResourceInfo resource, String previousName) {}
}
