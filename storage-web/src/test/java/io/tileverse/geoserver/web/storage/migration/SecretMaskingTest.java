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
package io.tileverse.geoserver.web.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SecretMaskingTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("keys")
    void secretKeysHideValues(String key, String expected) {
        Map<String, Serializable> parameters = Map.of(key, "value");

        Map<String, String> shown = SecretMasking.parameters(parameters, null);

        assertThat(shown).containsOnly(Map.entry(key, expected));
    }

    static Stream<Arguments> keys() {
        return Stream.of(
                Arguments.of("Password", MigrationPlan.MASK),
                Arguments.of("storage.s3.secretAccessKey", MigrationPlan.MASK),
                Arguments.of("storage.s3.accessKeyId", MigrationPlan.MASK),
                Arguments.of("sasToken", MigrationPlan.MASK),
                Arguments.of("uri", "value"));
    }

    @Test
    void noPlanDropsTheQuery() {
        String shown = SecretMasking.text("https://h/f.parquet?sig=TK", null);

        assertThat(shown).isEqualTo("https://h/f.parquet");
    }

    @Test
    void planScrubsTheSecrets() {
        MigrationPlan plan = MigrationPlan.to("New").secretValue("TK").build();

        String shown = SecretMasking.text("https://h/f.parquet?sig=TK", plan);

        assertThat(shown).isEqualTo("https://h/f.parquet?sig=" + MigrationPlan.MASK);
    }
}
