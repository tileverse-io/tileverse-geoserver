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
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.TagTester;
import org.apache.wicket.util.tester.WicketTester;
import org.apache.wicket.validation.Validatable;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.StorageConfig;

import io.tileverse.geoserver.web.storage.browser.FileColumns;
import io.tileverse.geoserver.web.storage.browser.GeoServerFixture;
import io.tileverse.geoserver.web.storage.browser.PickMode;
import io.tileverse.geoserver.web.storage.browser.StorageBrowsing;
import io.tileverse.geoserver.web.storage.browser.StorageLocationField;

import de.agilecoders.wicket.webjars.WicketWebjars;

/** Also tests {@link StorageUriValidator}, which needs no page. */
class StorageAwareCoverageStoreEditPanelTest {

    /** {@code startComponentInPage} keeps the form's own id. */
    private static final String FORM_PATH = "form";

    /** The text field of the URL panel, relative to the form. */
    private static final String URL_TEXT_PATH = "panel:urlPanel:border:border_body:paramValue";

    /** The Browse link of the URL panel, relative to the page. */
    private static final String BROWSE_LINK_PATH = "form:panel:urlPanel:border:border_body:browse";

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        // a Form needs a <form> tag and its panel a nested tag, neither provided by the default page markup
        tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><span wicket:id='panel'></span></form></body></html>";
            }
        };
        // GeoServer's resource bundles are not loaded, and no assertion depends on label text
        tester.getApplication().getResourceSettings().setThrowExceptionOnMissingResource(false);
        // the select2 region dropdown needs webjars installed before the first render
        WicketWebjars.install(tester.getApplication());
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void rendersWithARequiredUrlField() {
        TestPanel panel = renderPanel(newStore());

        TextParamPanel<?> urlPanel = (TextParamPanel<?>) panel.get("urlPanel");

        assertThat(urlPanel.getFormComponent().isRequired()).isTrue();
    }

    @Test
    void theUrlFieldShowsTheDescriptionOfTheStoreTypeAsATooltip() {
        BundleEntry.define(tester, "TestPanel.urlPlaceholder.tooltip", "One file, or a folder of them");

        renderPanel(newStore());

        TagTester urlPanel = tester.getTagByWicketId("urlPanel");
        assertThat(urlPanel.getAttribute("title")).isEqualTo("One file, or a folder of them");
    }

    @Test
    void theUrlFieldHasNoTooltipForAStoreTypeWithoutADescription() {
        renderPanel(newStore());

        TagTester urlPanel = tester.getTagByWicketId("urlPanel");
        assertThat(urlPanel.getAttribute("title")).isNull();
    }

    @Test
    void rendersTheStorageSectionWithASingleProviderSelector() {
        TestPanel panel = renderPanel(newStore());

        StorageParamsPanel storage = (StorageParamsPanel) panel.get("storageParams");

        assertThat(storage).isNotNull();
        assertThat(tester.getLastResponseAsString()).contains("type=\"radio\"");
    }

    @Test
    void leavesOutTheCachingParametersWhenTheHookSaysSo() {
        Map<String, Serializable> connectionParameters = new HashMap<>();
        connectionParameters.put(StorageConfig.PROVIDER_ID_KEY, "s3");
        renderPanel(newStore(connectionParameters));

        assertThat(tester.getLastResponseAsString()).doesNotContain("storage.caching.enabled");
    }

    @Test
    void submittingTheFormWritesTheUrlAndTheEditedParameter() {
        // the region field submits only while visible, which requires s3 selected up front
        Map<String, Serializable> connectionParameters = new HashMap<>();
        connectionParameters.put(StorageConfig.PROVIDER_ID_KEY, "s3");
        CoverageStoreInfo storeInfo = newStore(connectionParameters);
        renderPanel(storeInfo);

        StorageParamsPanel storage =
                (StorageParamsPanel) tester.getComponentFromLastRenderedPage("form:panel:storageParams");
        FormComponent<?> region = ((ParamPanel<?>) storage.fieldFor("storage.s3.region")).getFormComponent();

        FormTester formTester = tester.newFormTester(FORM_PATH);
        formTester.setValue(URL_TEXT_PATH, "s3://bucket/key.tif");
        formTester.setValue(region, "us-west-2");
        formTester.submit();

        tester.assertNoErrorMessage();
        CoverageStoreInfo submitted = submittedStoreInfo();
        assertThat(submitted.getURL()).isEqualTo("s3://bucket/key.tif");
        assertThat(submitted.getConnectionParameters()).containsEntry("storage.s3.region", "us-west-2");
    }

    @Test
    void anUntouchedSubmitStoresTheDefaultsOfTheSelectedBackendOnly() {
        Map<String, Serializable> connectionParameters = new HashMap<>();
        connectionParameters.put(StorageConfig.PROVIDER_ID_KEY, "s3");
        connectionParameters.put("storage.s3.endpoint", "http://localhost:9000");
        connectionParameters.put("storage.azure.account-key", "stale");
        CoverageStoreInfo storeInfo = newStore(connectionParameters);
        storeInfo.setURL("s3://bucket/key.tif");
        renderPanel(storeInfo);

        tester.newFormTester(FORM_PATH).submit();

        tester.assertNoErrorMessage();
        assertThat(submittedStoreInfo().getConnectionParameters())
                .containsOnly(
                        entry(StorageConfig.PROVIDER_ID_KEY, "s3"),
                        entry("storage.s3.endpoint", "http://localhost:9000"),
                        entry("storage.s3.requester-pays", Boolean.FALSE),
                        entry("storage.s3.anonymous", Boolean.FALSE));
    }

    @Test
    void aStoreSavedWithoutAProviderKeepsItsBackendParametersOnSave() {
        Map<String, Serializable> connectionParameters = new HashMap<>();
        connectionParameters.put("storage.s3.region", "us-west-2");
        connectionParameters.put("storage.s3.endpoint", "http://localhost:9000");
        CoverageStoreInfo storeInfo = newStore(connectionParameters);
        storeInfo.setURL("s3://bucket/key.tif");
        renderPanel(storeInfo);

        tester.newFormTester(FORM_PATH).submit();

        tester.assertNoErrorMessage();
        assertThat(submittedStoreInfo().getConnectionParameters())
                .containsEntry(StorageConfig.PROVIDER_ID_KEY, "s3")
                .containsEntry("storage.s3.region", "us-west-2")
                .containsEntry("storage.s3.endpoint", "http://localhost:9000");
    }

    @Test
    void aSubclassWithMarkupOfItsOwnRendersItsFieldBetweenTheUrlAndTheStorageParameters() {
        renderExtendingPanel(newStore());

        tester.assertComponent("form:panel:urlPanel", TextParamPanel.class);
        tester.assertComponent("form:panel:storageParams", StorageParamsPanel.class);
        TagTester ownField = tester.getTagByWicketId(ExtendingCoverageStoreEditPanel.OWN_FIELD_ID);
        assertThat(ownField.getValue()).isEqualTo(ExtendingCoverageStoreEditPanel.OWN_FIELD_TEXT);
        int urlField = renderedPositionOf("urlPanel");
        int fieldOfTheSubclass = renderedPositionOf(ExtendingCoverageStoreEditPanel.OWN_FIELD_ID);
        int storageSection = renderedPositionOf("storageParams");
        assertThat(urlField).as("the URL field renders").isNotNegative();
        assertThat(fieldOfTheSubclass)
                .as("the field of the subclass follows the URL field")
                .isGreaterThan(urlField);
        assertThat(storageSection)
                .as("the storage section follows the field of the subclass")
                .isGreaterThan(fieldOfTheSubclass);
    }

    @Test
    void browsingAddsABrowseButton() {
        BundleEntry.define(tester, "TestPanel.urlPlaceholder.tooltip", "One file, or a folder of them");

        BrowsingTestPanel panel = renderBrowsingPanel(newStore());

        StorageLocationField urlField = (StorageLocationField) panel.get("urlPanel");
        TagTester urlTag = tester.getTagByWicketId("urlPanel");

        assertThat(urlField.getFormComponent().isRequired()).isTrue();
        assertThat(urlTag.getAttribute("title")).isEqualTo("One file, or a folder of them");
        tester.assertComponent(BROWSE_LINK_PATH, AjaxSubmitLink.class);
    }

    @Test
    void browseLeavesTheStoreAsItWas(@TempDir Path folder) throws IOException {
        Path scenes = Files.createDirectories(folder.resolve("scenes"));
        GeoServerFixture.sandboxedTo(folder, folder);
        try {
            CoverageStoreInfo storeInfo = newStore(new HashMap<>(Map.of(StorageConfig.PROVIDER_ID_KEY, "file")));
            storeInfo.setURL("/stored/x.tif");
            renderBrowsingPanel(storeInfo);
            Map<String, Serializable> rendered = new HashMap<>(storeInfo.getConnectionParameters());
            FormTester form = tester.newFormTester(FORM_PATH, false);
            form.setValue(URL_TEXT_PATH, scenes.toString());

            tester.executeAjaxEvent(BROWSE_LINK_PATH, "click");

            assertThat(tester.getLastResponseAsString()).contains("gs-panel-StorageBrowserPanel");
            assertThat(storeInfo.getURL()).isEqualTo("/stored/x.tif");
            assertThat(storeInfo.getConnectionParameters()).isEqualTo(rendered);
        } finally {
            GeoServerFixture.clear();
        }
    }

    @Test
    void browsingKeepsTheUrlValidation() {
        renderBrowsingPanel(newStore());
        FormTester form = tester.newFormTester(FORM_PATH);
        form.setValue(URL_TEXT_PATH, "not a location");

        form.submit();

        assertThat(tester.getMessages(FeedbackMessage.ERROR)).isNotEmpty();
    }

    @Test
    void aBadStorageValueStopsBrowse(@TempDir Path folder) {
        GeoServerFixture.sandboxedTo(folder, folder);
        try {
            renderBrowsingPanel(newStore(new HashMap<>(Map.of(StorageConfig.PROVIDER_ID_KEY, "http"))));
            StorageParamsPanel storage =
                    (StorageParamsPanel) tester.getComponentFromLastRenderedPage("form:panel:storageParams");
            FormComponent<?> timeout =
                    ((ParamPanel<?>) storage.fieldFor("storage.http.timeout-millis")).getFormComponent();
            FormTester form = tester.newFormTester(FORM_PATH, false);
            form.setValue(URL_TEXT_PATH, folder.toString());
            form.setValue(timeout, "soon");

            tester.executeAjaxEvent(BROWSE_LINK_PATH, "click");

            assertThat(tester.getLastResponseAsString()).doesNotContain("gs-panel-StorageBrowserPanel");
            assertThat(timeout.hasErrorMessage()).isTrue();
        } finally {
            GeoServerFixture.clear();
        }
    }

    private TestPanel renderPanel(CoverageStoreInfo storeInfo) {
        Form<CoverageStoreInfo> form = new Form<>("form", new Model<>(storeInfo));
        TestPanel panel = new TestPanel("panel", form);
        form.add(panel);
        tester.startComponentInPage(form);
        return panel;
    }

    private void renderExtendingPanel(CoverageStoreInfo storeInfo) {
        Form<CoverageStoreInfo> form = new Form<>(FORM_PATH, new Model<>(storeInfo));
        ExtendingCoverageStoreEditPanel panel = new ExtendingCoverageStoreEditPanel("panel", form);
        form.add(panel);
        tester.startComponentInPage(form);
    }

    private BrowsingTestPanel renderBrowsingPanel(CoverageStoreInfo storeInfo) {
        Form<CoverageStoreInfo> form = new Form<>(FORM_PATH, new Model<>(storeInfo));
        BrowsingTestPanel panel = new BrowsingTestPanel("panel", form);
        form.add(panel);
        tester.startComponentInPage(form);
        return panel;
    }

    /** The offset of the tag of a component in the last response, negative when the component did not render. */
    private int renderedPositionOf(String wicketId) {
        String rendered = tester.getLastResponseAsString();
        return rendered.indexOf("wicket:id=\"" + wicketId + "\"");
    }

    @SuppressWarnings("unchecked")
    private CoverageStoreInfo submittedStoreInfo() {
        Form<CoverageStoreInfo> form =
                (Form<CoverageStoreInfo>) tester.getLastRenderedPage().get(FORM_PATH);
        return form.getModelObject();
    }

    private static CoverageStoreInfo newStore() {
        return new CoverageStoreInfoImpl(null);
    }

    private static CoverageStoreInfo newStore(Map<String, Serializable> connectionParameters) {
        CoverageStoreInfo storeInfo = newStore();
        storeInfo.getConnectionParameters().putAll(connectionParameters);
        return storeInfo;
    }

    @ParameterizedTest
    @MethodSource("recognizedStorageLocations")
    void acceptsRecognizedStorageLocations(String location) {
        assertThat(isValid(location)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unrecognizedStorageLocations")
    void rejectsUnrecognizedStorageLocations(String location) {
        assertThat(isValid(location)).isFalse();
    }

    private static Stream<String> recognizedStorageLocations() {
        return Stream.of(
                "s3://bucket/key.tif",
                "https://host/x.tif",
                "/data/x.tif",
                "/data/my dir/x.tif",
                "/data/50%.tif",
                "/data/a#b.tif",
                "C:\\data\\x.tif");
    }

    private static Stream<String> unrecognizedStorageLocations() {
        return Stream.of("notaurl://", "://missing-scheme", "not a location", "s3:/my bucket/x.tif");
    }

    private static boolean isValid(String location) {
        Validatable<String> validatable = new Validatable<>(location);
        new StorageUriValidator().validate(validatable);
        return validatable.isValid();
    }

    private static final class TestPanel extends StorageAwareCoverageStoreEditPanel {

        TestPanel(String id, Form storeEditForm) {
            super(id, storeEditForm);
        }

        @Override
        protected boolean cachingParameters() {
            return false;
        }

        @Override
        protected String urlPlaceholderKey() {
            return "TestPanel.urlPlaceholder";
        }
    }

    private static final class BrowsingTestPanel extends StorageAwareCoverageStoreEditPanel {

        BrowsingTestPanel(String id, Form storeEditForm) {
            super(id, storeEditForm);
        }

        @Override
        protected Optional<StorageBrowsing> browsing() {
            return Optional.of(StorageBrowsing.of(PickMode.FILE_OR_FOLDER, FileColumns.NONE));
        }

        @Override
        protected String urlPlaceholderKey() {
            return "TestPanel.urlPlaceholder";
        }
    }
}
