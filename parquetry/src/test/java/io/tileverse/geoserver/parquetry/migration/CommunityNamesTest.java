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
package io.tileverse.geoserver.parquetry.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommunityNamesTest {

    @ParameterizedTest
    @MethodSource("names")
    void followsTheCommunityRule(String typeName, String expected) {
        assertThat(CommunityNames.layerName(typeName)).isEqualTo(expected);
    }

    static Stream<Arguments> names() {
        String withNonAsciiLetter = "caf" + (char) 0xE9;
        return Stream.of(
                Arguments.of("roads", "roads"),
                Arguments.of(" roads ", "roads"),
                Arguments.of("my data", "my_data"),
                Arguments.of("a--b__c", "a_b_c"),
                Arguments.of("2024_roads", "_2024_roads"),
                Arguments.of("theme=buildings/type=building", "theme_buildings_type_building"),
                Arguments.of("trailing_", "trailing"),
                Arguments.of("", "_"),
                Arguments.of("-", "_"),
                Arguments.of(".hidden", "_.hidden"),
                Arguments.of(withNonAsciiLetter, "caf"));
    }

    @ParameterizedTest
    @MethodSource("namesWithQuery")
    void appendsTheQueryString(String typeName, String writtenQuery, String expected) {
        assertThat(CommunityNames.layerNameWithQuery(typeName, writtenQuery)).isEqualTo(expected);
    }

    static Stream<Arguments> namesWithQuery() {
        return Stream.of(
                Arguments.of("countries", "s3_use_ssl=false", "countries.parquet_s3_use_ssl_false"),
                Arguments.of("coastlines", "s3_use_ssl=false", "coastlines.parquet_s3_use_ssl_false"),
                Arguments.of(
                        "countries",
                        "s3_use_ssl=false&s3_region=us-east-1",
                        "countries.parquet_s3_use_ssl_false_s3_region_us_east_1"));
    }

    @ParameterizedTest
    @MethodSource("namesWithSecrets")
    void shownNameMasksSecrets(String nativeName, List<String> secrets, String expected) {
        assertThat(CommunityNames.shownName(nativeName, secrets)).isEqualTo(expected);
    }

    static Stream<Arguments> namesWithSecrets() {
        String secretKey = "wJalr/XUtn+FEMI";
        return Stream.of(
                Arguments.of(
                        "roads.parquet_s3_access_key_id_AK_s3_secret_access_key_wJalr_XUtn_FEMI",
                        List.of(secretKey),
                        "roads.parquet_s3_access_key_id_AK_s3_secret_access_key_********"),
                Arguments.of(
                        "roads.parquet_s3_secret_access_key_wJalr_XUtn_FEMI_s3_session_token_TK",
                        List.of(secretKey, "TK"),
                        "roads.parquet_s3_secret_access_key_********_s3_session_token_********"),
                Arguments.of("roads.parquet_secret_key_a_b", List.of("-a//b/"), "roads.parquet_secret_key_********"),
                Arguments.of("roads", List.of(secretKey), "roads"),
                Arguments.of("roads", List.of("/"), "roads"),
                Arguments.of("roads.parquet_s3_use_ssl_false", List.of(), "roads.parquet_s3_use_ssl_false"));
    }
}
