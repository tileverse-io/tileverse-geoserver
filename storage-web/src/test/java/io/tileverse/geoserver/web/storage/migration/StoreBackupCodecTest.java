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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StoreBackupCodecTest {

    /** A word with one non-ASCII letter, built from its char code to keep the source ASCII. */
    private static final String ACCENTED = "caf" + (char) 0xE9;

    private static final Instant MIGRATED_AT = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void roundTripsABackup() {
        StoreBackup backup = new StoreBackup(
                "community-geoparquet",
                MIGRATED_AT,
                "GeoParquet",
                null,
                Map.of(
                        "uri", "s3://b/a=b?x=1&y=2",
                        "note", "line1\nline2",
                        "name", ACCENTED,
                        "threads", "4",
                        "fetch size", "100"),
                Map.of("FeatureTypeInfoImpl-1", "roads", "CoverageInfoImpl-2", ""),
                Map.of("kind", "S3"));

        assertThat(roundTrip(backup)).isEqualTo(backup);
    }

    @Test
    void keepsTheUrl() {
        StoreBackup backup = new StoreBackup(
                "old-raster", MIGRATED_AT, "OldRaster", "old://https://h/f.tif", Map.of(), Map.of(), Map.of());

        assertThat(roundTrip(backup).url()).isEqualTo("old://https://h/f.tif");
    }

    @Test
    void encodingIsStable() {
        StoreBackup backup = backupWithParameters(orderedMap("a", "1", "b", "2", "c", "3"));
        StoreBackup equalBackup = backupWithParameters(orderedMap("c", "3", "b", "2", "a", "1"));

        String text = StoreBackupCodec.encode(backup);

        assertThat(StoreBackupCodec.encode(equalBackup)).isEqualTo(text);
        assertThat(text.lines()).noneMatch(line -> line.startsWith("#"));
    }

    @ParameterizedTest
    @MethodSource("textsThatAreNotBackups")
    void rejectsOtherText(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> StoreBackupCodec.decode(text));
    }

    static Stream<Arguments> textsThatAreNotBackups() {
        return Stream.of(Arguments.of((String) null), Arguments.of("hello"));
    }

    @Test
    void rejectsABadInstant() {
        String text = "migration=m\nat=yesterday\ntype=X\n";

        assertThatIllegalArgumentException().isThrownBy(() -> StoreBackupCodec.decode(text));
    }

    private static StoreBackup roundTrip(StoreBackup backup) {
        String text = StoreBackupCodec.encode(backup);
        return StoreBackupCodec.decode(text);
    }

    private static StoreBackup backupWithParameters(Map<String, String> parameters) {
        return new StoreBackup("m", MIGRATED_AT, "X", null, parameters, parameters, parameters);
    }

    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
