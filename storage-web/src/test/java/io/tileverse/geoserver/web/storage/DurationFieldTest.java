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
package io.tileverse.geoserver.web.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the two halves of the ISO-8601 bridge behind the duration widget: decomposition shows a stored string as an
 * integer amount in the largest exactly-representing unit, and composition writes the user's amount + unit back as the
 * strict ISO-8601 string tileverse-storage parses. A blank or unparseable stored value decomposes to an empty amount
 * over seconds; a sub-millisecond remainder truncates to milliseconds, the accepted trade-off of the design.
 */
class DurationFieldTest {

    static Stream<Arguments> decompositions() {
        return Stream.of(
                Arguments.of("PT2M", 2L, ChronoUnit.MINUTES),
                Arguments.of("PT90S", 90L, ChronoUnit.SECONDS),
                Arguments.of("PT1M30S", 90L, ChronoUnit.SECONDS),
                Arguments.of("PT0.5S", 500L, ChronoUnit.MILLIS),
                Arguments.of("PT1H", 1L, ChronoUnit.HOURS),
                Arguments.of("PT2H30M", 150L, ChronoUnit.MINUTES),
                Arguments.of("PT0S", 0L, ChronoUnit.SECONDS),
                Arguments.of("PT0.0005S", 0L, ChronoUnit.MILLIS),
                Arguments.of(null, null, ChronoUnit.SECONDS),
                Arguments.of("", null, ChronoUnit.SECONDS),
                Arguments.of("  ", null, ChronoUnit.SECONDS),
                Arguments.of("2 minutes", null, ChronoUnit.SECONDS));
    }

    static Stream<Arguments> compositions() {
        return Stream.of(
                Arguments.of(2L, ChronoUnit.MINUTES, "PT2M"),
                Arguments.of(90L, ChronoUnit.SECONDS, "PT1M30S"),
                Arguments.of(500L, ChronoUnit.MILLIS, "PT0.5S"),
                Arguments.of(0L, ChronoUnit.SECONDS, "PT0S"),
                Arguments.of(3L, ChronoUnit.HOURS, "PT3H"));
    }

    @ParameterizedTest
    @MethodSource("decompositions")
    void decomposesToTheLargestExactlyRepresentingUnit(String iso, Long amount, ChronoUnit unit) {
        DurationField.Decomposed decomposed = DurationField.decompose(iso);

        assertThat(decomposed.amount()).isEqualTo(amount);
        assertThat(decomposed.unit()).isEqualTo(unit);
    }

    @ParameterizedTest
    @MethodSource("compositions")
    void composesTheStrictIsoString(long amount, ChronoUnit unit, String iso) {
        assertThat(DurationField.compose(amount, unit)).isEqualTo(iso);
    }
}
