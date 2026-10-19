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
package io.tileverse.geoserver.web.storage.browser;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.feedback.ComponentFeedbackMessageFilter;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.FormComponentFeedbackBorder;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.validation.IValidator;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.geoserver.web.wicket.GSModalWindow;

/**
 * A store location field with a "Browse..." button, laid out like GeoServer's {@code FileInput}. The button opens a
 * {@link StorageBrowserPanel} over the storage named by the typed location and storage parameters, without validating
 * or saving the form, and a choice goes back into the field.
 */
public class StorageLocationField extends Panel implements ParamPanel<String> {

    private static final long serialVersionUID = 1L;
    private static final String REQUIRED_MARK = " *";
    private static final int DIALOG_WIDTH = 900;
    private static final int DIALOG_HEIGHT = 600;

    private final IModel<String> label;
    private final StorageBrowsing browsing;
    private final TypedStorageParameters typedParameters;
    private final TextField<String> textField;
    private final FeedbackPanel feedback;
    private final GSModalWindow dialog;

    /** The token of the browser of the open dialog; null while it is closed. */
    private String token;

    @SafeVarargs
    public StorageLocationField(
            String id,
            IModel<String> location,
            IModel<String> label,
            boolean required,
            StorageBrowsing browsing,
            TypedStorageParameters typedParameters,
            IValidator<String>... validators) {
        super(id, location);
        this.label = label;
        this.browsing = browsing;
        this.typedParameters = typedParameters;
        IModel<String> labelText = () -> label.getObject() + (required ? REQUIRED_MARK : "");
        add(new Label("paramName", labelText));
        this.textField = textField(location, label, required, validators);
        FormComponentFeedbackBorder border = new FormComponentFeedbackBorder("border");
        border.add(textField);
        border.add(browseLink());
        add(border);
        this.feedback = new FeedbackPanel("feedback", new ComponentFeedbackMessageFilter(this));
        feedback.setOutputMarkupId(true);
        add(feedback);
        this.dialog = new GSModalWindow("dialog");
        dialog.setInitialWidth(DIALOG_WIDTH);
        dialog.setInitialHeight(DIALOG_HEIGHT);
        dialog.setWindowClosedCallback(target -> closeBrowser());
        add(dialog);
    }

    @Override
    public FormComponent<String> getFormComponent() {
        return textField;
    }

    @Override
    public void onEvent(IEvent<?> event) {
        if (event.getPayload() instanceof LocationChosen chosen) {
            event.stop();
            writeChoice(chosen.location(), chosen.target());
        }
    }

    private static TextField<String> textField(
            IModel<String> location, IModel<String> label, boolean required, IValidator<String>[] validators) {
        TextField<String> field = new TextField<>("paramValue", location);
        field.setRequired(required);
        field.setLabel(label);
        field.setOutputMarkupId(true);
        for (IValidator<String> validator : validators) {
            field.add(validator);
        }
        return field;
    }

    /** Submits the form without processing it: no validation runs and no model changes. */
    private AjaxSubmitLink browseLink() {
        AjaxSubmitLink link = new AjaxSubmitLink("browse") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                browse(target);
            }
        };
        link.setDefaultFormProcessing(false);
        return link;
    }

    /**
     * Opens the dialog over what the form holds now: the typed location and storage parameters. A storage parameter
     * failing its conversion shows its error, as a save does, and the dialog stays closed.
     */
    private void browse(AjaxRequestTarget target) {
        Optional<Properties> parameters = typedParameters.read();
        if (parameters.isEmpty()) {
            repaintFeedbackPanels(target);
            return;
        }
        // A second click can arrive before the dialog shows: the first browser would stay open until idle.
        closeBrowser();
        try {
            token = StorageBrowserSessions.shared().open(typedLocation(), parameters.get(), browsing);
        } catch (BrowseException failure) {
            error(BrowseMessages.of(failure, this));
            target.add(feedback);
            return;
        }
        dialog.setContent(new StorageBrowserPanel(dialog.getContentId(), token, browsing));
        dialog.setTitle(label);
        dialog.show(target);
    }

    /**
     * Repaints the feedback panels of the page: the failed fields report their errors there. Panels without a markup
     * id, as built by some store pages, are skipped: a target refuses them.
     */
    private void repaintFeedbackPanels(AjaxRequestTarget target) {
        getPage().visitChildren(FeedbackPanel.class, (FeedbackPanel panel, IVisit<Void> visit) -> {
            if (panel.getOutputMarkupId()) {
                target.add(panel);
            }
        });
    }

    /** The text submitted in the field, unconverted: an empty field browses the local roots. */
    private String typedLocation() {
        String input = textField.getInput();
        if (input == null) {
            return "";
        }
        return input.trim();
    }

    private void writeChoice(URI location, AjaxRequestTarget target) {
        textField.clearInput();
        textField.setModelObject(textOf(location));
        target.add(textField);
        dialog.close(target);
    }

    /** A local choice is an absolute path for a store asking for one; a folder then ends with the separator. */
    private String textOf(URI location) {
        boolean local = "file".equalsIgnoreCase(location.getScheme());
        if (!browsing.localPaths() || !local) {
            return location.toString();
        }
        String path = Path.of(location).toString();
        if (location.getPath().endsWith("/")) {
            return endingWithSeparator(path);
        }
        return path;
    }

    /** A file system root, such as {@code /}, already ends with the separator. */
    private static String endingWithSeparator(String path) {
        if (path.endsWith(File.separator)) {
            return path;
        }
        return path + File.separator;
    }

    private void closeBrowser() {
        if (token != null) {
            StorageBrowserSessions.shared().close(token);
            token = null;
        }
    }
}
