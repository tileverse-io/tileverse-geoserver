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

/**
 * What a store page browses for. Kept by the store page, hence serializable.
 *
 * @param pickMode what the store can pick
 * @param columns the format columns of the store
 * @param localPaths whether a local choice is written as an absolute path instead of a {@code file:} URI, as the COG
 *     store needs: GeoServer's sandbox check reads a {@code file:} URL of a coverage store as a file name relative to
 *     the working directory
 */
public record StorageBrowsing(PickMode pickMode, FileColumns columns, boolean localPaths) implements Serializable {

    /** Browsing writing local choices as {@code file:} URIs. */
    public static StorageBrowsing of(PickMode pickMode, FileColumns columns) {
        return new StorageBrowsing(pickMode, columns, false);
    }

    /** The same browsing, writing local choices as absolute paths. */
    public StorageBrowsing withLocalPaths() {
        return new StorageBrowsing(pickMode, columns, true);
    }
}
