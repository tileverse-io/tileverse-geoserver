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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BrowserOrderTest {

    @ParameterizedTest
    @MethodSource("nameOrders")
    void byNameKeepsFoldersFirst(boolean ascending, List<String> expected) {
        List<BrowserEntry> rows =
                List.of(file("a.txt", 0), folder("Docs"), file("A.txt", 0), file("b.txt", 0), folder("archive"));

        List<String> names = sortedNames(rows, BrowserOrder.byName(ascending));

        assertThat(names).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> nameOrders() {
        return Stream.of(
                Arguments.of(true, List.of("archive", "Docs", "A.txt", "a.txt", "b.txt")),
                Arguments.of(false, List.of("Docs", "archive", "b.txt", "a.txt", "A.txt")));
    }

    @ParameterizedTest
    @MethodSource("sizeOrders")
    void propertyOrderTiesByName(boolean ascending, List<String> expected) {
        List<BrowserEntry> rows =
                List.of(file("c.txt", 30), file("b.txt", 10), folder("sub"), file("d.txt", 20), file("a.txt", 10));
        Comparator<BrowserEntry> bySize = Comparator.comparingLong(BrowserEntry::size);

        List<String> names = sortedNames(rows, BrowserOrder.by(bySize, ascending));

        assertThat(names).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> sizeOrders() {
        return Stream.of(
                Arguments.of(true, List.of("sub", "a.txt", "b.txt", "d.txt", "c.txt")),
                Arguments.of(false, List.of("sub", "c.txt", "d.txt", "a.txt", "b.txt")));
    }

    // a text value in a number column has no number to sort by
    @ParameterizedTest
    @MethodSource("numberOrders")
    void valuesWithoutNumberComeLast(boolean ascending, List<String> expected) {
        Map<String, ColumnValue> values = Map.of(
                "one.tif", ColumnValue.count(1),
                "two.tif", ColumnValue.count(2),
                "unknown.tif", ColumnValue.text("n/a"));
        List<BrowserEntry> rows = List.of(
                file("unknown.tif", 0), file("two.tif", 0), file("failed.tif", 0), folder("sub"), file("one.tif", 0));
        Comparator<BrowserEntry> order = BrowserOrder.byValues(LengthColumns.LENGTH, ascending, valuesNamed(values));

        List<String> names = sortedNames(rows, order);

        assertThat(names).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> numberOrders() {
        return Stream.of(
                Arguments.of(true, List.of("sub", "one.tif", "two.tif", "failed.tif", "unknown.tif")),
                Arguments.of(false, List.of("sub", "two.tif", "one.tif", "failed.tif", "unknown.tif")));
    }

    @ParameterizedTest
    @MethodSource("textOrders")
    void textValuesSortByText(boolean ascending, List<String> expected) {
        Map<String, ColumnValue> values = Map.of(
                "a.tif", ColumnValue.text("banana"),
                "b.tif", ColumnValue.text("Cherry"),
                "c.tif", ColumnValue.text("apple"));
        List<BrowserEntry> rows = List.of(file("d.tif", 0), file("b.tif", 0), file("a.tif", 0), file("c.tif", 0));
        Comparator<BrowserEntry> order = BrowserOrder.byValues(LengthColumns.FIRST, ascending, valuesNamed(values));

        List<String> names = sortedNames(rows, order);

        assertThat(names).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> textOrders() {
        return Stream.of(
                Arguments.of(true, List.of("c.tif", "a.tif", "b.tif", "d.tif")),
                Arguments.of(false, List.of("b.tif", "a.tif", "c.tif", "d.tif")));
    }

    private static Function<BrowserEntry, Optional<ColumnValue>> valuesNamed(Map<String, ColumnValue> byName) {
        return row -> Optional.ofNullable(byName.get(row.name()));
    }

    private static List<String> sortedNames(List<BrowserEntry> rows, Comparator<BrowserEntry> order) {
        return rows.stream().sorted(order).map(BrowserEntry::name).toList();
    }

    private static BrowserEntry file(String name, long size) {
        return new BrowserEntry(name, name, false, size, null);
    }

    private static BrowserEntry folder(String name) {
        return new BrowserEntry(name, name + "/", true, 0, null);
    }
}
