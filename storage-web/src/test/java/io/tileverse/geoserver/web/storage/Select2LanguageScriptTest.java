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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.agilecoders.wicket.webjars.WicketWebjars;

/**
 * Every Select2 widget puts wicketstuff's language script in the page head, and a script the server cannot serve leaves
 * Wicket's Ajax pipeline suspended: the backend switch on a store page then never shows the selected backend's fields.
 * wicketstuff-select2 9.23.0 references that script as a package resource it does not ship; storage-web ships it, and
 * this test requests the script exactly as the browser does.
 */
class Select2LanguageScriptTest {

    /** The language script URL as rendered in the head, relative to the page; the resource path is captured. */
    private static final Pattern LANGUAGE_SCRIPT = Pattern.compile(
            "src=\"[./]*(?:wicket/)?resource/(org\\.wicketstuff\\.select2\\.Select2LanguageResourceReference/[^\"]+)\"");

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><div wicket:id='panel'></div></form></body></html>";
            }
        };
        tester.getApplication().getResourceSettings().setThrowExceptionOnMissingResource(false);
        WicketWebjars.install(tester.getApplication());
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void servesTheLanguageScriptTheWidgetRendersInTheHead() {
        Form<Void> form = new Form<>("form");
        form.add(Select2ChoiceParamPanel.ofStrings(
                "panel", Model.of("region"), Model.of("us-east-1"), List.of("us-east-1")));
        tester.startComponentInPage(form);
        String resourcePath = languageScriptPath(tester.getLastResponse().getDocument());

        tester.executeUrl("wicket/resource/" + resourcePath);

        assertThat(tester.getLastResponse().getStatus()).isEqualTo(200);
        assertThat(tester.getLastResponseAsString()).contains("select2/i18n/en");
    }

    private static String languageScriptPath(String document) {
        Matcher matcher = LANGUAGE_SCRIPT.matcher(document);
        assertThat(matcher.find())
                .as("language script in the head of %s", document)
                .isTrue();
        return matcher.group(1);
    }
}
