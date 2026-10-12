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

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.junit.jupiter.api.Test;

class OptionalFlagModelTest {

    private static final String KEY = "storage.test.flag";

    private final Map<String, Serializable> params = new HashMap<>();
    private final IModel<Map<String, Serializable>> paramsModel = Model.ofMap(params);
    private final OptionalFlagModel model =
            new OptionalFlagModel(new StorageParamModel<>(paramsModel, KEY, Boolean.class));

    @Test
    void untickedStoresNothing() {
        model.setObject(Boolean.FALSE);

        assertThat(params).doesNotContainKey(KEY);
    }

    @Test
    void tickedStoresTrue() {
        model.setObject(Boolean.TRUE);

        assertThat(params).containsEntry(KEY, Boolean.TRUE);
    }

    @Test
    void untickingRemovesAStoredTrue() {
        params.put(KEY, Boolean.TRUE);

        model.setObject(Boolean.FALSE);

        assertThat(params).doesNotContainKey(KEY);
    }

    @Test
    void untickingRemovesATrueStoredAsText() {
        params.put(KEY, "true");

        model.setObject(Boolean.FALSE);

        assertThat(params).doesNotContainKey(KEY);
    }

    // set over REST: the page cannot write it and must not remove it
    @Test
    void untickedKeepsAStoredFalse() {
        params.put(KEY, Boolean.FALSE);

        model.setObject(Boolean.FALSE);

        assertThat(params).containsEntry(KEY, Boolean.FALSE);
    }

    @Test
    void readsAMissingParameterAsNull() {
        assertThat(model.getObject()).isNull();
    }

    @Test
    void readsTheStoredValue() {
        params.put(KEY, "true");

        assertThat(model.getObject()).isEqualTo(Boolean.TRUE);
    }
}
