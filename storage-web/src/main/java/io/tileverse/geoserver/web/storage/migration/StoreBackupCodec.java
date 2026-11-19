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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Writes a {@link StoreBackup} as {@link Properties} text and reads it back. The text has no comment lines and lists
 * its keys in sorted order: equal backups give the same text, and a saved store does not change when nothing changed.
 */
public final class StoreBackupCodec {

    private static final String MIGRATION = "migration";
    private static final String AT = "at";
    private static final String TYPE = "type";
    private static final String URL = "url";
    private static final String PARAMETER_PREFIX = "param.";
    private static final String NATIVE_NAME_PREFIX = "native.";
    private static final String SETTING_PREFIX = "setting.";

    private StoreBackupCodec() {}

    public static String encode(StoreBackup backup) {
        Properties properties = toProperties(backup);
        return write(properties);
    }

    /** @throws IllegalArgumentException when the text is not a backup */
    public static StoreBackup decode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Not a store backup: null text");
        }
        Properties properties = read(text);
        return fromProperties(properties);
    }

    private static Properties toProperties(StoreBackup backup) {
        Properties properties = new Properties();
        properties.setProperty(MIGRATION, backup.migrationId());
        properties.setProperty(AT, backup.migratedAt().toString());
        properties.setProperty(TYPE, backup.type());
        if (backup.url() != null) {
            properties.setProperty(URL, backup.url());
        }
        putAll(properties, PARAMETER_PREFIX, backup.connectionParameters());
        putAll(properties, NATIVE_NAME_PREFIX, backup.nativeNames());
        putAll(properties, SETTING_PREFIX, backup.settings());
        return properties;
    }

    private static void putAll(Properties properties, String prefix, Map<String, String> values) {
        values.forEach((key, value) -> properties.setProperty(prefix + key, value));
    }

    /**
     * Lets {@link Properties} escape the keys and values, then drops its date comment and sorts the lines. Each entry
     * is one line because Properties escapes line breaks, and no entry line starts with "#" because Properties escapes
     * that character too.
     */
    private static String write(Properties properties) {
        StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String stored = writer.toString();
        return stored.lines()
                .filter(line -> !line.startsWith("#"))
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static Properties read(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    private static StoreBackup fromProperties(Properties properties) {
        requireKnownKeys(properties);
        String migrationId = required(properties, MIGRATION);
        Instant migratedAt = instant(required(properties, AT));
        String type = required(properties, TYPE);
        String url = properties.getProperty(URL);
        Map<String, String> parameters = valuesWithPrefix(properties, PARAMETER_PREFIX);
        Map<String, String> nativeNames = valuesWithPrefix(properties, NATIVE_NAME_PREFIX);
        Map<String, String> settings = valuesWithPrefix(properties, SETTING_PREFIX);
        return new StoreBackup(migrationId, migratedAt, type, url, parameters, nativeNames, settings);
    }

    private static void requireKnownKeys(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!isKnownKey(key)) {
                throw new IllegalArgumentException("Not a store backup, unknown key: " + key);
            }
        }
    }

    private static boolean isKnownKey(String key) {
        boolean fixedKey = MIGRATION.equals(key) || AT.equals(key) || TYPE.equals(key) || URL.equals(key);
        boolean prefixedKey = key.startsWith(PARAMETER_PREFIX)
                || key.startsWith(NATIVE_NAME_PREFIX)
                || key.startsWith(SETTING_PREFIX);
        return fixedKey || prefixedKey;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Not a store backup, missing key: " + key);
        }
        return value;
    }

    private static Map<String, String> valuesWithPrefix(Properties properties, String prefix) {
        Map<String, String> values = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                values.put(key.substring(prefix.length()), properties.getProperty(key));
            }
        }
        return values;
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Not a store backup, bad instant: " + text, e);
        }
    }
}
