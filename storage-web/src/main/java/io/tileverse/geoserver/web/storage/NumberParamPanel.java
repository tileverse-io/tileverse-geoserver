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
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.validation.FormComponentFeedbackBorder;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.geoserver.web.data.store.panel.ParamPanel;

/**
 * A labelled number input, laid out like GeoServer's {@code TextParamPanel} around Wicket's {@link NumberTextField}.
 * The browser keeps letters out of the field, and the value is written and parsed in plain digits regardless of the
 * locale.
 *
 * <p>The bounds and the step are those of the {@link NumberTextField}: none unless set, as values or as models read at
 * each render. A value out of the bounds is rejected on submit too.
 *
 * @param <N> the number type of the parameter
 */
@SuppressWarnings("serial")
public class NumberParamPanel<N extends Number & Comparable<N>> extends Panel implements ParamPanel<N> {

    private final NumberTextField<N> numberField;

    /**
     * @param id the component id
     * @param paramValue the model of the parameter value
     * @param paramLabelModel the label of the field
     * @param type the number type, converting the input
     */
    public NumberParamPanel(String id, IModel<N> paramValue, IModel<String> paramLabelModel, Class<N> type) {
        super(id, paramValue);
        add(new Label("paramName", paramLabelModel));
        numberField = new NumberTextField<>("paramValue", paramValue, type);
        numberField.setLabel(paramLabelModel);
        FormComponentFeedbackBorder border = new FormComponentFeedbackBorder("border");
        border.add(numberField);
        add(border);
    }

    /**
     * @param minimum the lowest value accepted
     * @return this panel
     */
    public NumberParamPanel<N> setMinimum(N minimum) {
        numberField.setMinimum(minimum);
        return this;
    }

    /**
     * @param minimum the model of the lowest value accepted
     * @return this panel
     */
    public NumberParamPanel<N> setMinimum(IModel<N> minimum) {
        numberField.setMinimum(minimum);
        return this;
    }

    /**
     * @param maximum the highest value accepted
     * @return this panel
     */
    public NumberParamPanel<N> setMaximum(N maximum) {
        numberField.setMaximum(maximum);
        return this;
    }

    /**
     * @param maximum the model of the highest value accepted
     * @return this panel
     */
    public NumberParamPanel<N> setMaximum(IModel<N> maximum) {
        numberField.setMaximum(maximum);
        return this;
    }

    /**
     * @param step the distance between two values offered by the browser; {@link NumberTextField#ANY} lifts it
     * @return this panel
     */
    public NumberParamPanel<N> setStep(N step) {
        numberField.setStep(step);
        return this;
    }

    /**
     * @param step the model of the distance between two values offered by the browser
     * @return this panel
     */
    public NumberParamPanel<N> setStep(IModel<N> step) {
        numberField.setStep(step);
        return this;
    }

    @Override
    public FormComponent<N> getFormComponent() {
        return numberField;
    }
}
