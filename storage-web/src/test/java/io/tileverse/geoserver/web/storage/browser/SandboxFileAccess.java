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
import java.nio.file.Path;
import java.util.List;

import org.geoserver.security.FileAccessManager;

/** A file access manager for tests: no restriction without a sandbox, the sandbox folder as the only root with one. */
final class SandboxFileAccess implements FileAccessManager {

    /** Null for no sandbox. */
    private final Path sandbox;

    SandboxFileAccess(Path sandbox) {
        this.sandbox = sandbox == null ? null : sandbox.toAbsolutePath().normalize();
    }

    @Override
    public List<File> getAvailableRoots() {
        if (sandbox == null) {
            return null;
        }
        return List.of(sandbox.toFile());
    }

    @Override
    public File getSandbox() {
        if (sandbox == null) {
            return null;
        }
        return sandbox.toFile();
    }

    @Override
    public boolean checkAccess(File file) {
        return sandbox == null || file.toPath().toAbsolutePath().normalize().startsWith(sandbox);
    }
}
