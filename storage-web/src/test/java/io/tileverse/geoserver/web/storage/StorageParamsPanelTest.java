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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.tileverse.storage.StorageConfig;

import io.tileverse.geoserver.web.storage.StorageParamsPanel.BackendSelection;

import de.agilecoders.wicket.webjars.WicketWebjars;

class StorageParamsPanelTest {

    private static final String PROVIDER = StorageConfig.PROVIDER_ID_KEY;
    private static final Pattern HEADER = Pattern.compile("gs-storage-param-group-header\"[^>]*>([^<]*)<");

    /** Matches the radio group widget's own stylesheet link, however Wicket names the rendered resource. */
    private static final Pattern RADIO_GROUP_STYLESHEET = Pattern.compile("RadioGroupParamPanel[^\"]*\\.css");

    /** The link to the searchable dropdown's stylesheet, which keeps its clear button clickable. */
    private static final Pattern SEARCHABLE_DROPDOWN_STYLESHEET =
            Pattern.compile("Select2ChoiceParamPanel[^\"]*\\.css");

    /** Matches a provider segment; requires {@code class} before {@code title}, tolerating attributes between. */
    private static final Pattern PROVIDER_SEGMENT_WITH_TOOLTIP = Pattern.compile("gs-list-item-class\"[^>]*title=\"");

    private final Map<String, Serializable> params = new HashMap<>();
    private final IModel<Map<String, Serializable>> paramsModel = Model.ofMap(params);

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
        // GeoServer's resource bundles are not loaded; labels fall back to their keys, as the assertions expect
        tester.getApplication().getResourceSettings().setThrowExceptionOnMissingResource(false);
        // the select2 region dropdown needs webjars installed before the first render
        WicketWebjars.install(tester.getApplication());
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void rendersCachingFirstThenBackendGroupsInDisplayOrderWithOneHeaderEach() {
        params.put("storage.azure.account-key", "k");
        params.put("storage.gcs.project-id", "p");
        params.put("storage.http.username", "u");
        params.put("storage.s3.region", "us-east-1");
        params.put("storage.file.idle-timeout", "PT30S");

        renderPanel(BackendSelection.MULTIPLE_BACKENDS, true);

        assertThat(headers(tester.getLastResponseAsString()))
                .containsExactly("caching", "http", "s3", "gcs", "azure", "file");
    }

    @Test
    void theLocalFileProviderShowsItsIdleTimeoutAndNoCaching() {
        params.put(PROVIDER, "file");

        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        assertThat(panel.rowFor("storage.file.idle-timeout").isVisible()).isTrue();
        assertThat(panel.fieldFor("storage.file.idle-timeout")).isInstanceOf(DurationParamPanel.class);
        assertThat(panel.rowFor("storage.caching.enabled").isVisible()).isFalse();
        assertThat(panel.rowFor("storage.s3.region").isVisible()).isFalse();
    }

    @Test
    void singleProviderShowsOnlyThatBackendAndCaching() {
        params.put(PROVIDER, "s3");

        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        assertThat(panel.rowFor("storage.s3.region").isVisible()).isTrue();
        assertThat(panel.rowFor("storage.caching.enabled").isVisible()).isTrue();
        assertThat(panel.rowFor("storage.azure.account-key").isVisible()).isFalse();
        assertThat(panel.rowFor("storage.http.username").isVisible()).isFalse();
        String markup = tester.getLastResponseAsString();
        assertThat(markup).contains("storage.s3.region").doesNotContain("storage.azure.account-key");
    }

    @Test
    void rendersTheRadioGroupStylesheetOnce() {
        params.put(PROVIDER, "s3");

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        // getLastResponseAsString() strips everything outside <body>; the stylesheet link renders in <head>
        String document = tester.getLastResponse().getDocument();
        assertThat(countMatches(RADIO_GROUP_STYLESHEET, document)).isEqualTo(1);
    }

    @Test
    void rendersTheSearchableDropdownStylesheetOnce() {
        params.put(PROVIDER, "s3");

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        String document = tester.getLastResponse().getDocument();
        assertThat(countMatches(SEARCHABLE_DROPDOWN_STYLESHEET, document)).isEqualTo(1);
    }

    @Test
    void everyProviderSegmentHasATooltip() {
        params.put(PROVIDER, "s3");

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        String markup = tester.getLastResponseAsString();
        assertThat(markup).contains("title=\"azure-datalake\"").contains("title=\"http\"");
        assertThat(countMatches(PROVIDER_SEGMENT_WITH_TOOLTIP, markup))
                .isEqualTo(StorageParams.providerIds().size());
    }

