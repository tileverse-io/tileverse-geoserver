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
package io.tileverse.geoserver.web.storage;

import java.io.Serializable;
import java.util.Map;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.web.data.store.StoreEditPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;

import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;

/**
 * Base store edit panel for the raster coverage stores reading through tileverse-storage: a URL field and a
 * {@link StorageParamsPanel} with a single-provider selector, because a coverage store reads from one backend.
 *
 * <p>It extends {@link StoreEditPanel} directly because a coverage store has no factory parameters for GeoServer's
 * stock panel to list.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (StoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public abstract class StorageAwareCoverageStoreEditPanel extends StoreEditPanel {

    private static final String URL_PROPERTY = "URL";
    private static final String CONNECTION_PARAMETERS_PROPERTY = "connectionParameters";

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected StorageAwareCoverageStoreEditPanel(String componentId, Form storeEditForm) {
        super(componentId, storeEditForm);
        IModel formModel = storeEditForm.getModel();
        IModel<Map<String, Serializable>> connectionParameters =
                new PropertyModel<>(formModel, CONNECTION_PARAMETERS_PROPERTY);
        add(urlPanel(formModel));
        add(new StorageParamsPanel(
                "storageParams", connectionParameters, BackendSelection.SINGLE_PROVIDER, cachingParameters()));
    }

    /** Whether to show the caching parameters; a store enforcing its own caching policy returns false. */
    protected boolean cachingParameters() {
        return true;
    }

    /** The resource key of the URL field's placeholder, hinting at the locations accepted by the store. */
    protected abstract String urlPlaceholderKey();

    @SuppressWarnings("unchecked")
    private TextParamPanel<String> urlPanel(IModel formModel) {
        IModel<String> urlModel = new PropertyModel<>(formModel, URL_PROPERTY);
        IModel<String> label = new ResourceModel("url", "URL");
        TextParamPanel<String> panel =
                new TextParamPanel<>("urlPanel", urlModel, label, true, new StorageUriValidator());
        IModel<String> placeholder = new ResourceModel(urlPlaceholderKey(), "");
        panel.getFormComponent().add(AttributeModifier.replace("placeholder", placeholder));
        return panel;
    }
}
