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

import java.util.Set;

/** A sort by a format column: the column, the files to read for it, and whether they exceed the limit. */
final class FormatSort {

    private final FileColumn column;
    private final Set<String> fileKeys;
    private final boolean tooMany;

    FormatSort(FileColumn column, Set<String> fileKeys, boolean tooMany) {
        this.column = column;
        this.fileKeys = Set.copyOf(fileKeys);
        this.tooMany = tooMany;
    }

    /** Whether this sort is the one by {@code otherColumn} over the files of {@code otherKeys}. */
    boolean sorts(FileColumn otherColumn, Set<String> otherKeys) {
        return column.equals(otherColumn) && fileKeys.equals(otherKeys);
    }

    /** Whether the files to read exceeded the limit when this sort started. */
    boolean tooMany() {
        return tooMany;
    }

    SortProgress progress(FormatReads reads, int limit) {
        int read = 0;
        for (String key : fileKeys) {
            if (reads.details(key).isPresent()) {
                read++;
            }
        }
        return new SortProgress(column, read, fileKeys.size(), limit, tooMany);
    }
}
