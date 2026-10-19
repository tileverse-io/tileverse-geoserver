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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.StorageConfig;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

class BrowseStartTest {

    @TempDir
    Path folder;

    @Test
    void fileStartsInItsFolder() throws IOException {
        Path scene = createFile("scenes/a.tif");

        StartingPoint start = startAt(scene.toString());

        assertThat(start.local()).isTrue();
        assertThat(start.root()).isEqualTo(folder.toUri());
        assertThat(start.folderKey()).isEqualTo("scenes/");
    }

    @Test
    void folderStartsInItself() throws IOException {
        createFile("scenes/a.tif");

        assertThat(startAt(folder.resolve("scenes").toString()).folderKey()).isEqualTo("scenes/");
        assertThat(startAt(folder.resolve("scenes") + "/").folderKey()).isEqualTo("scenes/");
    }

    @Test
    void fileUriStartsInItsFolder() throws IOException {
        Path scene = createFile("my dir/a.tif");

        assertThat(startAt(scene.toUri().toString()).folderKey()).isEqualTo("my dir/");
    }

    @Test
    void globStartsBeforeIt() throws IOException {
        createFile("scenes/2024/a.tif");

        assertThat(startAt(folder.resolve("scenes/2024") + "/*.tif").folderKey())
                .isEqualTo("scenes/2024/");
        assertThat(startAt(folder.resolve("scenes") + "/*/a.tif").folderKey()).isEqualTo("scenes/");
    }

    @Test
    void emptyStartsAtTheFirstRoot() {
        StartingPoint start = startAt("");

        assertThat(start.root()).isEqualTo(folder.toUri());
        assertThat(start.folderKey()).isEmpty();
    }

    @Test
    void relativeUrlUsesTheDataDir() throws IOException {
        createFile("data/coverages/a.tif");
        StubLocalFiles localFiles = StubLocalFiles.rootedAt(folder).withDataDirectory(folder.resolve("data"));

        StartingPoint start = BrowseStart.of("file:coverages/a.tif", new Properties(), localFiles);

        assertThat(start.folderKey()).isEqualTo("data/coverages/");
    }

    @Test
    void missingFolderStartsAbove() throws IOException {
        createFile("scenes/a.tif");

        assertThat(startAt(folder.resolve("scenes/missing/x.tif").toString()).folderKey())
                .isEqualTo("scenes/");
    }

    @Test
    void outsideTheRootsStartsAtFirst() {
        Path outside = folder.getParent();

        StartingPoint start = startAt(outside.toString());

        assertThat(start.root()).isEqualTo(folder.toUri());
        assertThat(start.folderKey()).isEmpty();
    }

    @Test
    void deniedFolderStartsAtFirst() throws IOException {
        Path denied = createFile("private/a.tif").getParent();
        StubLocalFiles localFiles = StubLocalFiles.rootedAt(folder).denying(denied);

        StartingPoint start = BrowseStart.of(denied.toString(), new Properties(), localFiles);

        assertThat(start.root()).isEqualTo(folder.toUri());
        assertThat(start.folderKey()).isEmpty();
    }

    // the sandbox check applies to the opened folder, spelled as on disk
    @Test
    void deniedOtherCaseStartsAtFirst() throws IOException {
        Path denied = createFile("Private/a.tif").getParent();
        Path typed = folder.resolve("private");
        assumeTrue(Files.isDirectory(typed), "needs a case-insensitive file system");
        StubLocalFiles localFiles = StubLocalFiles.rootedAt(folder).denying(denied);

        StartingPoint start = BrowseStart.of(typed.toString(), new Properties(), localFiles);

        assertThat(start.root()).isEqualTo(folder.toUri());
        assertThat(start.folderKey()).isEmpty();
    }

