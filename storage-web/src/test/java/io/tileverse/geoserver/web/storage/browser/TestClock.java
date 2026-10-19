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
import java.time.Instant;
import java.time.InstantSource;

/** A clock moved by the tests. */
final class TestClock implements InstantSource {

    private Instant now = Instant.parse("2026-10-18T10:00:00Z");

    @Override
    public synchronized Instant instant() {
        return now;
    }

    synchronized void advance(Duration duration) {
        now = now.plus(duration);
    }
}
