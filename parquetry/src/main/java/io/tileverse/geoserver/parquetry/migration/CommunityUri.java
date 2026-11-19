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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

/**
 * The {@code uri} parameter of a community GeoParquet store: the location of the data, and the S3 settings appended to
 * it as a query string.
 *
 * @param location the text before the first {@code ?}
 * @param query the decoded query values by key, in their order in the URI
 */
record CommunityUri(String location, Map<String, String> query) {

    private static final String PARQUET_EXTENSION = ".parquet";

    private static final String GLOB_CHARACTERS = "*?{}[]";

    private static final String RECURSIVE_GLOB_SEGMENT = "**";

    CommunityUri {
        query = Collections.unmodifiableMap(new LinkedHashMap<>(query));
    }

    static CommunityUri parse(String uri) {
        String location = locationOf(uri);
        Map<String, String> query = new LinkedHashMap<>();
        rawQuery(uri).forEach((key, writtenValue) -> query.put(key, decode(writtenValue)));
        return new CommunityUri(location, query);
    }

    /** The query values as written in the URI, before decoding, by decoded key and in their order in the URI. */
    static Map<String, String> rawQuery(String uri) {
        String queryText = writtenQuery(uri);
        return parseQuery(queryText);
    }

    /** The query string as written in the URI, without its {@code ?}; empty when the URI has none. */
    static String writtenQuery(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0) {
            return "";
        }
        return uri.substring(queryStart + 1);
    }

    /** The URI with the values of the given query keys replaced by {@link MigrationPlan#MASK}. */
    String masked(Set<String> secretKeys) {
        if (query.isEmpty()) {
            return location;
        }
        StringJoiner pairs = new StringJoiner("&", location + "?", "");
        query.forEach((key, value) -> {
            String shownValue = secretKeys.contains(key) ? MigrationPlan.MASK : value;
            pairs.add(key + "=" + shownValue);
        });
        return pairs.toString();
    }

    /** Whether the location names one Parquet file, rather than a directory or a glob to list. */
    boolean isSingleFile() {
        String lowerCaseLocation = location.toLowerCase(Locale.ROOT);
        return lowerCaseLocation.endsWith(PARQUET_EXTENSION) && !hasGlobCharacter(location);
    }

    /**
     * The folder above the first glob segment, when the location has a recursive glob segment ({@code **}). The
     * community store reads a Hive tree through such a glob.
     */
    Optional<String> recursiveGlobRoot() {
        List<String> segments = List.of(location.split("/", -1));
        if (!segments.contains(RECURSIVE_GLOB_SEGMENT)) {
            return Optional.empty();
        }
        int rootLength = 0;
        for (String segment : segments) {
            if (hasGlobCharacter(segment)) {
                break;
            }
            rootLength += segment.length() + 1;
        }
        return Optional.of(location.substring(0, rootLength));
    }

    boolean isS3() {
        return location.regionMatches(true, 0, "s3://", 0, "s3://".length());
    }

    private static String locationOf(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0) {
            return uri;
        }
        return uri.substring(0, queryStart);
    }

    private static boolean hasGlobCharacter(String text) {
        return text.chars().anyMatch(character -> GLOB_CHARACTERS.indexOf(character) >= 0);
    }

    private static Map<String, String> parseQuery(String queryText) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : queryText.split("&")) {
            addPair(query, pair);
        }
        return query;
    }

    private static void addPair(Map<String, String> query, String pair) {
        if (pair.isEmpty()) {
            return;
        }
        int equals = pair.indexOf('=');
        if (equals < 0) {
            query.put(decode(pair), "");
            return;
        }
        String key = decode(pair.substring(0, equals));
        String writtenValue = pair.substring(equals + 1);
        query.put(key, writtenValue);
    }

    /**
     * Decodes the percent escapes. A plus sign stays a plus sign, as in DuckDB: AWS secret keys often hold one, and
     * form decoding would turn it into a space. A malformed escape is kept as written, to show the URI as stored.
     */
    private static String decode(String text) {
        String plusEscaped = text.replace("+", "%2B");
        try {
            return URLDecoder.decode(plusEscaped, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEscape) {
            return text;
        }
    }
}
