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

import org.junit.jupiter.api.Test;

import io.tileverse.storage.StorageConfig;
import io.tileverse.storage.StorageParameter;
import io.tileverse.storage.spi.StorageProvider;

class StorageParamsTest {

    @Test
    void cachingComesFirstThenBackendGroupsInDisplayOrder() {
        List<String> groups = StorageParams.orderedParameters(true).stream()
                .map(StorageParameter::group)
                .distinct()
                .toList();

        assertThat(groups).containsExactly("caching", "http", "s3", "gcs", "azure", "file");
    }

    @Test
    void keepsEachProvidersDeclaredOrderWithinItsGroup() {
        List<String> declaredHttpKeys = provider("http").getParameters().stream()
                .filter(parameter -> "http".equals(parameter.group()))
                .map(StorageParameter::key)
                .toList();

        List<String> httpKeys = keysInGroup("http");

        assertThat(httpKeys).containsExactlyElementsOf(declaredHttpKeys);
        assertThat(httpKeys)
                .startsWith(
                        "storage.http.timeout-millis", "storage.http.trust-all-certificates", "storage.http.username");
    }

    @Test
    void everyKeyIsUniqueAndStoragePrefixed() {
        List<String> keys = keys(StorageParams.orderedParameters(true));

        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).allMatch(key -> key.startsWith("storage."));
    }

    @Test
    void leavesOutTheCachingGroupWhenTheFlagIsOff() {
        List<String> withCaching = keys(StorageParams.orderedParameters(true));
        List<String> withoutCaching = keys(StorageParams.orderedParameters(false));

        assertThat(withCaching).anyMatch(key -> key.startsWith("storage.caching."));
        assertThat(withoutCaching).noneMatch(key -> key.startsWith("storage.caching."));
        assertThat(withoutCaching).hasSize(withCaching.size() - 1);
    }

    @Test
    void listsTheLocalFileGroupLastAndNeverTheProviderKey() {
        List<String> keys = keys(StorageParams.orderedParameters(true));

        assertThat(keys).last().isEqualTo("storage.file.idle-timeout");
        assertThat(keys).doesNotContain(StorageConfig.PROVIDER_ID_KEY);
    }

    @Test
    void providerIdsFollowTheDisplayOrderAndAreComplete() {
        List<String> registered = StorageProvider.getProviders().stream()
                .map(StorageProvider::getId)
                .toList();

        List<String> providerIds = StorageParams.providerIds();

        assertThat(providerIds).containsExactlyInAnyOrderElementsOf(registered);
        assertThat(providerIds).startsWith("http", "s3", "gcs", "azure", "azure-datalake", "file");
    }

    private static List<String> keys(List<StorageParameter<?>> parameters) {
        return parameters.stream().map(StorageParameter::key).toList();
    }

    private static List<String> keysInGroup(String group) {
        return StorageParams.orderedParameters(true).stream()
                .filter(parameter -> group.equals(parameter.group()))
                .map(StorageParameter::key)
                .toList();
    }

    private static StorageProvider provider(String id) {
        return StorageProvider.getProviders().stream()
                .filter(provider -> id.equals(provider.getId()))
                .findFirst()
                .orElseThrow();
    }
}
