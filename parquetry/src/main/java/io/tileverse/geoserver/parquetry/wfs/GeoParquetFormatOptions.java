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

import java.util.Locale;
import java.util.Map;

import org.geoserver.platform.ServiceException;

import io.tileverse.parquetry.data.Compression;
import io.tileverse.parquetry.data.WriteOptions;
import io.tileverse.parquetry.data.WriteOptions.Builder;
import io.tileverse.parquetry.data.WriteOptions.ParquetVersion;
import io.tileverse.parquetry.data.WriteOptions.RowGroupSize;

/**
 * Translates the GetFeature {@code format_options} map into a {@link WriteOptions} for the GeoParquet output format.
 * Understands three knobs, matched case-insensitively against the option keys: {@code parquetVersion} ({@code "1.1"} or
 * {@code "2.0"}), {@code rowGroupSize} (a positive row count), and {@code compression} (one of {@code none},
 * {@code uncompressed}, {@code snappy}, {@code gzip}, {@code lz4}, {@code zstd}). Any option left unset keeps the
 * {@link WriteOptions} default for that knob; an unrecognized or unparsable value raises a
 * {@link ServiceException#INVALID_PARAMETER_VALUE} naming the offending value.
 */
final class GeoParquetFormatOptions {

    private static final String PARQUET_VERSION = "parquetVersion";
    private static final String ROW_GROUP_SIZE = "rowGroupSize";
    private static final String COMPRESSION = "compression";

    private GeoParquetFormatOptions() {}

    static WriteOptions parse(Map<String, ?> formatOptions) {
        Builder builder = WriteOptions.builder();
        WfsOutputFormats.option(formatOptions, PARQUET_VERSION)
                .ifPresent(value -> builder.parquetVersion(parquetVersion(value)));
        WfsOutputFormats.option(formatOptions, ROW_GROUP_SIZE)
                .ifPresent(value -> builder.rowGroupSize(RowGroupSize.rows(rowCount(value))));
        WfsOutputFormats.option(formatOptions, COMPRESSION)
                .ifPresent(value -> builder.defaultCompression(compression(value)));
        return builder.build();
    }

    private static ParquetVersion parquetVersion(String value) {
        return switch (value) {
            case "1.1" -> ParquetVersion.V1_1;
            case "2.0" -> ParquetVersion.V2_0;
            default -> throw invalidOption(PARQUET_VERSION, value);
        };
    }

    private static long rowCount(String value) {
        long rows;
        try {
            rows = Long.parseLong(value);
        } catch (NumberFormatException notANumber) {
            throw invalidOption(ROW_GROUP_SIZE, value);
        }
        if (rows <= 0) {
            throw invalidOption(ROW_GROUP_SIZE, value);
        }
        return rows;
    }

    private static Compression compression(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "uncompressed" -> Compression.uncompressed();
            case "snappy" -> Compression.snappy();
            case "gzip" -> Compression.gzip();
            case "lz4" -> Compression.lz4Raw();
            case "zstd" -> Compression.zstd(Compression.Zstd.DEFAULT_LEVEL);
            default -> throw invalidOption(COMPRESSION, value);
        };
    }

    private static ServiceException invalidOption(String optionName, String value) {
        return new ServiceException(
                "Invalid " + optionName + " format_option value: " + value,
                ServiceException.INVALID_PARAMETER_VALUE,
                "format_options");
    }
}
