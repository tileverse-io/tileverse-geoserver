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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.web.GeoServerApplication;
import org.geotools.util.logging.Logging;

import io.tileverse.geoserver.web.storage.migration.JobResult.Kind;
import io.tileverse.geoserver.web.storage.migration.MigrationNote.Severity;

/**
 * The service behind the store migration page. It lists the stores recognized by the installed migrations and the
 * migrated stores with their states, and runs checks, migrations and reverts as background jobs. A job reads its store
 * from the catalog when it starts: the store may have changed or gone since the page listed it.
 */
public final class StoreMigrations {

    /** The failure text of a job whose store left the catalog. It is a label key, translated by the page. */
    static final String STORE_GONE = "StoreMigration.storeGone";

    /** The label key of the reason of a migrated row. Its argument is the date of the migration. */
    static final String MIGRATED_ON = "StoreMigration.migratedOn";

    /** The label key of the reason of a migrated row whose last revert failed. */
    static final String REVERT_FAILED = "StoreMigration.revertFailed";

    /**
     * The connection parameters holding the location of a store without url, by preference, when its migration does not
     * name the location.
     */
    private static final List<String> LOCATION_KEYS = List.of("uri", "url");

    private static final Logger LOGGER = Logging.getLogger(StoreMigrations.class);

    private static final Comparator<MigrationRow> BY_WORKSPACE_AND_STORE =
            Comparator.comparing(MigrationRow::workspace).thenComparing(MigrationRow::store);

    /** Holds the stores listed by the page and changed by the jobs. */
    private final Catalog catalog;

    /** The migrations installed in the server. */
    private final List<StoreMigration> migrations;

    /** Writes migrations and reverts to the catalog. */
    private final StoreMigrator migrator;

    /** Runs the jobs and keeps their results. */
    private final MigrationJobs jobs;

