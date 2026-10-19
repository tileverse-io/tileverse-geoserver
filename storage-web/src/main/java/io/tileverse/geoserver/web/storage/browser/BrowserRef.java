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

import java.io.Serializable;
import java.util.Optional;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

/** A component's handle on the browser of its dialog: the token, looked up in the shared sessions on each request. */
record BrowserRef(String token) implements Serializable {

    /** The browser, or empty once closed after its idle timeout. */
    Optional<StorageBrowser> browser() {
        return StorageBrowserSessions.shared().get(token);
    }

    /** The browser, for an action: a closed one stops the action with a message. */
    StorageBrowser require() {
        return browser().orElseThrow(() -> new BrowseException(Reason.EXPIRED));
    }
}
