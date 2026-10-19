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

/**
 * The characters starting a glob in a listing pattern: {@code *}, {@code ?}, {@code [} and an opening brace. Tileverse
 * reads a pattern holding one of them as a glob. On object storage, such a pattern lists the whole folder where the
 * glob starts, or everything below it with {@code **}, and filters the keys afterwards. Code building a pattern from
 * typed text uses this class to find where the glob starts.
 */
final class GlobCharacters {

    private static final String CHARACTERS = "*?[{";

    private GlobCharacters() {}

    /** The index of the first glob character in {@code text}, or -1 if it has none. */
    static int firstIn(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (CHARACTERS.indexOf(text.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }
}
