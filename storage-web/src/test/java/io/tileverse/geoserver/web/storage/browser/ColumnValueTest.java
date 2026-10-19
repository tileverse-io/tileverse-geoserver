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

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ColumnValueTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    // French groups digits with narrow no-break spaces. A plain text keeps its quotes, braces and digits.
    @ParameterizedTest
    @MethodSource("textsByLanguage")
    void writesTheLanguageOfTheUser(ColumnValue value, Locale language, String shown) {
        assertThat(value.textIn(language)).isEqualTo(shown);
    }

    static Stream<Arguments> textsByLanguage() {
        ColumnValue million = ColumnValue.count(1_000_000);
        ColumnValue dimensions = ColumnValue.formatted(10_980.0 * 10_980, "{0} x {1}", 10_980, 10_980);
        ColumnValue message = ColumnValue.text("can't read {0} in 10000 bytes");
        return Stream.of(
                Arguments.of(million, Locale.ENGLISH, "1,000,000"),
                Arguments.of(million, Locale.GERMAN, "1.000.000"),
                Arguments.of(million, SPANISH, "1.000.000"),
                Arguments.of(million, Locale.FRENCH, "1 000 000"),
                Arguments.of(million, Locale.ITALIAN, "1.000.000"),
                Arguments.of(dimensions, Locale.ENGLISH, "10,980 x 10,980"),
                Arguments.of(dimensions, Locale.GERMAN, "10.980 x 10.980"),
                Arguments.of(message, Locale.GERMAN, "can't read {0} in 10000 bytes"));
    }

    @ParameterizedTest
    @MethodSource("sortNumbers")
    void sortsByItsNumber(ColumnValue value, OptionalDouble number) {
        assertThat(value.number()).isEqualTo(number);
    }

    static Stream<Arguments> sortNumbers() {
        return Stream.of(
                Arguments.of(ColumnValue.count(1_000_000), OptionalDouble.of(1_000_000)),
                Arguments.of(ColumnValue.formatted(3, "{0} x {1}", 3, "Byte"), OptionalDouble.of(3)),
                Arguments.of(ColumnValue.text("EPSG:32633"), OptionalDouble.empty()));
    }
}
