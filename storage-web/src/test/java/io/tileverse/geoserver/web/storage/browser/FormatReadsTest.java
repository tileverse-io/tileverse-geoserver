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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.tileverse.storage.StorageEntry;
import io.tileverse.storage.StorageFactory;

class FormatReadsTest {

    @TempDir
    Path folder;

    private final ManualExecutor executor = new ManualExecutor();
    private final FileDetailsCache cache = new FileDetailsCache(100, Duration.ofMinutes(30));
    private OpenStorage storage;

    @BeforeEach
    void openStorage() {
        storage = new OpenStorage(StorageFactory.open(folder.toUri()));
    }

    @AfterEach
    void closeStorage() {
        storage.close();
    }

    @Test
    void readsFourAtATime() throws IOException {
        FormatReads reads = reads(new Properties(), 4);

        reads.show(files("a.txt", "b.txt", "c.txt", "d.txt", "e.txt", "f.txt"));

        assertThat(executor.pending()).isEqualTo(4);
        executor.runNext();
        assertThat(executor.pending()).isEqualTo(4);
        executor.runAll();
        assertThat(reads.details("f.txt")).isPresent();
    }

    @Test
    void skipsFilesNotRead() throws IOException {
        FormatReads reads = reads(new Properties(), 4);

        reads.show(files("x.bin"));

        assertThat(executor.pending()).isZero();
        assertThat(reads.details("x.bin")).isEmpty();
    }

    @Test
    void readsTheValuesOfAFile() throws IOException {
        FormatReads reads = reads(new Properties(), 4);

        reads.show(files("abc.txt"));
        executor.runAll();

        FileDetails details = reads.details("abc.txt").orElseThrow();
        assertThat(details).isInstanceOfSatisfying(FileDetails.Values.class, values -> {
            assertThat(values.byColumn().get("length")).isEqualTo(ColumnValue.count(7));
            assertThat(values.byColumn().get("first")).isEqualTo(ColumnValue.text("a"));
        });
    }

    @Test
    void theCacheSparesARead() throws IOException {
        List<StorageEntry.File> shown = files("a.txt");
        reads(new Properties(), 4).show(shown);
        executor.runAll();

        FormatReads second = reads(new Properties(), 4);
        second.show(shown);

        assertThat(executor.pending()).isZero();
        assertThat(second.details("a.txt")).isPresent();
    }

    @Test
    void otherParametersReadAgain() throws IOException {
        List<StorageEntry.File> shown = files("a.txt");
        reads(new Properties(), 4).show(shown);
        executor.runAll();
        Properties other = new Properties();
        other.setProperty("storage.s3.aws-access-key-id", "another");

        reads(other, 4).show(shown);

        assertThat(executor.pending()).isEqualTo(1);
    }

    @Test
    void hiddenRowsLoseTheirReads() throws IOException {
        List<StorageEntry.File> shown = files("a.txt", "b.txt", "c.txt", "d.txt", "e.txt", "f.txt");
        FormatReads reads = reads(new Properties(), 4);
        reads.show(shown);

        reads.show(shown.subList(0, 1));
        executor.runAll();

        assertThat(reads.details("d.txt")).as("a running read finishes").isPresent();
        assertThat(reads.details("e.txt")).as("a queued read is cancelled").isEmpty();
    }

    @Test
    void aFailedReadIsUnavailable() throws IOException {
        FormatReads reads = reads(new Properties(), 4);

        reads.show(files("broken.txt"));
        executor.runAll();

        assertThat(reads.details("broken.txt")).contains(new FileDetails.Unavailable("truncated file"));
    }

    @Test
    void failuresStayOutOfTheCache() throws IOException {
        List<StorageEntry.File> shown = files("broken.txt");
        reads(new Properties(), 4).show(shown);
        executor.runAll();

        reads(new Properties(), 4).show(shown);

        assertThat(executor.pending()).isEqualTo(1);
    }

    @Test
    void resetDropsTheValues() throws IOException {
        List<StorageEntry.File> shown = files("a.txt");
        FormatReads reads = reads(new Properties(), 4);
        reads.show(shown);
        executor.runAll();

        reads.reset(storage);

        assertThat(reads.details("a.txt")).isEmpty();
        reads.show(shown);
        assertThat(executor.pending()).as("the shared cache answers").isZero();
        assertThat(reads.details("a.txt")).isPresent();
    }

