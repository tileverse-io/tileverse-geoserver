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
import java.util.Properties;

/** Reads the storage parameters typed in a store form, for browsing before a save. */
@FunctionalInterface
public interface TypedStorageParameters extends Serializable {

    /** The typed parameters as text, or empty when a value failed its conversion and shows an error on its field. */
    Optional<Properties> read();
}
