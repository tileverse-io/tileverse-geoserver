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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Page;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.geoserver.web.data.store.DataAccessNewPage;
import org.geoserver.web.util.MapModel;
import org.junit.Before;
import org.junit.Test;

import io.tileverse.geoserver.web.storage.browser.FileColumns;
import io.tileverse.geoserver.web.storage.browser.PickMode;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;
import io.tileverse.geoserver.web.storage.browser.StorageLocationField;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;
import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * Renders the new-store page of each parquetry store type, as GeoServer shows it, and checks what its Browse button
 * browses for.
 */
public class StorePagesIT extends GeoServerWicketTestSupport {

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

    /** The one location field of the page: each store type browses one parameter. */
    private StorageLocationField openNewStorePage(String storeType) {
        tester.startPage(new DataAccessNewPage(storeType));
        Page page = tester.getLastRenderedPage();
        List<StorageLocationField> fields = new ArrayList<>();
        page.visitChildren(
                StorageLocationField.class, (StorageLocationField field, IVisit<Void> visit) -> fields.add(field));
        assertEquals(1, fields.size());
        return fields.get(0);
    }

    private static String keyOf(StorageLocationField field) {
        MapModel<?> model = (MapModel<?>) field.getDefaultModel();
        return model.getExpression();
    }
}
