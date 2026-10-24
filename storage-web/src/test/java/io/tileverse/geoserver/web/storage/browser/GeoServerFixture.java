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

import java.nio.file.Path;

import org.geoserver.platform.GeoServerExtensionsHelper;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.security.FileAccessManager;

/**
 * GeoServer's extension lookup for tests without a GeoServer: a resource loader over a data directory, and a file
 * access manager confining the user to a sandbox folder when given one. A test clears it after use.
 */
public final class GeoServerFixture {

    private GeoServerFixture() {}

    /** A data directory, and no sandbox. */
    public static void withDataDirectory(Path dataDirectory) {
        register(dataDirectory, null);
    }

    /** A data directory, and a sandbox folder as the only root open to the user. */
    public static void sandboxedTo(Path dataDirectory, Path sandbox) {
        register(dataDirectory, sandbox);
    }

    public static void clear() {
        GeoServerExtensionsHelper.clear();
        GeoServerExtensionsHelper.setIsSpringContext(true);
    }

    private static void register(Path dataDirectory, Path sandbox) {
        GeoServerExtensionsHelper.setIsSpringContext(false);
        GeoServerResourceLoader loader = new GeoServerResourceLoader(dataDirectory.toFile());
        GeoServerExtensionsHelper.singleton("resourceLoader", loader, GeoServerResourceLoader.class);
        SandboxFileAccess fileAccess = new SandboxFileAccess(sandbox);
        GeoServerExtensionsHelper.singleton("fileAccessManager", fileAccess, FileAccessManager.class);
    }
}
