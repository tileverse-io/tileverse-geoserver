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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.WicketTester;
import org.apache.wicket.validation.Validatable;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.web.data.store.panel.ParamPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.StorageConfig;

import de.agilecoders.wicket.webjars.WicketWebjars;

/** Also tests {@link StorageUriValidator}, which needs no page. */
class StorageAwareCoverageStoreEditPanelTest {

    /** {@code startComponentInPage} keeps the form's own id. */
    private static final String FORM_PATH = "form";

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
        formTester.setValue("panel:urlPanel:border:border_body:paramValue", "s3://bucket/key.tif");
        formTester.setValue(region, "us-west-2");
        formTester.submit();

        tester.assertNoErrorMessage();
        CoverageStoreInfo submitted = submittedStoreInfo();
        assertThat(submitted.getURL()).isEqualTo("s3://bucket/key.tif");
        assertThat(submitted.getConnectionParameters()).containsEntry("storage.s3.region", "us-west-2");
    }

    private TestPanel renderPanel(CoverageStoreInfo storeInfo) {
        Form<CoverageStoreInfo> form = new Form<>("form", new Model<>(storeInfo));
        TestPanel panel = new TestPanel("panel", form);
        form.add(panel);
        tester.startComponentInPage(form);
        return panel;
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
        return Stream.of("s3://bucket/key.tif", "https://host/x.tif", "/data/x.tif");
    }

    private static Stream<String> unrecognizedStorageLocations() {
        return Stream.of("notaurl://", "://missing-scheme");
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
}
