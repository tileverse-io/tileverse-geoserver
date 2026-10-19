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
 * How far a sort by a format column got.
 *
 * @param column the column sorted by
 * @param read the files of the list with a value, read or failed
 * @param total the files of the list read by the format columns
 * @param limit the files read at most for a sort
 * @param tooMany whether more files miss a value than the limit allows: the order then stays as it was
 */
public record SortProgress(FileColumn column, int read, int total, int limit, boolean tooMany) {

    /** Whether the rows can sort: each file of the list has its value. */
    public boolean complete() {
        return !tooMany && read >= total;
    }
}
