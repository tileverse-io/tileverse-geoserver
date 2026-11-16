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

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geotools.api.data.DataAccessFactory.Param;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.geoserver.web.storage.migration.MigrationNote;
import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

class GeoParquetTranslationTest {

    @ParameterizedTest
    @MethodSource("translations")
    void translatesParameters(Map<String, Serializable> community, Map<String, String> expected) {
        MigrationPlan plan = GeoParquetTranslation.translate(community);

        assertThat(plan.targetParameters()).isEqualTo(expected);
        assertThat(plan.isBlocked()).isFalse();
    }

    static Stream<Arguments> translations() {
        return Stream.of(
                Arguments.of(Map.of("uri", "file:/d/roads.parquet"), Map.of("geoparquet", "file:/d/roads.parquet")),
                Arguments.of(
                        Map.of("uri", "s3://b/r.parquet?region=us-west-2&access_key=AK&secret_key=SK"),
                        Map.of(
                                "geoparquet", "s3://b/r.parquet",
                                "storage.s3.region", "us-west-2",
                                "storage.s3.aws-access-key-id", "AK",
                                "storage.s3.aws-secret-access-key", "SK")),
                Arguments.of(
                        Map.of(
                                "uri",
                                "s3://b/r.parquet?s3_region=eu-west-1&s3_access_key_id=AK&s3_secret_access_key=SK"),
                        Map.of(
                                "geoparquet", "s3://b/r.parquet",
                                "storage.s3.region", "eu-west-1",
                                "storage.s3.aws-access-key-id", "AK",
                                "storage.s3.aws-secret-access-key", "SK")),
                Arguments.of(
                        Map.of(
                                "uri", "s3://b/d/**/*.parquet",
                                "use_aws_credential_chain", Boolean.TRUE,
                                "aws_region", "eu-west-1",
                                "aws_profile", "prod",
                                "endpoint", "minio:9000",
                                "url_style", "path"),
                        Map.of(
                                "geoparquet", "s3://b/d/**/*.parquet",
                                "storage.s3.region", "eu-west-1",
                                "storage.s3.default-credentials-profile", "prod",
                                "storage.s3.endpoint", "https://minio:9000",
                                "storage.s3.force-path-style", "true")),
                Arguments.of(
                        Map.of(
                                "uri", "s3://naturalearth/*.parquet?s3_use_ssl=false",
                                "endpoint", "s3proxy:80",
                                "url_style", "path"),
                        Map.of(
                                "geoparquet", "s3://naturalearth/*.parquet",
                                "storage.s3.endpoint", "http://s3proxy:80",
                                "storage.s3.force-path-style", "true",
                                "storage.s3.anonymous", "true")),
                Arguments.of(
                        Map.of("uri", "s3://b/r.parquet"),
                        Map.of("geoparquet", "s3://b/r.parquet", "storage.s3.anonymous", "true")),
                Arguments.of(
                        Map.of(
                                "uri", "s3://b/r.parquet?region=us-west-2",
                                "aws_region", "eu-west-1",
                                "use_aws_credential_chain", "true"),
                        Map.of("geoparquet", "s3://b/r.parquet", "storage.s3.region", "us-west-2")),
                Arguments.of(
                        Map.of(
                                "uri", "https://h/r.parquet",
                                "namespace", "http://ns",
                                "primary_key_id", "gid",
                                "threads", Integer.valueOf(4),
                                "memory_limit", "1GB"),
                        Map.of("geoparquet", "https://h/r.parquet", "namespace", "http://ns", "fid", "gid")));
    }

    @Test
    void targetKeysAreFactoryParameters() {
        Set<String> factoryKeys = factoryParameterKeys();

        translations().forEach(vector -> {
            @SuppressWarnings("unchecked")
            Map<String, Serializable> community = (Map<String, Serializable>) vector.get()[0];
            MigrationPlan plan = GeoParquetTranslation.translate(community);
            assertThat(factoryKeys).containsAll(plan.targetParameters().keySet());
        });
    }

