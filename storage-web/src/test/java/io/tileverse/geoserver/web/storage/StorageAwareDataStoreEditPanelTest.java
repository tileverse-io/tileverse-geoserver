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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.data.store.ParamInfo;
import org.geotools.api.data.DataAccessFactory;
import org.geotools.api.data.DataAccessFactory.Param;
import org.geotools.api.data.Parameter;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the two contracts of the base edit panel that hold without a running GeoServer: the custom-widget choice - a
 * Duration-typed key gets the duration widget, the s3 region key gets the searchable dropdown, any other key falls
 * through to GeoServer's stock input - and the fail-loud guard the ordered-key sourcing relies on. Sourcing the store's
 * parameter order re-resolves the store factory; an unresolvable factory must fail with a clear, store-named diagnostic
 * rather than a NullPointerException that would leave the store edit page with an opaque stack trace.
 */
class StorageAwareDataStoreEditPanelTest {

    private WicketTester tester;

    @BeforeEach
    void startTester() {
        tester = new WicketTester();
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
    }

    @Test
    void buildsTheDurationWidgetForADurationTypedKey() {
        Panel panel = customStoragePanel("storage.azure.max-retry-delay");

        assertThat(panel).isInstanceOf(DurationParamPanel.class);
    }

    @Test
    void buildsTheSearchableDropdownForTheS3RegionKey() {
        Panel panel = customStoragePanel("storage.s3.region");

        assertThat(panel).isInstanceOf(Select2ChoiceParamPanel.class);
    }

    @Test
    void fallsThroughToTheStockInputForAnyOtherKey() {
        assertThat(customStoragePanel("storage.azure.account-key")).isNull();
        assertThat(customStoragePanel("namespace")).isNull();
    }

    private static Panel customStoragePanel(String paramKey) {
        IModel<Map<String, Serializable>> paramsModel = Model.ofMap(new HashMap<>());
        return StorageAwareDataStoreEditPanel.customStoragePanel("panel", paramsModel, paramInfo(paramKey));
    }

    /**
     * A {@link ParamInfo} for {@code paramKey}; the widget choice keys off the name alone, never the binding. The
     * options metadata stands in for the region list the real factories declare; the searchable dropdown feeds its
     * choices from it.
     */
    private static ParamInfo paramInfo(String paramKey) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Parameter.OPTIONS, new ArrayList<>(List.of("us-east-1", "eu-west-1")));
        Param param = new Param(paramKey, String.class, paramKey, true, null, metadata);
        return new ParamInfo(param);
    }

    @Test
    void requireResolvedFailsLoudNamingTheStoreWhenTheFactoryIsNull() {
        assertThatThrownBy(() -> StorageAwareDataStoreEditPanel.requireResolved(null, "acme:places"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme:places");
    }

    @Test
    void requireResolvedReturnsTheResolvedFactory() {
        DataAccessFactory factory = new ShapefileDataStoreFactory();
        assertThat(StorageAwareDataStoreEditPanel.requireResolved(factory, "acme:places"))
                .isSameAs(factory);
    }
}
