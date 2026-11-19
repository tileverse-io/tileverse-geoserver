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
import java.util.Map;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.platform.GeoServerExtensionsHelper;

/**
 * An in-memory catalog with one store of each kind, for the migration tests. Workspace "ws" holds the data store
 * "vectors" with the feature type "roads", and the coverage store "rasters" with the coverage "dem". The accessors read
 * the objects back from the catalog on each call: a test sees what was saved. A test calls {@link #clear()} after use.
 */
public final class MigrationCatalogs {

    public static final String WORKSPACE = "ws";

    /** The catalog holding the two stores. */
    private final Catalog catalog;

    private MigrationCatalogs(Catalog catalog) {
        this.catalog = catalog;
    }

    /** A new {@link CatalogImpl} holding the two stores. */
    public static MigrationCatalogs withStores() {
        return withStores(new CatalogImpl());
    }

    /** The given empty catalog, filled with the two stores; a test passes a subclass to make saves fail. */
    public static MigrationCatalogs withStores(CatalogImpl catalog) {
        GeoServerExtensionsHelper.setIsSpringContext(false);
        MigrationCatalogs catalogs = new MigrationCatalogs(catalog);
        catalogs.addStores();
        return catalogs;
    }

    /** Clears GeoServer's extension lookup set up by {@link #withStores()}. */
    public static void clear() {
        GeoServerExtensionsHelper.clear();
        GeoServerExtensionsHelper.setIsSpringContext(true);
    }

    public Catalog catalog() {
        return catalog;
    }

    /** The data store of type "Old". */
    public DataStoreInfo vectors() {
        return catalog.getDataStoreByName(WORKSPACE, "vectors");
    }

    /** The feature type of "vectors", native name "old_roads". */
    public FeatureTypeInfo roads() {
        return catalog.getFeatureTypeByName(WORKSPACE, "roads");
    }

    /** The coverage store of type "OldRaster", url "old://https://h/dem.tif". */
    public CoverageStoreInfo rasters() {
        return catalog.getCoverageStoreByName(WORKSPACE, "rasters");
    }

    /** The coverage of "rasters", native coverage name "old_coverage". */
    public CoverageInfo dem() {
        return catalog.getCoverageByName(WORKSPACE, "dem");
    }

    private void addStores() {
        CatalogFactory factory = catalog.getFactory();
        WorkspaceInfo workspace = addWorkspace(factory);
        NamespaceInfo namespace = addNamespace(factory);
        addVectors(factory, workspace, namespace);
        addRasters(factory, workspace, namespace);
    }

    private WorkspaceInfo addWorkspace(CatalogFactory factory) {
        WorkspaceInfo workspace = factory.createWorkspace();
        workspace.setName(WORKSPACE);
        catalog.add(workspace);
        return catalog.getWorkspaceByName(WORKSPACE);
    }

    private NamespaceInfo addNamespace(CatalogFactory factory) {
        NamespaceInfo namespace = factory.createNamespace();
        namespace.setPrefix(WORKSPACE);
        namespace.setURI("http://tileverse.io/ws");
        catalog.add(namespace);
        return catalog.getNamespaceByPrefix(WORKSPACE);
    }

    private void addVectors(CatalogFactory factory, WorkspaceInfo workspace, NamespaceInfo namespace) {
        DataStoreInfo store = factory.createDataStore();
        store.setName("vectors");
        store.setType("Old");
        store.setWorkspace(workspace);
        store.setEnabled(true);
        store.getConnectionParameters().putAll(vectorParameters());
        store.getMetadata().put("legacy", "x");
        catalog.add(store);

        FeatureTypeInfo featureType = factory.createFeatureType();
        featureType.setName("roads");
        featureType.setNativeName("old_roads");
        featureType.setNamespace(namespace);
        featureType.setStore(vectors());
        featureType.setEnabled(true);
        catalog.add(featureType);
    }

    private static Map<String, Serializable> vectorParameters() {
        return Map.of(
                "dbtype", "old",
                "uri", "file:/d/roads.parquet",
                "secret", "SK",
                "threads", Integer.valueOf(4),
                "flag", Boolean.TRUE);
    }

    private void addRasters(CatalogFactory factory, WorkspaceInfo workspace, NamespaceInfo namespace) {
        CoverageStoreInfo store = factory.createCoverageStore();
        store.setName("rasters");
        store.setType("OldRaster");
        store.setURL("old://https://h/dem.tif");
        store.setWorkspace(workspace);
        store.setEnabled(true);
        catalog.add(store);

        CoverageInfo coverage = factory.createCoverage();
        coverage.setName("dem");
        coverage.setNativeCoverageName("old_coverage");
        coverage.setNamespace(namespace);
        coverage.setStore(rasters());
        coverage.setEnabled(true);
        catalog.add(coverage);
    }
}
