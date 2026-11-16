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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ProjectionPolicy;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geoserver.web.GeoServerApplication;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.Query;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.tileverse.geoserver.web.storage.migration.BackupCipher;
import io.tileverse.geoserver.web.storage.migration.CheckOutcome;
import io.tileverse.geoserver.web.storage.migration.CheckResult;
import io.tileverse.geoserver.web.storage.migration.GeoServerBackupCipher;
import io.tileverse.geoserver.web.storage.migration.LayerComparison;
import io.tileverse.geoserver.web.storage.migration.MigrationDetails;
import io.tileverse.geoserver.web.storage.migration.MigrationMarker;
import io.tileverse.geoserver.web.storage.migration.MigrationNote;
import io.tileverse.geoserver.web.storage.migration.MigrationPlan;
import io.tileverse.geoserver.web.storage.migration.MigrationRow;
import io.tileverse.geoserver.web.storage.migration.RowState;
import io.tileverse.geoserver.web.storage.migration.StoreMigrations;
import io.tileverse.geoserver.web.storage.migration.StoreMigrator;

/**
 * Checks, migrates and reverts a community GeoParquet store over the Parquet files of the test fixtures. The community
 * module is not on the test classpath: the old schemas of its layers cannot be read.
 */
public class CommunityGeoParquetMigrationIT extends GeoServerSystemTestSupport {

    private static final String STORE = "community";

    private static final String POINTS_FILE = "points_4326.parquet";

    private static final String TABLE_FILE = "table.parquet";

    /** Points with a bounding box column declared as the covering of their geometry. */
    private static final String COVERING_FILE = "points_covering.parquet";

