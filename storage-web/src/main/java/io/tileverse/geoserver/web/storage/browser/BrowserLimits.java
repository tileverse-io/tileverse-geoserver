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

import java.time.Duration;

/**
 * The bounds of browsing.
 *
 * @param listedEntries the entries kept by a listing before it stops
 * @param readsPerBrowser the format reads running at once for one browser
 * @param sortReads the files read at most for a sort by a format column
 * @param sessions the browsers open at once on the server
 * @param heldEntries the listed entries held by the open browsers together
 * @param idleTimeout the time without a request before a browser closes
 */
record BrowserLimits(
        int listedEntries, int readsPerBrowser, int sortReads, int sessions, int heldEntries, Duration idleTimeout) {

    static final BrowserLimits DEFAULTS = new BrowserLimits(10_000, 4, 1_000, 100, 200_000, Duration.ofMinutes(10));
}
