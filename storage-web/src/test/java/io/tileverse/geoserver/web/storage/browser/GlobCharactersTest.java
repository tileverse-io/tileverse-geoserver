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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GlobCharactersTest {

    @ParameterizedTest
    @MethodSource("texts")
    void findsTheFirstGlobCharacter(String text, int expected) {
        assertThat(GlobCharacters.firstIn(text)).isEqualTo(expected);
    }

    static Stream<Arguments> texts() {
        return Stream.of(
                Arguments.of("scene.tif", -1),
                Arguments.of("", -1),
                Arguments.of("a*b", 1),
                Arguments.of("a?b", 1),
                Arguments.of("[ab]", 0),
                Arguments.of("x{a,b}", 1),
                Arguments.of("a/b/**", 4),
                Arguments.of("a{b*", 1),
                Arguments.of("a]b}c", -1));
    }
}
