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

class StorageParamModelTest {

    private static final String KEY = "storage.http.timeout-millis";

    private final Map<String, Serializable> params = new HashMap<>();
    private final IModel<Map<String, Serializable>> paramsModel = Model.ofMap(params);

    @Test
    void readsAStringBehindABooleanParameterAsABoolean() {
        params.put("storage.s3.requester-pays", "true");

        StorageParamModel<Boolean> model =
                new StorageParamModel<>(paramsModel, "storage.s3.requester-pays", Boolean.class);

        assertThat(model.getObject()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void readsAStringBehindAnIntegerParameterAsAnInteger() {
        params.put(KEY, "5000");

        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        assertThat(model.getObject()).isEqualTo(5000);
    }

    @Test
    void passesATypedValueThrough() {
        params.put(KEY, 7);

        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        assertThat(model.getObject()).isEqualTo(7);
    }

    @Test
    void readsAnUnconvertibleStringAsNull() {
        params.put(KEY, "soon");

        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        assertThat(model.getObject()).isNull();
    }

    @Test
    void readsAMissingKeyAsNull() {
        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        assertThat(model.getObject()).isNull();
    }

    @Test
    void readingAStringBehindABooleanRewritesTheStoredValueAsTyped() {
        params.put("storage.s3.requester-pays", "true");
        StorageParamModel<Boolean> model =
                new StorageParamModel<>(paramsModel, "storage.s3.requester-pays", Boolean.class);

        model.getObject();

        assertThat(params.get("storage.s3.requester-pays")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void readingAStringBehindAnIntegerRewritesTheStoredValueAsTyped() {
        params.put(KEY, "5000");
        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        model.getObject();

        assertThat(params.get(KEY)).isEqualTo(5000);
    }

    @Test
    void readingAnUnconvertibleStringLeavesTheStoredValueUntouched() {
        params.put(KEY, "soon");
        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        model.getObject();

        assertThat(params.get(KEY)).isEqualTo("soon");
    }

    @Test
    void readingATypedValueWritesNothingNew() {
        Integer storedValue = 5000;
        params.put(KEY, storedValue);
        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        model.getObject();

        assertThat(params.get(KEY)).isSameAs(storedValue);
    }

    @Test
    void writesTheTypedValue() {
        StorageParamModel<Integer> model = new StorageParamModel<>(paramsModel, KEY, Integer.class);

        model.setObject(5000);

        assertThat(params).containsEntry(KEY, 5000);
        assertThat(params.get(KEY)).isInstanceOf(Integer.class);
    }
}
