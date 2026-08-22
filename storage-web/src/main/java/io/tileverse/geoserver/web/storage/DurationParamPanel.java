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

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.validation.FormComponentFeedbackBorder;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.geoserver.web.data.store.panel.ParamPanel;

/**
 * A labelled duration input for a Duration-typed connection parameter: an integer amount beside a time-unit dropdown in
 * place of a raw ISO-8601 text box, over the same stored string value. Follows the label + feedback-border +
 * form-component shape of GeoServer's {@code TextParamPanel}; the composition itself lives in {@link DurationField}.
 */
@SuppressWarnings("serial")
class DurationParamPanel extends Panel implements ParamPanel<String> {

    private final DurationField durationField;

    DurationParamPanel(String id, IModel<String> paramValue, IModel<String> paramLabelModel) {
        super(id, paramValue);
        add(new Label("paramName", paramLabelModel));
        durationField = new DurationField("paramValue", paramValue);
        durationField.setLabel(paramLabelModel);
        FormComponentFeedbackBorder border = new FormComponentFeedbackBorder("border");
        border.add(durationField);
        add(border);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        PackageResourceReference stylesheet =
                new PackageResourceReference(DurationParamPanel.class, "DurationParamPanel.css");
        response.render(CssHeaderItem.forReference(stylesheet));
    }

    @Override
    public FormComponent<String> getFormComponent() {
        return durationField;
    }
}
