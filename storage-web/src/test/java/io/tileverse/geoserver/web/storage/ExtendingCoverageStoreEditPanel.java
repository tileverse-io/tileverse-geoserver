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

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;

/** A coverage store panel with markup of its own: one field below the URL field of the base panel. */
@SuppressWarnings({"serial", "java:S110"}) // as the base panel: no serialVersionUID, the deep hierarchy is GeoServer's
class ExtendingCoverageStoreEditPanel extends StorageAwareCoverageStoreEditPanel {

    static final String OWN_FIELD_ID = "ownField";

    static final String OWN_FIELD_TEXT = "a field of the subclass";

    ExtendingCoverageStoreEditPanel(String id, Form storeEditForm) {
        super(id, storeEditForm);
        add(new Label(OWN_FIELD_ID, OWN_FIELD_TEXT));
    }

    @Override
    protected String urlPlaceholderKey() {
        return "ExtendingCoverageStoreEditPanel.urlPlaceholder";
    }
}
