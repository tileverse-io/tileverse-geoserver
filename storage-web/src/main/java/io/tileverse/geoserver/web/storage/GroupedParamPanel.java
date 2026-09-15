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
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * One parameter field with an optional group header; hiding the wrapper hides both, leaving no orphan header behind a
 * deselected group.
 */
@SuppressWarnings("serial")
class GroupedParamPanel extends Panel {

    /** The wicket id required of the wrapped field. */
    static final String FIELD_ID = "field";

    GroupedParamPanel(String id, Panel field, IModel<String> headerOrNull) {
        super(id);
        add(sectionHeader(headerOrNull));
        add(field);
    }

    private static Label sectionHeader(IModel<String> headerOrNull) {
        Label header = new Label("header", headerOrNull != null ? headerOrNull : Model.of(""));
        header.setVisible(headerOrNull != null);
        return header;
    }
}
