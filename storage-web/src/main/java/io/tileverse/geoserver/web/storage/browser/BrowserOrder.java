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

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/** The orders of the browser's rows: folders come first whatever the direction, and ties keep the name order. */
final class BrowserOrder {

    private static final Comparator<BrowserEntry> FOLDERS_FIRST = Comparator.comparing(row -> !row.folder());

    private static final Comparator<BrowserEntry> NAMES = Comparator.comparing(
                    BrowserEntry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(BrowserEntry::name);

    private BrowserOrder() {}

    static Comparator<BrowserEntry> byName(boolean ascending) {
        return FOLDERS_FIRST.thenComparing(directed(NAMES, ascending));
    }

    static Comparator<BrowserEntry> by(Comparator<BrowserEntry> property, boolean ascending) {
        return FOLDERS_FIRST.thenComparing(directed(property, ascending)).thenComparing(NAMES);
    }

    /**
     * Folders, then the files with a value to sort by, then those without one, such as the files not read by the
     * columns, the failed reads, and the values without a number in a number column. The direction applies to the
     * values only.
     */
    static Comparator<BrowserEntry> byValues(
            FileColumn column, boolean ascending, Function<BrowserEntry, Optional<ColumnValue>> values) {
        Function<BrowserEntry, Optional<ColumnValue>> sortable = row -> sortableValue(column, values.apply(row));
        Comparator<BrowserEntry> withValuesFirst =
                Comparator.comparing(row -> sortable.apply(row).isEmpty());
        Comparator<BrowserEntry> byValue = directed(valueOrder(column, sortable), ascending);
        return FOLDERS_FIRST
                .thenComparing(withValuesFirst)
                .thenComparing(byValue)
                .thenComparing(NAMES);
    }

    /** A number column sorts by the number of a value: a value without a number has nothing to sort by. */
    private static Optional<ColumnValue> sortableValue(FileColumn column, Optional<ColumnValue> value) {
        if (column.sort() == ColumnSort.NUMBER) {
            return value.filter(found -> found.number().isPresent());
        }
        return value;
    }

    /** Rows without a sortable value compare as equal here: they come last anyway. */
    private static Comparator<BrowserEntry> valueOrder(
            FileColumn column, Function<BrowserEntry, Optional<ColumnValue>> sortable) {
        if (column.sort() == ColumnSort.NUMBER) {
            return Comparator.comparingDouble(row -> numberOf(sortable.apply(row)));
        }
        return Comparator.comparing(row -> textOf(sortable.apply(row)), String.CASE_INSENSITIVE_ORDER);
    }

    private static double numberOf(Optional<ColumnValue> sortable) {
        if (sortable.isEmpty()) {
            return 0;
        }
        OptionalDouble number = sortable.get().number();
        return number.getAsDouble();
    }

    /** Written in {@link Locale#ROOT}: the browser knows no language, and a plain text reads the same in each one. */
    private static String textOf(Optional<ColumnValue> value) {
        return value.map(found -> found.textIn(Locale.ROOT)).orElse("");
    }

    private static Comparator<BrowserEntry> directed(Comparator<BrowserEntry> order, boolean ascending) {
        if (ascending) {
            return order;
        }
        return order.reversed();
    }
}
