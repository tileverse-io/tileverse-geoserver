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
package io.tileverse.geoserver.parquetry.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.geoserver.web.data.store.DataAccessEditPage;
import org.geoserver.web.data.store.DataAccessNewPage;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.geoserver.web.util.MapModel;
import org.junit.Before;
import org.junit.Test;

import io.tileverse.geoserver.web.storage.StorageParamsPanel;
import io.tileverse.geoserver.web.storage.browser.FileColumns;
import io.tileverse.geoserver.web.storage.browser.PickMode;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;
import io.tileverse.geoserver.web.storage.browser.StorageLocationField;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * Renders the store pages of each parquetry store type, as GeoServer shows them: what the Browse button of a new store
 * browses for, and the default of the byte-range cache on the page of a store reading from S3.
 */
public class StorePagesIT extends GeoServerWicketTestSupport {

    private static final String CACHING_KEY = "storage.caching.enabled";

    private static final String PROVIDER_KEY = "storage.provider";

    @Before
    public void logIn() {
        login();
    }

    @Test
    public void geoParquetBrowsesFilesAndFolders() {
        StorageLocationField field = openNewStorePage(new GeoParquetDataStoreFactory().getDisplayName());
        String key = keyOf(field);

        assertEquals("geoparquet", key);
        GeoParquetDataStoreEditPanel panel = field.findParent(GeoParquetDataStoreEditPanel.class);
        assertNotNull(panel);
        StorageBrowsing browsing = panel.browsing(key).orElseThrow();
        assertEquals(PickMode.FILE_OR_FOLDER, browsing.pickMode());
        assertTrue(browsing.columns() instanceof ParquetFileColumns);
    }

    @Test
    public void icebergBrowsesFolders() {
        StorageLocationField field = openNewStorePage(new IcebergDataStoreFactory().getDisplayName());
        String key = keyOf(field);

        assertEquals("iceberg", key);
        IcebergDataStoreEditPanel panel = field.findParent(IcebergDataStoreEditPanel.class);
        assertNotNull(panel);
        StorageBrowsing browsing = panel.browsing(key).orElseThrow();
        assertEquals(PickMode.FOLDER, browsing.pickMode());
        assertEquals(FileColumns.NONE, browsing.columns());
    }

    @Test
    public void stacBrowsesFiles() {
        StorageLocationField field = openNewStorePage(new StacDataStoreFactory().getDisplayName());
        String key = keyOf(field);

        assertEquals("stac-geoparquet", key);
        StacDataStoreEditPanel panel = field.findParent(StacDataStoreEditPanel.class);
        assertNotNull(panel);
        StorageBrowsing browsing = panel.browsing(key).orElseThrow();
        assertEquals(PickMode.FILE, browsing.pickMode());
        assertTrue(browsing.columns() instanceof ParquetFileColumns);
    }

    // tileverse declares the byte-range cache off. The store declares it on, and its page seeds that default.
    @Test
    public void stacCachesByDefault() {
        String type = new StacDataStoreFactory().getDisplayName();
        Map<String, Serializable> parameters =
                Map.of(StacDataStoreFactory.STAC_URI.key, "s3://bucket/catalog.json", "storage.s3.region", "us-east-1");

        StorageParamsPanel storage = openEditPage("stac", type, parameters);

        assertEquals(Boolean.TRUE, cachingFlagOf(storage));
    }

    @Test
    public void geoParquetCachesByDefault() {
        String type = new GeoParquetDataStoreFactory().getDisplayName();
        Map<String, Serializable> parameters =
                Map.of(GeoParquetDataStoreFactory.GEOPARQUET_URI.key, "s3://bucket/file.parquet", PROVIDER_KEY, "s3");

        StorageParamsPanel storage = openEditPage("geoparquet", type, parameters);

        assertEquals(Boolean.TRUE, cachingFlagOf(storage));
    }

    @Test
    public void icebergCachesByDefault() {
        String type = new IcebergDataStoreFactory().getDisplayName();
        Map<String, Serializable> parameters =
                Map.of(IcebergDataStoreFactory.ICEBERG_URI.key, "s3://bucket/warehouse/table", PROVIDER_KEY, "s3");

        StorageParamsPanel storage = openEditPage("iceberg", type, parameters);

        assertEquals(Boolean.TRUE, cachingFlagOf(storage));
    }

    /** The one location field of the page: each store type browses one parameter. */
    private StorageLocationField openNewStorePage(String storeType) {
        tester.startPage(new DataAccessNewPage(storeType));
        return onlyComponentOfThePage(StorageLocationField.class);
    }

    /** The storage section of the edit page of a store saved with {@code parameters}. */
    private StorageParamsPanel openEditPage(String name, String storeType, Map<String, Serializable> parameters) {
        Catalog catalog = getCatalog();
        DataStoreInfo store = catalog.getFactory().createDataStore();
        store.setName(name);
        store.setWorkspace(catalog.getDefaultWorkspace());
        store.setType(storeType);
        store.getConnectionParameters().putAll(parameters);
        // GeoServer's own page saves the namespace with each vector store
        store.getConnectionParameters()
                .put("namespace", catalog.getDefaultNamespace().getURI());
        catalog.add(store);
        tester.startPage(new DataAccessEditPage(store.getId()));
        return onlyComponentOfThePage(StorageParamsPanel.class);
    }

    private <T extends Component> T onlyComponentOfThePage(Class<T> type) {
        Page page = tester.getLastRenderedPage();
        List<T> found = new ArrayList<>();
        page.visitChildren(type, (T component, IVisit<Void> visit) -> found.add(component));
        assertEquals(1, found.size());
        return found.get(0);
    }

    /** The box of the caching row, which the page shows for a remote backend. */
    private static Boolean cachingFlagOf(StorageParamsPanel storage) {
        Component row = storage.rowFor(CACHING_KEY);
        assertNotNull("the page has a caching row", row);
        assertTrue("the caching row shows", row.isVisible());
        ParamPanel<?> field = (ParamPanel<?>) storage.fieldFor(CACHING_KEY);
        return (Boolean) field.getFormComponent().getModelObject();
    }

    private static String keyOf(StorageLocationField field) {
        MapModel<?> model = (MapModel<?>) field.getDefaultModel();
        return model.getExpression();
    }
}
