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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RadioGroupParamPanelTest {

    private static final Pattern TITLE_ATTRIBUTE = Pattern.compile("title=\"");

    /** A var() without a fallback resolves to nothing on a theme lacking the variable, such as GeoServer 2.28's. */
    private static final Pattern THEME_VARIABLE_WITHOUT_FALLBACK = Pattern.compile("var\\(--gs-[a-z0-9-]+\\)");

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        // a Form needs a <form> tag and its panel a nested tag, neither provided by the default page markup
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
    void rendersNoTitleAttributeWithoutChoiceTooltips() {
        Form<Void> form = new Form<>("form");
        RadioGroupParamPanel<String> panel =
                new RadioGroupParamPanel<>("panel", Model.of("label"), Model.of("a"), List.of("a", "b"));
        form.add(panel);

        tester.startComponentInPage(form);

        assertThat(tester.getLastResponseAsString()).doesNotContain("title=");
    }

    @Test
    void rendersATitleOnlyForChoicesWithATooltip() {
        Form<Void> form = new Form<>("form");
        RadioGroupParamPanel<String> panel =
                new RadioGroupParamPanel<>("panel", Model.of("label"), Model.of("a"), List.of("a", "b"));
        panel.choiceTooltips(choice -> "b".equals(choice) ? Model.of("Choice B") : null);
        form.add(panel);

        tester.startComponentInPage(form);

        assertThat(countMatches(TITLE_ATTRIBUTE, tester.getLastResponseAsString()))
                .isEqualTo(1);
    }

    @Test
    void everyThemeVariableInTheStylesheetHasAFallback() throws IOException {
        String stylesheet = readStylesheet();

        List<String> withoutFallback = new ArrayList<>();
        Matcher matcher = THEME_VARIABLE_WITHOUT_FALLBACK.matcher(stylesheet);
        while (matcher.find()) {
            withoutFallback.add(matcher.group());
        }

        assertThat(withoutFallback).isEmpty();
    }

    private static String readStylesheet() throws IOException {
        try (InputStream stream = RadioGroupParamPanel.class.getResourceAsStream("RadioGroupParamPanel.css")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countMatches(Pattern pattern, String markup) {
        Matcher matcher = pattern.matcher(markup);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
