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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageFactory;

class FolderListerTest {

    @TempDir
    Path folder;

    private Storage local;

    @BeforeEach
    void openLocalStorage() {
        local = StorageFactory.open(folder.toUri());
    }

    @AfterEach
    void closeLocalStorage() throws IOException {
        local.close();
    }

    @Test
    void listsOneLevel() throws IOException {
        createFiles("a.tif", "b.parquet", "sub/deep.tif");

        FolderListing listing = new FolderLister(10).list(local, "");

        assertThat(keysOf(listing)).containsExactlyInAnyOrder("a.tif", "b.parquet", "sub/");
        assertThat(listing.hasFolder("sub/")).isTrue();
        assertThat(listing.file("a.tif")).isPresent();
        assertThat(listing.truncated()).isFalse();
    }

    @Test
    void foldersHaveNoTrailingName() throws IOException {
        createFiles("sub/deep.tif");

        BrowserEntry row = new FolderLister(10).list(local, "").rows().get(0);

        assertThat(row.name()).isEqualTo("sub");
        assertThat(row.folder()).isTrue();
    }

    @Test
    void stopsAtTheLimit() throws IOException {
        createFiles("1.tif", "2.tif", "3.tif", "4.tif", "5.tif");

        FolderListing listing = new FolderLister(3).list(local, "");

        assertThat(listing.size()).isEqualTo(3);
        assertThat(listing.truncated()).isTrue();
    }

    @Test
    void aFullListIsNotTruncated() throws IOException {
        createFiles("1.tif", "2.tif", "3.tif");

        assertThat(new FolderLister(3).list(local, "").truncated()).isFalse();
    }

    @Test
    void searchKeepsTheLetterCase() throws IOException {
        createFiles("scene_1.tif", "Scene_2.tif", "other.tif");

        FolderListing found = new FolderLister(10).search(local, "", "scene");

        assertThat(keysOf(found)).containsExactly("scene_1.tif");
    }

    @Test
    void listsAndSearchesANestedFolder() throws IOException {
        createFiles("sub/scene_1.tif", "sub/other.tif");
        FolderLister lister = new FolderLister(10);

        FolderListing listing = lister.list(local, "sub/");
        FolderListing found = lister.search(local, "sub/", "sc");

        assertThat(keysOf(listing)).containsExactlyInAnyOrder("sub/scene_1.tif", "sub/other.tif");
        assertThat(keysOf(found)).containsExactly("sub/scene_1.tif");
    }

    @Test
    void prefixesBecomeFolders() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/x.tif", "x").with("a/b/y.tif", "y");

        FolderListing listing = new FolderLister(10).list(bucket, "a/");

        assertThat(keysOf(listing)).containsExactlyInAnyOrder("a/x.tif", "a/b/");
        assertThat(listing.hasFolder("a/b/")).isTrue();
    }

    // the S3 console marks a folder with an object under the folder's own key
    @Test
    void skipsTheFolderMarker() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/", "").with("a/x.tif", "x");

        assertThat(keysOf(new FolderLister(10).list(bucket, "a/"))).containsExactly("a/x.tif");
    }

    @Test
    void searchListsByPrefix() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/scene.tif", "x").with("a/other.tif", "y");

        FolderListing found = new FolderLister(10).search(bucket, "a/", "sc");

        assertThat(bucket.listedPatterns()).containsExactly("a/sc");
        assertThat(keysOf(found)).containsExactly("a/scene.tif");
    }

    // on S3 a glob lists the whole folder, and "**" everything below it
    @Test
    void searchTextGlobIsLiteral() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/")
                .with("a/x*y.tif", "x")
                .with("a/xyz.tif", "y")
                .with("a/b/x.tif", "z");

        FolderListing found = new FolderLister(10).search(bucket, "a/", "x*");

        assertThat(bucket.listedPatterns()).containsExactly("a/x");
        assertThat(keysOf(found)).containsExactly("a/x*y.tif");
    }

    // the engine refuses a . or .. segment in a pattern, and typing .gitkeep starts with one
    @ParameterizedTest
    @MethodSource("dotTexts")
    void dotTextsListTheFolder(String text) {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/..hidden", "x").with("a/x.tif", "y");

        FolderListing found = new FolderLister(10).search(bucket, "a/", text);

        assertThat(bucket.listedPatterns()).containsExactly("a/");
        assertThat(keysOf(found)).containsExactly("a/..hidden");
    }

    // a slash in the text would list a level deeper
    @Test
    void slashTextStaysInTheFolder() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/x/y.tif", "x").with("a/xy.tif", "y");

        FolderListing found = new FolderLister(10).search(bucket, "a/", "x/y");

        assertThat(bucket.listedPatterns()).containsExactly("a/x");
        assertThat(keysOf(found)).isEmpty();
    }

    // ADLS Gen2 reads a pattern as a directory name, and a/sc names none
    @Test
    void realDirectoriesListTheFolder() {
        FakeObjectStorage lake = new FakeObjectStorage("abfss://container@account.dfs.core.windows.net/")
                .withRealDirectories()
                .with("a/scene.tif", "x")
                .with("a/other.tif", "y");

        FolderListing found = new FolderLister(10).search(lake, "a/", "sc");

        assertThat(lake.listedPatterns()).containsExactly("a/");
        assertThat(keysOf(found)).containsExactly("a/scene.tif");
    }

    // a text starting with a glob character narrows nothing on the server
    @Test
    void bucketSearchReadsOneListing() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/")
                .with("a.tif", "x")
                .with("b.tif", "x")
                .with("c.tif", "x")
                .with("{d}.tif", "x");

        FolderListing found = new FolderLister(2).search(bucket, "", "{");

        assertThat(keysOf(found)).isEmpty();
    }

    // the search reads one entry past the limit, as the folder listing does to tell it was cut
    @Test
    void bucketSearchReadsLimitPlusOne() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/")
                .with("a.tif", "x")
                .with("b.tif", "x")
                .with("{c}.tif", "x");

        FolderListing found = new FolderLister(2).search(bucket, "", "{");

        assertThat(keysOf(found)).containsExactly("{c}.tif");
    }

    // only the local files are read whole: a remote backend with real directories is bounded too
    @Test
    void realDirectoriesStayBounded() {
        FakeObjectStorage lake = new FakeObjectStorage("abfss://container@account.dfs.core.windows.net/")
                .withRealDirectories()
                .with("a.tif", "x")
                .with("b.tif", "x")
                .with("c.tif", "x")
                .with("d.tif", "x");

        FolderListing found = new FolderLister(2).search(lake, "", "d");

        assertThat(keysOf(found)).isEmpty();
    }

    // the file scheme marks the local backend, and a search reads its whole folder
    @Test
    void localSearchReadsAllTheFolder() {
        FakeObjectStorage disk = new FakeObjectStorage("file:///data/")
                .with("a.tif", "x")
                .with("b.tif", "x")
                .with("c.tif", "x")
                .with("{d}.tif", "x");

        FolderListing found = new FolderLister(2).search(disk, "", "{");

        assertThat(keysOf(found)).containsExactly("{d}.tif");
    }

    static Stream<String> dotTexts() {
        return Stream.of(".", "..");
    }

    private void createFiles(String... relativePaths) throws IOException {
        for (String relativePath : relativePaths) {
            Path file = folder.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, relativePath);
        }
    }

    private static List<String> keysOf(FolderListing listing) {
        return listing.rows().stream().map(BrowserEntry::key).toList();
    }
}
