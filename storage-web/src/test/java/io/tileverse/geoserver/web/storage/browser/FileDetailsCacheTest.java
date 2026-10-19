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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.tileverse.storage.StorageEntry;

class FileDetailsCacheTest {

    private static final URI ROOT = URI.create("s3://bucket/");

    @Test
    void sameInputsGiveTheSameKey() {
        assertThat(keyOf(params("secret"), file("etag-1"))).isEqualTo(keyOf(params("secret"), file("etag-1")));
    }

    @Test
    void secretsChangeTheKey() {
        assertThat(keyOf(params("secret"), file("etag-1"))).isNotEqualTo(keyOf(params("other"), file("etag-1")));
    }

    @Test
    void versionsChangeTheKey() {
        assertThat(keyOf(params("secret"), file("etag-1"))).isNotEqualTo(keyOf(params("secret"), file("etag-2")));
    }

    @Test
    void noEtagUsesSizeAndDate() {
        StorageEntry.File older = withoutEtag(Instant.EPOCH);
        StorageEntry.File newer = withoutEtag(Instant.EPOCH.plusSeconds(1));

        assertThat(keyOf(params("secret"), older)).isNotEqualTo(keyOf(params("secret"), newer));
    }

    @Test
    void columnsChangeTheKey() {
        String digest = FileDetailsCache.digest(params("secret"));
        String lengthKey = FileDetailsCache.key(digest, new LengthColumns(), ROOT, file("etag-1"));
        String noneKey = FileDetailsCache.key(digest, FileColumns.NONE, ROOT, file("etag-1"));

        assertThat(lengthKey).isNotEqualTo(noneKey);
    }

    @Test
    void digestIgnoresTheOrder() {
        Properties first = new Properties();
        first.setProperty("storage.s3.region", "us-east-1");
        first.setProperty("storage.provider", "s3");
        Properties second = new Properties();
        second.setProperty("storage.provider", "s3");
        second.setProperty("storage.s3.region", "us-east-1");

        assertThat(FileDetailsCache.digest(first)).isEqualTo(FileDetailsCache.digest(second));
    }

    @Test
    void keepsTheValuesPut() {
        FileDetailsCache cache = FileDetailsCache.defaults();
        FileDetails.Values values = new FileDetails.Values(Map.of("length", ColumnValue.text("7")));

        cache.put("key", values);

        assertThat(cache.get("key")).contains(values);
        assertThat(cache.get("other")).isEmpty();
    }

    private static String keyOf(Properties storageParams, StorageEntry.File file) {
        return FileDetailsCache.key(FileDetailsCache.digest(storageParams), new LengthColumns(), ROOT, file);
    }

    private static Properties params(String secret) {
        Properties storageParams = new Properties();
        storageParams.setProperty("storage.s3.aws-secret-access-key", secret);
        return storageParams;
    }

    private static StorageEntry.File file(String etag) {
        return new StorageEntry.File(
                "a.txt", 7, Instant.EPOCH, Optional.of(etag), Optional.empty(), Optional.empty(), Map.of());
    }

    private static StorageEntry.File withoutEtag(Instant lastModified) {
        return new StorageEntry.File(
                "a.txt", 7, lastModified, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }
}
