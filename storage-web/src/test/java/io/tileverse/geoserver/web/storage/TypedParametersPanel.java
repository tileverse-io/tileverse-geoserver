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

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;

/** The storage section with a link reading its typed parameters as Browse does: with no form processing. */
public class TypedParametersPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private final StorageParamsPanel storage;

    /** Null until read, or after a value failed its conversion. */
    private Properties lastRead;

    private boolean converted;

    public TypedParametersPanel(String id, IModel<Map<String, Serializable>> params, BackendSelection selection) {
        super(id);
        storage = new StorageParamsPanel("storage", params, selection, true);
        add(storage);
        AjaxSubmitLink read = new AjaxSubmitLink("read") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                Optional<Properties> typed = storage.typedParameters();
                converted = typed.isPresent();
                lastRead = typed.orElse(null);
            }
        };
        read.setDefaultFormProcessing(false);
        add(read);
    }

    StorageParamsPanel storage() {
        return storage;
    }

    Properties lastRead() {
        return lastRead;
    }

    boolean converted() {
        return converted;
    }
}
