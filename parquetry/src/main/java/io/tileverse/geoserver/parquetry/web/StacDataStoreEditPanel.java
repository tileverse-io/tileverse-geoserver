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

import java.util.Optional;

import org.apache.wicket.markup.html.form.Form;
import org.geoserver.catalog.DataStoreInfo;

import io.tileverse.geoserver.web.storage.StorageAwareDataStoreEditPanel;
import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;
import io.tileverse.geoserver.web.storage.browser.PickMode;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;

import io.tileverse.parquetry.geotools.parquet.StacDataStoreFactory;

/**
 * A store edit panel for the STAC GeoParquet DataStore. The store reads from more than one storage backend at once: its
 * catalog document and its data assets may live on different backends. The shared storage section renders one checkbox
 * per backend instead of the single-provider selector, and shows the fields of each ticked backend together with the
 * memory cache toggle. The URL field browses for one file: a catalog document, or an items table read with the Parquet
 * columns.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (DefaultDataStoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public class StacDataStoreEditPanel extends StorageAwareDataStoreEditPanel {

    private static final String LOCATION_KEY = StacDataStoreFactory.STAC_URI.key;

    public StacDataStoreEditPanel(String componentId, Form<DataStoreInfo> storeEditForm) {
        super(componentId, storeEditForm, BackendSelection.MULTIPLE_BACKENDS, true);
    }

    @Override
    protected Optional<StorageBrowsing> browsing(String locationKey) {
        if (!LOCATION_KEY.equals(locationKey)) {
            return Optional.empty();
        }
        StorageBrowsing browsing = StorageBrowsing.of(PickMode.FILE, new ParquetFileColumns());
        return Optional.of(browsing);
    }
}