    public StoreMigrations(
            Catalog catalog, List<StoreMigration> migrations, StoreMigrator migrator, MigrationJobs jobs) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.migrations = List.copyOf(migrations);
        this.migrator = Objects.requireNonNull(migrator, "migrator");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
    }

    /**
     * The service of the application: its catalog, its migration beans and the shared jobs. The backups are encrypted
     * like the store passwords.
     */
    public static StoreMigrations of(GeoServerApplication application) {
        Catalog catalog = application.getCatalog();
        List<StoreMigration> migrations = application.getBeansOfType(StoreMigration.class);
        BackupCipher cipher = new GeoServerBackupCipher(application.getSecurityManager());
        StoreMigrator migrator = new StoreMigrator(catalog, cipher, Clock.systemUTC());
        return new StoreMigrations(catalog, migrations, migrator, MigrationJobs.shared());
    }

    /** The stores recognized by the migrations and the migrated stores, sorted by workspace and store name. */
    public List<MigrationRow> rows() {
        List<MigrationRow> rows = new ArrayList<>();
        for (Recognized recognized : recognizedStores().values()) {
            rows.add(rowOf(recognized.store(), recognized.migration(), null));
        }
        for (StoreInfo store : migratedStores()) {
            rows.add(migratedRowOf(store));
        }
        rows.sort(BY_WORKSPACE_AND_STORE);
        return rows;
    }

    /** Checks the given stores recognized by a migration. A store with a running job is left alone. */
    public void check(Collection<String> storeIds) {
        Map<String, Recognized> recognized = recognizedStores();
        for (String storeId : storeIds) {
            Recognized store = recognized.get(storeId);
            if (store != null) {
                submitCheck(storeId, store.migration());
            }
        }
    }

    /**
     * Migrates the given stores whose last result is a passed check. The job checks the store again before it writes.
     * The administrator confirmed the check shown. A new check with a worse outcome, or with a difference or a blocker
     * missing from the check shown, is kept as the new check, and the store stays as it is.
     */
    public void migrate(Collection<String> storeIds) {
        Map<String, Recognized> recognized = recognizedStores();
        for (String storeId : storeIds) {
            Recognized store = recognized.get(storeId);
            Optional<CheckResult> shown = passedCheck(storeId);
            if (store != null && shown.isPresent()) {
                submitMigration(storeId, store.migration(), shown.get());
            }
        }
    }

    /** Reverts the given migrated stores. A reverted store starts over as not checked. */
    public void revert(Collection<String> storeIds) {
        for (String storeId : storeIds) {
            Optional<StoreInfo> store = migratedStore(storeId);
            if (store.isPresent()) {
                submitRevert(storeId);
            }
        }
    }

    /**
     * Drops the backups of the given migrated stores, on the calling thread. The stores keep their migrated definition
     * and leave the list. A store with a running job is left alone, and no job of a store starts during its discard. A
     * failed discard becomes the last result of the store rather than an error of the request.
     */
    public void discardBackup(Collection<String> storeIds) {
        for (String storeId : storeIds) {
            Optional<StoreInfo> store = migratedStore(storeId);
            if (store.isPresent()) {
                discardHere(storeId);
            }
        }
    }

    /**
     * The parameters, notes and failure of a store. For a migrated store, the current parameters with the secrets
     * masked. For another store, both sides of its last check. Empty when there is nothing to show.
     */
    public Optional<MigrationDetails> details(String storeId) {
        Optional<JobResult> last = jobs.lastResult(storeId);
        Optional<StoreInfo> migrated = migratedStore(storeId);
        if (migrated.isPresent()) {
            JobResult lastOrNull = last.orElse(null);
            return Optional.of(migratedDetails(migrated.get(), lastOrNull));
        }
        return last.flatMap(StoreMigrations::checkedDetails);
    }

    /** Whether a job of any store runs. */
    public boolean isWorking() {
        return jobs.anyWorking();
    }

    /**
     * The stores recognized by the migrations by store id, with the first migration recognizing the store. A migrated
     * store is left out even when a migration recognizes it: it is listed once, as migrated, and a check of it would
     * fail on its backup.
     */
    private Map<String, Recognized> recognizedStores() {
        Map<String, Recognized> stores = new LinkedHashMap<>();
        for (StoreMigration migration : migrations) {
            for (StoreInfo store : notMigrated(migration.recognize(catalog))) {
                stores.putIfAbsent(store.getId(), new Recognized(store, migration));
            }
        }
        return stores;
    }

    private static List<StoreInfo> notMigrated(List<StoreInfo> stores) {
        return stores.stream().filter(store -> !MigrationMarker.isOn(store)).toList();
    }

    private List<StoreInfo> migratedStores() {
        List<StoreInfo> stores = catalog.getStores(StoreInfo.class);
        return stores.stream().filter(MigrationMarker::isOn).toList();
    }

    /**
     * The backup of a migrated store, null when it cannot be read. One unreadable backup must not break the listing of
     * the other stores.
     */
    private StoreBackup readableBackupOf(StoreInfo store) {
        try {
            Optional<StoreBackup> backup = migrator.backupOf(store);
            return backup.orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The row of a migrated store, with the kind of the migration named in its backup. A migration no longer installed
     * shows its id; a backup that cannot be read shows the store type.
     */
    private MigrationRow migratedRowOf(StoreInfo store) {
        StoreBackup backup = readableBackupOf(store);
        if (backup == null) {
            return rowOf(store, store.getType(), null, null);
        }
        String migrationId = backup.migrationId();
        Optional<StoreMigration> installed = installedMigration(migrationId);
        if (installed.isEmpty()) {
            return rowOf(store, migrationId, null, backup);
        }
        return rowOf(store, installed.get(), backup);
    }

    private Optional<StoreMigration> installedMigration(String migrationId) {
        return migrations.stream()
                .filter(migration -> migration.id().equals(migrationId))
                .findFirst();
    }

    /** The backup is null for a store not migrated, or with a backup that cannot be read. */
    private MigrationRow rowOf(StoreInfo store, StoreMigration migration, StoreBackup backup) {
        return rowOf(store, migration.kindLabelKey(), migration, backup);
    }

    /**
     * Reads whether a job runs before the last result: a job ending in between leaves its result behind. The migration
     * is null when it is not installed; the backup is null for a store not migrated, or with a backup that cannot be
     * read.
     */
    private MigrationRow rowOf(StoreInfo store, String kindLabelKey, StoreMigration migration, StoreBackup backup) {
        String storeId = store.getId();
        boolean working = jobs.isWorking(storeId);
        JobResult last = jobs.lastResult(storeId).orElse(null);
        CheckResult check = checkOf(last);
        String workspace = store.getWorkspace().getName();
        String location = locationOf(store, migration, check);
        int layers = resourcesOf(store).size();
        RowState state = stateOf(store, working, last);
        MigrationNote reason = reasonOf(state, last, backup);
        return new MigrationRow(storeId, workspace, store.getName(), kindLabelKey, location, layers, state, reason);
    }

    /** The check of a result, null without a result or for a result without check. */
    private static CheckResult checkOf(JobResult result) {
        return result == null ? null : result.check();
    }

    /**
     * The location named by the migration of the store, else the one found by the page. The migration is null when it
     * is not installed, and the check is null when the store has none.
     */
    private static String locationOf(StoreInfo store, StoreMigration migration, CheckResult check) {
        Optional<String> named = migration == null ? Optional.empty() : migration.location(store);
        String location = named.orElseGet(() -> storedLocationOf(store));
        MigrationPlan plan = check == null ? null : check.plan();
        return SecretMasking.location(location, plan);
    }

    /** The url of the store, else the first location parameter present, else an empty text. */
    private static String storedLocationOf(StoreInfo store) {
        if (store instanceof CoverageStoreInfo coverageStore && coverageStore.getURL() != null) {
            return coverageStore.getURL();
        }
        Map<String, Serializable> parameters = store.getConnectionParameters();
        for (String key : LOCATION_KEYS) {
            Serializable value = parameters.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    /** The last result is null when the store has none. */
    private static RowState stateOf(StoreInfo store, boolean working, JobResult last) {
        if (working) {
            return RowState.WORKING;
        }
        if (MigrationMarker.isOn(store)) {
            return RowState.MIGRATED;
        }
        if (last != null && last.isFailed()) {
            return RowState.FAILED;
        }
        CheckResult check = checkOf(last);
        if (check == null) {
            return RowState.NOT_CHECKED;
        }
        return stateOf(check);
    }

    private static RowState stateOf(CheckResult check) {
        return switch (check.outcome()) {
            case READY -> RowState.READY;
            case READY_WITH_DIFFERENCES -> RowState.READY_WITH_DIFFERENCES;
            case BLOCKED -> RowState.BLOCKED;
        };
    }

    /**
     * The reason of the state of a row, null without a reason. A migrated store shows the date of its migration, or
     * that its last revert failed: the failed revert leaves the store migrated, and the date alone would hide the
     * failure. A migrated store without a readable backup has no date. Another store shows the reason found by its
     * check.
     */
    private static MigrationNote reasonOf(RowState state, JobResult last, StoreBackup backup) {
        if (state == RowState.MIGRATED) {
            return migratedReasonOf(last, backup);
        }
        CheckResult check = checkOf(last);
        return check == null ? null : reasonOf(check);
    }

    /** The last result and the backup are null when the store has none. */
    private static MigrationNote migratedReasonOf(JobResult last, StoreBackup backup) {
        if (isFailedRevert(last)) {
            return MigrationNote.info(REVERT_FAILED);
        }
        return backup == null ? null : migratedOn(backup);
    }

    private static boolean isFailedRevert(JobResult result) {
        return result != null && result.kind() == Kind.REVERT && result.isFailed();
    }

    /** The day of the migration as an ISO date in UTC, the same for administrators in any time zone. */
    private static MigrationNote migratedOn(StoreBackup backup) {
        LocalDate day = LocalDate.ofInstant(backup.migratedAt(), ZoneOffset.UTC);
        String date = DateTimeFormatter.ISO_LOCAL_DATE.format(day);
        return MigrationNote.info(MIGRATED_ON, date);
    }

    /** The first blocker, else the first difference, else null. */
    private static MigrationNote reasonOf(CheckResult check) {
        List<MigrationNote> notes = check.allNotes();
        Optional<MigrationNote> blocker = firstWith(notes, Severity.BLOCKER);
        return blocker.or(() -> firstWith(notes, Severity.DIFFERENCE)).orElse(null);
    }

    private static Optional<MigrationNote> firstWith(List<MigrationNote> notes, Severity severity) {
        return notes.stream().filter(note -> note.severity() == severity).findFirst();
    }

    private void submitCheck(String storeId, StoreMigration migration) {
        JobBody body = (job, store) -> JobResult.checked(job.check(store, migration));
        jobs.submit(storeId, new StoreJob(storeId, Kind.CHECK, body));
    }

    /** The last result of the store when it is a check that did not block. */
    private Optional<CheckResult> passedCheck(String storeId) {
        Optional<JobResult> last = jobs.lastResult(storeId);
        Optional<CheckResult> check =
                last.filter(result -> result.kind() == Kind.CHECK).map(JobResult::check);
        return check.filter(passed -> passed.outcome() != CheckOutcome.BLOCKED);
    }

    private void submitMigration(String storeId, StoreMigration migration, CheckResult shown) {
        JobBody body = (job, store) -> migrateStore(job, store, migration, shown);
        jobs.submit(storeId, new StoreJob(storeId, Kind.MIGRATE, body));
    }

    private JobResult migrateStore(StoreJob job, StoreInfo store, StoreMigration migration, CheckResult shown) {
        CheckResult check = job.check(store, migration);
        if (isUnconfirmed(check, shown)) {
            return JobResult.checked(check);
        }
        migrator.apply(store, migration.id(), check.plan());
        return JobResult.migrated(check);
    }

    /**
     * Whether the new check tells something the administrator did not confirm: a worse outcome, or a difference or a
     * blocker missing from the check shown. Two notes match on their severity, key and arguments.
     */
    private static boolean isUnconfirmed(CheckResult check, CheckResult shown) {
        if (check.outcome().worseThan(shown.outcome())) {
            return true;
        }
        List<MigrationNote> confirmed = shown.allNotes();
        List<MigrationNote> weighing = notesWeighingOnTheDecision(check);
        return !confirmed.containsAll(weighing);
    }

    private static List<MigrationNote> notesWeighingOnTheDecision(CheckResult check) {
        List<MigrationNote> notes = check.allNotes();
        return notes.stream().filter(note -> note.severity() != Severity.INFO).toList();
    }

    /** A discarded store leaves the list: its result is dropped. */
    private void discardHere(String storeId) {
        JobBody body = (job, store) -> discardStore(store);
        Optional<JobResult> result = jobs.runHere(storeId, new StoreJob(storeId, Kind.DISCARD, body));
        boolean discarded = result.filter(done -> !done.isFailed()).isPresent();
        if (discarded) {
            jobs.forget(storeId);
        }
    }

    private JobResult discardStore(StoreInfo store) {
        migrator.discardBackup(store);
        return JobResult.discarded();
    }

    private void submitRevert(String storeId) {
        JobBody body = (job, store) -> revertStore(store);
        jobs.submit(storeId, new StoreJob(storeId, Kind.REVERT, body));
    }

    private JobResult revertStore(StoreInfo store) {
        migrator.revert(store);
        return JobResult.reverted();
    }

    /** The store when it is in the catalog and migrated. */
    private Optional<StoreInfo> migratedStore(String storeId) {
        StoreInfo store = catalog.getStore(storeId, StoreInfo.class);
        return Optional.ofNullable(store).filter(MigrationMarker::isOn);
    }

    /**
     * The current parameters of a migrated store, and the notes and failure of its last job. The last result is null
     * when the store has none.
     */
    private static MigrationDetails migratedDetails(StoreInfo store, JobResult last) {
        CheckResult check = checkOf(last);
        MigrationPlan plan = check == null ? null : check.plan();
        Map<String, String> after = SecretMasking.parameters(store.getConnectionParameters(), plan);
        List<MigrationNote> notes = check == null ? List.of() : check.allNotes();
        List<LayerComparison> layers = check == null ? List.of() : check.layers();
        String failure = last == null ? null : last.failure();
        return new MigrationDetails(Map.of(), after, notes, layers, failure);
    }

    /** Both sides of the last check. A failure without a check shows alone; a revert leaves nothing to show. */
    private static Optional<MigrationDetails> checkedDetails(JobResult result) {
        CheckResult check = result.check();
        if (check != null) {
            MigrationPlan plan = check.plan();
            return Optional.of(new MigrationDetails(
                    plan.sourceDisplay(), plan.targetDisplay(), check.allNotes(), check.layers(), result.failure()));
        }
        if (result.isFailed()) {
            return Optional.of(new MigrationDetails(Map.of(), Map.of(), List.of(), List.of(), result.failure()));
        }
        return Optional.empty();
    }

    private List<ResourceInfo> resourcesOf(StoreInfo store) {
        return catalog.getResourcesByStore(store, ResourceInfo.class);
    }

    /** A store recognized by a migration. */
    private record Recognized(StoreInfo store, StoreMigration migration) {}

    /** What a job does to its store, read from the catalog when the job starts. */
    @FunctionalInterface
    private interface JobBody {
        JobResult run(StoreJob job, StoreInfo store);
    }

    /**
     * One job on one store. It turns its failures into a failed result and a warning in the server log. Once the store
     * is translated, the plan masks the secrets in the failure text and in the log.
     */
    private final class StoreJob implements Callable<JobResult> {

        /** The id of the store. The job reads the store from the catalog when it starts. */
        private final String storeId;

        /** The kind of the result, failed or not. */
        private final Kind kind;

        /** The work done on the store. */
        private final JobBody body;

        /** Null until the store is translated. Only the thread of the job uses it. */
        private MigrationPlan plan;

        /** The store as workspace:name for the log, null until the job reads the store. */
        private String storeName;

        StoreJob(String storeId, Kind kind, JobBody body) {
            this.storeId = storeId;
            this.kind = kind;
            this.body = body;
        }

        /**
         * A linkage or service configuration error comes from a broken library, such as a storage provider. It fails
         * the job like an exception rather than ending the worker thread without a result.
         */
        @Override
        public JobResult call() {
            try {
                return runOnCurrentStore();
            } catch (RuntimeException | LinkageError | ServiceConfigurationError e) {
                JobFailure failure = new JobFailure(e, plan);
                logFailure(failure);
                return JobResult.failed(kind, failure.text());
            }
        }

        private JobResult runOnCurrentStore() {
            StoreInfo store = catalog.getStore(storeId, StoreInfo.class);
            if (store == null) {
                return JobResult.failed(kind, STORE_GONE);
            }
            storeName = store.getWorkspace().getName() + ":" + store.getName();
            return body.run(this, store);
        }

        /** Translates and verifies the store. A blocked plan is not verified: its result has no layers. */
        CheckResult check(StoreInfo store, StoreMigration migration) {
            List<ResourceInfo> resources = resourcesOf(store);
            plan = migration.translate(store, resources);
            if (plan.isBlocked()) {
                return new CheckResult(plan, List.of(), List.of());
            }
            CheckResult result = migration.verify(store, resources, plan, catalog);
            plan = result.plan();
            return result;
        }

        /** The stack trace goes to FINE: the warning tells the administrator what failed and why. */
        private void logFailure(JobFailure failure) {
            String store = storeName == null ? "with id " + storeId : storeName;
            LOGGER.warning(
                    "The store migration job " + kind + " of store " + store + " failed: " + failure.causeChain());
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(failure.stackTrace());
            }
        }
    }
}
