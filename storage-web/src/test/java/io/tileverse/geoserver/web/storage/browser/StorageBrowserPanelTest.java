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
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.wicket.Component;
import org.apache.wicket.ConverterLocator;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.util.convert.IConverter;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.TagTester;
import org.apache.wicket.util.tester.WicketTester;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.web.util.DataDirectoryConverterLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StorageBrowserPanelTest {

    private static final StorageBrowsing FILES_OR_FOLDERS =
            StorageBrowsing.of(PickMode.FILE_OR_FOLDER, FileColumns.NONE);
    private static final String BROWSER = "recorder:browser";
    private static final String TABLE = BROWSER + ":content:table";
    private static final String NAME_HEADER = TABLE + ":listContainer:sortableLinks:0:header:link";
    private static final String SIZE_HEADER = TABLE + ":listContainer:sortableLinks:1:header:link";
    private static final String LENGTH_HEADER = TABLE + ":listContainer:sortableLinks:3:header:link";
    private static final String EXPIRED = "This browser was idle too long. Close it and browse again.";

    @TempDir
    Path folder;

    private WicketTester tester;
    private String token;

    @BeforeEach
    void startTester() {
        GeoServerFixture.sandboxedTo(folder, folder);
        tester = BrowserTesters.labelledAsGeoServer();
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
        if (token != null) {
            StorageBrowserSessions.shared().close(token);
        }
        GeoServerFixture.clear();
    }

    @Test
    void rendersTheFolderRows() throws IOException {
        createFiles("scenes/b.tif", "a.tif");

        render("", FILES_OR_FOLDERS);

        assertThat(tester.getLastResponseAsString()).contains("scenes/").contains("a.tif");
    }

    @Test
    void folderRowsNavigate() throws IOException {
        createFiles("scenes/b.tif", "a.tif");
        render("", FILES_OR_FOLDERS);

        click("scenes/");

        assertThat(browser().folder()).isEqualTo("scenes/");
        assertThat(tester.getLastResponseAsString()).contains("b.tif");
    }

    @Test
    void crumbsNavigateUp() throws IOException {
        createFiles("scenes/b.tif", "a.tif");
        render(folder.resolve("scenes").toString(), FILES_OR_FOLDERS);

        click(Path.of(browser().root()).toString());

        assertThat(browser().folder()).isEmpty();
    }

    // GeoServer's links are block-level: without the stylesheet, each crumb takes a line of its own
    @Test
    void crumbsKeepToOneLine() throws IOException {
        createFiles("scenes/b.tif");

        render(folder.resolve("scenes").toString(), FILES_OR_FOLDERS);

        assertThat(tester.getLastResponse().getDocument()).containsPattern("StorageBrowserPanel[^\"]*\\.css");
    }

    // GeoServer's converter for File writes a file outside the data directory as null
    @Test
    void rootPickerShowsThePath(@TempDir Path dataDirectory) {
        convertFilesAsGeoServer(dataDirectory);

        render("", FILES_OR_FOLDERS);

        TagTester picker = tester.getTagByWicketId("root");
        assertThat(picker.getValue()).contains(">" + folder + "</option>");
    }

    // GeoServer's table counts its rows when it is built, before the folder is listed
    @Test
    void pagerCountsTheFolderRows() throws IOException {
        createFiles("scenes/b.tif", "a.tif");
        render("", FILES_OR_FOLDERS);
        assertThat(tester.getLastResponseAsString()).contains("Results 1 to 2 (out of 2 items)");

        click("scenes/");

        assertThat(tester.getLastResponseAsString()).contains("Results 1 to 1 (out of 1 items)");
    }

    @Test
    void aFileChoiceSendsItsUri() throws IOException {
        Path file = createFiles("a.tif");
        ChoiceRecorder recorder = render("", FILES_OR_FOLDERS);

        click("a.tif");

        assertThat(recorder.chosen()).isEqualTo(file.toUri());
    }

    @Test
    void folderChoiceSendsTheFolder() throws IOException {
        createFiles("scenes/b.tif");
        StorageBrowsing folders = StorageBrowsing.of(PickMode.FOLDER, FileColumns.NONE);
        ChoiceRecorder recorder = render(folder.resolve("scenes").toString(), folders);

        tester.clickLink(BROWSER + ":content:chooseFolder", true);

        assertThat(recorder.chosen().toString()).endsWith("/scenes/");
    }

    @Test
    void filesAreTextForFolderStores() throws IOException {
        createFiles("scenes/b.tif", "a.tif");

        render("", StorageBrowsing.of(PickMode.FOLDER, FileColumns.NONE));

        assertThat(linkLabelled("a.tif")).isEmpty();
        assertThat(linkLabelled("scenes/")).isPresent();
    }

    @Test
    void formatCellsShowTheirValues() throws IOException {
        createFiles("abcdefghijklm.txt");
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        BrowserEntry row = browser().rows().get(0);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> browser().details(row).isPresent());
        fireLazyLoadTimer();

        assertThat(tester.getLastResponseAsString()).contains(">17<");
    }

    // A file read before the table shows its row needs no poll: the shared cache keeps its values across a refresh
    @Test
    void readValuesShowAtOnce() throws IOException {
        createFiles("abcdefghijklm.txt");
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        awaitReadsOfFilesNamed("");

        tester.clickLink(BROWSER + ":content:refresh", true);

        assertThat(tester.getLastResponseAsString()).contains(">17<");
    }

    @Test
    void cellsPollFourTimesASecond() throws IOException {
        HeldLengthColumns.hold();
        try {
            createFiles("a.txt");
            render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));

            assertThat(loadingCellsTimer().getUpdateInterval()).isEqualTo(Duration.ofMillis(250));
        } finally {
            HeldLengthColumns.release();
        }
    }

    @ParameterizedTest
    @MethodSource("countsOf1234")
    void cellNumbersTakeTheLanguage(Locale language, String shown) throws IOException {
        Files.writeString(folder.resolve("long.txt"), "x".repeat(1234));
        tester.getSession().setLocale(language);
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        BrowserEntry row = browser().rows().get(0);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> browser().details(row).isPresent());
        fireLazyLoadTimer();

        assertThat(tester.getLastResponseAsString()).contains(">" + shown + "<");
    }

    static Stream<Arguments> countsOf1234() {
        return Stream.of(
                Arguments.of(Locale.ENGLISH, "1,234"),
                Arguments.of(Locale.GERMAN, "1.234"),
                Arguments.of(Locale.FRENCH, "1\u202F234"));
    }

    @ParameterizedTest
    @MethodSource("sizesOf1536Bytes")
    void sizeTakesTheLanguage(Locale language, String shown) throws IOException {
        Files.writeString(folder.resolve("half.txt"), "x".repeat(1536));
        tester.getSession().setLocale(language);

        render("", FILES_OR_FOLDERS);

        assertThat(tester.getLastResponseAsString()).contains(">" + shown + "<");
    }

    static Stream<Arguments> sizesOf1536Bytes() {
        return Stream.of(
                Arguments.of(Locale.ENGLISH, "1.5 KB"),
                Arguments.of(Locale.GERMAN, "1,5 KB"),
                Arguments.of(Locale.FRENCH, "1,5 KB"));
    }

    @Test
    void cellsStopWithoutBrowser() throws IOException {
        HeldLengthColumns.hold();
        try {
            createFiles("a.txt");
            render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));
            StorageBrowserSessions.shared().close(token);

            fireLazyLoadTimer();

            assertThat(tester.getLastRenderedPage().getBehaviors(FormatCellsTimer.class))
                    .isEmpty();
        } finally {
            HeldLengthColumns.release();
        }
    }

    @Test
    void sortShowsItsProgressFirst() throws IOException {
        createFiles("aaa.txt", "b.txt");
        HeldLengthColumns.hold();
        try {
            render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));

            tester.clickLink(BROWSER + ":content:table:listContainer:sortableLinks:3:header:link", true);

            assertThat(tester.getLastResponseAsString()).contains("Reading 0 of 2 files to sort by length.");
        } finally {
            HeldLengthColumns.release();
        }
        await().atMost(Duration.ofSeconds(10))
                .until(() ->
                        browser().sortProgress().map(SortProgress::complete).orElse(false));

        tester.executeBehavior(sortNoticeTimer());

        String response = tester.getLastResponseAsString();
        assertThat(response.indexOf("b.txt")).isLessThan(response.indexOf("aaa.txt"));
    }

    @Test
    void cannotListShowsItsMessage() {
        Properties http = new Properties();
        http.setProperty("storage.provider", "http");
        token = StorageBrowserSessions.shared().open("https://example.invalid/data/x.tif", http, FILES_OR_FOLDERS);

        tester.startComponentInPage(new ChoiceRecorder("recorder", token, FILES_OR_FOLDERS));

        assertThat(tester.getLastResponseAsString()).contains("This backend cannot list.");
        tester.assertVisible(BROWSER + ":retry");
    }

    @Test
    void expiredBrowserSaysSo() throws IOException {
        createFiles("scenes/b.tif");
        render("", FILES_OR_FOLDERS);
        StorageBrowserSessions.shared().close(token);

        click("scenes/");

        assertThat(tester.getLastResponseAsString())
                .contains("This browser was idle too long. Close it and browse again.");
    }

    // A search changes the rows to sort: the sort reads their missing values and shows its progress
    @Test
    void searchKeepsTheFormatSort() throws IOException {
        createSizedFiles("a", 25, 100);
        createSizedFiles("z", 5, 1);
        render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));
        awaitReadsOfFilesNamed("a");
        search("a0");
        tester.clickLink(LENGTH_HEADER, true);
        awaitSort();
        HeldLengthColumns.hold();
        try {
            search("txt");

            assertThat(tester.getLastResponseAsString()).contains("Reading 25 of 30 files to sort by length.");
        } finally {
            HeldLengthColumns.release();
        }
        awaitSort();

        tester.executeBehavior(sortNoticeTimer());

        assertThat(tester.getLastResponseAsString()).containsSubsequence(">z00.txt<", ">z04.txt<", ">a00.txt<");
    }

    // Over the limit of a sort, a search leaves the rows in name order and asks to narrow the list
    @Test
    void bigSearchAsksToNarrowTheSort() throws IOException {
        createSizedFiles("a", 5, 100);
        createSizedFiles("m", 1100, 1);
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        awaitReadsOfFilesNamed("a");
        search("a0");
        tester.clickLink(LENGTH_HEADER, true);
        awaitSort();

        search("txt");

        assertThat(tester.getLastResponseAsString())
                .contains("Sorting by length reads the files of the list first, up to 1,000 of them.");
    }

    // Only another listing builds new cells: a loaded cell stays when the rows sort
    @Test
    void sortKeepsTheLoadedCells() throws IOException {
        createFiles("a.txt", "b.txt");
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        awaitReadsOfFilesNamed("");
        pollLoadingCells();
        List<FormatCell> before = formatCells();

        tester.clickLink(NAME_HEADER, true);
        tester.clickLink(NAME_HEADER, true);

        assertThat(formatCells()).hasSameSizeAs(before).containsExactlyInAnyOrderElementsOf(before);
    }

    // The page goes to GeoServer's page store: its rows, models and cells must serialize
    @Test
    void pageStaysSerializable() throws IOException {
        createFiles("scenes/b.txt", "a.txt");
        render("", StorageBrowsing.of(PickMode.FILE_OR_FOLDER, new LengthColumns()));
        tester.clickLink(LENGTH_HEADER, true);
        tester.clickLink(BROWSER + ":content:refresh", true);

        byte[] stored =
                tester.getApplication().getFrameworkSettings().getSerializer().serialize(tester.getLastRenderedPage());

        assertThat(stored).isNotNull();
    }

    // GeoServer's table keeps the cell of an equal row: a refreshed row must show the values read again
    @Test
    void refreshReadsFailedFilesAgain() throws IOException {
        createFiles("a.txt");
        FlakyLengthColumns.fail();
        try {
            render("", StorageBrowsing.of(PickMode.FILE, new FlakyLengthColumns()));
            awaitFirstRow(FileDetails.Unavailable.class);
            pollLoadingCells();
            assertThat(tester.getLastResponseAsString()).contains("unavailable");
        } finally {
            FlakyLengthColumns.recover();
        }

        tester.clickLink(BROWSER + ":content:refresh", true);
        awaitFirstRow(FileDetails.Values.class);
        pollLoadingCells();

        assertThat(tester.getLastResponseAsString()).contains(">5<");
    }

    @Test
    void expiredSortSaysSo() throws IOException {
        createFiles("a.tif", "b.tif");
        render("", FILES_OR_FOLDERS);
        StorageBrowserSessions.shared().close(token);

        tester.clickLink(NAME_HEADER, true);

        assertThat(tester.getLastResponseAsString()).contains(EXPIRED);
    }

    @Test
    void expiredSearchSaysSo() throws IOException {
        createFiles("a.tif", "b.tif");
        render("", FILES_OR_FOLDERS);
        StorageBrowserSessions.shared().close(token);

        search("a");

        assertThat(tester.getLastResponseAsString()).contains(EXPIRED);
    }

    // Wicket answers a click on a disabled link with its access denied page: the pager keeps its links and counts
    @Test
    void expiredPagingSaysSo() throws IOException {
        createNumberedFiles("f", 30);
        render("", FILES_OR_FOLDERS);
        StorageBrowserSessions.shared().close(token);

        tester.clickLink(TABLE + ":navigatorBottom:navigator:next", true);

        assertThat(tester.getLastResponseAsString()).contains(EXPIRED).contains("Results 26 to 30 (out of 30 items)");
    }

    // Only the table's own links report an expired browser: the polls and the page requests stay quiet
    @Test
    void pollsStayQuietWithoutBrowser() throws IOException {
        createFiles("aaa.txt", "b.txt");
        HeldLengthColumns.hold();
        try {
            ChoiceRecorder recorder = render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));
            tester.clickLink(LENGTH_HEADER, true);
            AbstractDefaultAjaxBehavior pagePing = pagePingOf(recorder);
            StorageBrowserSessions.shared().close(token);

            fireLazyLoadTimer();
            assertThat(tester.getLastResponseAsString()).doesNotContain(EXPIRED);
            tester.executeBehavior(sortNoticeTimer());
            assertThat(tester.getLastResponseAsString()).doesNotContain(EXPIRED);
            tester.executeBehavior(pagePing);
            assertThat(tester.getLastResponseAsString()).doesNotContain(EXPIRED);
        } finally {
            HeldLengthColumns.release();
        }
    }

    @Test
    void noRetryForAnExpiredBrowser() throws IOException {
        createFiles("scenes/b.tif");
        render("", FILES_OR_FOLDERS);
        StorageBrowserSessions.shared().close(token);

        click("scenes/");

        tester.assertInvisible(BROWSER + ":retry");
    }

    @Test
    void folderOpenShowsTheFirstPage() throws IOException {
        createNumberedFiles("big/b", 30);
        createNumberedFiles("r", 30);
        render(folder.resolve("big").toString(), FILES_OR_FOLDERS);
        tester.clickLink(TABLE + ":navigatorBottom:navigator:next", true);
        assertThat(tester.getLastResponseAsString()).contains("b29.tif");

        click(Path.of(browser().root()).toString());

        assertThat(tester.getLastResponseAsString()).contains("r00.tif").doesNotContain("r29.tif");
    }

    @Test
    void folderOpenResetsTheOrder() throws IOException {
        createFile("scenes/x1.tif", 10);
        createFile("scenes/x2.tif", 2);
        render("", FILES_OR_FOLDERS);
        tester.clickLink(SIZE_HEADER, true);

        click("scenes/");

        assertThat(tester.getLastResponseAsString()).containsSubsequence(">x1.tif<", ">x2.tif<");
    }

    @Test
    void folderOpenCancelsTheFormatSort() throws IOException {
        createFile("scenes/x1.txt", 10);
        createFile("r1.txt", 1);
        render("", StorageBrowsing.of(PickMode.FILE, new LengthColumns()));
        tester.clickLink(LENGTH_HEADER, true);

        click("scenes/");

        assertThat(browser().sortProgress()).isEmpty();
    }

    @Test
    void noticeCancelResetsTheOrder() throws IOException {
        createFiles("aaa.txt", "b.txt");
        HeldLengthColumns.hold();
        try {
            render("", StorageBrowsing.of(PickMode.FILE, new HeldLengthColumns()));
            tester.clickLink(LENGTH_HEADER, true);

            tester.clickLink(BROWSER + ":content:sortNotice:cancel", true);

            assertThat(tester.getLastResponseAsString()).doesNotContain("Reading");
            assertThat(browser().sortProgress()).isEmpty();
        } finally {
            HeldLengthColumns.release();
        }
    }

    private ChoiceRecorder render(String location, StorageBrowsing browsing) {
        token = StorageBrowserSessions.shared().open(location, new Properties(), browsing);
        ChoiceRecorder recorder = new ChoiceRecorder("recorder", token, browsing);
        tester.startComponentInPage(recorder);
        return recorder;
    }

    /** Registers GeoServer's converter for File on the tester's application, over the given data directory. */
    private void convertFilesAsGeoServer(Path dataDirectory) {
        GeoServerResourceLoader loader = new GeoServerResourceLoader(dataDirectory.toFile());
        IConverter<File> converter = new DataDirectoryConverterLocator(loader).getConverter(File.class);
        ConverterLocator converters = (ConverterLocator) tester.getApplication().getConverterLocator();
        converters.set(File.class, converter);
    }

    private StorageBrowser browser() {
        return StorageBrowserSessions.shared().get(token).orElseThrow();
    }

    private void click(String label) {
        EventLink link = linkLabelled(label).orElseThrow(() -> new AssertionError("no link " + label));
        tester.executeAjaxEvent(link.get("link"), "click");
    }

    private Optional<EventLink> linkLabelled(String label) {
        List<EventLink> found = new ArrayList<>();
        tester.getLastRenderedPage().visitChildren(EventLink.class, (EventLink link, IVisit<Void> visit) -> {
            if (label.equals(link.getDefaultModelObject())) {
                found.add(link);
            }
        });
        return found.stream().findFirst();
    }

    private void fireLazyLoadTimer() {
        tester.executeBehavior(loadingCellsTimer());
    }

    private FormatCellsTimer loadingCellsTimer() {
        return tester.getLastRenderedPage().getBehaviors(FormatCellsTimer.class).get(0);
    }

    private AbstractAjaxTimerBehavior sortNoticeTimer() {
        Component notice = tester.getComponentFromLastRenderedPage(BROWSER + ":content:sortNotice");
        return notice.getBehaviors(AbstractAjaxTimerBehavior.class).get(0);
    }

    private List<FormatCell> formatCells() {
        List<FormatCell> cells = new ArrayList<>();
        tester.getLastRenderedPage().visitChildren(FormatCell.class, (FormatCell cell, IVisit<Void> visit) -> {
            cells.add(cell);
        });
        return cells;
    }

    private void search(String text) {
        String form = TABLE + ":filterForm";
        FormTester search = tester.newFormTester(form);
        search.setValue("filter", text);
        tester.executeAjaxEvent(form + ":submit", "click");
    }

    private void awaitSort() {
        await().atMost(Duration.ofSeconds(10))
                .until(() ->
                        browser().sortProgress().map(SortProgress::complete).orElse(false));
    }

    private void awaitReadsOfFilesNamed(String prefix) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> browser().rows().stream()
                        .filter(row -> row.name().startsWith(prefix))
                        .allMatch(row -> browser().details(row).isPresent()));
    }

    private void awaitFirstRow(Class<? extends FileDetails> kind) {
        BrowserEntry row = browser().rows().get(0);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> browser().details(row).filter(kind::isInstance).isPresent());
    }

    /** Fires the poll of the loading cells. A read ended before the page showed leaves nothing to poll. */
    private void pollLoadingCells() {
        List<FormatCellsTimer> timers = tester.getLastRenderedPage().getBehaviors(FormatCellsTimer.class);
        if (!timers.isEmpty()) {
            tester.executeBehavior(timers.get(0));
        }
    }

    /** A request of the store page holding the dialog: it repaints nothing of the dialog. */
    private AbstractDefaultAjaxBehavior pagePingOf(ChoiceRecorder recorder) {
        AbstractDefaultAjaxBehavior ping = new AbstractDefaultAjaxBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void respond(AjaxRequestTarget target) {
                // Nothing to do: the request alone makes the page broadcast its target to the dialog.
            }
        };
        recorder.add(ping);
        return ping;
    }

    private void createFile(String relativePath, int length) throws IOException {
        Path file = folder.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x".repeat(length));
    }

    private void createSizedFiles(String prefix, int count, int length) throws IOException {
        for (int i = 0; i < count; i++) {
            createFile(String.format("%s%02d.txt", prefix, i), length);
        }
    }

    private void createNumberedFiles(String prefix, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            createFiles(String.format("%s%02d.tif", prefix, i));
        }
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
}
