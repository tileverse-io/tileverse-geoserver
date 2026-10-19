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

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * The value of one format cell: its text and, for a column sorted as a number, the number used to sort it. The text is
 * a {@link MessageFormat} pattern over arguments. A cell writes the numbers among the arguments in the language of the
 * user, with the group separator of that language: 1,000,000 in English, 1.000.000 in German.
 *
 * @param pattern the pattern of the text, numbering its arguments from {@code {0}}
 * @param arguments the numbers and texts written by the pattern
 * @param number the number sorting a {@link ColumnSort#NUMBER} column, or empty
 */
public record ColumnValue(String pattern, List<Object> arguments, OptionalDouble number) {

    private static final String ONE_ARGUMENT = "{0}";

    public ColumnValue {
        arguments = List.copyOf(arguments);
    }

    /** A value shown as given and sorted by its text. */
    public static ColumnValue text(String text) {
        return new ColumnValue(ONE_ARGUMENT, List.of(text), OptionalDouble.empty());
    }

    /** A count, such as the rows of a file, sorted by its value. */
    public static ColumnValue count(long count) {
        return new ColumnValue(ONE_ARGUMENT, List.of(count), OptionalDouble.of(count));
    }

    /**
     * A value written by {@code pattern}, such as {@code "{0} x {1}"} for the width and height of an image, and sorted
     * by {@code number}. The pattern must be a constant of the code, because a quote or a brace in it is syntax. A text
     * read from a file goes in the arguments: an argument shows as given.
     */
    public static ColumnValue formatted(double number, String pattern, Object... arguments) {
        return new ColumnValue(pattern, List.of(arguments), OptionalDouble.of(number));
    }

    /** The text in the language of {@code locale}. */
    public String textIn(Locale locale) {
        MessageFormat format = new MessageFormat(pattern, locale);
        return format.format(arguments.toArray());
    }
}
