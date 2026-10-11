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

import java.util.Locale;
import java.util.stream.Stream;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.TagTester;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The labelled number input of a number parameter: what the browser is told, and what a submit accepts, for the number
 * type, the bounds and the step given to the panel.
 */
class NumberParamPanelTest {

    private static final String FORM_PATH = "form";

    private static final String FIELD_PATH = "panel:border:border_body:paramValue";

    private final IModel<Integer> value = new Model<>();

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        // A Form needs a <form> tag and the panel inside it a nested tag; the default page markup is a single <span>.
        tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><div wicket:id='panel'></div></form></body></html>";
            }
        };
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void rendersANumberInputUnderItsLabel() {
        render(newPanel());

        TagTester input = renderedInput();
        assertThat(input.getName()).isEqualTo("input");
        assertThat(input.getAttribute("type")).isEqualTo("number");
        TagTester label = tester.getTagByWicketId("paramName");
        assertThat(label.getValue()).isEqualTo("Max retries");
    }

    @Test
    void rendersNoBoundsNorStepUnlessSet() {
        render(newPanel());

        TagTester input = renderedInput();
        assertThat(input.getAttribute("min")).isNull();
        assertThat(input.getAttribute("max")).isNull();
        assertThat(input.getAttribute("step")).isNull();
    }

    @Test
    void rendersTheBoundsAndTheStepSet() {
        NumberParamPanel<Integer> panel = newPanel();
        panel.setMinimum(1);
        panel.setMaximum(50);
        panel.setStep(5);

        render(panel);

        TagTester input = renderedInput();
        assertThat(input.getAttribute("min")).isEqualTo("1");
        assertThat(input.getAttribute("max")).isEqualTo("50");
        assertThat(input.getAttribute("step")).isEqualTo("5");
    }

    @Test
    void readsTheBoundsAndTheStepOfModelsAtEachRender() {
        IModel<Integer> minimum = Model.of(1);
        IModel<Integer> maximum = Model.of(50);
        IModel<Integer> step = Model.of(5);
        NumberParamPanel<Integer> panel = newPanel();
        panel.setMinimum(minimum);
        panel.setMaximum(maximum);
        panel.setStep(step);
        render(panel);

        minimum.setObject(10);
        maximum.setObject(90);
        step.setObject(20);
        submit("30");

        TagTester input = renderedInput();
        assertThat(input.getAttribute("min")).isEqualTo("10");
        assertThat(input.getAttribute("max")).isEqualTo("90");
        assertThat(input.getAttribute("step")).isEqualTo("20");
    }

    @Test
    void showsItsValueInPlainDigitsInALocaleWritingOtherDigits() {
        tester.getSession().setLocale(Locale.forLanguageTag("ar-EG"));
        value.setObject(20_000);

        render(newPanel());

        TagTester input = renderedInput();
        assertThat(input.getAttribute("value")).isEqualTo("20000");
    }

    @Test
    void anEmptyValueRendersAnEmptyField() {
        render(newPanel());

        TagTester input = renderedInput();
        assertThat(input.getAttribute("value")).isEmpty();
    }

    @Test
    void submittingDigitsStoresTheNumber() {
        render(newPanel());

        submit("42");

        tester.assertNoErrorMessage();
        assertThat(value.getObject()).isEqualTo(42);
    }

    @Test
    void submittingAnEmptiedFieldStoresNoValue() {
        value.setObject(42);
        render(newPanel());

        submit("");

        tester.assertNoErrorMessage();
        assertThat(value.getObject()).isNull();
    }

    @Test
    void acceptsANegativeValueWhenNoMinimumIsSet() {
        render(newPanel());

        submit("-1");

        tester.assertNoErrorMessage();
        assertThat(value.getObject()).isEqualTo(-1);
    }

    static Stream<String> entriesOutOfOneToFifty() {
        return Stream.of("0", "51");
    }

    @ParameterizedTest
    @MethodSource("entriesOutOfOneToFifty")
    void rejectsAValueOutOfItsBoundsLeavingTheStoredOneUntouched(String entered) {
        value.setObject(42);
        NumberParamPanel<Integer> panel = newPanel();
        panel.setMinimum(1);
        panel.setMaximum(50);
        render(panel);

        submit(entered);

        FormComponent<Integer> field = panel.getFormComponent();
        assertThat(field.hasErrorMessage()).as("the field reports the entry").isTrue();
        assertThat(value.getObject()).isEqualTo(42);
    }

    static Stream<String> entriesOtherThanAnInteger() {
        return Stream.of("many", "1.5", "12abc", "99999999999");
    }

    @ParameterizedTest
    @MethodSource("entriesOtherThanAnInteger")
    void anIntegerPanelRejectsAnotherEntryLeavingTheStoredValueUntouched(String entered) {
        value.setObject(42);
        NumberParamPanel<Integer> panel = newPanel();
        render(panel);

        submit(entered);

        FormComponent<Integer> field = panel.getFormComponent();
        assertThat(field.hasErrorMessage()).as("the field reports the entry").isTrue();
        assertThat(value.getObject()).isEqualTo(42);
    }

    @Test
    void aLongPanelStoresANumberBeyondTheIntegers() {
        IModel<Long> size = new Model<>();
        NumberParamPanel<Long> panel = new NumberParamPanel<>("panel", size, Model.of("Size"), Long.class);
        render(panel);

        submit("99999999999");

        tester.assertNoErrorMessage();
        assertThat(size.getObject()).isEqualTo(99_999_999_999L);
    }

    @Test
    void aDoublePanelStoresAFractionAndRendersAnyStep() {
        IModel<Double> ratio = new Model<>();
        NumberParamPanel<Double> panel = new NumberParamPanel<>("panel", ratio, Model.of("Ratio"), Double.class);
        panel.setStep(NumberTextField.ANY);
        render(panel);

        submit("1.5");

        tester.assertNoErrorMessage();
        assertThat(ratio.getObject()).isEqualTo(1.5);
        TagTester input = renderedInput();
        assertThat(input.getAttribute("step")).isEqualTo("any");
        assertThat(input.getAttribute("value")).isEqualTo("1.5");
    }

    private NumberParamPanel<Integer> newPanel() {
        return new NumberParamPanel<>("panel", value, Model.of("Max retries"), Integer.class);
    }

    private void render(NumberParamPanel<?> panel) {
        Form<Void> form = new Form<>(FORM_PATH);
        form.add(panel);
        tester.startComponentInPage(form);
    }

    private TagTester renderedInput() {
        return tester.getTagByWicketId("paramValue");
    }

    private void submit(String entered) {
        FormTester formTester = tester.newFormTester(FORM_PATH);
        formTester.setValue(FIELD_PATH, entered);
        formTester.submit();
    }
}
