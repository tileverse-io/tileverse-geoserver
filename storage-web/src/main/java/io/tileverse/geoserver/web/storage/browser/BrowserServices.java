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

/**
 * What the browsers of a server share: the local file rules, the storage opener, the read pool, the values cache and
 * the limits.
 */
record BrowserServices(
        LocalFiles localFiles,
        StorageOpener opener,
        FileDetailsLoader loader,
        FileDetailsCache cache,
        BrowserLimits limits) {}
