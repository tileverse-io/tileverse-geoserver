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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.data.store.DefaultDataStoreEditPanel;
import org.geoserver.web.data.store.ParamInfo;

import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;

/**
 * Base store edit panel for the parquetry DataStores: GeoServer's stock panel renders the core parameters, and one
 * {@link StorageParamsPanel} takes the row of the first {@code storage.*} parameter, blanking the other storage rows.
 * The section thus appears where the factory lists its storage parameters, with no markup slot needed in subclasses.
 *
 * <p>Adapted from GeoServer's {@code PMTilesDataStoreEditPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
// S110: the GeoServer/Wicket panel hierarchy (DefaultDataStoreEditPanel) exceeds Sonar's parent-count limit.
@SuppressWarnings({"serial", "java:S110"})
public abstract class StorageAwareDataStoreEditPanel extends DefaultDataStoreEditPanel {

    private static final String NAMESPACE_KEY = "namespace";
    private static final String TITLE_ATTRIBUTE = "title";

    private final BackendSelection selection;
    private final boolean cachingParameters;

    /**
     * Created once, for the first {@code storage.*} parameter; relies on GeoServer's parameter list reusing its items,
     * since a repopulated list would give that parameter a placeholder and drop the section.
     */
    private StorageParamsPanel storageSection;

    /** Stand-ins for the other {@code storage.*} parameters. */
    private final List<Component> storagePlaceholders = new ArrayList<>();

    protected StorageAwareDataStoreEditPanel(
            String componentId,
            Form<DataStoreInfo> storeEditForm,
            BackendSelection selection,
            boolean cachingParameters) {
        super(componentId, storeEditForm);
        this.selection = selection;
        this.cachingParameters = cachingParameters;
    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();
        DataStoreInfo storeInfo = (DataStoreInfo) storeEditForm.getModelObject();
        alignNamespaceWithWorkspace(storeInfo);
        hidePlaceholderRows();
        dropTooltipFromStorageSection();
    }

    /**
     * GeoServer syncs the namespace only when the workspace dropdown changes, which never happens on the
     * workspace-scoped "Add new store" page; the default workspace's namespace would then break WFS GetFeature, whose
     * catalog lookup is keyed by namespace.
     */
    private void alignNamespaceWithWorkspace(DataStoreInfo storeInfo) {
        WorkspaceInfo workspace = storeInfo.getWorkspace();
        if (workspace == null) {
            return;
        }
        NamespaceInfo namespace = GeoServerApplication.get().getCatalog().getNamespaceByPrefix(workspace.getName());
        if (namespace == null) {
            return;
        }
        storeInfo.getConnectionParameters().put(NAMESPACE_KEY, namespace.getURI());
    }

    /** Hides the whole row, not only the placeholder, because GeoServer's theme pads every row. */
    private void hidePlaceholderRows() {
        storagePlaceholders.forEach(placeholder -> placeholder.getParent().setVisible(false));
    }

    /** GeoServer's stock list adds the hosted parameter's description as a tooltip, wrong for a whole section. */
    private void dropTooltipFromStorageSection() {
        if (storageSection == null) {
            return;
        }
        List<AttributeModifier> modifiers = storageSection.getBehaviors(AttributeModifier.class);
        for (AttributeModifier modifier : modifiers) {
            if (TITLE_ATTRIBUTE.equals(modifier.getAttribute())) {
                storageSection.remove(modifier);
            }
        }
    }

    @Override
    protected Panel getInputComponent(
            String componentId, IModel<Map<String, Serializable>> paramsModel, ParamInfo paramMetadata) {
        if (!isStorageParam(paramMetadata.getName())) {
            return super.getInputComponent(componentId, paramsModel, paramMetadata);
        }
        if (storageSection == null) {
            storageSection = new StorageParamsPanel(componentId, paramsModel, selection, cachingParameters);
            return storageSection;
        }
        return placeholder(componentId);
    }

    private static boolean isStorageParam(String paramName) {
        return !StorageParamVisibility.groupOf(paramName).isEmpty();
    }

    private Panel placeholder(String componentId) {
        EmptyPanel placeholder = new EmptyPanel(componentId);
        placeholder.setVisible(false);
        storagePlaceholders.add(placeholder);
        return placeholder;
    }

    /**
     * Leaves an options-bearing parameter empty until the user picks one, and never seeds a {@code storage.*} default:
     * stored for an unselected backend or a hidden parameter, it would override the engine's own default.
     */
    @Override
    protected void applyParamDefault(ParamInfo paramInfo, StoreInfo info) {
        if (isStorageParam(paramInfo.getName())) {
            return;
        }
        super.applyParamDefault(paramInfo, info);
        List<Serializable> options = paramInfo.getOptions();
        if (options != null && !options.isEmpty()) {
            info.getConnectionParameters().remove(paramInfo.getName());
        }
    }
}