    @Test
    void noRootIsRefused() {
        StubLocalFiles localFiles = StubLocalFiles.withoutRoots(folder);

        assertThatThrownBy(() -> BrowseStart.of("", new Properties(), localFiles))
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_LOCAL_ROOT));
    }

    // tileverse lists a folder typed in another letter case as empty
    @Test
    void typedCaseUsesTheDiskCase() throws IOException {
        createFile("Scenes/a.tif");
        assumeTrue(Files.isDirectory(folder.resolve("scenes")), "needs a case-insensitive file system");

        assertThat(startAt(folder.resolve("scenes").toString()).folderKey()).isEqualTo("Scenes/");
    }

    // a link keeps its name although its target is spelled in another letter case
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege")
    void linkKeepsTheTypedName() throws IOException {
        Path target = createFile("elsewhere/Data/a.tif").getParent();
        Files.createSymbolicLink(folder.resolve("data"), target);

        StartingPoint start = startAt(folder.resolve("data/a.tif").toString());

        assertThat(start.folderKey()).isEqualTo("data/");
    }

    // the real path of a junction to a drive root on Windows is a root, and a root has no name
    @Test
    void aRootRealPathKeepsTheTypedName() {
        Path root = folder.getRoot();

        assertThat(BrowseStart.nameOf(root, "data")).isEqualTo("data");
    }

    @Test
    void s3FileStartsInItsFolder() {
        StartingPoint start = startAt("s3://bucket/a/b/x.tif");

        assertThat(start.local()).isFalse();
        assertThat(start.root()).isEqualTo(URI.create("s3://bucket/"));
        assertThat(start.folderKey()).isEqualTo("a/b/");
    }

    @Test
    void s3BucketStartsAtItsRoot() {
        assertThat(startAt("s3://bucket").folderKey()).isEmpty();
        assertThat(startAt("s3://bucket/").folderKey()).isEmpty();
    }

    @Test
    void s3GlobStartsBeforeIt() {
        assertThat(startAt("s3://bucket/a/**/*.tif").folderKey()).isEqualTo("a/");
    }

    @Test
    void noBucketIsRefused() {
        assertThatThrownBy(() -> startAt("s3://"))
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_CONTAINER));
        assertThatThrownBy(() -> startAt("az://account/"))
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_CONTAINER));
    }

    @Test
    void invalidUriIsRefused() {
        assertThatThrownBy(() -> startAt("s3://bucket/my dir/x.tif"))
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_LOCATION));
    }

    @Test
    void encodedKeysAreDecoded() {
        assertThat(startAt("s3://bucket/my%20dir/x.tif").folderKey()).isEqualTo("my dir/");
    }

    @Test
    void pathStyleHttpRootIsTheBucket() {
        StartingPoint start = startAt("https://minio:9000/bucket/a/x.tif", "s3");

        assertThat(start.root()).isEqualTo(URI.create("https://minio:9000/bucket/"));
        assertThat(start.folderKey()).isEqualTo("a/");
    }

    @Test
    void awsPathStyleRootIsTheBucket() {
        StartingPoint start = startAt("https://s3.us-west-2.amazonaws.com/bucket/a/x.tif", "s3");

        assertThat(start.root()).isEqualTo(URI.create("https://s3.us-west-2.amazonaws.com/bucket/"));
        assertThat(start.folderKey()).isEqualTo("a/");
    }

    @Test
    void virtualHostedRootIsTheHost() {
        StartingPoint start = startAt("https://bucket.s3.us-west-2.amazonaws.com/a/x.tif", "s3");

        assertThat(start.root()).isEqualTo(URI.create("https://bucket.s3.us-west-2.amazonaws.com/"));
        assertThat(start.folderKey()).isEqualTo("a/");
    }

    @Test
    void azureRootIsTheContainer() {
        StartingPoint start = startAt("az://account/container/a/x.tif");

        assertThat(start.root()).isEqualTo(URI.create("az://account/container/"));
        assertThat(start.folderKey()).isEqualTo("a/");
    }

    @Test
    void dataLakeRootIsTheAuthority() {
        StartingPoint start = startAt("abfss://container@account.dfs.core.windows.net/a/x.parquet");

        assertThat(start.root()).isEqualTo(URI.create("abfss://container@account.dfs.core.windows.net/"));
        assertThat(start.folderKey()).isEqualTo("a/");
    }

    @Test
    void plainHttpRootIsTheHost() {
        StartingPoint start = startAt("https://host/data/x.tif");

        assertThat(start.root()).isEqualTo(URI.create("https://host/"));
        assertThat(start.folderKey()).isEqualTo("data/");
    }

    @ParameterizedTest
    @MethodSource("localLocations")
    void readsLocalLocations(String location) {
        assertThat(BrowseStart.isLocal(location)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("objectLocations")
    void readsObjectLocations(String location) {
        assertThat(BrowseStart.isLocal(location)).isFalse();
    }

    static Stream<String> localLocations() {
        return Stream.of("", "/data/x.tif", "C:\\data\\x.tif", "file:x.tif", "FILE:/x.tif", "data/x.tif");
    }

    static Stream<String> objectLocations() {
        return Stream.of("s3://bucket/x.tif", "http://host/x.tif", "gs://bucket/", "az://a/c/");
    }

    private StartingPoint startAt(String location) {
        return BrowseStart.of(location, new Properties(), StubLocalFiles.rootedAt(folder));
    }

    private StartingPoint startAt(String location, String providerId) {
        Properties storageParams = new Properties();
        storageParams.setProperty(StorageConfig.PROVIDER_ID_KEY, providerId);
        return BrowseStart.of(location, storageParams, StubLocalFiles.rootedAt(folder));
    }

    private Path createFile(String relativePath) throws IOException {
        Path file = folder.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
