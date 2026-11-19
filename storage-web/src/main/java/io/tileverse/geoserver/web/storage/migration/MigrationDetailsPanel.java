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
package io.tileverse.geoserver.web.storage.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.danekja.java.util.function.serializable.SerializableFunction;

import io.tileverse.geoserver.web.storage.migration.MigrationNote.Severity;

/**
 * The details of one store in the migration page: its parameters before and after the migration, the findings of its
 * last check, its layers and the failure of its last job. The values are shown as given: the service masks their
 * secrets. The panel hides itself while there are no details to show.
 */
final class MigrationDetailsPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private static final Comparator<MigrationNote> WORST_FIRST =
            Comparator.comparing(MigrationNote::severity).reversed();

    /** The details of the store shown, null when there is none. Loaded again on each request. */
    private final IModel<MigrationDetails> details;

    MigrationDetailsPanel(String id, IModel<MigrationDetails> details) {
        super(id, details);
        this.details = details;
        setOutputMarkupPlaceholderTag(true);
        add(parameterSection("beforeSection", MigrationDetails::before));
        add(parameterSection("afterSection", MigrationDetails::after));
        add(notesSection());
        add(layersSection());
        add(failureSection());
    }

    /**
     * The label of a note in the language of the session, from the bundles of the plugin making the note. A key without
     * a label shows as is: a missing label must not break the page.
     */
    static String textOf(MigrationNote note, Component component) {
        String key = note.messageKey();
        Object[] arguments = note.arguments().toArray();
        StringResourceModel label = new StringResourceModel(key, component).setParameters(arguments);
        label.setDefaultValue(key);
        return label.getString();
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
        setVisible(details.getObject() != null);
    }

    /** A table of the parameters of one side of the migration, hidden when that side has none. */
    private WebMarkupContainer parameterSection(
            String id, SerializableFunction<MigrationDetails, Map<String, String>> side) {
        IModel<Map<String, String>> parameters = details.map(side);
        IModel<List<String>> keys = parameters.map(map -> new ArrayList<>(map.keySet()));
        ListView<String> rows = new ListView<>("parameters", keys) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<String> item) {
                String key = item.getModelObject();
                item.add(new Label("key", key));
                item.add(new Label("value", parameters.getObject().get(key)));
            }
        };
        WebMarkupContainer section = sectionShowing(id, keys);
        section.add(rows);
        return section;
    }

    private WebMarkupContainer notesSection() {
        IModel<List<MigrationNote>> notes = details.map(MigrationDetails::notes);
        WebMarkupContainer section = sectionShowing("notesSection", notes);
        section.add(noteList("notes", notes));
        return section;
    }

    /** One row per layer with its native names before and after the migration, and its notes below. */
    private WebMarkupContainer layersSection() {
        IModel<List<LayerComparison>> layers = details.map(MigrationDetails::layers);
        ListView<LayerComparison> rows = new ListView<>("layers", layers) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<LayerComparison> item) {
                LayerComparison layer = item.getModelObject();
                item.add(new Label("layer", layer.layerName()));
                item.add(new Label("nativeBefore", layer.nativeNameBefore()));
                item.add(new Label("nativeAfter", layer.nativeNameAfter()));
                IModel<List<MigrationNote>> notes = item.getModel().map(LayerComparison::notes);
                WebMarkupContainer notesRow = sectionShowing("layerNotesRow", notes);
                notesRow.add(noteList("layerNotes", notes));
                item.add(notesRow);
            }
        };
        WebMarkupContainer section = sectionShowing("layersSection", layers);
        section.add(rows);
        return section;
    }

    /** The failure text may be a label key, such as the one of a store gone, or a text to show as is. */
    private WebMarkupContainer failureSection() {
        IModel<String> failure = details.map(MigrationDetails::failure);
        IModel<String> text = failure.map(shown -> getString(shown, null, shown));
        WebMarkupContainer section = new WebMarkupContainer("failureSection") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(failure.getObject() != null);
            }
        };
        section.add(new Label("failure", text));
        return section;
    }

    /** The notes with the blockers first, then the differences, then the other facts. */
    private ListView<MigrationNote> noteList(String id, IModel<List<MigrationNote>> notes) {
        IModel<List<MigrationNote>> worstFirst = notes.map(MigrationDetailsPanel::worstFirst);
        return new ListView<>(id, worstFirst) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<MigrationNote> item) {
                MigrationNote note = item.getModelObject();
                item.add(AttributeModifier.replace("class", feedbackClassOf(note.severity())));
                item.add(new Label("text", textOf(note, MigrationDetailsPanel.this)));
            }
        };
    }

    private static List<MigrationNote> worstFirst(List<MigrationNote> notes) {
        return notes.stream().sorted(WORST_FIRST).toList();
    }

    /** GeoServer styles its feedback messages by these classes. */
    private static String feedbackClassOf(Severity severity) {
        return switch (severity) {
            case BLOCKER -> "feedbackPanelERROR";
            case DIFFERENCE -> "feedbackPanelWARNING";
            case INFO -> "feedbackPanelINFO";
        };
    }

    /** A container shown when its content is not empty. */
    private static WebMarkupContainer sectionShowing(String id, IModel<? extends Collection<?>> content) {
        return new WebMarkupContainer(id) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                Collection<?> shown = content.getObject();
                setVisible(shown != null && !shown.isEmpty());
            }
        };
    }
}