    // a read running at reset belongs to the old listing: its values reach the shared cache only
    @Test
    void oldReadsSkipTheNewListing() throws IOException {
        List<StorageEntry.File> shown = files("a.txt");
        FormatReads reads = reads(new Properties(), 4);
        reads.show(shown);

        reads.reset(storage);
        executor.runAll();

        assertThat(reads.details("a.txt")).isEmpty();
        reads.show(shown);
        assertThat(executor.pending())
                .as("the old read filled the shared cache")
                .isZero();
        assertThat(reads.details("a.txt")).isPresent();
    }

    @Test
    void shownRowsReadFirst() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt", "d.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.queueForSort(all.subList(1, 4));

        reads.show(all.subList(0, 1));
        executor.runNext();
        executor.runNext();

        assertThat(reads.details("a.txt")).isPresent();
        assertThat(reads.details("c.txt")).isEmpty();
    }

    @Test
    void sortReadsSurvivePaging() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.queueForSort(all);

        reads.show(List.of());
        executor.runAll();

        assertThat(reads.details("c.txt")).isPresent();
    }

    @Test
    void cancelledSortReadsStop() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.queueForSort(all);

        reads.cancelSortReads();
        executor.runAll();

        assertThat(reads.details("a.txt")).as("the running read").isPresent();
        assertThat(reads.details("c.txt")).isEmpty();
    }

    @Test
    void aShownSortReadSurvivesPaging() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.queueForSort(all);

        reads.show(all.subList(1, 3));
        reads.show(List.of());
        executor.runAll();

        assertThat(reads.details("c.txt")).isPresent();
    }

    @Test
    void pageReadsJoinTheSort() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.show(all);
        reads.queueForSort(all);

        reads.show(List.of());
        executor.runAll();

        assertThat(reads.details("c.txt")).isPresent();
    }

    @Test
    void cancelKeepsTheShownReads() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "c.txt");
        FormatReads reads = reads(new Properties(), 1);
        reads.queueForSort(all);
        reads.show(all.subList(1, 2));

        reads.cancelSortReads();
        executor.runAll();

        assertThat(reads.details("b.txt")).as("a file shown").isPresent();
        assertThat(reads.details("c.txt")).isEmpty();
    }

    @Test
    void anErrorFreesTheReadSlot() throws IOException {
        FormatReads reads = reads(new Properties(), 1);
        reads.show(files("error.txt", "a.txt"));

        assertThatThrownBy(executor::runNext).isInstanceOf(AssertionError.class);

        assertThat(reads.details("error.txt")).containsInstanceOf(FileDetails.Unavailable.class);
        assertThat(executor.pending()).as("the next read starts").isEqualTo(1);
    }

    @Test
    void countsTheMissingValues() throws IOException {
        List<StorageEntry.File> all = files("a.txt", "b.txt", "x.bin");
        FormatReads reads = reads(new Properties(), 4);
        reads.show(all.subList(0, 1));
        executor.runAll();

        assertThat(reads.missingCount(all)).isEqualTo(1);
    }

    @Test
    void closingStopsTheQueue() throws IOException {
        FormatReads reads = reads(new Properties(), 1);
        reads.show(files("a.txt", "b.txt"));

        reads.close();
        executor.runAll();

        assertThat(executor.pending()).isZero();
        assertThat(reads.details("b.txt")).isEmpty();
    }

    private FormatReads reads(Properties storageParams, int maxRunning) {
        FileDetailsLoader loader = new FileDetailsLoader(executor);
        FormatReads reads = new FormatReads(new LengthColumns(), loader, cache, storageParams, maxRunning);
        reads.reset(storage);
        return reads;
    }

    /** Writes the files, each holding its own name, and returns their entries. */
    private List<StorageEntry.File> files(String... names) throws IOException {
        List<StorageEntry.File> files = new ArrayList<>();
        for (String name : names) {
            Files.writeString(folder.resolve(name), name);
            files.add(storage.storage().stat(name).orElseThrow());
        }
        return files;
    }
}
