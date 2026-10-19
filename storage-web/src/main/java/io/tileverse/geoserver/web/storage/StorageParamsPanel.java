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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormChoiceComponentUpdatingBehavior;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.IFormModelUpdateListener;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.util.visit.IVisitor;
import org.geoserver.web.data.store.panel.ParamPanel;

import io.tileverse.storage.StorageConfig;
import io.tileverse.storage.StorageParameter;

/**
 * The tileverse storage section of a store edit page, shared by the vector and raster store panels: a backend selector
 * and one row per {@code storage.*} parameter, grouped under a header per backend, showing only the selected backends'
 * rows.
 *
 * <p>A store keeps the parameters of its selected backends only: the section stores their declared defaults as soon as
 * their rows show, and a submit drops the parameters of the other backends. A stored default keeps the store stable
 * across a change of the engine's default. A Boolean parameter without a declared default is stored once its box is
 * ticked, and left to the engine until then.
 *
 * <p>A single-provider store naming no provider is served by the backend selected by its URL, and opens with that
 * backend selected.
 */
@SuppressWarnings("serial")
public class StorageParamsPanel extends Panel implements IFormModelUpdateListener {

    /** How the user selects the backends to show. */
    public enum BackendSelection {
        /** A radio bound to {@code storage.provider}, for a store reading from one backend at a time. */
        SINGLE_PROVIDER,
        /**
         * One checkbox per backend, pre-checked from the store's keys, for a store whose items may live on several
         * backends; the checked set is UI state, never a connection parameter.
         */
        MULTIPLE_BACKENDS
    }

    private static final String PROVIDER_KEY = StorageConfig.PROVIDER_ID_KEY;
    private static final String SELECTOR_ID = "selector";
    private static final String ROWS_ID = "rows";
    private static final String GROUP_LABEL_PREFIX = "storage.group.";
    private static final String PROVIDER_LABEL_PREFIX = PROVIDER_KEY + ".";
    private static final String CACHING_GROUP = "caching";

    private final IModel<Map<String, Serializable>> connectionParameters;
    private final BackendSelection selection;

    /** Keyed by parameter key, in render order. */
    private final Map<String, Component> rowsByKey = new LinkedHashMap<>();

    /** Keyed by parameter key, in the form written by the parameter's field. */
    private final Map<String, Serializable> declaredDefaults;

    /** Set only for {@link BackendSelection#MULTIPLE_BACKENDS}. */
    private CheckGroupParamPanel backendSelector;

    /** A section for a store with no URL to read: a single-provider store opens on its stored provider only. */
    public StorageParamsPanel(
            String id,
            IModel<Map<String, Serializable>> connectionParameters,
            BackendSelection selection,
            boolean cachingParameters) {
        this(id, connectionParameters, new Model<>(), selection, cachingParameters);
    }

    /**
     * @param location the store URL, read once, when the panel opens; a single-provider store naming no provider opens
     *     on the backend selected by it
     */
    public StorageParamsPanel(
            String id,
            IModel<Map<String, Serializable>> connectionParameters,
            IModel<String> location,
            BackendSelection selection,
            boolean cachingParameters) {
        super(id);
        this.connectionParameters = connectionParameters;
        this.selection = selection;
        if (!cachingParameters) {
            dropStoredCachingSettings();
        }
        if (selection == BackendSelection.SINGLE_PROVIDER) {
            selectTheProviderOfTheLocationUnlessStored(location);
        }
        List<StorageParameter<?>> parameters = StorageParams.orderedParameters(cachingParameters);
        this.declaredDefaults = declaredDefaults(parameters);
        add(selector());
        add(rows(parameters));
        seedDeclaredDefaults();
    }

    /** The backend groups whose rows show: the selected provider's groups, or the checked backends. */
    public Set<String> selectedGroups() {
        if (selection == BackendSelection.MULTIPLE_BACKENDS) {
            return new LinkedHashSet<>(backendSelector.getFormComponent().getModelObject());
        }
        String providerId = providerIdModel().getObject();
        return StorageParamVisibility.groupsForProvider(providerId);
    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();
        applyVisibility(null);
    }

    /**
     * Runs on a form submit, after the fields wrote their values; GeoServer's own save hook skips the raster store
     * pages. Dropping the unselected backends here, not on a selection change, keeps their values while the user
     * toggles the selector.
     */
    @Override
    public void updateModel() {
        dropParametersOfUnselectedBackends();
    }

    /** The row rendering {@code key}, toggled with its backend group; null when the section omits the parameter. */
    public Component rowFor(String key) {
        return rowsByKey.get(key);
    }

    /** The widget rendering {@code key}. */
    public Panel fieldFor(String key) {
        return (Panel) rowFor(key).get(GroupedParamPanel.FIELD_ID);
    }

