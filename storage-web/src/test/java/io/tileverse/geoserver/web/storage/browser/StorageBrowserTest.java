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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.AccessDeniedException;
import io.tileverse.storage.StorageException;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

class StorageBrowserTest {

    private static final BrowserLimits LIMITS = new BrowserLimits(10, 4, 1_000, 100, 200_000, Duration.ofMinutes(10));
    private static final StorageBrowsing FILES_OR_FOLDERS =
            StorageBrowsing.of(PickMode.FILE_OR_FOLDER, FileColumns.NONE);

    @TempDir
    Path folder;

    private final ManualExecutor executor = new ManualExecutor();
    private final List<URI> openedRoots = new ArrayList<>();
    private final List<Properties> openedWith = new ArrayList<>();
    private StorageBrowser browser;

    @AfterEach
    void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
    }

    @Test
    void listsTheStartFolder() throws IOException {
        createFiles("scenes/a.tif", "scenes/b.tif");

        browser = openLocal(folder.resolve("scenes/a.tif").toString(), FILES_OR_FOLDERS);

        assertThat(browser.folder()).isEqualTo("scenes/");
        assertThat(namesOf(browser.rows())).containsExactlyInAnyOrder("a.tif", "b.tif");
    }

    @Test
    void opensAListedFolder() throws IOException {
        createFiles("scenes/2024/a.tif");
        browser = openLocal("", FILES_OR_FOLDERS);

        browser.openFolder("scenes/");

        assertThat(browser.folder()).isEqualTo("scenes/");
        assertThat(namesOf(browser.rows())).containsExactly("2024");
    }

    @Test
    void opensAnAncestor() throws IOException {
        createFiles("scenes/2024/a.tif");
        browser = openLocal(folder.resolve("scenes/2024").toString(), FILES_OR_FOLDERS);

        browser.openFolder("scenes/");

        assertThat(namesOf(browser.rows())).containsExactly("2024");
    }

    @Test
    void crumbsStartAtTheRoot() throws IOException {
        createFiles("scenes/2024/a.tif");

        browser = openLocal(folder.resolve("scenes/2024").toString(), FILES_OR_FOLDERS);

        assertThat(browser.breadcrumbs())
                .extracting(Crumb::label)
                .containsExactly(Path.of(browser.root()).toString(), "scenes", "2024");
        assertThat(browser.breadcrumbs()).extracting(Crumb::key).containsExactly("", "scenes/", "scenes/2024/");
    }

    @Test
    void refusesAnUnlistedFolder() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal("", FILES_OR_FOLDERS);

        assertRefused(() -> browser.openFolder("nope/"), Reason.FORBIDDEN);
    }

    @ParameterizedTest
    @MethodSource("parentKeys")
    void refusesParentKeys(String key) throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal(folder.resolve("scenes").toString(), FILES_OR_FOLDERS);

        assertRefused(() -> browser.openFolder(key), Reason.FORBIDDEN);
    }

    static Stream<String> parentKeys() {
        return Stream.of("../", "scenes/../", "%2e%2e/", "/etc/");
    }

    @Test
    void refusesToChooseADotFolder() {
        browser = openAtDotFolder();

        assertRefused(() -> browser.chooseFolder(), Reason.FORBIDDEN);
    }

    @Test
    void refusesToSearchADotFolder() {
        browser = openAtDotFolder();

        assertRefused(() -> browser.search("b"), Reason.FORBIDDEN);
    }

    @Test
    void refusesADeniedFolder() throws IOException {
        createFiles("private/a.tif", "public/b.tif");
        LocalFiles localFiles = StubLocalFiles.rootedAt(folder).denying(folder.resolve("private"));
        browser = open("", new Properties(), FILES_OR_FOLDERS, localFiles, StorageOpener.TILEVERSE, LIMITS);
        browser.openFolder(browser.folder());

        assertRefused(() -> browser.openFolder("private/"), Reason.FORBIDDEN);
    }

    @Test
    void choosesAFileUri() throws IOException {
        Path file = createFiles("my dir/a#b%.tif");
        browser = openLocal(file.toString(), FILES_OR_FOLDERS);

        URI chosen = browser.chooseFile("my dir/a#b%.tif");

        assertThat(chosen).isEqualTo(file.toUri());
        assertThat(chosen.toString()).endsWith("/my%20dir/a%23b%25.tif");
    }

    @Test
    void choosesTheFolderShown() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal(folder.resolve("scenes").toString(), StorageBrowsing.of(PickMode.FOLDER, FileColumns.NONE));

        assertThat(browser.chooseFolder().toString()).endsWith("/scenes/");
    }

    @Test
    void filesOnlyRefusesFolders() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal(folder.resolve("scenes").toString(), StorageBrowsing.of(PickMode.FILE, FileColumns.NONE));

        assertRefused(() -> browser.chooseFolder(), Reason.NOT_PICKABLE);
    }

    @Test
    void foldersOnlyRefusesFiles() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal(folder.resolve("scenes").toString(), StorageBrowsing.of(PickMode.FOLDER, FileColumns.NONE));

        assertRefused(() -> browser.chooseFile("scenes/a.tif"), Reason.NOT_PICKABLE);
    }

    @Test
    void refusesAnUnlistedFile() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal(folder.resolve("scenes").toString(), FILES_OR_FOLDERS);

        assertRefused(() -> browser.chooseFile("scenes/elsewhere.tif"), Reason.FORBIDDEN);
    }

    @Test
    void opensAnotherRoot() throws IOException {
        Path one = Files.createDirectories(folder.resolve("one"));
        Path two = Files.createDirectories(folder.resolve("two"));
        Files.writeString(two.resolve("b.tif"), "b");
        browser = open(
                "",
                new Properties(),
                FILES_OR_FOLDERS,
                StubLocalFiles.withRoots(one, two),
                StorageOpener.TILEVERSE,
                LIMITS);
        browser.openFolder(browser.folder());

        browser.openRoot(1);

        assertThat(browser.root()).isEqualTo(two.toUri());
        assertThat(namesOf(browser.rows())).containsExactly("b.tif");
    }

    @ParameterizedTest
    @MethodSource("missingRoots")
    void refusesAMissingRoot(int index) throws IOException {
        Path one = Files.createDirectories(folder.resolve("one"));
        Path two = Files.createDirectories(folder.resolve("two"));
        browser = openLocal("", FILES_OR_FOLDERS, StubLocalFiles.withRoots(one, two));

        assertRefused(() -> browser.openRoot(index), Reason.FORBIDDEN);
    }

    static IntStream missingRoots() {
        return IntStream.of(-1, 2);
    }

    @Test
    void refusesADeniedRoot() throws IOException {
        Path one = Files.createDirectories(folder.resolve("one"));
        Path two = Files.createDirectories(folder.resolve("two"));
        LocalFiles localFiles = StubLocalFiles.withRoots(one, two).denying(two);
        browser = openLocal("", FILES_OR_FOLDERS, localFiles);

        assertRefused(() -> browser.openRoot(1), Reason.FORBIDDEN);
        assertThat(browser.root()).isEqualTo(one.toUri());
    }

    @Test
    void bucketPrefixesAreFolders() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").with("a/x.tif", "x").with("a/b/y.tif", "y");

        browser = openBucket(bucket, "s3://bucket/a/x.tif", new Properties(), LIMITS);

        assertThat(openedRoots).containsExactly(URI.create("s3://bucket/"));
        assertThat(browser.folder()).isEqualTo("a/");
        assertThat(browser.rows()).extracting(BrowserEntry::key).containsExactlyInAnyOrder("a/x.tif", "a/b/");
    }

    @Test
    void bucketChoiceIsEncoded() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a/my dir/b#c%.tif", "x");
        browser = openBucket(bucket, "s3://bucket/a/my%20dir/", new Properties(), LIMITS);

        URI chosen = browser.chooseFile("a/my dir/b#c%.tif");

        assertThat(chosen).hasToString("s3://bucket/a/my%20dir/b%23c%25.tif");
        assertThat(chosen.getPath()).isEqualTo("/a/my dir/b#c%.tif");
    }

    // a store reads a raw glob character in its location as a glob
    @ParameterizedTest
    @MethodSource("globNamedFiles")
    void bucketChoiceHidesGlobs(String name, String encodedName) {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a/" + name, "x");
        browser = openBucket(bucket, "s3://bucket/a/", new Properties(), LIMITS);

        URI chosen = browser.chooseFile("a/" + name);

        assertThat(chosen).hasToString("s3://bucket/a/" + encodedName);
        assertThat(chosen.getPath()).isEqualTo("/a/" + name);
    }

    static Stream<Arguments> globNamedFiles() {
        return Stream.of(
                Arguments.of("x*y.tif", "x%2Ay.tif"),
                Arguments.of("x?y.tif", "x%3Fy.tif"),
                Arguments.of("x[1].tif", "x%5B1%5D.tif"),
                Arguments.of("x{a}.tif", "x%7Ba%7D.tif"));
    }

    @Test
    void keyWithAColonRoundTrips() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a/x:y.tif", "x");
        browser = openBucket(bucket, "s3://bucket/a/", new Properties(), LIMITS);

        assertThat(browser.chooseFile("a/x:y.tif").getPath()).isEqualTo("/a/x:y.tif");
    }

    @Test
    void nonAsciiKeyRoundTrips() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a/caf\u00e9.tif", "x");
        browser = openBucket(bucket, "s3://bucket/a/", new Properties(), LIMITS);

        URI chosen = browser.chooseFile("a/caf\u00e9.tif");

        assertThat(URI.create(chosen.toString()).getPath()).isEqualTo("/a/caf\u00e9.tif");
    }

    @Test
    void searchesATruncatedListing() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/")
                .with("a/b1.tif", "1")
                .with("a/b2.tif", "2")
                .with("a/c.tif", "3");
        BrowserLimits twoEntries = new BrowserLimits(2, 4, 1_000, 100, 200_000, Duration.ofMinutes(10));
        browser = openBucket(bucket, "s3://bucket/a/", new Properties(), twoEntries);

        assertThat(browser.truncated()).isTrue();
        assertThat(namesOf(browser.search("b"))).containsExactly("b1.tif", "b2.tif");
        assertThat(bucket.listedPatterns()).contains("a/b");
        assertThat(browser.heldEntries()).isEqualTo(4);
    }

    @Test
    void leavesOutCachingParameters() {
        Properties typed = new Properties();
        typed.setProperty("storage.caching.enabled", "true");
        typed.setProperty("storage.s3.region", "us-east-1");

        browser = openBucket(new FakeObjectStorage("s3://bucket/"), "s3://bucket/", typed, LIMITS);

        assertThat(openedWith.get(0)).containsOnlyKeys("storage.s3.region");
    }

    @Test
    void localKeepsFileParameters() throws IOException {
        createFiles("a.tif");
        Properties typed = new Properties();
        typed.setProperty("storage.provider", "s3");
        typed.setProperty("storage.file.idle-timeout", "PT1S");
        StorageOpener recording = (root, params) -> {
            openedWith.add(params);
            return StorageOpener.TILEVERSE.open(root, params);
        };

        browser = open("", typed, FILES_OR_FOLDERS, StubLocalFiles.rootedAt(folder), recording, LIMITS);

        assertThat(openedWith.get(0)).containsOnlyKeys("storage.file.idle-timeout");
    }

    @Test
    void reportsABackendUnableToList() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").unableToList();
        browser = open(
                "s3://bucket/a/",
                new Properties(),
                FILES_OR_FOLDERS,
                StubLocalFiles.rootedAt(folder),
                opener(bucket),
                LIMITS);

        assertRefused(() -> browser.openFolder("a/"), Reason.CANNOT_LIST);
    }

    @Test
    void reportsDeniedListing() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").failingToList(new AccessDeniedException("403"));
        browser = open(
                "s3://bucket/a/",
                new Properties(),
                FILES_OR_FOLDERS,
                StubLocalFiles.rootedAt(folder),
                opener(bucket),
                LIMITS);

        assertRefused(() -> browser.openFolder("a/"), Reason.ACCESS_DENIED);
    }

    @Test
    void hidesParameterValues() {
        FakeObjectStorage bucket =
                new FakeObjectStorage("s3://bucket/").failingToList(new StorageException("denied for AKIA12345"));
        Properties typed = new Properties();
        typed.setProperty("storage.s3.aws-access-key-id", "AKIA12345");
        browser = open(
                "s3://bucket/a/", typed, FILES_OR_FOLDERS, StubLocalFiles.rootedAt(folder), opener(bucket), LIMITS);

        assertThatThrownBy(() -> browser.openFolder("a/")).isInstanceOfSatisfying(BrowseException.class, e -> {
            assertThat(e.reason()).isEqualTo(Reason.FAILED);
            assertThat(e.detail()).isEqualTo("denied for ***");
        });
    }

    // a glob character in a listing pattern makes tileverse list the whole bucket below the glob's prefix
    @Test
    void refusesAGlobNamedFolder() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a[1]/x.tif", "x");
        browser = openBucket(bucket, "s3://bucket/", new Properties(), LIMITS);

        assertRefused(() -> browser.openFolder("a[1]/"), Reason.UNLISTABLE_NAME);
        assertThat(bucket.listedPatterns()).containsExactly("");
    }

    @Test
    void readsTheRowsShown() throws IOException {
        createFiles("a.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));

        browser.showRows(browser.rows());
        assertThat(executor.pending()).isEqualTo(1);
        executor.runAll();

        assertThat(browser.details(browser.rows().get(0))).isPresent();
    }

    @Test
    void closesAfterRunningReads() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/").with("a.txt", "abc");
        browser = open(
                "s3://bucket/",
                new Properties(),
                StorageBrowsing.of(PickMode.FILE, new LengthColumns()),
                StubLocalFiles.rootedAt(folder),
                opener(bucket),
                LIMITS);
        browser.openFolder("");
        browser.showRows(browser.rows());

        browser.close();
        assertThat(bucket.closed()).isFalse();
        executor.runAll();

        assertThat(bucket.closed()).isTrue();
        browser = null;
    }

    @Test
    void aClosedBrowserIsExpired() throws IOException {
        createFiles("scenes/a.tif");
        browser = openLocal("", FILES_OR_FOLDERS);

        browser.close();

        assertRefused(() -> browser.openFolder("scenes/"), Reason.EXPIRED);
    }

    @Test
    void sortReadsMissingValues() throws IOException {
        createFiles("aaa.txt", "b.txt", "cc.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));

        SortProgress started = browser.sortBy(LengthColumns.LENGTH, browser.rows());
        assertThat(started.read()).isZero();
        assertThat(started.total()).isEqualTo(3);
        executor.runAll();

        assertThat(browser.sortBy(LengthColumns.LENGTH, browser.rows()).complete())
                .isTrue();
        assertThat(namesOf(sorted(LengthColumns.LENGTH, true))).containsExactly("b.txt", "cc.txt", "aaa.txt");
    }

    @Test
    void sortStopsAtItsBound() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        BrowserLimits twoReads = new BrowserLimits(10, 4, 2, 100, 200_000, Duration.ofMinutes(10));
        browser = openLengths(twoReads);

        SortProgress progress = browser.sortBy(LengthColumns.LENGTH, browser.rows());

        assertThat(progress.tooMany()).isTrue();
        assertThat(progress.complete()).isFalse();
        assertThat(executor.pending()).isZero();
    }

    @Test
    void tooManySortCountsAgain() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        BrowserLimits twoReads = new BrowserLimits(10, 4, 2, 100, 200_000, Duration.ofMinutes(10));
        browser = openLengths(twoReads);
        assertThat(browser.sortBy(LengthColumns.LENGTH, browser.rows()).tooMany())
                .isTrue();
        browser.showRows(browser.rows().subList(0, 1));
        executor.runAll();

        SortProgress again = browser.sortBy(LengthColumns.LENGTH, browser.rows());

        assertThat(again.tooMany()).isFalse();
        assertThat(executor.pending()).isEqualTo(2);
    }

    @Test
    void rowsWithoutValuesComeLast() throws IOException {
        createFiles("sub/x.tif", "aaa.txt", "b.bin", "broken.txt", "c.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.LENGTH, browser.rows());
        executor.runAll();

        assertThat(namesOf(sorted(LengthColumns.LENGTH, true)))
                .containsExactly("sub", "c.txt", "aaa.txt", "b.bin", "broken.txt");
        assertThat(namesOf(sorted(LengthColumns.LENGTH, false)))
                .containsExactly("sub", "aaa.txt", "c.txt", "b.bin", "broken.txt");
    }

    @Test
    void textSortIgnoresTheCase() throws IOException {
        createFiles("one.txt", "Two.txt", "apple.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.FIRST, browser.rows());
        executor.runAll();

        assertThat(namesOf(sorted(LengthColumns.FIRST, true))).containsExactly("apple.txt", "one.txt", "Two.txt");
    }

    @Test
    void pagingKeepsSortReads() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        browser = openLengths(oneReadAtATime());
        browser.sortBy(LengthColumns.LENGTH, browser.rows());

        browser.showRows(List.of());
        executor.runAll();

        assertThat(browser.sortProgress().orElseThrow().complete()).isTrue();
    }

    @Test
    void cancelStopsTheSortReads() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        browser = openLengths(oneReadAtATime());
        browser.sortBy(LengthColumns.LENGTH, browser.rows());

        browser.cancelSort();
        executor.runAll();

        assertThat(browser.sortProgress()).isEmpty();
        assertThat(browser.rows())
                .filteredOn(row -> browser.details(row).isEmpty())
                .hasSize(2);
    }

    @Test
    void anotherFolderCancelsTheSort() throws IOException {
        createFiles("a.txt", "b.txt", "sub/x.txt");
        browser = openLengths(oneReadAtATime());
        browser.sortBy(LengthColumns.LENGTH, browser.rows());

        browser.openFolder("sub/");
        executor.runAll();

        assertThat(browser.sortProgress()).isEmpty();
        assertThat(browser.details(browser.rows().get(0))).isEmpty();
    }

    @Test
    void anotherListReadsItsValues() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.LENGTH, List.of(rowNamed("a.txt")));
        executor.runAll();

        SortProgress wider = browser.sortBy(LengthColumns.LENGTH, browser.rows());

        assertThat(wider.read()).isEqualTo(1);
        assertThat(wider.total()).isEqualTo(3);
        assertThat(executor.pending()).isEqualTo(2);
    }

    // lengths 9 and 10: as text, 10 comes first
    @Test
    void numbersSortAsNumbers() throws IOException {
        createFiles("b2345.txt", "a23456.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.LENGTH, browser.rows());
        executor.runAll();

        assertThat(namesOf(sorted(LengthColumns.LENGTH, true))).containsExactly("b2345.txt", "a23456.txt");
    }

    // descending: the direction applies to the values, not to the names
    @Test
    void equalValuesKeepTheNameOrder() throws IOException {
        createFiles("b.txt", "a.txt", "c.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.LENGTH, browser.rows());
        executor.runAll();
        List<BrowserEntry> rows = new ArrayList<>(browser.rows());
        rows.sort(Comparator.comparing(BrowserEntry::name).reversed());

        rows.sort(browser.formatOrder(LengthColumns.LENGTH, false));

        assertThat(namesOf(rows)).containsExactly("a.txt", "b.txt", "c.txt");
    }

    @Test
    void unreadFilesDoNotBlockTheSort() throws IOException {
        createFiles("a.txt", "b.bin");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));

        SortProgress started = browser.sortBy(LengthColumns.LENGTH, browser.rows());
        executor.runAll();

        assertThat(started.total()).isEqualTo(1);
        assertThat(browser.sortProgress().orElseThrow().complete()).isTrue();
    }

    @Test
    void anotherSortCancelsOldReads() throws IOException {
        createFiles("a.txt", "b.txt", "c.txt");
        browser = openLengths(oneReadAtATime());
        browser.sortBy(LengthColumns.LENGTH, browser.rows());

        browser.sortBy(LengthColumns.LENGTH, browser.rows().subList(0, 1));
        executor.runAll();

        assertThat(browser.rows())
                .filteredOn(row -> browser.details(row).isEmpty())
                .hasSize(2);
    }

    @Test
    void anotherColumnStartsOver() throws IOException {
        createFiles("a.txt", "b.txt");
        browser = openLocal("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        browser.sortBy(LengthColumns.LENGTH, browser.rows());

        SortProgress other = browser.sortBy(LengthColumns.FIRST, browser.rows());

        assertThat(other.column()).isEqualTo(LengthColumns.FIRST);
    }

    private StorageBrowser openLocal(String location, StorageBrowsing browsing) {
        return openLocal(location, browsing, StubLocalFiles.rootedAt(folder));
    }

    private StorageBrowser openLocal(String location, StorageBrowsing browsing, LocalFiles localFiles) {
        StorageBrowser opened = open(location, new Properties(), browsing, localFiles, StorageOpener.TILEVERSE, LIMITS);
        opened.openFolder(opened.folder());
        return opened;
    }

    private StorageBrowser openBucket(
            FakeObjectStorage bucket, String location, Properties typed, BrowserLimits limits) {
        StorageBrowser opened =
                open(location, typed, FILES_OR_FOLDERS, StubLocalFiles.rootedAt(folder), opener(bucket), limits);
        opened.openFolder(opened.folder());
        return opened;
    }

    private StorageBrowser openLengths(BrowserLimits limits) {
        StorageBrowsing lengths = StorageBrowsing.of(PickMode.FILE, new LengthColumns());
        StorageBrowser opened =
                open("", new Properties(), lengths, StubLocalFiles.rootedAt(folder), StorageOpener.TILEVERSE, limits);
        opened.openFolder(opened.folder());
        return opened;
    }

    private static BrowserLimits oneReadAtATime() {
        return new BrowserLimits(10, 1, 1_000, 100, 200_000, Duration.ofMinutes(10));
    }

    /** Opens at a typed start folder with a dot segment, without listing it: the key rules alone must refuse it. */
    private StorageBrowser openAtDotFolder() {
        FakeObjectStorage bucket = new FakeObjectStorage("s3://bucket/");
        LocalFiles localFiles = StubLocalFiles.rootedAt(folder);
        return open("s3://bucket/a/../b/", new Properties(), FILES_OR_FOLDERS, localFiles, opener(bucket), LIMITS);
    }

    private StorageBrowser open(
            String location,
            Properties typed,
            StorageBrowsing browsing,
            LocalFiles localFiles,
            StorageOpener opener,
            BrowserLimits limits) {
        FileDetailsCache cache = new FileDetailsCache(100, Duration.ofMinutes(30));
        BrowserServices services =
                new BrowserServices(localFiles, opener, new FileDetailsLoader(executor), cache, limits);
        return StorageBrowser.open(location, typed, browsing, services);
    }

    private StorageOpener opener(FakeObjectStorage bucket) {
        return (root, params) -> {
            openedRoots.add(root);
            openedWith.add(params);
            return bucket;
        };
    }

    private static void assertRefused(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BrowseException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private Path createFiles(String... relativePaths) throws IOException {
        Path last = null;
        for (String relativePath : relativePaths) {
            last = folder.resolve(relativePath);
            Files.createDirectories(last.getParent());
            Files.writeString(last, relativePath);
        }
        return last;
    }

    private static List<String> namesOf(List<BrowserEntry> rows) {
        return rows.stream().map(BrowserEntry::name).toList();
    }

    private List<BrowserEntry> sorted(FileColumn column, boolean ascending) {
        List<BrowserEntry> rows = new ArrayList<>(browser.rows());
        rows.sort(browser.formatOrder(column, ascending));
        return rows;
    }

    private BrowserEntry rowNamed(String name) {
        return browser.rows().stream()
                .filter(row -> row.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
