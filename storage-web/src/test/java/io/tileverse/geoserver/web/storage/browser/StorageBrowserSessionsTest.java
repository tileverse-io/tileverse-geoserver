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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

class StorageBrowserSessionsTest {

    private static final BrowserLimits LIMITS = new BrowserLimits(10, 4, 1_000, 100, 200_000, Duration.ofMinutes(10));
    private static final StorageBrowsing BROWSING = StorageBrowsing.of(PickMode.FILE_OR_FOLDER, FileColumns.NONE);

    @TempDir
    Path folder;

    private final TestClock clock = new TestClock();
    private final List<FakeObjectStorage> opened = new ArrayList<>();

    @Test
    void tokensAreIsolated() {
        StorageBrowserSessions sessions = sessions(LIMITS);

        String first = sessions.open("s3://one/", new Properties(), BROWSING);
        String second = sessions.open("s3://two/", new Properties(), BROWSING);

        assertThat(first).isNotEqualTo(second);
        assertThat(sessions.get(first).orElseThrow().root()).hasToString("s3://one/");
        assertThat(sessions.get(second).orElseThrow().root()).hasToString("s3://two/");
    }

    @Test
    void anIdleBrowserCloses() {
        StorageBrowserSessions sessions = sessions(LIMITS);
        String token = sessions.open("s3://one/", new Properties(), BROWSING);

        clock.advance(Duration.ofMinutes(11));
        sessions.sweep();

        assertThat(opened.get(0).closed()).isTrue();
        assertThat(sessions.size()).isZero();
        assertThat(sessions.get(token)).isEmpty();
    }

    @Test
    void requestsKeepItOpen() {
        StorageBrowserSessions sessions = sessions(LIMITS);
        String token = sessions.open("s3://one/", new Properties(), BROWSING);

        clock.advance(Duration.ofMinutes(6));
        sessions.get(token);
        clock.advance(Duration.ofMinutes(6));
        sessions.sweep();

        assertThat(sessions.get(token)).isPresent();
    }

    @Test
    void closesTheLeastRecentlyUsed() {
        BrowserLimits twoSessions = new BrowserLimits(10, 4, 1_000, 2, 200_000, Duration.ofMinutes(10));
        StorageBrowserSessions sessions = sessions(twoSessions);
        String first = sessions.open("s3://one/", new Properties(), BROWSING);
        String second = sessions.open("s3://two/", new Properties(), BROWSING);
        sessions.get(first);

        String third = sessions.open("s3://three/", new Properties(), BROWSING);

        assertThat(sessions.get(second)).isEmpty();
        assertThat(opened.get(1).closed()).isTrue();
        assertThat(sessions.get(first)).isPresent();
        assertThat(sessions.get(third)).isPresent();
    }

    @Test
    void boundsTheEntriesHeld() {
        BrowserLimits threeEntries = new BrowserLimits(10, 4, 1_000, 100, 3, Duration.ofMinutes(10));
        StorageBrowserSessions sessions = sessions(threeEntries);
        String first = sessions.open("s3://one/a/", new Properties(), BROWSING);
        sessions.get(first).orElseThrow().openFolder("a/");
        String second = sessions.open("s3://two/a/", new Properties(), BROWSING);
        sessions.get(second).orElseThrow().openFolder("a/");

        assertThat(sessions.get(second)).isPresent();

        assertThat(sessions.get(first)).isEmpty();
    }

    // the browser asked for stays open although it alone holds more entries than the bound
    @Test
    void keepsTheBrowserAsked() {
        BrowserLimits oneEntry = new BrowserLimits(10, 4, 1_000, 100, 1, Duration.ofMinutes(10));
        StorageBrowserSessions sessions = sessions(oneEntry);
        String token = sessions.open("s3://one/a/", new Properties(), BROWSING);
        sessions.get(token).orElseThrow().openFolder("a/");

        assertThat(sessions.get(token)).isPresent();
        assertThat(opened.get(0).closed()).isFalse();
        assertThat(sessions.size()).isOne();
    }

    @Test
    void closeClosesTheStorage() {
        StorageBrowserSessions sessions = sessions(LIMITS);
        String token = sessions.open("s3://one/", new Properties(), BROWSING);

        sessions.close(token);

        assertThat(sessions.get(token)).isEmpty();
        assertThat(opened.get(0).closed()).isTrue();
    }

    @Test
    void aFailedOpenKeepsNothing() {
        StorageOpener failing = (root, params) -> {
            throw new IllegalStateException("no provider");
        };
        StorageBrowserSessions sessions = new StorageBrowserSessions(services(failing, LIMITS), clock);

        assertThatThrownBy(() -> sessions.open("s3://one/", new Properties(), BROWSING))
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(Reason.CANNOT_OPEN));
        assertThat(sessions.size()).isZero();
    }

    // A browser holds its lock while it lists a slow bucket.
    @Test
    void aBusyBrowserStallsNoOther() throws Exception {
        StorageBrowserSessions sessions = sessions(LIMITS);
        String busy = sessions.open("s3://one/", new Properties(), BROWSING);
        String other = sessions.open("s3://two/", new Properties(), BROWSING);
        StorageBrowser busyBrowser = sessions.get(busy).orElseThrow();

        synchronized (busyBrowser) {
            CompletableFuture<Optional<StorageBrowser>> found =
                    CompletableFuture.supplyAsync(() -> sessions.get(other));

            assertThat(found.get(5, TimeUnit.SECONDS)).isPresent();
        }
    }

    private StorageBrowserSessions sessions(BrowserLimits limits) {
        StorageOpener opener = (root, params) -> {
            FakeObjectStorage bucket =
                    new FakeObjectStorage(root.toString()).with("a/x.tif", "x").with("a/y.tif", "y");
            opened.add(bucket);
            return bucket;
        };
        return new StorageBrowserSessions(services(opener, limits), clock);
    }

    private BrowserServices services(StorageOpener opener, BrowserLimits limits) {
        FileDetailsLoader loader = new FileDetailsLoader(Runnable::run);
        FileDetailsCache cache = new FileDetailsCache(10, Duration.ofMinutes(30));
        return new BrowserServices(StubLocalFiles.rootedAt(folder), opener, loader, cache, limits);
    }
}
