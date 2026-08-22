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
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure decision behind the group headers: given the store's ordered connection-parameter keys, which keys
 * begin a titled backend group. The parameters arrive contiguous per group, in the order the store factory reports
 * them: the core fields, the provider selector, then the backend groups.
 */
class GroupedParamHeaderTest {

    private static final List<String> ORDERED_KEYS = List.of(
            "geoparquet",
            "namespace",
            "fid",
            "layer-grouping",
            "storage.provider",
            "storage.azure.account-key",
            "storage.azure.endpoint",
            "storage.caching.enabled",
            "storage.gcs.endpoint",
            "storage.gcs.project-id",
            "storage.http.bearer-token",
            "storage.http.username",
            "storage.s3.endpoint",
            "storage.s3.region");

    @Test
    void marksTheFirstKeyOfEachTitledGroupInOrder() {
        Set<String> firstKeys = StorageParamVisibility.firstParamKeysPerGroup(ORDERED_KEYS);
        assertThat(firstKeys)
                .containsExactly(
                        "storage.azure.account-key",
                        "storage.caching.enabled",
                        "storage.gcs.endpoint",
                        "storage.http.bearer-token",
                        "storage.s3.endpoint");
    }

    @Test
    void coreFieldsAndTheProviderSelectorNeverBeginAGroup() {
        Set<String> firstKeys = StorageParamVisibility.firstParamKeysPerGroup(ORDERED_KEYS);
        assertThat(firstKeys).doesNotContain("geoparquet", "namespace", "fid", "layer-grouping", "storage.provider");
    }

    @Test
    void aTitledGroupYieldsOneHeaderEvenWhenAnUntitledFieldInterruptsIt() {
        List<String> interrupted =
                List.of("storage.s3.endpoint", "storage.provider", "storage.s3.region", "storage.azure.endpoint");
        Set<String> firstKeys = StorageParamVisibility.firstParamKeysPerGroup(interrupted);
        assertThat(firstKeys).containsExactly("storage.s3.endpoint", "storage.azure.endpoint");
    }
}
