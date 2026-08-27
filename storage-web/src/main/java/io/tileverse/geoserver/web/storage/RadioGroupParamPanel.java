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

import static org.geoserver.web.util.WebUtils.IsWicketCssFileEmpty;

import java.io.Serializable;
import java.util.List;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.geoserver.web.data.store.panel.ParamPanel;

// Adapted from GeoServer's pmtiles-store community module, modified by Multiversio LLC in 2026.
// (c) 2025 Open Source Geospatial Foundation - all rights reserved
// This code is licensed under the GPL 2.0 license, available at the root
// application directory.

/** A segmented radio choice panel, rendering one {@link Radio} per choice inside a {@link RadioGroup}. */
@SuppressWarnings("serial")
public class RadioGroupParamPanel<T extends Serializable> extends Panel implements ParamPanel<T> {

    private static final boolean isCssEmpty = IsWicketCssFileEmpty(RadioGroupParamPanel.class);

    private RadioGroup<T> group;

    /** The radio choices, kept to set their tooltips after construction. */
    private DynamicRadioChoices<T> choiceItems;

    public RadioGroupParamPanel(String id, IModel<String> label, IModel<T> model, List<T> choices) {
        this(id, label, model, choices, opt -> null);
    }

    public RadioGroupParamPanel(
            String id,
            IModel<String> label,
            IModel<T> model,
            List<T> choices,
            SerializableFunction<T, IModel<String>> choiceLabels) {
        super(id, model);

        group = new RadioGroup<>("group", model);
        choiceItems = new DynamicRadioChoices<>("choices", choices, choiceLabels);
        group.add(choiceItems);
        add(new Label("paramName", label));
        add(group);
    }

    @Override
    public RadioGroup<T> getFormComponent() {
        return group;
    }

    /** Adds a tooltip to each choice; a function returning {@code null} leaves that choice without one. */
    public RadioGroupParamPanel<T> choiceTooltips(SerializableFunction<T, IModel<String>> tooltips) {
        choiceItems.tooltips = tooltips;
        return this;
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // if the panel-specific CSS file contains actual css then have the browser load the css
        if (!isCssEmpty) {
            @SuppressWarnings("rawtypes")
            Class<? extends RadioGroupParamPanel> scope = getClass();
            String name = scope.getSimpleName() + ".css";
            PackageResourceReference reference = new PackageResourceReference(scope, name);
            response.render(CssHeaderItem.forReference(reference));
        }
    }

    /** ListView to dynamically generate the radios */
    private static class DynamicRadioChoices<I> extends ListView<I> {

        private SerializableFunction<I, IModel<String>> choiceLabels;

        /** Tooltip per choice, set after construction through {@link RadioGroupParamPanel#choiceTooltips}. */
        private SerializableFunction<I, IModel<String>> tooltips;

        DynamicRadioChoices(String id, List<I> choices, SerializableFunction<I, IModel<String>> choiceLabels) {
            super(id, choices);
            this.choiceLabels = choiceLabels;
        }

        @Override
        protected void populateItem(ListItem<I> item) {
            // Add a Radio component to the group, using the item's model object as the value
            item.add(new Radio<>("paramValue", item.getModel()));
            // Add a Label for the radio button
            IModel<String> labelModel = labelModel(item.getModelObject());
            item.add(new Label("label", labelModel));
            addTooltipIfConfigured(item);
        }

        private void addTooltipIfConfigured(ListItem<I> item) {
            if (tooltips == null) {
                return;
            }
            IModel<String> tooltipModel = tooltips.apply(item.getModelObject());
            if (tooltipModel != null) {
                item.add(AttributeModifier.replace("title", tooltipModel));
            }
        }

        private IModel<String> labelModel(I modelObject) {
            IModel<String> labelModel = choiceLabels.apply(modelObject);
            if (labelModel == null) {
                labelModel = new Model<>(String.valueOf(modelObject));
            }
            return labelModel;
        }
    }
}