    /**
     * The storage parameters as typed in the section, for browsing before a save: the stored values of the selected
     * backends, overridden by the input submitted in their fields. A field showing its stored value keeps it: an
     * untouched secret submits GeoServer's placeholder, never the secret. The caching parameters are left out, since
     * they tune the reads of the store, not browsing. An unticked flag without a declared default is left out, as in a
     * save, except a stored false: that one stays. A value failing its conversion reports the error on its field, as a
     * save does. No model changes.
     *
     * @return the parameters as text, or empty when a value failed its conversion
     */
    public Optional<Properties> typedParameters() {
        Set<String> selectedGroups = selectedGroups();
        Properties typed = storedParameters(selectedGroups);
        boolean converted = true;
        for (String key : typedKeys(selectedGroups)) {
            boolean read = readTypedValue(key, typed);
            converted = converted && read;
        }
        if (!converted) {
            return Optional.empty();
        }
        return Optional.of(typed);
    }

    private void applyVisibility(AjaxRequestTarget targetOrNull) {
        ComponentVisibility.apply(rowsByKey, selectedGroups(), targetOrNull);
    }

    /** The keys of the fields shown, apart from the caching parameters. */
    private List<String> typedKeys(Set<String> selectedGroups) {
        List<String> keys = new ArrayList<>();
        for (String key : rowsByKey.keySet()) {
            if (isTypedKey(key, selectedGroups)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** The stored values of the selected backends and of the provider, as text. */
    private Properties storedParameters(Set<String> selectedGroups) {
        Properties stored = new Properties();
        connectionParameters.getObject().forEach((key, value) -> {
            if (value != null && isTypedKey(key, selectedGroups)) {
                stored.setProperty(key, String.valueOf(value));
            }
        });
        return stored;
    }

    /** A {@code storage.*} parameter shown for the selected backends, apart from the caching ones. */
    private static boolean isTypedKey(String key, Set<String> selectedGroups) {
        boolean storageKey = !StorageParamVisibility.groupOf(key).isEmpty();
        return storageKey && !isCachingKey(key) && StorageParamVisibility.isVisible(key, selectedGroups);
    }

    /**
     * Converts the input of the field of {@code key}, its inner fields first: a duration combines its amount and its
     * unit. An input equal to the value shown leaves the stored value in {@code typed}.
     *
     * @return false when the input failed its conversion
     */
    private boolean readTypedValue(String key, Properties typed) {
        FormComponent<?> field = ((ParamPanel<?>) fieldFor(key)).getFormComponent();
        IVisitor<FormComponent<?>, Void> convert = (component, visit) -> component.validate();
        FormComponent.visitFormComponentsPostOrder(field, convert);
        if (!field.isValid()) {
            return false;
        }
        Object input = field.getConvertedInput();
        if (Objects.equals(input, field.getModelObject())) {
            return true;
        }
        if (input == null || isUntickedOptionalFlag(field, input)) {
            typed.remove(key);
        } else {
            typed.setProperty(key, String.valueOf(input));
        }
        return true;
    }

    /**
     * An unticked box leaves an optional flag out, as a save does. The engine then applies its own rule for the flag,
     * such as path style for a custom S3 endpoint. A typed {@code false} would replace that rule.
     *
     * <p>The field tells whether the flag is optional: only the box of an optional flag is built over an
     * {@link OptionalFlagModel}. Asking the field keeps this read in step with the form.
     */
    private static boolean isUntickedOptionalFlag(FormComponent<?> field, Object input) {
        return Boolean.FALSE.equals(input) && field.getDefaultModel() instanceof OptionalFlagModel;
    }

    private void dropParametersOfUnselectedBackends() {
        Set<String> selectedGroups = selectedGroups();
        Set<String> storedKeys = connectionParameters.getObject().keySet();
        storedKeys.removeIf(key -> StorageParamVisibility.belongsToUnselectedBackend(key, selectedGroups));
    }

    /** Nothing on the page could change a stored caching setting once its rows are hidden. */
    private void dropStoredCachingSettings() {
        connectionParameters.getObject().keySet().removeIf(StorageParamsPanel::isCachingKey);
    }

    private static boolean isCachingKey(String key) {
        return CACHING_GROUP.equals(StorageParamVisibility.groupOf(key));
    }

    /**
     * A store saved without a provider is served by the backend selected by its URL. Selecting it here shows the
     * parameters in effect and keeps a save from dropping them.
     */
    private void selectTheProviderOfTheLocationUnlessStored(IModel<String> location) {
        Map<String, Serializable> stored = connectionParameters.getObject();
        if (stored.get(PROVIDER_KEY) != null) {
            return;
        }
        Optional<String> providerId = StorageParams.providerSelectedBy(location.getObject());
        providerId.ifPresent(id -> stored.put(PROVIDER_KEY, id));
    }

    private static Map<String, Serializable> declaredDefaults(List<StorageParameter<?>> parameters) {
        Map<String, Serializable> defaults = new LinkedHashMap<>();
        for (StorageParameter<?> parameter : parameters) {
            Optional<Serializable> storedDefault = StorageParamInputs.storedDefault(parameter);
            storedDefault.ifPresent(value -> defaults.put(parameter.key(), value));
        }
        return defaults;
    }

    private Component selector() {
        if (selection == BackendSelection.MULTIPLE_BACKENDS) {
            backendSelector = backendSelector();
            return backendSelector;
        }
        return providerSelector();
    }

    private RadioGroupParamPanel<String> providerSelector() {
        IModel<String> label = new ResourceModel(PROVIDER_KEY, PROVIDER_KEY);
        RadioGroupParamPanel<String> panel = new RadioGroupParamPanel<>(
                SELECTOR_ID, label, providerIdModel(), StorageParams.providerIds(), StorageParamsPanel::providerLabel);
        panel.choiceTooltips(StorageParamsPanel::providerTooltip);
        panel.getFormComponent().add(showSelectedBackendsOnChange());
        return panel;
    }

    private IModel<String> providerIdModel() {
        return new StorageParamModel<>(connectionParameters, PROVIDER_KEY, String.class);
    }

    private CheckGroupParamPanel backendSelector() {
        IModel<String> label = new ResourceModel("storage.backends", "Storage backends");
        Set<String> initiallyChecked =
                StorageParamVisibility.selectedGroupsFromParameters(connectionParameters.getObject());
        List<String> backends = StorageParamVisibility.selectableGroups();
        CheckGroupParamPanel panel = new CheckGroupParamPanel(
                SELECTOR_ID, label, initiallyChecked, backends, StorageParamsPanel::providerLabel);
        panel.getFormComponent().add(showSelectedBackendsOnChange());
        return panel;
    }

    private AjaxFormChoiceComponentUpdatingBehavior showSelectedBackendsOnChange() {
        return new AjaxFormChoiceComponentUpdatingBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                seedDeclaredDefaults();
                applyVisibility(target);
            }
        };
    }

    /**
     * Stores the declared defaults of the shown parameters lacking a value. Without it, an untouched checkbox would
     * submit {@code false} over a declared default of {@code true}.
     */
    private void seedDeclaredDefaults() {
        Set<String> selectedGroups = selectedGroups();
        Map<String, Serializable> stored = connectionParameters.getObject();
        declaredDefaults.forEach((key, value) -> {
            if (StorageParamVisibility.isVisible(key, selectedGroups)) {
                stored.putIfAbsent(key, value);
            }
        });
    }

    private static IModel<String> providerLabel(String providerId) {
        return new ResourceModel(PROVIDER_LABEL_PREFIX + providerId, providerId);
    }

    private static IModel<String> providerTooltip(String providerId) {
        return new ResourceModel(PROVIDER_LABEL_PREFIX + providerId + ".tooltip", providerId);
    }

    private RepeatingView rows(List<StorageParameter<?>> parameters) {
        RepeatingView rows = new RepeatingView(ROWS_ID);
        String previousGroup = null;
        for (StorageParameter<?> parameter : parameters) {
            boolean firstOfGroup = !parameter.group().equals(previousGroup);
            rows.add(row(rows.newChildId(), parameter, firstOfGroup));
            previousGroup = parameter.group();
        }
        return rows;
    }

    private GroupedParamPanel row(String id, StorageParameter<?> parameter, boolean firstOfGroup) {
        Panel field = StorageParamInputs.inputFor(connectionParameters, parameter);
        IModel<String> header = firstOfGroup ? headerModel(parameter.group()) : null;
        GroupedParamPanel row = new GroupedParamPanel(id, field, header);
        // Ajax can re-render a hidden row only through its placeholder tag
        row.setOutputMarkupPlaceholderTag(true);
        rowsByKey.put(parameter.key(), row);
        return row;
    }

    /** Resolved at render, hence a provider switch retitles the row on its next render. */
    private IModel<String> headerModel(String group) {
        return () -> groupHeaderText(group);
    }

    /**
     * The selected provider's own header when one is declared and applies to {@code group}, otherwise the group's own
     * header, defaulting to the group id.
     */
    private String groupHeaderText(String group) {
        if (selection == BackendSelection.SINGLE_PROVIDER) {
            String providerHeader = selectedProviderHeaderForGroupOrNull(group);
            if (providerHeader != null) {
                return providerHeader;
            }
        }
        return getString(GROUP_LABEL_PREFIX + group, null, group);
    }

    /** {@code null} unless the selected provider declares {@code group} and has its own header resource. */
    private String selectedProviderHeaderForGroupOrNull(String group) {
        String providerId = providerIdModel().getObject();
        if (!StorageParamVisibility.groupsForProvider(providerId).contains(group)) {
            return null;
        }
        String providerHeaderKey = GROUP_LABEL_PREFIX + providerId;
        return getLocalizer().getStringIgnoreSettings(providerHeaderKey, this, null, null);
    }
}
