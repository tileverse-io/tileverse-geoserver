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
package io.tileverse.geoserver.web.storage.migration;

/**
 * A migration or a revert could not be saved to the catalog. The store is unchanged. The renamed resources of the store
 * get their previous names back when the catalog accepts the save.
 */
public class MigrationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MigrationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
