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

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The open browsers of the server, keyed by a random token kept by the page of each dialog. A browser closes on a
 * choice, on Cancel, on the close button of its window, or once idle for the timeout of the limits. The limits also
 * bound the number of browsers and the entries listed by them together: beyond a bound, the least recently used browser
 * closes first. The entries bound is checked when a browser opens, when one is asked for and by the sweep. It is not a
 * hard cap: listings made between two checks may exceed it, for up to a minute.
 */
public final class StorageBrowserSessions {

    private static final long SWEEP_MINUTES = 1;

    private final BrowserServices services;
    private final InstantSource clock;

    /** In access order: the eldest is the least recently used. Guarded by this. */
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>(16, 0.75f, true);

    StorageBrowserSessions(BrowserServices services, InstantSource clock) {
        this.services = services;
        this.clock = clock;
    }

    /** The instance shared by the store pages of the server. */
    public static StorageBrowserSessions shared() {
        return Shared.INSTANCE;
    }

    /**
     * Opens a browser over the storage named by a typed location and storage parameters.
     *
     * @return the token of the browser
     * @throws BrowseException if the location names nothing to browse, or its storage cannot be opened
     */
    public String open(String location, Properties storageParams, StorageBrowsing browsing) {
        StorageBrowser browser = StorageBrowser.open(location, storageParams, browsing, services);
        String token = UUID.randomUUID().toString();
        List<StorageBrowser> closing = new ArrayList<>();
        register(token, browser, closing);
        closeAll(closing);
        return token;
    }

    /** The browser of {@code token}, or empty once closed. A request keeps the browser open for another timeout. */
    public Optional<StorageBrowser> get(String token) {
        List<StorageBrowser> closing = new ArrayList<>();
        Optional<StorageBrowser> found = touch(token, closing);
        closeAll(closing);
        return found;
    }

    public void close(String token) {
        Session removed;
        synchronized (this) {
            removed = sessions.remove(token);
        }
        if (removed != null) {
            removed.browser().close();
        }
    }

    /** Closes the idle browsers, then those beyond the bounds; run every minute by the shared instance. */
    void sweep() {
        List<StorageBrowser> closing = new ArrayList<>();
        synchronized (this) {
            removeIdle(closing);
            removeBeyondBounds("", closing);
        }
        closeAll(closing);
    }

    synchronized int size() {
        return sessions.size();
    }

    private synchronized void register(String token, StorageBrowser browser, List<StorageBrowser> closing) {
        removeIdle(closing);
        sessions.put(token, new Session(browser, clock.instant()));
        removeBeyondBounds(token, closing);
    }

    private synchronized Optional<StorageBrowser> touch(String token, List<StorageBrowser> closing) {
        removeIdle(closing);
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        session.touch(clock.instant());
        removeBeyondBounds(token, closing);
        return Optional.of(session.browser());
    }

    private void removeIdle(List<StorageBrowser> closing) {
        Instant oldestAllowed = clock.instant().minus(services.limits().idleTimeout());
        Iterator<Session> all = sessions.values().iterator();
        while (all.hasNext()) {
            Session session = all.next();
            if (session.lastRequest().isBefore(oldestAllowed)) {
                all.remove();
                closing.add(session.browser());
            }
        }
    }

    /** Removes the least recently used browsers until the bounds hold, never the one of {@code kept}. */
    private void removeBeyondBounds(String kept, List<StorageBrowser> closing) {
        Iterator<Map.Entry<String, Session>> eldestFirst = sessions.entrySet().iterator();
        while (beyondBounds() && eldestFirst.hasNext()) {
            Map.Entry<String, Session> eldest = eldestFirst.next();
            if (!eldest.getKey().equals(kept)) {
                eldestFirst.remove();
                closing.add(eldest.getValue().browser());
            }
        }
    }

    private boolean beyondBounds() {
        BrowserLimits limits = services.limits();
        return sessions.size() > limits.sessions() || heldEntries() > limits.heldEntries();
    }

    private int heldEntries() {
        int held = 0;
        for (Session session : sessions.values()) {
            held += session.browser().heldEntries();
        }
        return held;
    }

    /** Closed outside the lock: closing a browser waits for its running request. */
    private static void closeAll(List<StorageBrowser> closing) {
        for (StorageBrowser browser : closing) {
            browser.close();
        }
    }

    /** A browser and the time of its last request; guarded by the lock of the holder. */
    private static final class Session {

        private final StorageBrowser browser;
        private Instant lastRequest;

        Session(StorageBrowser browser, Instant lastRequest) {
            this.browser = browser;
            this.lastRequest = lastRequest;
        }

        StorageBrowser browser() {
            return browser;
        }

        Instant lastRequest() {
            return lastRequest;
        }

        void touch(Instant now) {
            lastRequest = now;
        }
    }

    /** Created on first use: GeoServer's rules for local files are looked up then. */
    private static final class Shared {

        private static final StorageBrowserSessions INSTANCE = create();

        private Shared() {}

        private static StorageBrowserSessions create() {
            BrowserServices services = new BrowserServices(
                    new GeoServerLocalFiles(),
                    StorageOpener.TILEVERSE,
                    FileDetailsLoader.defaults(),
                    FileDetailsCache.defaults(),
                    BrowserLimits.DEFAULTS);
            StorageBrowserSessions sessions = new StorageBrowserSessions(services, InstantSource.system());
            ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "storage-browser-sweeper");
                thread.setDaemon(true);
                return thread;
            });
            sweeper.scheduleWithFixedDelay(sessions::sweep, SWEEP_MINUTES, SWEEP_MINUTES, TimeUnit.MINUTES);
            return sessions;
        }
    }
}
