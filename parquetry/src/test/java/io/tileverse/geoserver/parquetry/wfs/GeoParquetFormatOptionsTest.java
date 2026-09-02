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
package io.tileverse.geoserver.parquetry.wfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.geoserver.platform.ServiceException;
import org.junit.jupiter.api.Test;

import io.tileverse.parquetry.data.Compression;
import io.tileverse.parquetry.data.WriteOptions;
import io.tileverse.parquetry.data.WriteOptions.ParquetVersion;
import io.tileverse.parquetry.data.WriteOptions.RowGroupSize;

class GeoParquetFormatOptionsTest {

    @Test
    void emptyOptionsYieldDefaults() {
        assertThat(GeoParquetFormatOptions.parse(Map.of())).isEqualTo(WriteOptions.defaults());
    }

    @Test
    void parsesTheThreeKnobs() {
        WriteOptions options = GeoParquetFormatOptions.parse(
                Map.of("PARQUETVERSION", "2.0", "ROWGROUPSIZE", "50000", "COMPRESSION", "snappy"));
        assertThat(options.parquetVersion()).isEqualTo(ParquetVersion.V2_0);
        assertThat(options.rowGroupSize()).isEqualTo(RowGroupSize.rows(50000));
        assertThat(options.defaultCompression()).isEqualTo(Compression.snappy());
    }

    @Test
    void nullOptionValueIsTreatedAsAbsent() {
        Map<String, String> options = new HashMap<>();
        options.put("compression", null);
        assertThat(GeoParquetFormatOptions.parse(options)).isEqualTo(WriteOptions.defaults());
    }

    @Test
    void keysMatchCaseInsensitively() {
        WriteOptions options = GeoParquetFormatOptions.parse(Map.of("compression", "gzip"));
        assertThat(options.defaultCompression()).isEqualTo(Compression.gzip());
    }

    @Test
    void parquetVersion11IsAccepted() {
        assertThat(GeoParquetFormatOptions.parse(Map.of("PARQUETVERSION", "1.1"))
                        .parquetVersion())
                .isEqualTo(ParquetVersion.V1_1);
    }

    @Test
    void allCompressionCodecsParse() {
        assertThat(GeoParquetFormatOptions.parse(Map.of("COMPRESSION", "none")).defaultCompression())
                .isEqualTo(Compression.uncompressed());
        assertThat(GeoParquetFormatOptions.parse(Map.of("COMPRESSION", "zstd")).defaultCompression())
                .isEqualTo(Compression.zstd(Compression.Zstd.DEFAULT_LEVEL));
        assertThat(GeoParquetFormatOptions.parse(Map.of("COMPRESSION", "lz4")).defaultCompression())
                .isEqualTo(Compression.lz4Raw());
    }

    @Test
    void badValuesRaiseInvalidParameterValue() {
        assertThatThrownBy(() -> GeoParquetFormatOptions.parse(Map.of("COMPRESSION", "paq8")))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("code", ServiceException.INVALID_PARAMETER_VALUE)
                .hasFieldOrPropertyWithValue("locator", "format_options")
                .hasMessageContaining("paq8");
        assertThatThrownBy(() -> GeoParquetFormatOptions.parse(Map.of("ROWGROUPSIZE", "many")))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> GeoParquetFormatOptions.parse(Map.of("ROWGROUPSIZE", "-5")))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> GeoParquetFormatOptions.parse(Map.of("PARQUETVERSION", "3.0")))
                .isInstanceOf(ServiceException.class);
    }
}
