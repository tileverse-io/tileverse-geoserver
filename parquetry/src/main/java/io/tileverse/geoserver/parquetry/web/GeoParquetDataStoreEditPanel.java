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

import org.apache.wicket.markup.html.form.Form;
import org.geoserver.catalog.DataStoreInfo;

import io.tileverse.geoserver.web.storage.StorageAwareDataStoreEditPanel;
import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;

/**
 * A store edit panel for the GeoParquet DataStore. The shared storage section renders the provider selector and the
 * selected backend's connection fields; caching is not exposed because the engine applies its own default.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (DefaultDataStoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public class GeoParquetDataStoreEditPanel extends StorageAwareDataStoreEditPanel {

    public GeoParquetDataStoreEditPanel(String componentId, Form<DataStoreInfo> storeEditForm) {
        super(componentId, storeEditForm, BackendSelection.SINGLE_PROVIDER, false);
    }
}
