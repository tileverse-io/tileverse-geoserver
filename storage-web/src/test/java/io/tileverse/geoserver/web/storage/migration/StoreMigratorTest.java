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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StoreMigratorTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    /** Reverses the text: a backup in clear text would show the secret. */
    private static final BackupCipher REVERSING = new BackupCipher() {
        @Override
        public String encode(String plain) {
            return new StringBuilder(plain).reverse().toString();
        }

        @Override
        public String decode(String stored) {
            return new StringBuilder(stored).reverse().toString();
        }
    };

    @AfterEach
    void clearExtensions() {
        MigrationCatalogs.clear();
    }

    @Test
    void applyRewritesTheStore() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();

        migrator(catalogs).apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));

        DataStoreInfo store = catalogs.vectors();
        assertThat(store.getType()).isEqualTo("New");
        assertThat(store.getConnectionParameters())
                .containsOnly(Map.entry("path", "file:/d/roads.parquet"), Map.entry("pass", "SK"));
        assertThat(store.getMetadata()).doesNotContainKey("legacy");
        assertThat(MigrationMarker.isOn(store)).isTrue();
    }

    @Test
    void applyRenamesResources() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        MigrationPlan rastersPlan =
                MigrationPlan.to("New").blankNativeName(catalogs.dem().getId()).build();

        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));
        migrator.apply(catalogs.rasters(), "test-migration", rastersPlan);

        assertThat(catalogs.roads().getNativeName()).isEqualTo("roads");
        assertThat(catalogs.dem().getNativeCoverageName()).isNull();
    }

    @Test
    void applyKeepsLayerIdentity() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        FeatureTypeInfo before = catalogs.roads();

        migrator(catalogs).apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));

        FeatureTypeInfo after = catalogs.roads();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getNamespace()).isEqualTo(before.getNamespace());
        assertThat(after.isEnabled()).isEqualTo(before.isEnabled());
    }

    @Test
    void backupIsEncoded() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = new StoreMigrator(catalogs.catalog(), REVERSING, fixedClock());
        String roadsId = catalogs.roads().getId();

        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));

        Serializable marker = catalogs.vectors().getMetadata().get(MigrationMarker.KEY);
        assertThat(marker).asString().doesNotContain("SK").doesNotContain("old_roads");
        Optional<StoreBackup> backup = migrator.backupOf(catalogs.vectors());
        assertThat(backup).hasValueSatisfying(original -> {
            assertThat(original.migrationId()).isEqualTo("test-migration");
            assertThat(original.migratedAt()).isEqualTo(NOW);
            assertThat(original.type()).isEqualTo("Old");
            assertThat(original.url()).isNull();
            assertThat(original.connectionParameters()).containsEntry("secret", "SK");
            assertThat(original.nativeNames()).containsExactly(Map.entry(roadsId, "old_roads"));
        });
    }

    @Test
    void revertRestoresTheOriginal() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));

        migrator.revert(catalogs.vectors());

        DataStoreInfo store = catalogs.vectors();
        assertThat(store.getType()).isEqualTo("Old");
        assertThat(parameterTexts(store))
                .containsOnly(
                        Map.entry("dbtype", "old"),
                        Map.entry("uri", "file:/d/roads.parquet"),
                        Map.entry("secret", "SK"),
                        Map.entry("threads", "4"),
                        Map.entry("flag", "true"));
        assertThat(store.getMetadata()).doesNotContainKey(MigrationMarker.KEY);
        assertThat(catalogs.roads().getNativeName()).isEqualTo("old_roads");
    }

    @Test
    void revertRestoresTheUrl() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        MigrationPlan plan = MigrationPlan.to("New")
                .url("https://h/dem.tif")
                .blankNativeName(catalogs.dem().getId())
                .build();
        migrator.apply(catalogs.rasters(), "test-migration", plan);
        assertThat(catalogs.rasters().getURL()).isEqualTo("https://h/dem.tif");

        migrator.revert(catalogs.rasters());

        CoverageStoreInfo store = catalogs.rasters();
        assertThat(store.getType()).isEqualTo("OldRaster");
        assertThat(store.getURL()).isEqualTo("old://https://h/dem.tif");
        assertThat(catalogs.dem().getNativeCoverageName()).isEqualTo("old_coverage");
    }

    @Test
    void applyNeedsAnUnmigratedStore() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));
        MigrationPlan secondPlan = MigrationPlan.to("Newer").build();
        DataStoreInfo migrated = catalogs.vectors();

        assertThatThrownBy(() -> migrator.apply(migrated, "test-migration", secondPlan))
                .isInstanceOf(IllegalStateException.class);

        Optional<StoreBackup> backup = migrator.backupOf(catalogs.vectors());
        assertThat(backup)
                .hasValueSatisfying(original -> assertThat(original.type()).isEqualTo("Old"));
        assertThat(catalogs.vectors().getType()).isEqualTo("New");
    }

    @Test
    void failedSaveRestoresNames() {
        StoreSaveFailingCatalog catalog = new StoreSaveFailingCatalog();
        MigrationCatalogs catalogs = MigrationCatalogs.withStores(catalog);
        MigrationPlan plan = vectorsPlan(catalogs);
        catalog.failStoreSaves = true;

        assertThatThrownBy(() -> migrator(catalogs).apply(catalogs.vectors(), "test-migration", plan))
                .isInstanceOf(MigrationFailedException.class);

        assertThat(catalogs.roads().getNativeName()).isEqualTo("old_roads");
        assertThat(MigrationMarker.isOn(catalogs.vectors())).isFalse();
    }

    // An error, from a broken library for example, is not wrapped: it goes up as thrown
    @Test
    void errorRestoresNames() {
        StoreSaveFailingCatalog catalog = new StoreSaveFailingCatalog();
        MigrationCatalogs catalogs = MigrationCatalogs.withStores(catalog);
        MigrationPlan plan = vectorsPlan(catalogs);
        catalog.storeSaveError = new NoClassDefFoundError("io/example/MissingProvider");

        assertThatThrownBy(() -> migrator(catalogs).apply(catalogs.vectors(), "test-migration", plan))
                .isInstanceOf(NoClassDefFoundError.class);

        assertThat(catalogs.roads().getNativeName()).isEqualTo("old_roads");
        assertThat(MigrationMarker.isOn(catalogs.vectors())).isFalse();
    }

    @Test
    void failedRevertKeepsTheMarker() {
        StoreSaveFailingCatalog catalog = new StoreSaveFailingCatalog();
        MigrationCatalogs catalogs = MigrationCatalogs.withStores(catalog);
        StoreMigrator migrator = migrator(catalogs);
        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));
        DataStoreInfo migrated = catalogs.vectors();
        catalog.failStoreSaves = true;

        assertThatThrownBy(() -> migrator.revert(migrated)).isInstanceOf(MigrationFailedException.class);

        assertThat(MigrationMarker.isOn(catalogs.vectors())).isTrue();
        assertThat(catalogs.vectors().getType()).isEqualTo("New");
        assertThat(catalogs.roads().getNativeName()).isEqualTo("roads");
    }

    @Test
    void discardRemovesTheMarker() {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        migrator.apply(catalogs.vectors(), "test-migration", vectorsPlan(catalogs));

        migrator.discardBackup(catalogs.vectors());

        DataStoreInfo store = catalogs.vectors();
        assertThat(MigrationMarker.isOn(store)).isFalse();
        assertThat(store.getType()).isEqualTo("New");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backupOperations")
    void operationNeedsABackup(String name, BiConsumer<StoreMigrator, StoreInfo> operation) {
        MigrationCatalogs catalogs = MigrationCatalogs.withStores();
        StoreMigrator migrator = migrator(catalogs);
        DataStoreInfo store = catalogs.vectors();

        assertThatThrownBy(() -> operation.accept(migrator, store)).isInstanceOf(IllegalStateException.class);
    }

    static Stream<Arguments> backupOperations() {
        BiConsumer<StoreMigrator, StoreInfo> revert = StoreMigrator::revert;
        BiConsumer<StoreMigrator, StoreInfo> discardBackup = StoreMigrator::discardBackup;
        return Stream.of(Arguments.of("revert", revert), Arguments.of("discardBackup", discardBackup));
    }

    private static StoreMigrator migrator(MigrationCatalogs catalogs) {
        return new StoreMigrator(catalogs.catalog(), BackupCipher.NONE, fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static MigrationPlan vectorsPlan(MigrationCatalogs catalogs) {
        return MigrationPlan.to("New")
                .parameter("path", "file:/d/roads.parquet")
                .secret("pass", "SK")
                .nativeName(catalogs.roads().getId(), "roads")
                .removeMetadata("legacy")
                .build();
    }

    private static Map<String, String> parameterTexts(StoreInfo store) {
        Map<String, String> texts = new TreeMap<>();
        store.getConnectionParameters().forEach((key, value) -> texts.put(key, String.valueOf(value)));
        return texts;
    }

    /** A catalog refusing to save stores once told to, as a failing catalog backend would. */
    private static final class StoreSaveFailingCatalog extends CatalogImpl {

        /** Off while the test catalog is built. */
        private boolean failStoreSaves;

        /** Thrown by the store saves when not null. */
        private Error storeSaveError;

        @Override
        public void save(StoreInfo store) {
            if (storeSaveError != null) {
                throw storeSaveError;
            }
            if (failStoreSaves) {
                throw new IllegalStateException("store save refused by the test");
            }
            super.save(store);
        }
    }
}
