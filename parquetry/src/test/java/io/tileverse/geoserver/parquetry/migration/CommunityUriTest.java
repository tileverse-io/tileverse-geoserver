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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommunityUriTest {

    @Test
    void parseDecodesTheQuery() {
        CommunityUri uri = CommunityUri.parse("s3://b/r.parquet?secret_key=a%2Fb+c&region=us-west-2");

        assertThat(uri.location()).isEqualTo("s3://b/r.parquet");
        assertThat(uri.query()).containsExactly(Map.entry("secret_key", "a/b+c"), Map.entry("region", "us-west-2"));
    }

    @Test
    void rawQueryKeepsTheEscapes() {
        String uri = "s3://b/r.parquet?secret_key=a%2Fb&region=us-west-2";

        assertThat(CommunityUri.rawQuery(uri))
                .containsExactly(Map.entry("secret_key", "a%2Fb"), Map.entry("region", "us-west-2"));
    }

    @ParameterizedTest
    @MethodSource("writtenQueries")
    void writtenQueryKeepsTheText(String uri, String expected) {
        assertThat(CommunityUri.writtenQuery(uri)).isEqualTo(expected);
    }

    static Stream<Arguments> writtenQueries() {
        return Stream.of(
                Arguments.of("s3://b/*.parquet?s3_use_ssl=false", "s3_use_ssl=false"),
                Arguments.of("s3://b/r.parquet?secret_key=a%2Fb&region=r", "secret_key=a%2Fb&region=r"),
                Arguments.of("s3://b/r.parquet?", ""),
                Arguments.of("file:/d/r.parquet", ""));
    }

    @Test
    void parseKeepsMalformedEscapes() {
        CommunityUri uri = CommunityUri.parse("s3://b/r.parquet?secret_key=a%zz");

        assertThat(uri.query()).containsExactly(Map.entry("secret_key", "a%zz"));
    }

    @Test
    void parseWithoutQuery() {
        CommunityUri uri = CommunityUri.parse("file:/d/roads.parquet");

        assertThat(uri.location()).isEqualTo("file:/d/roads.parquet");
        assertThat(uri.query()).isEmpty();
    }

    @Test
    void maskedHidesSecretValues() {
        CommunityUri uri = CommunityUri.parse("s3://b/r.parquet?region=us-west-2&secret_key=SK");

        assertThat(uri.masked(Set.of("secret_key"))).isEqualTo("s3://b/r.parquet?region=us-west-2&secret_key=********");
    }

    @Test
    void singleFileIgnoresCase() {
        assertThat(CommunityUri.parse("s3://b/A.PARQUET").isSingleFile()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("listedLocations")
    void listedLocationsAreNotSingle(String location) {
        assertThat(CommunityUri.parse(location).isSingleFile()).isFalse();
    }

    static Stream<String> listedLocations() {
        return Stream.of("s3://b/d/*.parquet", "s3://b/d/", "s3://b/{a,b}.parquet", "s3://b/[ab].parquet");
    }

    @ParameterizedTest
    @MethodSource("recursiveGlobs")
    void recursiveGlobHasARoot(String location, Optional<String> expected) {
        assertThat(CommunityUri.parse(location).recursiveGlobRoot()).isEqualTo(expected);
    }

    static Stream<Arguments> recursiveGlobs() {
        return Stream.of(
                Arguments.of("s3://b/hive/**/*.parquet", Optional.of("s3://b/hive/")),
                Arguments.of("file:///d/hive/**/*", Optional.of("file:///d/hive/")),
                Arguments.of("file:/d/*/**/*.parquet", Optional.of("file:/d/")),
                Arguments.of("s3://b/d/*.parquet", Optional.empty()),
                Arguments.of("s3://b/d/**.parquet", Optional.empty()),
                Arguments.of("s3://b/d/", Optional.empty()));
    }

    @ParameterizedTest
    @MethodSource("schemes")
    void s3SchemeIsRecognized(String location, boolean expected) {
        assertThat(CommunityUri.parse(location).isS3()).isEqualTo(expected);
    }

    static Stream<Arguments> schemes() {
        return Stream.of(
                Arguments.of("s3://b/r.parquet", true),
                Arguments.of("S3://b/r.parquet", true),
                Arguments.of("https://h/r.parquet", false),
                Arguments.of("file:/d/s3.parquet", false));
    }
}
