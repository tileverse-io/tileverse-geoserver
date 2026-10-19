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

import java.io.File;
import java.util.List;

/**
 * The local folders open to the current user. The server answers with the roots of GeoServer's file chooser and its
 * file sandbox, tests with stubs.
 */
interface LocalFiles {

    /** The roots to browse from, in the order of GeoServer's file chooser; empty when no folder is open. */
    List<File> roots();

    /** Whether the current user may list or choose {@code file}. */
    boolean allows(File file);

    /** The data directory, used as the base of a relative location. */
    File dataDirectory();
}
