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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.util.MapModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Renders and submits a {@link DurationParamPanel} over a connection-parameters map to pin the widget contract: a
 * stored ISO-8601 string renders as an integer amount with its unit pre-selected and the unit choices labelled in plain
 * words, a submit writes the recomposed ISO-8601 string back into the map, a blank amount unsets the parameter, a
 * negative amount is rejected leaving the map untouched, and an unparseable stored value renders as a blank amount
 * without failing the page.
 */
class DurationParamPanelTest {

    private static final String KEY = "storage.azure.max-retry-delay";

    private WicketTester tester;
    private Map<String, Serializable> params;

    @BeforeEach
    void startTester() {
        tester = new WicketTester();
        params = new HashMap<>();
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void rendersTheStoredIsoStringAsAmountAndPreselectedUnit() {
        params.put(KEY, "PT2M");

        DurationParamPanelTestPage page = startPage();

        assertThat(amountField(page).getValue()).isEqualTo("2");
        String response = tester.getLastResponseAsString();
        assertThat(response).contains("<option selected=\"selected\" value=\"MINUTES\">minutes</option>");
    }

    @Test
    void offersTheUnitsAsPlainWords() {
        startPage();

        String response = tester.getLastResponseAsString();
        assertThat(response)
                .contains(">milliseconds</option>")
                .contains(">seconds</option>")
                .contains(">minutes</option>")
                .contains(">hours</option>");
    }

    @Test
    void submitsTheRecomposedIsoString() {
        params.put(KEY, "PT2M");
        DurationParamPanelTestPage page = startPage();

        FormTester formTester = tester.newFormTester("form");
        formTester.setValue(formPath(page, amountField(page)), "90");
        formTester.select(formPath(page, unitChoice(page)), DurationField.UNITS.indexOf(ChronoUnit.SECONDS));
        formTester.submit();

        assertThat(params).containsEntry(KEY, "PT1M30S");
    }

    @Test
    void unsetsTheParameterWhenTheAmountIsBlank() {
        params.put(KEY, "PT2M");
        DurationParamPanelTestPage page = startPage();

        FormTester formTester = tester.newFormTester("form");
        formTester.setValue(formPath(page, amountField(page)), "");
        formTester.submit();

        assertThat(params.get(KEY)).isNull();
    }

    @Test
    void rejectsANegativeAmountLeavingTheStoredValueUntouched() {
        params.put(KEY, "PT2M");
        DurationParamPanelTestPage page = startPage();

        FormTester formTester = tester.newFormTester("form");
        formTester.setValue(formPath(page, amountField(page)), "-5");
        formTester.submit();

        assertThat(tester.getFeedbackMessages(message -> message.isError())).isNotEmpty();
        assertThat(params).containsEntry(KEY, "PT2M");
    }

    @Test
    void rendersAnUnparseableStoredValueAsABlankAmount() {
        params.put(KEY, "2 minutes");

        DurationParamPanelTestPage page = startPage();

        assertThat(amountField(page).getValue()).isEmpty();
    }

    private DurationParamPanelTestPage startPage() {
        MapModel<String> model = new MapModel<>(params, KEY);
        return tester.startPage(new DurationParamPanelTestPage(model));
    }

    private static FormComponent<?> amountField(DurationParamPanelTestPage page) {
        return (FormComponent<?>) ((DurationField) page.panel.getFormComponent()).get("amount");
    }

    private static Component unitChoice(DurationParamPanelTestPage page) {
        return ((DurationField) page.panel.getFormComponent()).get("unit");
    }

    /** The component's path relative to the page's form, the shape {@link FormTester} addresses fields by. */
    private static String formPath(DurationParamPanelTestPage page, Component component) {
        String formPrefix = page.form.getPageRelativePath() + ":";
        return component.getPageRelativePath().substring(formPrefix.length());
    }
}
