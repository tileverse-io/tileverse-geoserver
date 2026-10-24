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
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Local file rules for tests: fixed roots, a rule for the folders allowed, and a data directory. */
final class StubLocalFiles implements LocalFiles {

    private final List<File> roots;
    private final Predicate<Path> allowed;
    private final File dataDirectory;

    private StubLocalFiles(List<File> roots, Predicate<Path> allowed, File dataDirectory) {
        this.roots = roots;
        this.allowed = allowed;
        this.dataDirectory = dataDirectory;
    }

    /** One root, also the data directory, with everything allowed. */
    static StubLocalFiles rootedAt(Path root) {
        return new StubLocalFiles(List.of(root.toFile()), path -> true, root.toFile());
    }

    /** Several roots, the first one also the data directory, with everything allowed. */
    static StubLocalFiles withRoots(Path... roots) {
        List<File> files = Arrays.stream(roots).map(Path::toFile).toList();
        return new StubLocalFiles(files, path -> true, files.get(0));
    }

    /** No root open to the user. */
    static StubLocalFiles withoutRoots(Path dataDirectory) {
        return new StubLocalFiles(List.of(), path -> true, dataDirectory.toFile());
    }

    StubLocalFiles withDataDirectory(Path dataDirectory) {
        return new StubLocalFiles(roots, allowed, dataDirectory.toFile());
    }

    /** Refuses the files under {@code denied}, as a sandbox would. */
    StubLocalFiles denying(Path denied) {
        Path normalized = denied.toAbsolutePath().normalize();
        return new StubLocalFiles(roots, path -> !path.startsWith(normalized), dataDirectory);
    }

    @Override
    public List<File> roots() {
        return roots;
    }

    @Override
    public boolean allows(File file) {
        return allowed.test(file.toPath().toAbsolutePath().normalize());
    }

    @Override
    public File dataDirectory() {
        return dataDirectory;
    }
}
