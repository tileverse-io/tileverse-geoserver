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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.data.store.panel.CheckBoxParamPanel;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.geoserver.web.data.store.panel.PasswordParamPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.tileverse.storage.StorageParameter;
import io.tileverse.storage.spi.StorageProvider;

class StorageParamInputsTest {

    private final Map<String, Serializable> params = new HashMap<>();
    private final IModel<Map<String, Serializable>> paramsModel = Model.ofMap(params);

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><div wicket:id='field'></div></form></body></html>";
            }
        };
        tester.getApplication().getResourceSettings().setThrowExceptionOnMissingResource(false);
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void aSecretRendersAsAPasswordField() {
        assertThat(inputFor(StorageParameter::password)).isInstanceOf(PasswordParamPanel.class);
    }

    @Test
    void aBooleanRendersAsACheckbox() {
        assertThat(inputFor(parameter -> parameter.type() == Boolean.class)).isInstanceOf(CheckBoxParamPanel.class);
    }

    @Test
    void aDurationRendersAsTheDurationWidget() {
        assertThat(inputFor(parameter -> parameter.type() == Duration.class)).isInstanceOf(DurationParamPanel.class);
    }

    @Test
    void aParameterWithSampleValuesRendersAsTheSearchableDropdown() {
        Panel input = inputFor(parameter -> !parameter.sampleValues().isEmpty());

        assertThat(input).isInstanceOf(Select2ChoiceParamPanel.class);
    }

    @Test
    void anIntegerRendersAsATypedTextField() {
        Panel input = inputFor(parameter -> parameter.type() == Integer.class);

        assertThat(input).isInstanceOf(TextParamPanel.class);
        assertThat(((ParamPanel<?>) input).getFormComponent().getType()).isEqualTo(Integer.class);
    }

    @Test
    void aPlainStringRendersAsATextField() {
        Panel input = inputFor(parameter -> parameter.type() == String.class
                && !parameter.password()
                && parameter.sampleValues().isEmpty());

        assertThat(input).isInstanceOf(TextParamPanel.class);
        assertThat(((ParamPanel<?>) input).getFormComponent().getType()).isIn(null, String.class);
    }

    @Test
    void everyInputTakesTheFieldSlotId() {
        for (StorageParameter<?> parameter : StorageParams.orderedParameters(true)) {
            assertThat(StorageParamInputs.inputFor(paramsModel, parameter).getId())
                    .as(parameter.key())
                    .isEqualTo(GroupedParamPanel.FIELD_ID);
        }
    }

    @Test
    void aStringBehindAnIntegerParameterRendersItsValue() {
        StorageParameter<?> timeout = parameter(p -> "storage.http.timeout-millis".equals(p.key()));
        params.put(timeout.key(), "5000");
        Form<Void> form = new Form<>("form");
        form.add(StorageParamInputs.inputFor(paramsModel, timeout));

        tester.startComponentInPage(form);

        assertThat(tester.getLastResponseAsString()).contains("value=\"5000\"");
    }

    @Test
    void everyInputHasItsDescriptionAsTooltip() {
        for (StorageParameter<?> parameter : StorageParams.orderedParameters(true)) {
            Panel input = StorageParamInputs.inputFor(paramsModel, parameter);

            assertThat(input.getBehaviors(AttributeModifier.class))
                    .as(parameter.key())
                    .anyMatch(behavior -> "title".equals(behavior.getAttribute()));
        }
    }

    @Test
    void aRenderedFieldShowsItsDescriptionOnHover() {
        StorageParameter<?> timeout = parameter(p -> "storage.http.timeout-millis".equals(p.key()));
        Form<Void> form = new Form<>("form");
        form.add(StorageParamInputs.inputFor(paramsModel, timeout));

        tester.startComponentInPage(form);

        String markup = tester.getLastResponseAsString();
        String description = timeout.description();
        assertThat(markup).contains("title=\"" + description);
    }

    private Panel inputFor(Predicate<StorageParameter<?>> which) {
        return StorageParamInputs.inputFor(paramsModel, parameter(which));
    }

    private static StorageParameter<?> parameter(Predicate<StorageParameter<?>> which) {
        return StorageProvider.getProviders().stream()
                .flatMap(provider -> provider.getParameters().stream())
                .filter(which)
                .findFirst()
                .orElseThrow();
    }
}
