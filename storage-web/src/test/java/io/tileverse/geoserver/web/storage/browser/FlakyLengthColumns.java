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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.tileverse.storage.RangeReader;

/** {@link LengthColumns} with reads failing until the test lets them work, to read a failed file again. */
final class FlakyLengthColumns implements FileColumns {

    private static final long serialVersionUID = 1L;

    private static volatile boolean failing;

    private final LengthColumns lengths = new LengthColumns();

    /** Fails the reads starting from now on. */
    static void fail() {
        failing = true;
    }

    /** Lets the reads work again. */
    static void recover() {
        failing = false;
    }

    @Override
    public List<FileColumn> columns() {
        return lengths.columns();
    }

    @Override
    public boolean reads(String fileName) {
        return lengths.reads(fileName);
    }

    @Override
    public Map<String, ColumnValue> read(RangeReader file) throws IOException {
        if (failing) {
            throw new IOException("flaky read");
        }
        return lengths.read(file);
    }
}
