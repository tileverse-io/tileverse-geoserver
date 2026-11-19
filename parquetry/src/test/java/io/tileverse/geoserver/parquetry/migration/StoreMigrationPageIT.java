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

import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.web.GeoServerLoginPage;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.geoserver.web.ToolPage;
import org.junit.Test;

import io.tileverse.geoserver.web.storage.migration.StoreMigrationPage;

import jakarta.servlet.Filter;

/**
 * Renders the store migration page in a full GeoServer, with the migration of this plugin installed and a community
 * GeoParquet store in the catalog.
 */
public class StoreMigrationPageIT extends GeoServerWicketTestSupport {

    private static final String STORE = "community";

    /** The page redirects an anonymous user through the Spring Security filters. */
    @Override
    protected List<Filter> getFilters() {
        Filter filterChain = (Filter) GeoServerExtensions.bean("filterChainProxy");
        return List.of(filterChain);
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        DataStoreInfo store = catalog.getFactory().createDataStore();
        store.setName(STORE);
        store.setWorkspace(catalog.getDefaultWorkspace());
        store.setType("GeoParquet");
        store.setEnabled(true);
        Map<String, Serializable> parameters = store.getConnectionParameters();
        parameters.put("dbtype", "geoparquet");
        parameters.put("uri", "file:/data/points.parquet");
        catalog.add(store);
    }

    // the kind label comes from the bundle of this plugin, the page from storage-web
    @Test
    public void adminSeesTheStore() {
        login();

        tester.startPage(StoreMigrationPage.class);

        tester.assertRenderedPage(StoreMigrationPage.class);
        String markup = tester.getLastResponseAsString();
        assertTrue(markup.contains(">" + STORE + "<"));
        assertTrue(markup.contains("GeoParquet (community)"));
    }

    @Test
    public void anonymousIsSentToLogin() {
        logout();

        tester.startPage(StoreMigrationPage.class);

        tester.assertRenderedPage(GeoServerLoginPage.class);
    }

    @Test
    public void toolsMenuLinksThePage() {
        login();

        tester.startPage(ToolPage.class);

        List<Class<?>> linkedPages = linkedPagesOf(tester.getLastRenderedPage());
        assertTrue(linkedPages.contains(StoreMigrationPage.class));
    }

    private static List<Class<?>> linkedPagesOf(Page page) {
        List<Class<?>> pages = new ArrayList<>();
        page.visitChildren(
                BookmarkablePageLink.class,
                (BookmarkablePageLink<?> link, IVisit<Void> visit) -> pages.add(link.getPageClass()));
        return pages;
    }
}