    /** A query string with an access key id and a secret key, as written in a community uri. */
    private static final String CREDENTIALS = "s3_access_key_id=AK&s3_secret_access_key=wJalr/XUtn+FEMI";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);

    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** The migration under test. */
    private final CommunityGeoParquetMigration migration = new CommunityGeoParquetMigration();

    /** The folder holding the copies of the fixtures. */
    private Path folder;

    /** GeoWebCache would read the schema of a new layer, and the community module is not there to open it. */
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        GWC gwc = GWC.get();
        GWCConfig config = gwc.getConfig();
        config.setCacheLayersByDefault(false);
        gwc.saveConfig(config);
    }

    @Before
    public void copyFixtures() throws IOException {
        folder = temporaryFolder.getRoot().toPath();
        copyFixture(POINTS_FILE);
        copyFixture(TABLE_FILE);
    }

    @After
    public void removeTheStore() {
        WorkspaceInfo workspace = getCatalog().getDefaultWorkspace();
        removeStore(workspace.getName(), STORE);
    }

    @Test
    public void recognizesTheCommunityStore() {
        DataStoreInfo store = addCommunityStore(singleFileUri());

        List<StoreInfo> recognized = migration.recognize(getCatalog());

        List<String> ids = recognized.stream().map(StoreInfo::getId).toList();
        assertEquals(List.of(store.getId()), ids);
    }

    @Test
    public void locationFollowsTheMigration() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        assertEquals(Optional.of(singleFileUri()), migration.location(store));

        store.getConnectionParameters().put("geoparquet", "file:/migrated.parquet");
        store.getMetadata().put(MigrationMarker.KEY, "a backup");

        assertEquals(Optional.of("file:/migrated.parquet"), migration.location(store));
    }

    @Test
    public void checkNamesTheGap() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        assertEquals(CheckOutcome.READY_WITH_DIFFERENCES, result.outcome());
        assertTrue(hasNote(result.notes(), "GeoParquetMigration.attributesNotCompared"));
        assertEquals(1, result.layers().size());
        LayerComparison layer = result.layers().get(0);
        assertEquals("points", layer.layerName());
        assertEquals(List.of(), layer.notes());
        assertFalse(result.plan().targetParameters().containsKey("layer-grouping"));
        assertTrue(hasNote(result.notes(), "GeoParquetMigration.featureIdsChange"));
    }

    @Test
    public void migratedLayerServes() throws IOException {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        FeatureTypeInfo points = addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);
        CheckResult result = check(store);

        migrator(BackupCipher.NONE).apply(store, CommunityGeoParquetMigration.ID, result.plan());

        Catalog catalog = getCatalog();
        assertEquals("Parquet", catalog.getDataStore(store.getId()).getType());
        FeatureTypeInfo migrated = catalog.getFeatureType(points.getId());
        int count = migrated.getFeatureSource(null, null).getCount(Query.ALL);
        assertTrue("the migrated layer has features", count > 0);
        String featureId = firstFeatureId(migrated);
        assertFalse(featureId, featureId.startsWith("points_4326."));
    }

    // The page runs its jobs on the shared pool, over the secured catalog, as the administrator logged in
    @Test
    public void pageJobsMigrateAndRevert() throws IOException {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        FeatureTypeInfo points = addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);
        List<String> storeIds = List.of(store.getId());
        loginAsAdmin();
        try {
            StoreMigrations migrations = StoreMigrations.of(GeoServerExtensions.bean(GeoServerApplication.class));

            migrations.check(storeIds);
            assertEquals(RowState.READY_WITH_DIFFERENCES, stateAfterJob(migrations, store));
            migrations.migrate(storeIds);
            assertEquals(RowState.MIGRATED, stateAfterJob(migrations, store));
            assertEquals("Parquet", getCatalog().getDataStore(store.getId()).getType());
            assertTrue("the migrated layer has features", featureCount(points) > 0);
            migrations.revert(storeIds);
            assertEquals(RowState.NOT_CHECKED, stateAfterJob(migrations, store));
            assertEquals("GeoParquet", getCatalog().getDataStore(store.getId()).getType());
        } finally {
            logout();
        }
    }

    @Test
    public void revertGivesItBack() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);
        StoreMigrator migrator = migrator(BackupCipher.NONE);
        migrator.apply(store, CommunityGeoParquetMigration.ID, check(store).plan());

        migrator.revert(store);

        DataStoreInfo reverted = getCatalog().getDataStore(store.getId());
        assertEquals("GeoParquet", reverted.getType());
        assertEquals(originalParametersAsText(), reverted.getConnectionParameters());
    }

    // GeoServer encrypts the store passwords of its configuration; the backup holds the uri with its secrets.
    @Test
    public void encryptedBackupHidesTheUri() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);
        StoreMigrator migrator = migrator(new GeoServerBackupCipher(getSecurityManager()));
        migrator.apply(store, CommunityGeoParquetMigration.ID, check(store).plan());

        DataStoreInfo migrated = getCatalog().getDataStore(store.getId());
        Serializable marker = migrated.getMetadata().get(MigrationMarker.KEY);
        migrator.revert(store);

        assertNotNull(marker);
        assertFalse(String.valueOf(marker).contains(singleFileUri()));
        DataStoreInfo reverted = getCatalog().getDataStore(store.getId());
        assertEquals(originalParametersAsText(), reverted.getConnectionParameters());
    }

    // the community store publishes my-points.parquet as my_points
    @Test
    public void renamedLayerServes() throws IOException {
        copyFixture(POINTS_FILE, "my-points.parquet");
        Path file = folder.resolve("my-points.parquet");
        DataStoreInfo store = addCommunityStore("file:" + file.toAbsolutePath());
        FeatureTypeInfo points = addFeatureType(store, "my_points", "my_points", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);
        migrator(BackupCipher.NONE).apply(store, CommunityGeoParquetMigration.ID, result.plan());

        Map<String, Optional<String>> expectedNames = Map.of(points.getId(), Optional.of("my-points"));
        assertEquals(expectedNames, result.plan().nativeNames());
        FeatureTypeInfo migrated = getCatalog().getFeatureType(points.getId());
        assertEquals("my-points", migrated.getNativeName());
        int count = migrated.getFeatureSource(null, null).getCount(Query.ALL);
        assertTrue("the renamed layer has features", count > 0);
    }

    // the community store appends the query string of its uri to the layer names
    @Test
    public void queryLayerIsRenamed() {
        DataStoreInfo store = addCommunityStore(folderGlobUri() + "?s3_use_ssl=false");
        String nativeName = "points_4326.parquet_s3_use_ssl_false";
        FeatureTypeInfo points = addFeatureType(store, "points", nativeName, DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        assertNotEquals(CheckOutcome.BLOCKED, result.outcome());
        Map<String, Optional<String>> expectedNames = Map.of(points.getId(), Optional.of("points_4326"));
        assertEquals(expectedNames, result.plan().nativeNames());
    }

    @Test
    public void secretInNameIsMasked() {
        DataStoreInfo store = addCommunityStore(folderGlobUri() + "?" + CREDENTIALS);
        String nativeName = "points_4326.parquet_s3_access_key_id_AK_s3_secret_access_key_wJalr_XUtn_FEMI";
        FeatureTypeInfo points = addFeatureType(store, "points", nativeName, DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        String shownName = "points_4326.parquet_s3_access_key_id_AK_s3_secret_access_key_********";
        LayerComparison layer = result.layers().get(0);
        assertEquals(shownName, layer.nativeNameBefore());
        Map<String, Optional<String>> expectedNames = Map.of(points.getId(), Optional.of("points_4326"));
        assertEquals(expectedNames, result.plan().nativeNames());
    }

    @Test
    public void missingSecretNameIsMasked() {
        DataStoreInfo store = addCommunityStore(folderGlobUri() + "?" + CREDENTIALS);
        String nativeName = "roads.parquet_s3_access_key_id_AK_s3_secret_access_key_wJalr_XUtn_FEMI";
        addFeatureType(store, "roads", nativeName, DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        String shownName = "roads.parquet_s3_access_key_id_AK_s3_secret_access_key_********";
        MigrationNote notFound = MigrationNote.blocker("GeoParquetMigration.layersNotFound", shownName);
        assertTrue(result.notes().contains(notFound));
    }

    @Test
    public void folderGroupsByFile() {
        DataStoreInfo store = addCommunityStore(folderGlobUri());
        addFeatureType(store, "points_4326", "points_4326", DefaultGeographicCRS.WGS84);
        addFeatureType(store, "table", "table", null);

        CheckResult result = check(store);

        assertNotEquals(CheckOutcome.BLOCKED, result.outcome());
        assertEquals("file", result.plan().targetParameters().get("layer-grouping"));
    }

    @Test
    public void missingLayerBlocks() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        addFeatureType(store, "nowhere", "nowhere", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        assertEquals(CheckOutcome.BLOCKED, result.outcome());
        MigrationNote expected = MigrationNote.blocker("GeoParquetMigration.layersNotFound", "nowhere");
        assertEquals(List.of(expected), result.notes());
    }

    // the community store may publish a layer per Hive partition, unknown to the pinned Parquetry
    @Test
    public void missingFolderLayerBlocks() {
        DataStoreInfo store = addCommunityStore(folderGlobUri());
        addFeatureType(store, "nowhere", "nowhere", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        MigrationNote notFound = MigrationNote.blocker("GeoParquetMigration.layersNotFound", "nowhere");
        MigrationNote needsNewer = MigrationNote.info("GeoParquetMigration.needsNewerParquetry");
        assertEquals(List.of(notFound, needsNewer), result.notes());
    }

    @Test
    public void missingFileCannotOpen() {
        Path missing = folder.resolve("missing.parquet");
        DataStoreInfo store = addCommunityStore("file:" + missing.toAbsolutePath());
        addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        assertEquals(CheckOutcome.BLOCKED, result.outcome());
        MigrationNote note = result.notes().get(0);
        assertEquals("GeoParquetMigration.cannotOpen", note.messageKey());
        String failure = note.arguments().get(0);
        assertTrue(failure, failure.contains("no files found"));
    }

    // a layer with saved attributes reads each of them from the store, and fails when one is missing
    @Test
    public void savedAttributeMissingBlocks() {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        FeatureTypeInfo points = addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);
        saveAttributes(points, "name", "bbox");

        CheckResult result = check(store);

        assertEquals(CheckOutcome.BLOCKED, result.outcome());
        MigrationNote expected = MigrationNote.blocker("GeoParquetMigration.savedAttributeMissing", "bbox");
        assertEquals(List.of(expected), result.layers().get(0).notes());
    }

    // the Parquetry store reads the bounding box column of the geometries and does not publish it
    @Test
    public void savedBoxColumnBlocks() throws IOException {
        copyFixture(COVERING_FILE);
        String uri = "file:" + folder.resolve(COVERING_FILE).toAbsolutePath();
        DataStoreInfo store = addCommunityStore(uri);
        FeatureTypeInfo points = addFeatureType(store, "points", "points_covering", DefaultGeographicCRS.WGS84);
        saveAttributes(points, "name", "geometry_bbox");

        CheckResult result = check(store);

        assertEquals(CheckOutcome.BLOCKED, result.outcome());
        MigrationNote expected = MigrationNote.blocker("GeoParquetMigration.savedBoxColumn", "geometry_bbox");
        assertEquals(List.of(expected), result.layers().get(0).notes());
    }

    // the community store needs a recursive glob to read a Hive tree, unknown to the pinned Parquetry
    @Test
    public void hiveGlobMergesFromRoot() throws IOException {
        DataStoreInfo store = addHiveStore("0");
        addFeatureType(store, "hive", "hive", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        assertNotEquals(CheckOutcome.BLOCKED, result.outcome());
        Map<String, Serializable> parameters = result.plan().targetParameters();
        assertEquals("merged", parameters.get("layer-grouping"));
        assertEquals(hiveRootUri(), parameters.get("geoparquet"));
        MigrationNote rewritten = MigrationNote.info("GeoParquetMigration.locationRewritten", hiveRootUri());
        assertTrue(result.plan().notes().contains(rewritten));
    }

    @Test
    public void hivePartitionsNeedNewer() throws IOException {
        DataStoreInfo store = addHiveStore("1");
        addFeatureType(store, "theme_a", "theme_a", DefaultGeographicCRS.WGS84);
        addFeatureType(store, "theme_b", "theme_b", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);

        MigrationNote notFound = MigrationNote.blocker("GeoParquetMigration.layersNotFound", "theme_a, theme_b");
        MigrationNote needsNewer = MigrationNote.info("GeoParquetMigration.needsNewerParquetry");
        assertEquals(List.of(notFound, needsNewer), result.notes());
    }

    @Test
    public void disabledStoreMigrates() {
        DataStoreInfo disabled = communityStore(singleFileUri());
        disabled.setEnabled(false);
        getCatalog().add(disabled);
        DataStoreInfo store = getCatalog().getDataStore(disabled.getId());
        addFeatureType(store, "points", "points_4326", DefaultGeographicCRS.WGS84);

        CheckResult result = check(store);
        migrator(BackupCipher.NONE).apply(store, CommunityGeoParquetMigration.ID, result.plan());

        assertNotEquals(CheckOutcome.BLOCKED, result.outcome());
        assertFalse(getCatalog().getDataStore(store.getId()).isEnabled());
    }

    @Test
    public void wrongCrsIsADifference() throws Exception {
        DataStoreInfo store = addCommunityStore(singleFileUri());
        addFeatureType(store, "points", "points_4326", CRS.decode("EPSG:3857"));

        CheckResult result = check(store);

        LayerComparison layer = result.layers().get(0);
        MigrationNote expected = MigrationNote.difference("GeoParquetMigration.crsDiffers", "EPSG:3857", "CRS:84");
        assertEquals(List.of(expected), layer.notes());
    }

    /**
     * The state of the store once its job ends, waiting up to {@link #JOB_TIMEOUT}. The message of a failed assertion
     * shows the failure kept by the job.
     */
    private static RowState stateAfterJob(StoreMigrations migrations, StoreInfo store) {
        Instant deadline = Instant.now().plus(JOB_TIMEOUT);
        MigrationRow row = rowOf(migrations, store);
        while (row.state() == RowState.WORKING && Instant.now().isBefore(deadline)) {
            pause();
            row = rowOf(migrations, store);
        }
        if (row.state() == RowState.WORKING || row.state() == RowState.FAILED) {
            Optional<MigrationDetails> details = migrations.details(store.getId());
            fail("job ended " + row.state() + ": " + details.map(MigrationDetails::failure));
        }
        return row.state();
    }

    private static MigrationRow rowOf(StoreMigrations migrations, StoreInfo store) {
        List<MigrationRow> rows = migrations.rows();
        Optional<MigrationRow> row = rows.stream()
                .filter(listed -> listed.storeId().equals(store.getId()))
                .findFirst();
        return row.orElseThrow(() -> new AssertionError("no row of " + store.getName()));
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for a migration job", e);
        }
    }

    private int featureCount(FeatureTypeInfo featureType) throws IOException {
        FeatureTypeInfo current = getCatalog().getFeatureType(featureType.getId());
        return current.getFeatureSource(null, null).getCount(Query.ALL);
    }

    private static String firstFeatureId(FeatureTypeInfo featureType) throws IOException {
        FeatureSource<? extends FeatureType, ? extends Feature> source = featureType.getFeatureSource(null, null);
        try (FeatureIterator<? extends Feature> features = source.getFeatures().features()) {
            Feature first = features.next();
            return first.getIdentifier().getID();
        }
    }

    private CheckResult check(StoreInfo store) {
        Catalog catalog = getCatalog();
        List<ResourceInfo> resources = catalog.getResourcesByStore(store, ResourceInfo.class);
        MigrationPlan plan = migration.translate(store, resources);
        return migration.verify(store, resources, plan, catalog);
    }

    private StoreMigrator migrator(BackupCipher cipher) {
        return new StoreMigrator(getCatalog(), cipher, CLOCK);
    }

    /**
     * A store over a Hive tree with two partition levels, read through a recursive glob as the community module needs.
     */
    private DataStoreInfo addHiveStore(String maxHiveDepth) throws IOException {
        copyIntoHiveTree("theme=a/type=x");
        copyIntoHiveTree("theme=a/type=y");
        copyIntoHiveTree("theme=b/type=z");
        DataStoreInfo store = communityStore(hiveRootUri() + "**/*.parquet");
        store.getConnectionParameters().put("max_hive_depth", maxHiveDepth);
        getCatalog().add(store);
        return getCatalog().getDataStore(store.getId());
    }

    private DataStoreInfo addCommunityStore(String uri) {
        DataStoreInfo store = communityStore(uri);
        getCatalog().add(store);
        return getCatalog().getDataStore(store.getId());
    }

    /** A store as saved by the community module, with one parameter saved as a number. */
    private DataStoreInfo communityStore(String uri) {
        Catalog catalog = getCatalog();
        DataStoreInfo store = catalog.getFactory().createDataStore();
        store.setName(STORE);
        store.setWorkspace(catalog.getDefaultWorkspace());
        store.setType("GeoParquet");
        store.setEnabled(true);
        Map<String, Serializable> parameters = store.getConnectionParameters();
        parameters.put("dbtype", "geoparquet");
        parameters.put("uri", uri);
        parameters.put("threads", 4);
        return store;
    }

    /** The parameters of the community store as kept in a backup. */
    private Map<String, Serializable> originalParametersAsText() {
        return Map.of("dbtype", "geoparquet", "uri", singleFileUri(), "threads", "4");
    }

    /** A feature type and its layer, built by hand: the community module is not there to read the schema. */
    private FeatureTypeInfo addFeatureType(
            DataStoreInfo store, String name, String nativeName, CoordinateReferenceSystem nativeCrs) {
        Catalog catalog = getCatalog();
        CatalogFactory factory = catalog.getFactory();
        NamespaceInfo namespace =
                catalog.getNamespaceByPrefix(store.getWorkspace().getName());
        FeatureTypeInfo featureType = factory.createFeatureType();
        featureType.setName(name);
        featureType.setNativeName(nativeName);
        featureType.setStore(store);
        featureType.setNamespace(namespace);
        featureType.setEnabled(true);
        setCrs(featureType, nativeCrs);
        catalog.add(featureType);
        LayerInfo layer = factory.createLayer();
        layer.setResource(featureType);
        layer.setDefaultStyle(catalog.getStyleByName(StyleInfo.DEFAULT_POINT));
        catalog.add(layer);
        return catalog.getFeatureType(featureType.getId());
    }

    /**
     * Saves the attributes of the layer by name, as the layer page does when an administrator customizes them. The
     * catalog would validate them against the community store, missing here: they go straight to its storage.
     */
    private void saveAttributes(FeatureTypeInfo featureType, String... names) {
        Catalog catalog = getCatalog();
        List<AttributeTypeInfo> attributes = featureType.getAttributes();
        for (String name : names) {
            AttributeTypeInfo attribute = catalog.getFactory().createAttribute();
            attribute.setName(name);
            attribute.setFeatureType(featureType);
            attributes.add(attribute);
        }
        catalog.getFacade().save(featureType);
    }

    private static void setCrs(FeatureTypeInfo featureType, CoordinateReferenceSystem nativeCrs) {
        if (nativeCrs == null) {
            return;
        }
        ReferencedEnvelope world = new ReferencedEnvelope(-180, 180, -90, 90, DefaultGeographicCRS.WGS84);
        featureType.setNativeCRS(nativeCrs);
        featureType.setSRS(srsOf(nativeCrs));
        featureType.setProjectionPolicy(ProjectionPolicy.FORCE_DECLARED);
        featureType.setNativeBoundingBox(world);
        featureType.setLatLonBoundingBox(world);
    }

    private static String srsOf(CoordinateReferenceSystem crs) {
        if (crs == DefaultGeographicCRS.WGS84) {
            return "EPSG:4326";
        }
        return CRS.toSRS(crs);
    }

    private static boolean hasNote(List<MigrationNote> notes, String messageKey) {
        return notes.stream().anyMatch(note -> note.messageKey().equals(messageKey));
    }

    private String singleFileUri() {
        Path file = folder.resolve(POINTS_FILE);
        return "file:" + file.toAbsolutePath();
    }

    private String folderGlobUri() {
        return "file:" + folder.toAbsolutePath() + File.separator + "*.parquet";
    }

    private String hiveRootUri() {
        Path root = folder.resolve("hive");
        return "file:" + root.toAbsolutePath() + File.separator;
    }

    private void copyIntoHiveTree(String partition) throws IOException {
        Path partitionFolder = folder.resolve("hive").resolve(partition);
        Files.createDirectories(partitionFolder);
        Path relativeFile = folder.relativize(partitionFolder.resolve("data.parquet"));
        copyFixture(POINTS_FILE, relativeFile.toString());
    }

    private void copyFixture(String fileName) throws IOException {
        copyFixture(fileName, fileName);
    }

    private void copyFixture(String fixtureName, String fileName) throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/fixtures/" + fixtureName)) {
            Files.copy(fixture, folder.resolve(fileName));
        }
    }
}
