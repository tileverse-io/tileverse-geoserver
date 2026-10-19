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

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.security.FileAccessManager;
import org.geoserver.web.wicket.browser.FileRootsFinder;

/**
 * The local folders open to the current user, by GeoServer's own rules: the roots of its file chooser and its file
 * sandbox. Without a sandbox, the roots are the data directory, and the home directory and the file system roots unless
 * {@code GEOSERVER_FILEBROWSER_HIDEFS} is {@code true}. With a sandbox, the roots are the folders allowed to the user.
 * The full administrator keeps the roots of the case without a sandbox, unless the system administrator set the sandbox
 * with {@code GEOSERVER_FILESYSTEM_SANDBOX}. A custom {@link FileAccessManager} applies through the same lookup. The
 * user is the one of the current request.
 */
final class GeoServerLocalFiles implements LocalFiles {

    private static final String HIDE_FILE_SYSTEM = "GEOSERVER_FILEBROWSER_HIDEFS";

    private final boolean hideFileSystem;

    GeoServerLocalFiles() {
        this(Boolean.parseBoolean(GeoServerExtensions.getProperty(HIDE_FILE_SYSTEM)));
    }

    GeoServerLocalFiles(boolean hideFileSystem) {
        this.hideFileSystem = hideFileSystem;
    }

    @Override
    public List<File> roots() {
        FileRootsFinder finder = new FileRootsFinder(hideFileSystem, true);
        return List.copyOf(finder.getRoots());
    }

    @Override
    public boolean allows(File file) {
        return FileAccessManager.lookupFileAccessManager().checkAccess(file);
    }

    @Override
    public File dataDirectory() {
        return GeoServerExtensions.bean(GeoServerResourceLoader.class).getBaseDirectory();
    }
}
