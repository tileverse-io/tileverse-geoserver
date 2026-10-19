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
import java.util.Optional;
import java.util.Properties;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.web.data.store.StoreEditPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;

import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;
import io.tileverse.geoserver.web.storage.browser.StorageLocationField;

/**
 * Base store edit panel for the raster coverage stores reading through tileverse-storage: a URL field and a
 * {@link StorageParamsPanel} with a single-provider selector, because a coverage store reads from one backend.
 *
 * <p>It extends {@link StoreEditPanel} directly because a coverage store has no factory parameters for GeoServer's
 * stock panel to list.
 *
 * <p>A subclass adds fields of its own between the URL field and the storage parameters with a markup file holding a
 * {@code <wicket:extend>} element; a subclass without markup renders this panel's.
 *
 * <p>A subclass returning a {@link StorageBrowsing} from {@link #browsing()} gets a "Browse..." button next to the URL
 * field.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (StoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public abstract class StorageAwareCoverageStoreEditPanel extends StoreEditPanel {

    private static final String URL_PROPERTY = "URL";
    private static final String CONNECTION_PARAMETERS_PROPERTY = "connectionParameters";
    private static final String URL_PANEL_ID = "urlPanel";

    /** The storage section, read by Browse for the parameters typed in it. */
    private final StorageParamsPanel storageParams;

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected StorageAwareCoverageStoreEditPanel(String componentId, Form storeEditForm) {
        super(componentId, storeEditForm);
        IModel formModel = storeEditForm.getModel();
        IModel<Map<String, Serializable>> connectionParameters =
                new PropertyModel<>(formModel, CONNECTION_PARAMETERS_PROPERTY);
        IModel<String> url = new PropertyModel<>(formModel, URL_PROPERTY);
        add(urlPanel(url));
        this.storageParams = new StorageParamsPanel(
                "storageParams", connectionParameters, url, BackendSelection.SINGLE_PROVIDER, cachingParameters());
        add(storageParams);
    }

    /** Whether to show the caching parameters; a store enforcing its own caching policy returns false. */
    protected boolean cachingParameters() {
        return true;
    }

    /**
     * The pick mode and the format columns of the Browse button of the URL field. The default is an empty result, and
     * the field stays a plain text field.
     *
     * <p>The panel calls this method from its constructor, before the fields of the subclass are set.
     */
    protected Optional<StorageBrowsing> browsing() {
        return Optional.empty();
    }

    /**
     * The resource key of the URL field's placeholder, hinting at the locations accepted by the store. The same key
     * followed by {@code .tooltip} gives the tooltip of the field, for a store type defining that resource.
     *
     * <p>The key must not have the form {@code <PanelClass>.url}, because GeoServer reads such a key as the label of
     * the URL field in that panel. The title of the Browse dialog and the required-field message would then show the
     * placeholder.
     */
    protected abstract String urlPlaceholderKey();

    private Component urlPanel(IModel<String> urlModel) {
        Optional<StorageBrowsing> browsing = browsing();
        if (browsing.isPresent()) {
            return browsingUrlPanel(urlModel, browsing.get());
        }
        return textUrlPanel(urlModel);
    }

    private TextParamPanel<String> textUrlPanel(IModel<String> urlModel) {
        TextParamPanel<String> panel =
                new TextParamPanel<>(URL_PANEL_ID, urlModel, urlLabel(), true, new StorageUriValidator());
        describe(panel, panel.getFormComponent());
        return panel;
    }

    private StorageLocationField browsingUrlPanel(IModel<String> urlModel, StorageBrowsing browsing) {
        StorageLocationField field = new StorageLocationField(
                URL_PANEL_ID,
                urlModel,
                urlLabel(),
                true,
                browsing,
                this::typedStorageParameters,
                new StorageUriValidator());
        describe(field, field.getFormComponent());
        return field;
    }

    /** The placeholder hints at the locations accepted by the store; the tooltip of the store type describes them. */
    private void describe(Component panel, FormComponent<String> field) {
        IModel<String> placeholder = new ResourceModel(urlPlaceholderKey(), "");
        field.add(AttributeModifier.replace("placeholder", placeholder));
        IModel<String> tooltip = Tooltips.of(urlPlaceholderKey(), panel, "");
        panel.add(AttributeModifier.replace("title", tooltip));
    }

    private static IModel<String> urlLabel() {
        return new ResourceModel("url", "URL");
    }

    /**
     * Reads the section when the user clicks Browse. The section is built after the URL field, and a reference taken
     * earlier would be null.
     */
    private Optional<Properties> typedStorageParameters() {
        return storageParams.typedParameters();
    }
}
