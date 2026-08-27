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
        addTooltip(input, parameter.description());
        return input;
    }

    private static Panel widgetFor(
            IModel<Map<String, Serializable>> connectionParameters, StorageParameter<?> parameter) {
        String key = parameter.key();
        IModel<String> label = new ResourceModel(key, key);
        if (parameter.password()) {
            return new PasswordParamPanel(FIELD_ID, stringModel(connectionParameters, key), label, false);
        }
        if (parameter.type() == Boolean.class) {
            IModel<Boolean> model = new StorageParamModel<>(connectionParameters, key, Boolean.class);
            return new CheckBoxParamPanel(FIELD_ID, model, label);
        }
        if (parameter.type() == Duration.class) {
            return new DurationParamPanel(FIELD_ID, stringModel(connectionParameters, key), label);
        }
        if (!parameter.sampleValues().isEmpty()) {
            return searchableDropdown(connectionParameters, parameter, label);
        }
        if (Number.class.isAssignableFrom(parameter.type())) {
            return numberField(connectionParameters, key, label, numberType(parameter));
        }
        return new TextParamPanel<>(FIELD_ID, stringModel(connectionParameters, key), label, false);
    }

    /** Shows the parameter description on hover, as GeoServer's stock parameter list does. */
    private static void addTooltip(Panel input, String description) {
        if (!description.isBlank()) {
            input.add(AttributeModifier.replace("title", description));
        }
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

    /** Converts the input to {@code type}, as GeoServer's stock panel does. */
    private static <N extends Serializable> TextParamPanel<N> numberField(
            IModel<Map<String, Serializable>> connectionParameters, String key, IModel<String> label, Class<N> type) {
        IModel<N> model = new StorageParamModel<>(connectionParameters, key, type);
        TextParamPanel<N> field = new TextParamPanel<>(FIELD_ID, model, label, false);
        field.getFormComponent().setType(type);
        return field;
    }

    private static StorageParamModel<String> stringModel(
            IModel<Map<String, Serializable>> connectionParameters, String key) {
        return new StorageParamModel<>(connectionParameters, key, String.class);
    }

    /** Every number type declared by the providers is {@link Serializable}. */
    @SuppressWarnings("unchecked")
    private static Class<? extends Serializable> numberType(StorageParameter<?> parameter) {
        return (Class<? extends Serializable>) parameter.type();
    }
}
