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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Check;
import org.apache.wicket.markup.html.form.CheckGroup;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.SetModel;
import org.danekja.java.util.function.serializable.SerializableFunction;

/**
 * A labelled checkbox group, the counterpart of {@link RadioGroupParamPanel}; its model is the set of checked choice
 * codes.
 *
 * <p>The checked set is panel state, never bound to a store's connection parameters: it only picks the fields shown,
 * and a store such as STAC deliberately has no {@code storage.provider} key.
 *
 * <p>Adapted from GeoServer's {@code RadioGroupParamPanel} (c) Open Source Geospatial Foundation, GPL-2.0.
 */
@SuppressWarnings("serial")
public class CheckGroupParamPanel extends Panel {

    private final CheckGroup<String> group;

    /**
     * @param initiallyChecked copied into the panel's own model
     * @param choices in render order
     */
    public CheckGroupParamPanel(
            String id,
            IModel<String> label,
            Set<String> initiallyChecked,
            List<String> choices,
            SerializableFunction<String, IModel<String>> choiceLabels) {
        super(id);
        group = new CheckGroup<>("group", new SetModel<>(new LinkedHashSet<>(initiallyChecked)));
        group.add(new DynamicCheckChoices("choices", choices, choiceLabels));
        add(new Label("paramName", label));
        add(group);
    }

    public CheckGroup<String> getFormComponent() {
        return group;
    }

    private static class DynamicCheckChoices extends ListView<String> {

        private final SerializableFunction<String, IModel<String>> choiceLabels;

        DynamicCheckChoices(
                String id, List<String> choices, SerializableFunction<String, IModel<String>> choiceLabels) {
            super(id, choices);
            this.choiceLabels = choiceLabels;
        }

        @Override
        protected void populateItem(ListItem<String> item) {
            item.add(new StableValueCheck("paramValue", item.getModel()));
            IModel<String> labelModel = labelModel(item.getModelObject());
            item.add(new Label("label", labelModel));
        }

        private IModel<String> labelModel(String code) {
            IModel<String> labelModel = choiceLabels.apply(code);
            if (labelModel == null) {
                labelModel = new Model<>(code);
            }
            return labelModel;
        }
    }

    /**
     * Submits its choice code, stable across the list's per-render rebuilds, rather than Wicket's generated value; this
     * keeps the selection when the form re-renders after a failed submit.
     */
    private static final class StableValueCheck extends Check<String> {

        StableValueCheck(String id, IModel<String> code) {
            super(id, code);
        }

        @Override
        public String getValue() {
            return getModelObject();
        }
    }
}
