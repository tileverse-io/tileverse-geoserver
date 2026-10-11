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
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.settings.ResourceSettings;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.tester.TagTester;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.GeoServerResourceStreamLocator;
import org.geoserver.web.GeoServerStringResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.StorageParameter;

/**
 * Holds the translations of the storage section to the texts they translate: a bundle per language covering the labels
 * of the default bundle and a tooltip per storage parameter, naming no other key, written in ASCII as read by
 * GeoServer. Also renders a parameter in a translated language, through GeoServer's own lookup of labels.
 */
class TranslationsTest {

    private static final String LABELS = "GeoServerApplication%s.properties";

    private static final String UNITS = "io/tileverse/geoserver/web/storage/DurationField%s.properties";

    private WicketTester tester;

    static Stream<String> languages() {
        return Stream.of("es", "de", "fr", "it");
    }

    @AfterEach
    void stopTester() {
        if (tester != null) {
            tester.destroy();
        }
    }

    @ParameterizedTest
    @MethodSource("languages")
    void aTranslationCoversTheLabelsOfTheDefaultBundle(String language) throws IOException {
        Set<String> translated = keysOf(LABELS, language);

        assertThat(translated).containsAll(keysOf(LABELS, null));
    }

    @ParameterizedTest
    @MethodSource("languages")
    void aTranslationHasATooltipForTheParametersOfTheStorageSection(String language) throws IOException {
        Properties translated = load(LABELS, language);

        for (StorageParameter<?> parameter : StorageParams.orderedParameters(true)) {
            String key = parameter.key() + ".tooltip";
            assertThat(translated.getProperty(key)).as(key).isNotBlank();
        }
    }

    @ParameterizedTest
    @MethodSource("languages")
    void aTranslationNamesNoKeyUnknownToTheDefaultBundleAndTheParameters(String language) throws IOException {
        Set<String> known = new HashSet<>(keysOf(LABELS, null));
        for (StorageParameter<?> parameter : StorageParams.orderedParameters(true)) {
            known.add(parameter.key() + ".tooltip");
        }

        assertThat(known).containsAll(keysOf(LABELS, language));
    }

    @ParameterizedTest
    @MethodSource("languages")
    void aTranslationNamesTheTimeUnitsOfADuration(String language) throws IOException {
        Properties units = load(UNITS, language);

        for (ChronoUnit unit : DurationField.UNITS) {
            String key = "ChronoUnit." + unit.name();
            assertThat(units.getProperty(key)).as(key).isNotBlank();
        }
        assertThat(units).hasSameSizeAs(DurationField.UNITS);
    }

    /** GeoServer reads a bundle as ISO-8859-1: a file saved as UTF-8 would show garbled accents. */
    @ParameterizedTest
    @MethodSource("languages")
    void aTranslationIsWrittenInAsciiWithUnicodeEscapes(String language) throws IOException {
        assertThat(nonAsciiBytesOf(LABELS, language)).as("labels").isZero();
        assertThat(nonAsciiBytesOf(UNITS, language)).as("units").isZero();
    }

    @Test
    void aParameterShowsItsNameAndItsTooltipInTheLanguageOfTheSession() throws IOException {
        Properties french = load(LABELS, "fr");
        startTesterLookingUpLabelsAsGeoServerDoes(Locale.FRENCH);
        StorageParameter<?> retries = parameter("storage.azure.max-retries");

        render(retries);

        TagTester label = tester.getTagByWicketId("paramName");
        TagTester field = tester.getTagByWicketId("field");
        CharSequence tooltip = Strings.unescapeMarkup(field.getAttribute("title"));
        assertThat(label.getValue())
                .isEqualTo(french.getProperty("storage.azure.max-retries"))
                .isNotEqualTo("Max retries");
        assertThat(tooltip)
                .hasToString(french.getProperty("storage.azure.max-retries.tooltip"))
                .isNotEqualTo(retries.description());
    }

    @Test
    void aParameterOfALanguageWithoutABundleShowsTheEnglishNameAndTheDescriptionOfItsProvider() {
        startTesterLookingUpLabelsAsGeoServerDoes(Locale.forLanguageTag("sv"));
        StorageParameter<?> retries = parameter("storage.azure.max-retries");

        render(retries);

        TagTester label = tester.getTagByWicketId("paramName");
        TagTester field = tester.getTagByWicketId("field");
        CharSequence tooltip = Strings.unescapeMarkup(field.getAttribute("title"));
        assertThat(label.getValue()).isEqualTo("Max retries");
        assertThat(tooltip).hasToString(retries.description());
    }

    @Test
    void aDurationOffersItsUnitsInTheLanguageOfTheSession() throws IOException {
        Properties french = load(UNITS, "fr");
        startTesterLookingUpLabelsAsGeoServerDoes(Locale.FRENCH);
        StorageParameter<?> delay = parameter("storage.azure.retry-delay");

        render(delay);

        String seconds = french.getProperty("ChronoUnit.SECONDS");
        assertThat(seconds).isNotEqualTo("seconds");
        assertThat(tester.getLastResponseAsString()).contains(">" + seconds + "</option>");
    }

    private void startTesterLookingUpLabelsAsGeoServerDoes(Locale language) {
        tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><div wicket:id='field'></div></form></body></html>";
            }
        };
        ResourceSettings resources = tester.getApplication().getResourceSettings();
        resources.setThrowExceptionOnMissingResource(false);
        resources.setResourceStreamLocator(new GeoServerResourceStreamLocator());
        List<IStringResourceLoader> loaders = resources.getStringResourceLoaders();
        loaders.add(0, new GeoServerStringResourceLoader());
        tester.getSession().setLocale(language);
    }

    private void render(StorageParameter<?> parameter) {
        Map<String, Serializable> params = new HashMap<>();
        IModel<Map<String, Serializable>> paramsModel = Model.ofMap(params);
        Form<Void> form = new Form<>("form");
        form.add(StorageParamInputs.inputFor(paramsModel, parameter));
        tester.startComponentInPage(form);
    }

    private static StorageParameter<?> parameter(String key) {
        return StorageParams.orderedParameters(true).stream()
                .filter(parameter -> key.equals(parameter.key()))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> keysOf(String bundle, String language) throws IOException {
        Properties properties = load(bundle, language);
        return properties.stringPropertyNames();
    }

    /** The bundle of a language, or the default one for a null language. */
    private static Properties load(String bundle, String language) throws IOException {
        URL file = fileOf(bundle, language);
        return read(file);
    }

    private static long nonAsciiBytesOf(String bundle, String language) throws IOException {
        URL file = fileOf(bundle, language);
        try (InputStream in = file.openStream()) {
            byte[] bytes = in.readAllBytes();
            long count = 0;
            for (byte each : bytes) {
                if (each < 0) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * The file of this module among the bundles of that name on the classpath, GeoServer's own included: the one naming
     * the provider selector, or the only one for the time units.
     */
    private static URL fileOf(String bundle, String language) throws IOException {
        String suffix = language == null ? "" : "_" + language;
        String path = bundle.formatted(suffix);
        ClassLoader classpath = TranslationsTest.class.getClassLoader();
        List<URL> candidates = Collections.list(classpath.getResources(path));
        for (URL candidate : candidates) {
            Properties properties = read(candidate);
            if (properties.containsKey("storage.provider") || properties.containsKey("ChronoUnit.SECONDS")) {
                return candidate;
            }
        }
        return fail("no bundle %s of this module among %s", path, candidates);
    }

    private static Properties read(URL file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = file.openStream()) {
            properties.load(in);
        }
        return properties;
    }
}