    @Test
    void switchingTheProviderRerendersTheRowsOverAjax() {
        params.put(PROVIDER, "s3");
        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);
        int azure = StorageParams.providerIds().indexOf("azure");

        FormTester form = tester.newFormTester("form");
        form.select("panel:selector:group", azure);
        // AjaxFormChoiceComponentUpdatingBehavior listens to "change"
        tester.executeAjaxEvent("form:panel:selector:group", "change");

        assertThat(params).containsEntry(PROVIDER, "azure");
        assertThat(panel.rowFor("storage.azure.account-key").isVisible()).isTrue();
        assertThat(panel.rowFor("storage.s3.region").isVisible()).isFalse();
        tester.assertComponentOnAjaxResponse(panel.rowFor("storage.azure.account-key"));
        tester.assertComponentOnAjaxResponse(panel.rowFor("storage.s3.region"));
    }

    @Test
    void theAzureDataLakeProviderTitlesTheAzureRowsAfterItself() {
        registerAzureGroupLabels();
        params.put(PROVIDER, "azure-datalake");

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        assertThat(headers(tester.getLastResponseAsString()))
                .contains("Azure Data Lake Storage Gen2 parameters")
                .doesNotContain("Azure Blob Storage parameters");
    }

    @Test
    void theAzureProviderKeepsTheAzureGroupTitle() {
        registerAzureGroupLabels();
        params.put(PROVIDER, "azure");

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        assertThat(headers(tester.getLastResponseAsString())).contains("Azure Blob Storage parameters");
    }

    @Test
    void switchingToAzureDataLakeRetitlesTheAzureRows() {
        registerAzureGroupLabels();
        params.put(PROVIDER, "azure");
        renderPanel(BackendSelection.SINGLE_PROVIDER, true);
        int azureDataLake = StorageParams.providerIds().indexOf("azure-datalake");

        FormTester form = tester.newFormTester("form");
        form.select("panel:selector:group", azureDataLake);
        // AjaxFormChoiceComponentUpdatingBehavior listens to "change"
        tester.executeAjaxEvent("form:panel:selector:group", "change");

        assertThat(tester.getLastResponse().getDocument()).contains("Azure Data Lake Storage Gen2 parameters");
    }

    @Test
    void multipleBackendsTitlesTheAzureRowsByGroup() {
        registerAzureGroupLabels();
        params.put("storage.azure.account-key", "k");

        renderPanel(BackendSelection.MULTIPLE_BACKENDS, true);

        assertThat(headers(tester.getLastResponseAsString())).contains("Azure Blob Storage parameters");
    }

    @Test
    void multipleBackendsPreChecksTheBackendsPresentInTheStore() {
        params.put("storage.s3.region", "us-east-1");
        params.put("storage.http.bearer-token", "t");

        StorageParamsPanel panel = renderPanel(BackendSelection.MULTIPLE_BACKENDS, true);

        assertThat(panel.selectedGroups()).containsExactlyInAnyOrder("s3", "http");
        assertThat(panel.rowFor("storage.s3.region").isVisible()).isTrue();
        assertThat(panel.rowFor("storage.http.username").isVisible()).isTrue();
        assertThat(panel.rowFor("storage.azure.account-key").isVisible()).isFalse();
        assertThat(panel.rowFor("storage.gcs.project-id").isVisible()).isFalse();
    }

    @Test
    void checkingABackendRerendersItsRowsOverAjax() {
        StorageParamsPanel panel = renderPanel(BackendSelection.MULTIPLE_BACKENDS, true);
        int gcs = StorageParamVisibility.selectableGroups().indexOf("gcs");

        FormTester form = tester.newFormTester("form");
        form.selectMultiple("panel:selector:group", new int[] {gcs});
        // AjaxFormChoiceComponentUpdatingBehavior listens to "change"
        tester.executeAjaxEvent("form:panel:selector:group", "change");

        assertThat(panel.selectedGroups()).containsExactly("gcs");
        assertThat(panel.rowFor("storage.gcs.project-id").isVisible()).isTrue();
        tester.assertComponentOnAjaxResponse(panel.rowFor("storage.gcs.project-id"));
    }

    @Test
    void leavesOutTheCachingRowsWhenTheFlagIsOff() {
        params.put(PROVIDER, "s3");

        renderPanel(BackendSelection.SINGLE_PROVIDER, false);

        String markup = tester.getLastResponseAsString();
        assertThat(markup).doesNotContain("storage.caching.enabled");
        assertThat(headers(markup)).containsExactly("s3");
    }

    @Test
    void renderingAStringBehindABooleanStoresTheBoolean() {
        params.put(PROVIDER, "s3");
        params.put("storage.s3.requester-pays", "true");

        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        FormComponent<?> requesterPays = formComponent(panel, "storage.s3.requester-pays");
        assertThat(requesterPays.getModelObject()).isEqualTo(Boolean.TRUE);
        assertThat(params.get("storage.s3.requester-pays")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void submittingAChangedBooleanWritesTheTypedValue() {
        params.put(PROVIDER, "s3");
        params.put("storage.s3.requester-pays", "true");
        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);
        FormComponent<?> requesterPays = formComponent(panel, "storage.s3.requester-pays");

        FormTester form = tester.newFormTester("form");
        form.setValue(requesterPays, "false");
        form.submit();

        tester.assertNoErrorMessage();
        assertThat(params.get("storage.s3.requester-pays")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void dropsStoredCachingSettingsWhenTheFlagIsOff() {
        params.put(PROVIDER, "s3");
        params.put("storage.caching.enabled", Boolean.TRUE);

        renderPanel(BackendSelection.SINGLE_PROVIDER, false);

        assertThat(params).doesNotContainKey("storage.caching.enabled");
        assertThat(params).containsEntry(PROVIDER, "s3");
    }

    @Test
    void keepsStoredCachingSettingsWhenTheFlagIsOn() {
        params.put(PROVIDER, "s3");
        params.put("storage.caching.enabled", Boolean.TRUE);

        renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        assertThat(params).containsEntry("storage.caching.enabled", Boolean.TRUE);
    }

    @Test
    void anIntegerFieldSubmitsAnInteger() {
        params.put(PROVIDER, "http");
        StorageParamsPanel panel = renderPanel(BackendSelection.SINGLE_PROVIDER, true);

        FormTester form = tester.newFormTester("form");
        form.setValue(formComponent(panel, "storage.http.timeout-millis"), "5000");
        form.submit();

        tester.assertNoErrorMessage();
        assertThat(params.get("storage.http.timeout-millis")).isEqualTo(5000);
    }

    private StorageParamsPanel renderPanel(BackendSelection selection, boolean cachingParameters) {
        Form<Void> form = new Form<>("form");
        StorageParamsPanel panel = new StorageParamsPanel("panel", paramsModel, selection, cachingParameters);
        form.add(panel);
        tester.startComponentInPage(form);
        return panel;
    }

    private static FormComponent<?> formComponent(StorageParamsPanel panel, String key) {
        return ((ParamPanel<?>) panel.fieldFor(key)).getFormComponent();
    }

    private static List<String> headers(String markup) {
        List<String> headers = new ArrayList<>();
        Matcher matcher = HEADER.matcher(markup);
        while (matcher.find()) {
            headers.add(matcher.group(1).trim());
        }
        return headers;
    }

    private static int countMatches(Pattern pattern, String markup) {
        Matcher matcher = pattern.matcher(markup);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Stands in for the GeoServer bundle, answering only the two Azure group header keys. */
    private void registerAzureGroupLabels() {
        tester.getApplication()
                .getResourceSettings()
                .getStringResourceLoaders()
                .add(0, new AzureGroupLabelsResourceLoader());
    }

    /**
     * Resolves {@code storage.group.azure} and {@code storage.group.azure-datalake}; every other key is {@code null}.
     */
    private static final class AzureGroupLabelsResourceLoader implements IStringResourceLoader {

        @Override
        public String loadStringResource(Class<?> clazz, String key, Locale locale, String style, String variation) {
            return azureGroupLabel(key);
        }

        @Override
        public String loadStringResource(
                Component component, String key, Locale locale, String style, String variation) {
            return azureGroupLabel(key);
        }

        private static String azureGroupLabel(String key) {
            if ("storage.group.azure".equals(key)) {
                return "Azure Blob Storage parameters";
            }
            if ("storage.group.azure-datalake".equals(key)) {
                return "Azure Data Lake Storage Gen2 parameters";
            }
            return null;
        }
    }
}
