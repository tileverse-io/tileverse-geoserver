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

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.util.logging.Logging;

import io.tileverse.storage.Storage;

/**
 * A storage held by a browser, closed once its last read ends after a close request: closing the dialog never cuts a
 * running read.
 */
final class OpenStorage {

    private static final Logger LOGGER = Logging.getLogger(OpenStorage.class);

    private final Storage storage;

    /** Guarded by this, as are the flags below. */
    private int reads;

    private boolean closeRequested;
    private boolean closed;

    OpenStorage(Storage storage) {
        this.storage = storage;
    }

    Storage storage() {
        return storage;
    }

    URI root() {
        return storage.baseUri();
    }

    /** Takes the storage for one read; false once a close was requested. */
    synchronized boolean acquire() {
        if (closeRequested) {
            return false;
        }
        reads++;
        return true;
    }

    synchronized void release() {
        reads--;
        closeIfIdle();
    }

    synchronized void close() {
        closeRequested = true;
        closeIfIdle();
    }

    private void closeIfIdle() {
        if (closeRequested && reads == 0 && !closed) {
            closed = true;
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try {
            storage.close();
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "closing a browsed storage failed", e);
        }
    }
}
