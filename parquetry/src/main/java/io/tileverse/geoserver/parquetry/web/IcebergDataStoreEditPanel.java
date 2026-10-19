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

import java.util.List;
import java.util.Optional;

import org.apache.wicket.markup.html.form.Form;
import org.geoserver.catalog.DataStoreInfo;

import io.tileverse.geoserver.web.storage.StorageAwareDataStoreEditPanel;
import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;
import io.tileverse.geoserver.web.storage.browser.FileColumns;
import io.tileverse.geoserver.web.storage.browser.PickMode;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;

import io.tileverse.parquetry.geotools.iceberg.IcebergDataStoreFactory;

/**
 * A store edit panel for the Iceberg DataStore. As for GeoParquet, it offers one storage provider and no caching
 * parameters. The URL field browses for a folder: a table, or a warehouse root. It has no format columns, because an
 * Iceberg table is a folder of files, not one file.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (DefaultDataStoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public class IcebergDataStoreEditPanel extends StorageAwareDataStoreEditPanel {

    private static final String LOCATION_KEY = IcebergDataStoreFactory.ICEBERG_URI.key;

    public IcebergDataStoreEditPanel(String componentId, Form<DataStoreInfo> storeEditForm) {
        super(componentId, storeEditForm, BackendSelection.SINGLE_PROVIDER, false, List.of(LOCATION_KEY));
    }

    @Override
    protected Optional<StorageBrowsing> browsing(String locationKey) {
        if (!LOCATION_KEY.equals(locationKey)) {
            return Optional.empty();
        }
        StorageBrowsing browsing = StorageBrowsing.of(PickMode.FOLDER, FileColumns.NONE);
        return Optional.of(browsing);
    }
}
