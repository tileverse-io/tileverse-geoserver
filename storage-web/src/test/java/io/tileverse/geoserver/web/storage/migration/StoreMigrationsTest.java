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

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.tileverse.geoserver.web.storage.browser.ManualExecutor;

class StoreMigrationsTest {

    /** Holds the store "vectors" of type "Old", recognized by the fake migration. */
    private MigrationCatalogs catalogs;

    /** The migration offered by the service under test. */
    private FakeMigration migration;

    /** The jobs of the service under test, read by the tests for the last results. */
    private MigrationJobs jobs;

    /** The records logged by the service, kept off the console. */
    private CapturedLog log;

    @BeforeEach
    void setUp() {
        catalogs = MigrationCatalogs.withStores();
        migration = new FakeMigration();
        log = CapturedLog.of(StoreMigrations.class);
    }

    @AfterEach
    void tearDown() {
        log.close();
        MigrationCatalogs.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsRecognizedStores() {
        StoreMigrations migrations = migrations(Runnable::run);

        List<MigrationRow> rows = migrations.rows();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.storeId()).isEqualTo(vectorsId());
            assertThat(row.workspace()).isEqualTo("ws");
            assertThat(row.store()).isEqualTo("vectors");
            assertThat(row.kindLabelKey()).isEqualTo(FakeMigration.KIND);
            assertThat(row.location()).isEqualTo("file:/d/roads.parquet");
            assertThat(row.layers()).isEqualTo(1);
            assertThat(row.state()).isEqualTo(RowState.NOT_CHECKED);
            assertThat(row.reason()).isNull();
        });
    }

    // An unknown query key blocks the check, and its value is no secret of the plan
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkedOrNot")
    void locationDropsTheQuery(String name, boolean checked) {
        StoreMigrations migrations = migrations(Runnable::run);
        DataStoreInfo store = catalogs.vectors();
        store.getConnectionParameters().put("uri", "s3://b/roads.parquet?aws_secret_access_key=XY");
        catalogs.catalog().save(store);
        if (checked) {
            migration.nextNotes(MigrationNote.blocker("unknown.key"));
            migrations.check(vectors());
        }

        MigrationRow row = vectorsRow(migrations);

        assertThat(row.location()).isEqualTo("s3://b/roads.parquet");
    }

    static Stream<Arguments> checkedOrNot() {
        return Stream.of(
                Arguments.of("not checked", false), Arguments.of("checked and blocked by an unknown key", true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outcomes")
    void outcomeBecomesTheState(CheckOutcome outcome, RowState expected) {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.nextNotes(noteFor(outcome));

        migrations.check(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(expected);
    }

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(CheckOutcome.READY, RowState.READY),
                Arguments.of(CheckOutcome.READY_WITH_DIFFERENCES, RowState.READY_WITH_DIFFERENCES),
                Arguments.of(CheckOutcome.BLOCKED, RowState.BLOCKED));
    }

    @Test
    void reasonIsTheFirstBlocker() {
        StoreMigrations migrations = migrations(Runnable::run);
        MigrationNote firstBlocker = MigrationNote.blocker("first.blocker");
        migration.nextNotes(MigrationNote.difference("a.difference"), firstBlocker, MigrationNote.blocker("other"));

        migrations.check(vectors());

        assertThat(vectorsRow(migrations).reason()).isEqualTo(firstBlocker);
    }

    @Test
    void workingWhileQueued() {
        ManualExecutor executor = new ManualExecutor();
        StoreMigrations migrations = migrations(executor);

        migrations.check(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.WORKING);
        assertThat(migrations.isWorking()).isTrue();
        executor.runAll();
        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.READY);
        assertThat(migrations.isWorking()).isFalse();
    }

    @Test
    void migrateNeedsAPassedCheck() {
        StoreMigrations migrations = migrations(Runnable::run);

        migrations.migrate(vectors());
        assertThat(isMigrated()).isFalse();

        migration.nextNotes(MigrationNote.blocker("a.blocker"));
        migrations.check(vectors());
        migrations.migrate(vectors());
        assertThat(isMigrated()).isFalse();
    }

    @Test
    void migrateAppliesThePlan() {
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());

        migrations.migrate(vectors());

        MigrationRow row = vectorsRow(migrations);
        assertThat(row.state()).isEqualTo(RowState.MIGRATED);
        assertThat(row.kindLabelKey()).isEqualTo(FakeMigration.KIND);
        assertThat(catalogs.vectors().getType()).isEqualTo("New");
    }

    @Test
    void worseResultWritesNothing() {
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migration.nextNotes(MigrationNote.blocker("a.blocker"));

        migrations.migrate(vectors());

        assertThat(isMigrated()).isFalse();
        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.BLOCKED);
    }

    // The data changed since the check: the administrator confirmed other differences
    @Test
    void newDifferenceWritesNothing() {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.nextNotes(MigrationNote.difference("a.difference", "shown"));
        migrations.check(vectors());
        MigrationNote unseen = MigrationNote.difference("a.difference", "unseen");
        migration.nextNotes(unseen);

        migrations.migrate(vectors());

        assertThat(isMigrated()).isFalse();
        MigrationRow row = vectorsRow(migrations);
        assertThat(row.state()).isEqualTo(RowState.READY_WITH_DIFFERENCES);
        assertThat(row.reason()).isEqualTo(unseen);
    }

    @Test
    void sameDifferencesMigrate() {
        StoreMigrations migrations = migrations(Runnable::run);
        MigrationNote difference = MigrationNote.difference("a.difference", "shown");
        migration.nextNotes(difference);
        migrations.check(vectors());
        migration.nextNotes(difference, MigrationNote.info("an.info"));

        migrations.migrate(vectors());

        assertThat(isMigrated()).isTrue();
    }

    // A migration listing migrated stores against the contract
    @Test
    void migratedStoreListedOnce() {
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migrations.migrate(vectors());
        migration.recognizeMigratedStores();

        migrations.check(vectors());

        assertThat(migrations.rows())
                .singleElement()
                .satisfies(row -> assertThat(row.state()).isEqualTo(RowState.MIGRATED));
        Optional<JobResult> last = jobs.lastResult(vectorsId());
        assertThat(last).map(JobResult::kind).contains(JobResult.Kind.MIGRATE);
    }

    @Test
    void revertBringsBackTheRow() {
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migrations.migrate(vectors());

        migrations.revert(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.NOT_CHECKED);
        assertThat(catalogs.vectors().getType()).isEqualTo("Old");
    }

    // The store stays migrated: its row says the revert failed rather than when it was migrated
    @Test
    void failedRevertShowsInTheRow() {
        ControlledCatalog catalog = new ControlledCatalog();
        catalogs = MigrationCatalogs.withStores(catalog);
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migrations.migrate(vectors());
        catalog.failStoreSaves = true;

        migrations.revert(vectors());

        MigrationRow row = vectorsRow(migrations);
        assertThat(row.state()).isEqualTo(RowState.MIGRATED);
        assertThat(row.reason()).isEqualTo(MigrationNote.info("StoreMigration.revertFailed"));
    }

    @Test
    void discardDropsTheRow() {
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migrations.migrate(vectors());

        migrations.discardBackup(vectors());

        assertThat(migrations.rows()).isEmpty();
        assertThat(isMigrated()).isFalse();
        assertThat(catalogs.vectors().getType()).isEqualTo("New");
    }

    // A discard has no plan to mask the secrets of a message: the failure names the class only
    @Test
    void discardFailureShowsInDetails() {
        ControlledCatalog catalog = new ControlledCatalog();
        catalogs = MigrationCatalogs.withStores(catalog);
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        migrations.migrate(vectors());
        catalog.failStoreSaves = true;

        migrations.discardBackup(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.MIGRATED);
        String failure = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(failure).isEqualTo("java.lang.IllegalStateException");
    }

    // A revert submitted during the discard would read a backup about to go
    @Test
    void noJobDuringDiscard() {
        ControlledCatalog catalog = new ControlledCatalog();
        catalogs = MigrationCatalogs.withStores(catalog);
        ManualExecutor executor = new ManualExecutor();
        StoreMigrations migrations = migrations(executor);
        migrateWith(migrations, executor);
        catalog.beforeStoreSave = () -> migrations.revert(vectors());

        migrations.discardBackup(vectors());

        assertThat(executor.pending()).isZero();
        assertThat(isMigrated()).isFalse();
    }

    @Test
    void discardWaitsForTheJob() {
        ManualExecutor executor = new ManualExecutor();
        StoreMigrations migrations = migrations(executor);
        migrateWith(migrations, executor);
        migrations.revert(vectors());

        migrations.discardBackup(vectors());

        assertThat(isMigrated()).isTrue();
        executor.runAll();
        assertThat(catalogs.vectors().getType()).isEqualTo("Old");
    }

    @Test
    void secondSubmitIsIgnored() {
        ManualExecutor executor = new ManualExecutor();
        StoreMigrations migrations = migrations(executor);

        migrations.check(vectors());
        migrations.check(vectors());

        assertThat(executor.pending()).isEqualTo(1);
    }

    // The store goes away between the submission of the migration and its run.
    @Test
    void deletedStoreFailsCleanly() {
        ManualExecutor executor = new ManualExecutor();
        StoreMigrations migrations = migrations(executor);
        String storeId = vectorsId();
        migrations.check(vectors());
        executor.runAll();
        migrations.migrate(vectors());
        removeVectors();

        executor.runAll();

        assertThat(migrations.rows()).isEmpty();
        Optional<MigrationDetails> details = migrations.details(storeId);
        assertThat(details).map(MigrationDetails::failure).contains("StoreMigration.storeGone");
    }

    @Test
    void failureHidesSecrets() {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.nextFailure(new IllegalStateException("cannot read s3://b/f?secret_key=SK"));

        migrations.check(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.FAILED);
        String failure = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(failure).contains(MigrationPlan.MASK).doesNotContain("SK");
    }

    // Without a plan, or without a message, the failure text is the exception class.
    @ParameterizedTest(name = "{0}")
    @MethodSource("failuresWithoutText")
    void failureKeepsTheClassName(String name, Consumer<FakeMigration> failure, String expected) {
        StoreMigrations migrations = migrations(Runnable::run);
        failure.accept(migration);

        migrations.check(vectors());

        String text = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(text).isEqualTo(expected);
    }

    static Stream<Arguments> failuresWithoutText() {
        RuntimeException translation = new IllegalArgumentException("bad uri s3://b/f?secret_key=SK");
        Consumer<FakeMigration> failedTranslation = fake -> fake.failTranslations(translation);
        Consumer<FakeMigration> nullMessage = fake -> fake.nextFailure(new UnsupportedOperationException());
        return Stream.of(
                Arguments.of("translate throws", failedTranslation, "java.lang.IllegalArgumentException"),
                Arguments.of("null message", nullMessage, "java.lang.UnsupportedOperationException"));
    }

    @Test
    void failureKeepsTheRootCause() {
        StoreMigrations migrations = migrations(Runnable::run);
        IOException cause = new IOException("access denied to s3://b/f?secret_key=SK");
        migration.nextFailure(new IllegalStateException("cannot open the store", cause));

        migrations.check(vectors());

        String failure = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(failure).isEqualTo("cannot open the store: access denied to s3://b/f?secret_key=********");
    }

    // The catalog refuses the save: the failure names the store, then tells why
    @Test
    void failedSaveKeepsTheCause() {
        ControlledCatalog catalog = new ControlledCatalog();
        catalogs = MigrationCatalogs.withStores(catalog);
        StoreMigrations migrations = migrations(Runnable::run);
        migrations.check(vectors());
        catalog.failStoreSaves = true;

        migrations.migrate(vectors());

        String failure = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(failure).isEqualTo("Could not migrate store vectors: store save refused by the test");
    }

    @Test
    void failureIsLoggedMasked() {
        StoreMigrations migrations = migrations(Runnable::run);
        IOException cause = new IOException("access denied to s3://b/f?secret_key=SK");
        migration.nextFailure(new IllegalStateException("cannot open the store", cause));

        migrations.check(vectors());

        assertThat(log.messages(Level.WARNING))
                .singleElement()
                .satisfies(warning -> assertThat(warning)
                        .contains("ws:vectors")
                        .contains("java.lang.IllegalStateException: cannot open the store")
                        .contains("caused by java.io.IOException: access denied to s3://b/f?secret_key=********")
                        .doesNotContain("secret_key=SK"));
        assertThat(log.messages(Level.FINE))
                .singleElement()
                .satisfies(trace -> assertThat(trace).contains("\tat ").doesNotContain("secret_key=SK"));
    }

    // Without a plan the log names the classes only: a message may echo the stored location
    @Test
    void logWithoutPlanHasClasses() {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.failTranslations(new IllegalArgumentException("bad uri s3://b/f?secret_key=SK"));

        migrations.check(vectors());

        assertThat(log.messages(Level.WARNING))
                .singleElement()
                .satisfies(warning -> assertThat(warning)
                        .endsWith("java.lang.IllegalArgumentException")
                        .doesNotContain("bad uri"));
    }

    // A broken storage provider throws errors rather than exceptions
    @ParameterizedTest(name = "{0}")
    @MethodSource("errors")
    void errorBecomesAFailure(String name, Error error) {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.nextError(error);

        migrations.check(vectors());

        assertThat(vectorsRow(migrations).state()).isEqualTo(RowState.FAILED);
        String failure = migrations.details(vectorsId()).orElseThrow().failure();
        assertThat(failure).isEqualTo(error.getMessage());
    }

    static Stream<Arguments> errors() {
        Error linkage = new NoClassDefFoundError("io/example/MissingProvider");
        Error serviceConfiguration = new ServiceConfigurationError("provider not found");
        return Stream.of(
                Arguments.of("linkage error", linkage),
                Arguments.of("service configuration error", serviceConfiguration));
    }

    @Test
    void jobSeesTheSubmitter() {
        StoreMigrations migrations = migrations(StoreMigrationsTest::runOnAnotherThread);
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", "geoserver");
        SecurityContextHolder.getContext().setAuthentication(admin);

        migrations.check(vectors());

        assertThat(migration.seenAuthentication()).isEqualTo(admin);
    }

    @Test
    void detailsShowBothSides() {
        StoreMigrations migrations = migrations(Runnable::run);
        migration.nextNotes(MigrationNote.difference("a.difference"));
        migrations.check(vectors());
        CheckResult check = jobs.lastResult(vectorsId()).orElseThrow().check();
        MigrationPlan plan = check.plan();

        MigrationDetails details = migrations.details(vectorsId()).orElseThrow();

        assertThat(details.before()).isEqualTo(plan.sourceDisplay());
        assertThat(details.after()).isEqualTo(plan.targetDisplay());
        assertThat(details.notes()).isEqualTo(check.allNotes());
        assertThat(details.layers()).isEqualTo(check.layers());
        assertThat(details.failure()).isNull();
    }

    // No job ran: without a plan, the keys and the url queries decide what is masked.
    @Test
    void migratedDetailsMaskSecrets() {
        StoreMigrations migrations = migrations(Runnable::run);
        MigrationPlan plan = MigrationPlan.to("New")
                .parameter("uri", "https://h/roads.parquet?sig=TK")
                .parameter("Password", "PW")
                .build();
        migrator().apply(catalogs.vectors(), FakeMigration.ID, plan);

        MigrationDetails details = migrations.details(vectorsId()).orElseThrow();

        assertThat(details.before()).isEmpty();
        assertThat(details.after())
                .containsOnly(Map.entry("uri", "https://h/roads.parquet"), Map.entry("Password", MigrationPlan.MASK));
    }

    // The migration of the store names its location; the query and its signature are left out
    @Test
    void migratedLocationFromMigration() {
        StoreMigrations migrations = migrations(Runnable::run);
        DataStoreInfo store = catalogs.vectors();
        store.getConnectionParameters().put("uri", "s3://b/roads.parquet?sig=TK");
        catalogs.catalog().save(store);
        migrations.check(vectors());

        migrations.migrate(vectors());

        assertThat(vectorsRow(migrations).location()).isEqualTo("s3://b/roads.parquet");
    }

    // A removed migration cannot name the location: the uri parameter does
    @Test
    void removedMigrationUsesUri() {
        StoreMigrations migrations = migrations(Runnable::run);
        MigrationPlan plan = MigrationPlan.to("New")
                .parameter("uri", "https://h/roads.parquet?sig=TK")
                .parameter(FakeMigration.PATH, "https://h/other.parquet")
                .build();
        migrator().apply(catalogs.vectors(), "removed-migration", plan);

        assertThat(vectorsRow(migrations).location()).isEqualTo("https://h/roads.parquet");
    }

    @Test
    void unknownMigrationShowsItsId() {
        StoreMigrations migrations = migrations(Runnable::run);
        MigrationPlan plan = MigrationPlan.to("New").build();
        migrator().apply(catalogs.vectors(), "removed-migration", plan);

        assertThat(vectorsRow(migrations).kindLabelKey()).isEqualTo("removed-migration");
    }

    // In the time zone of the server the migration happened on the 17th: the date is taken in UTC
    @Test
    void migratedRowShowsItsDate() {
        Clock lateOnThe16th = Clock.fixed(Instant.parse("2026-11-16T23:30:00Z"), ZoneOffset.UTC);
        StoreMigrations migrations = migrations(Runnable::run, lateOnThe16th);
        migrations.check(vectors());
        migrations.migrate(vectors());

        MigrationRow row = rowInTimeZone(migrations, "Pacific/Auckland");

        MigrationNote migratedOn = MigrationNote.info("StoreMigration.migratedOn", "2026-11-16");
        assertThat(row.reason()).isEqualTo(migratedOn);
    }

    private StoreMigrations migrations(Executor executor) {
        return migrations(executor, Clock.systemUTC());
    }

    private StoreMigrations migrations(Executor executor, Clock clock) {
        jobs = new MigrationJobs(executor);
        StoreMigrator migrator = new StoreMigrator(catalogs.catalog(), BackupCipher.NONE, clock);
        return new StoreMigrations(catalogs.catalog(), List.of(migration), migrator, jobs);
    }

    /** Lists the row of "vectors" with the default time zone of the JVM set to the given zone. */
    private MigrationRow rowInTimeZone(StoreMigrations migrations, String zone) {
        TimeZone serverZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        try {
            return vectorsRow(migrations);
        } finally {
            TimeZone.setDefault(serverZone);
        }
    }

    /** Checks and migrates "vectors", running the queued jobs after each step. */
    private void migrateWith(StoreMigrations migrations, ManualExecutor executor) {
        migrations.check(vectors());
        executor.runAll();
        migrations.migrate(vectors());
        executor.runAll();
    }

    private StoreMigrator migrator() {
        return new StoreMigrator(catalogs.catalog(), BackupCipher.NONE, Clock.systemUTC());
    }

    private String vectorsId() {
        return catalogs.vectors().getId();
    }

    private List<String> vectors() {
        return List.of(vectorsId());
    }

    private MigrationRow vectorsRow(StoreMigrations migrations) {
        String storeId = vectorsId();
        return migrations.rows().stream()
                .filter(row -> row.storeId().equals(storeId))
                .findFirst()
                .orElseThrow();
    }

    private boolean isMigrated() {
        return MigrationMarker.isOn(catalogs.vectors());
    }

    private void removeVectors() {
        Catalog catalog = catalogs.catalog();
        catalog.remove(catalogs.roads());
        catalog.remove(catalogs.vectors());
    }

    private static MigrationNote noteFor(CheckOutcome outcome) {
        return switch (outcome) {
            case READY -> MigrationNote.info("an.info");
            case READY_WITH_DIFFERENCES -> MigrationNote.difference("a.difference");
            case BLOCKED -> MigrationNote.blocker("a.blocker");
        };
    }

    /**
     * A catalog refusing to save stores once told to, as a failing catalog backend would, or running an action of the
     * test before it saves a store.
     */
    private static final class ControlledCatalog extends CatalogImpl {

        /** Off while the test catalog is built. */
        private boolean failStoreSaves;

        /** Runs before each store save when not null. */
        private Runnable beforeStoreSave;

        @Override
        public void save(StoreInfo store) {
            if (beforeStoreSave != null) {
                beforeStoreSave.run();
            }
            if (failStoreSaves) {
                throw new IllegalStateException("store save refused by the test");
            }
            super.save(store);
        }
    }

    /** Runs the task on a new thread and waits for its end, as a pool thread would run it. */
    private static void runOnAnotherThread(Runnable task) {
        Thread thread = new Thread(task, "test-migration-job");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
