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

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/** Hosts a {@link DurationParamPanel} inside a plain form for the WicketTester render and submit tests. */
@SuppressWarnings("serial")
class DurationParamPanelTestPage extends WebPage {

    final Form<Void> form;
    final DurationParamPanel panel;

    DurationParamPanelTestPage(IModel<String> paramValue) {
        form = new Form<>("form");
        add(form);
        panel = new DurationParamPanel("panel", paramValue, Model.of("Max retry delay"));
        form.add(panel);
    }
}
