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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the Duration-key detection against the real tileverse-storage provider registry: every {@code storage.*} key a
 * provider declares with a {@code Duration} type is recognized, and every other key - scalar storage params and core
 * store params alike - is not.
 */
class StorageParamTypesTest {

    static Stream<String> durationKeys() {
        return Stream.of(
                "storage.azure.retry-delay",
                "storage.azure.max-retry-delay",
                "storage.azure.try-timeout",
                "storage.file.idle-timeout");
    }

    static Stream<String> nonDurationKeys() {
        return Stream.of("storage.s3.region", "storage.caching.enabled", "storage.provider", "namespace");
    }

    @ParameterizedTest
    @MethodSource("durationKeys")
    void recognizesEveryDurationTypedProviderParameter(String key) {
        assertThat(StorageParamTypes.isDuration(key)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonDurationKeys")
    void rejectsScalarAndCoreParameterKeys(String key) {
        assertThat(StorageParamTypes.isDuration(key)).isFalse();
    }
}
