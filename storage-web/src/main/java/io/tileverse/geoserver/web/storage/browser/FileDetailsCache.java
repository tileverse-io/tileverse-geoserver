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
package io.tileverse.geoserver.web.storage.browser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.tileverse.storage.StorageEntry;

/**
 * The format values read recently, shared by the browsers of the server: a file shown again, in another dialog or
 * another folder, needs no read. Failed reads stay out: they go with the listing, and Refresh reads them again.
 */
final class FileDetailsCache {

    private static final int MAXIMUM_SIZE = 10_000;
    private static final Duration EXPIRY = Duration.ofMinutes(30);

    private final Cache<String, FileDetails.Values> values;

    FileDetailsCache(int maximumSize, Duration expiry) {
        this.values = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expiry)
                .build();
    }

    static FileDetailsCache defaults() {
        return new FileDetailsCache(MAXIMUM_SIZE, EXPIRY);
    }

    Optional<FileDetails.Values> get(String key) {
        return Optional.ofNullable(values.getIfPresent(key));
    }

    void put(String key, FileDetails.Values read) {
        values.put(key, read);
    }

    /**
     * The key of the values of {@code file}: a SHA-256 of what decides them. The storage parameters, secrets included,
     * keep different credentials apart, while stores reading with the server's own credentials share entries. The
     * version of the file is its etag, or its size and date when the backend gives no etag.
     */
    static String key(String parametersDigest, FileColumns columns, URI root, StorageEntry.File file) {
        String version = file.etag().orElseGet(() -> file.size() + "@" + file.lastModified());
        String columnsName = columns.getClass().getName();
        return sha256(String.join("\n", parametersDigest, columnsName, root.toString(), file.key(), version));
    }

    /** A SHA-256 of the storage parameters, sorted by key. */
    static String digest(Properties storageParams) {
        StringBuilder text = new StringBuilder();
        for (String key : new TreeSet<>(storageParams.stringPropertyNames())) {
            text.append(key).append('=').append(storageParams.getProperty(key)).append('\n');
        }
        return sha256(text.toString());
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of the Java platform", e);
        }
    }
}