    @Test
    void targetTypeIsTheFactoryName() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "file:/d/roads.parquet"));

        String displayName = new GeoParquetDataStoreFactory().getDisplayName();
        assertThat(plan.targetType()).isEqualTo(displayName);
    }

    @Test
    void secretKeyIsMasked() {
        MigrationPlan plan = GeoParquetTranslation.translate(
                Map.of("uri", "s3://b/r.parquet?region=us-west-2&access_key=AK&secret_key=SK"));

        assertThat(plan.sourceDisplay())
                .containsEntry("uri", "s3://b/r.parquet?region=us-west-2&access_key=AK&secret_key=********");
        assertThat(plan.targetDisplay()).containsEntry("storage.s3.aws-secret-access-key", MigrationPlan.MASK);
        assertThat(plan.scrub("x SK y")).isEqualTo("x ******** y");
    }

    @Test
    void sourceKeepsTheStoredOrder() {
        Map<String, Serializable> community = new LinkedHashMap<>();
        community.put("namespace", "http://ns");
        community.put("uri", "file:/d/roads.parquet");
        community.put("threads", Integer.valueOf(4));

        MigrationPlan plan = GeoParquetTranslation.translate(community);

        assertThat(plan.sourceDisplay())
                .containsExactly(
                        Map.entry("namespace", "http://ns"),
                        Map.entry("uri", "file:/d/roads.parquet"),
                        Map.entry("threads", "4"));
    }

    @Test
    void unknownQueryKeyBlocks() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "s3://b/r.parquet?foo=1"));

        assertThat(plan.isBlocked()).isTrue();
        assertThat(plan.notes()).contains(MigrationNote.blocker("GeoParquetMigration.unknownQueryKey", "foo"));
    }

    @Test
    void unknownQueryValueIsMasked() {
        MigrationPlan plan = GeoParquetTranslation.translate(
                Map.of("uri", "s3://b/r.parquet?region=r&s3_session_token=TK%2Fx&foo=bar"));

        assertThat(plan.sourceDisplay())
                .containsEntry("uri", "s3://b/r.parquet?region=r&s3_session_token=********&foo=********");
        assertThat(plan.notes())
                .contains(
                        MigrationNote.blocker("GeoParquetMigration.unknownQueryKey", "s3_session_token"),
                        MigrationNote.blocker("GeoParquetMigration.unknownQueryKey", "foo"));
        assertThat(plan.scrub("TK/x TK%2Fx bar")).isEqualTo("******** ******** bar");
    }

    @Test
    void unknownValueIsNotASecret() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "s3://b/r1.parquet?foo=1"));

        assertThat(plan.sourceDisplay()).containsEntry("uri", "s3://b/r1.parquet?foo=********");
        assertThat(plan.targetDisplay()).containsEntry("geoparquet", "s3://b/r1.parquet");
        assertThat(plan.scrub("r1.parquet")).isEqualTo("r1.parquet");
    }

    @Test
    void sessionTokenIsMasked() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "s3://b/r.parquet?session_token=TK"));

        assertThat(plan.sourceDisplay()).containsEntry("uri", "s3://b/r.parquet?session_token=********");
        assertThat(plan.scrub("x TK y")).isEqualTo("x ******** y");
    }

    @Test
    void secretAliasesAreScrubbed() {
        MigrationPlan plan = GeoParquetTranslation.translate(
                Map.of("uri", "s3://b/r.parquet?access_key=AK&secret_key=S1&s3_secret_access_key=S%2B2"));

        assertThat(plan.scrub("S1 S+2 S%2B2")).isEqualTo("******** ******** ********");
    }

    @ParameterizedTest
    @MethodSource("uriSecrets")
    void writtenSecretsAreListed(String uri, List<String> expected) {
        assertThat(GeoParquetTranslation.writtenSecrets(uri)).containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Arguments> uriSecrets() {
        return Stream.of(
                Arguments.of(
                        "s3://b/*.parquet?s3_access_key_id=AK&s3_secret_access_key=wJalr%2FX&s3_session_token=TK",
                        List.of("wJalr%2FX", "TK")),
                Arguments.of("s3://b/*.parquet?secret_key=S1&session_token=T1&region=r", List.of("S1", "T1")),
                Arguments.of("s3://b/*.parquet?secret_key=&s3_use_ssl=false", List.of()),
                Arguments.of("file:/d/r.parquet", List.of()));
    }

    @ParameterizedTest
    @MethodSource("withoutUri")
    void missingUriBlocks(Map<String, Serializable> community) {
        MigrationPlan plan = GeoParquetTranslation.translate(community);

        assertThat(plan.isBlocked()).isTrue();
        assertThat(plan.notes()).containsExactly(MigrationNote.blocker("GeoParquetMigration.noUri"));
    }

    static Stream<Map<String, Serializable>> withoutUri() {
        return Stream.of(Map.of("namespace", "http://ns"), Map.of("namespace", "http://ns", "uri", " "));
    }

    @Test
    void anonymousReadIsNoted() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "s3://b/r.parquet"));

        assertThat(plan.notes()).containsExactly(MigrationNote.info("GeoParquetMigration.anonymous"));
    }

    @ParameterizedTest
    @MethodSource("endpointSchemes")
    void endpointSchemeIsNoted(String query, String expectedScheme) {
        String uri = "s3://b/r.parquet?endpoint=minio:9000&access_key=AK&secret_key=SK" + query;

        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", uri));

        assertThat(plan.targetParameters()).containsEntry("storage.s3.endpoint", expectedScheme + "minio:9000");
        assertThat(plan.notes())
                .containsExactly(
                        MigrationNote.info("GeoParquetMigration.endpointScheme", expectedScheme, "minio:9000"));
    }

    static Stream<Arguments> endpointSchemes() {
        return Stream.of(
                Arguments.of("", "https://"),
                Arguments.of("&s3_use_ssl=true", "https://"),
                Arguments.of("&s3_use_ssl=false", "http://"),
                Arguments.of("&s3_use_ssl=FALSE", "http://"),
                Arguments.of("&s3_use_ssl=no", "https://"));
    }

    @Test
    void endpointWithSchemeIsKept() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of(
                "uri", "s3://b/r.parquet?s3_use_ssl=false",
                "endpoint", "https://minio:9000",
                "use_aws_credential_chain", "true"));

        assertThat(plan.targetParameters()).containsEntry("storage.s3.endpoint", "https://minio:9000");
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void sslSettingIsKnown() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of("uri", "s3://b/r.parquet?s3_use_ssl=false"));

        assertThat(plan.isBlocked()).isFalse();
        assertThat(plan.sourceDisplay()).containsEntry("uri", "s3://b/r.parquet?s3_use_ssl=false");
        assertThat(plan.targetParameters()).doesNotContainKey("storage.s3.endpoint");
        assertThat(plan.scrub("false")).isEqualTo("false");
    }

    @Test
    void queryEndpointWins() {
        MigrationPlan plan = GeoParquetTranslation.translate(Map.of(
                "uri", "s3://b/r.parquet?s3_endpoint=http://q:9000&s3_url_style=path",
                "endpoint", "http://p:9000",
                "use_aws_credential_chain", "true"));

        assertThat(plan.targetParameters())
                .containsEntry("storage.s3.endpoint", "http://q:9000")
                .containsEntry("storage.s3.force-path-style", "true");
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void droppedSettingsAreNoted() {
        Map<String, Serializable> community = new LinkedHashMap<>();
        community.put("uri", "https://h/r.parquet");
        community.put("dbtype", "geoparquet");
        community.put("threads", Integer.valueOf(4));
        community.put("memory_limit", "1GB");
        community.put("screenmap", " ");

        MigrationPlan plan = GeoParquetTranslation.translate(community);

        assertThat(plan.notes())
                .containsExactly(MigrationNote.info("GeoParquetMigration.dropped", "threads, memory_limit"));
    }

    @Test
    void hiveDepthIsSaved() {
        MigrationPlan plan = GeoParquetTranslation.translate(
                Map.of("uri", "https://h/d/*.parquet", "max_hive_depth", Integer.valueOf(1)));

        assertThat(plan.savedSettings()).isEqualTo(Map.of("max_hive_depth", "1"));
        assertThat(plan.targetParameters()).containsOnlyKeys("geoparquet");
        assertThat(plan.notes()).isEmpty();
    }

    private static Set<String> factoryParameterKeys() {
        Param[] parameters = new GeoParquetDataStoreFactory().getParametersInfo();
        List<Param> parameterList = Arrays.asList(parameters);
        return parameterList.stream().map(parameter -> parameter.key).collect(Collectors.toSet());
    }
}
