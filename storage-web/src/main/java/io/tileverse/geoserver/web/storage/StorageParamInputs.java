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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.web.data.store.panel.CheckBoxParamPanel;
import org.geoserver.web.data.store.panel.PasswordParamPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;

import io.tileverse.storage.StorageParameter;

/** Picks and builds the input widget for one storage parameter. */
final class StorageParamInputs {

    private static final String FIELD_ID = GroupedParamPanel.FIELD_ID;

    private StorageParamInputs() {}

    static Panel inputFor(IModel<Map<String, Serializable>> connectionParameters, StorageParameter<?> parameter) {
        Panel input = widgetFor(connectionParameters, parameter);
        addTooltip(input, parameter);
        return input;
    }

    /**
     * The declared default in the form written by the parameter's field: typed behind a checkbox or a number field, as
     * text behind the other fields, a duration as its ISO-8601 text.
     */
    static Optional<Serializable> storedDefault(StorageParameter<?> parameter) {
        Optional<?> declaredDefault = parameter.defaultValue();
        return declaredDefault.map(StorageParamInputs::storedForm);
    }

    private static Serializable storedForm(Object declaredDefault) {
        if (declaredDefault instanceof Boolean flag) {
            return flag;
        }
        if (declaredDefault instanceof Number number) {
            return number;
        }
        return declaredDefault.toString();
    }

    private static Panel widgetFor(
            IModel<Map<String, Serializable>> connectionParameters, StorageParameter<?> parameter) {
        String key = parameter.key();
        IModel<String> label = new ResourceModel(key, key);
        if (parameter.password()) {
            return new PasswordParamPanel(FIELD_ID, stringModel(connectionParameters, key), label, false);
        }
        if (parameter.type() == Boolean.class) {
            IModel<Boolean> model = flagModel(connectionParameters, parameter);
            return new CheckBoxParamPanel(FIELD_ID, model, label);
        }
        if (parameter.type() == Duration.class) {
            return new DurationParamPanel(FIELD_ID, stringModel(connectionParameters, key), label);
        }
        if (!parameter.sampleValues().isEmpty()) {
            return searchableDropdown(connectionParameters, parameter, label);
        }
        if (Number.class.isAssignableFrom(parameter.type())) {
            return numberField(connectionParameters, key, label, parameter.type());
        }
        return new TextParamPanel<>(FIELD_ID, stringModel(connectionParameters, key), label, false);
    }

    /**
     * Shows a description of the parameter on hover, as GeoServer's stock parameter list does: the bundle entry named
     * after the parameter key for the language of the session, else the description declared by the provider.
     */
    private static void addTooltip(Panel input, StorageParameter<?> parameter) {
        IModel<String> tooltip = Tooltips.of(parameter.key(), input, parameter.description());
        input.add(AttributeModifier.replace("title", tooltip));
    }

    /**
     * A flag without a declared default stays out of the store while its box is unticked, leaving the choice to the
     * engine. A flag with one is stored ticked or not: the section seeds its default.
     */
    private static IModel<Boolean> flagModel(
            IModel<Map<String, Serializable>> connectionParameters, StorageParameter<?> parameter) {
        StorageParamModel<Boolean> stored =
                new StorageParamModel<>(connectionParameters, parameter.key(), Boolean.class);
        if (parameter.defaultValue().isPresent()) {
            return stored;
        }
        return new OptionalFlagModel(stored);
    }

    private static Select2ChoiceParamPanel<String> searchableDropdown(
            IModel<Map<String, Serializable>> connectionParameters,
            StorageParameter<?> parameter,
            IModel<String> label) {
        List<String> options =
                parameter.sampleValues().stream().map(String::valueOf).sorted().toList();
        IModel<String> model = stringModel(connectionParameters, parameter.key());
        return Select2ChoiceParamPanel.ofStrings(FIELD_ID, label, model, options)
                .allowCustomValues(true);
    }

    /** The number types declared by the providers compare to themselves, as required by a number input. */
    @SuppressWarnings("unchecked")
    private static <N extends Number & Comparable<N>> NumberParamPanel<N> numberField(
            IModel<Map<String, Serializable>> connectionParameters,
            String key,
            IModel<String> label,
            Class<?> declaredType) {
        Class<N> type = (Class<N>) declaredType;
        IModel<N> model = new StorageParamModel<>(connectionParameters, key, type);
        return new NumberParamPanel<>(FIELD_ID, model, label, type);
    }

    private static StorageParamModel<String> stringModel(
            IModel<Map<String, Serializable>> connectionParameters, String key) {
        return new StorageParamModel<>(connectionParameters, key, String.class);
    }
}
