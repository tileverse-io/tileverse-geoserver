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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.geoserver.platform.GeoServerExtensionsHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoServerLocalFilesTest {

    /** Spelled out, not shared with the class under test, to catch a wrong name there. */
    private static final String HIDE_FILE_SYSTEM = "GEOSERVER_FILEBROWSER_HIDEFS";

    @TempDir
    Path folder;

    @AfterEach
    void clearGeoServer() {
        GeoServerFixture.clear();
    }

    @Test
    void hiddenFileSystemOffersTheDataDir() {
        GeoServerFixture.withDataDirectory(folder);

        assertThat(new GeoServerLocalFiles(true).roots()).containsExactly(folder.toFile());
    }

    @Test
    void shownFileSystemAddsItsRoots() {
        GeoServerFixture.withDataDirectory(folder);

        List<File> roots = new GeoServerLocalFiles(false).roots();

        assertThat(roots.get(0)).isEqualTo(folder.toFile());
        assertThat(roots).containsAll(Arrays.asList(File.listRoots()));
    }

    @Test
    void hideSettingKeepsOnlyTheDataDir() {
        GeoServerFixture.withDataDirectory(folder);
        GeoServerExtensionsHelper.property(HIDE_FILE_SYSTEM, "true");

        assertThat(new GeoServerLocalFiles().roots()).containsExactly(folder.toFile());
    }

    @Test
    void noSettingOffersTheFileSystem() {
        assumeFalse(machineDefinesTheSetting(), "needs the setting undefined on the machine");
        GeoServerFixture.withDataDirectory(folder);

        List<File> roots = new GeoServerLocalFiles().roots();

        assertThat(roots).containsAll(Arrays.asList(File.listRoots()));
    }

    @Test
    void theSandboxReplacesTheRoots() throws IOException {
        Path sandbox = Files.createDirectories(folder.resolve("sandbox"));
        GeoServerFixture.sandboxedTo(folder, sandbox);

        assertThat(new GeoServerLocalFiles(false).roots()).containsExactly(sandbox.toFile());
    }

    @Test
    void allowsFollowsTheSandbox() throws IOException {
        Path sandbox = Files.createDirectories(folder.resolve("sandbox"));
        GeoServerFixture.sandboxedTo(folder, sandbox);
        GeoServerLocalFiles localFiles = new GeoServerLocalFiles(false);

        assertThat(localFiles.allows(sandbox.resolve("a.tif").toFile())).isTrue();
        assertThat(localFiles.allows(folder.resolve("a.tif").toFile())).isFalse();
    }

    @Test
    void theDataDirComesFromTheLoader() {
        GeoServerFixture.withDataDirectory(folder);

        assertThat(new GeoServerLocalFiles(true).dataDirectory()).isEqualTo(folder.toFile());
    }

    private static boolean machineDefinesTheSetting() {
        return System.getProperty(HIDE_FILE_SYSTEM) != null || System.getenv(HIDE_FILE_SYSTEM) != null;
    }
}
