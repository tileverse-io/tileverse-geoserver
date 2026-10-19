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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import io.tileverse.storage.RangeReader;

/**
 * Format columns for tests over {@code .txt} files: the length of each file and its first character, read through its
 * reader. A file named {@code broken.txt} fails to read, as a truncated file would, and {@code error.txt} throws an
 * error, as a bug in a reader would.
 */
final class LengthColumns implements FileColumns {

    private static final long serialVersionUID = 1L;

    static final FileColumn LENGTH = new FileColumn("length", "LengthColumns.length", ColumnSort.NUMBER);
    static final FileColumn FIRST = new FileColumn("first", "LengthColumns.first", ColumnSort.TEXT);

    @Override
    public List<FileColumn> columns() {
        return List.of(LENGTH, FIRST);
    }

    @Override
    public boolean reads(String fileName) {
        return fileName.endsWith(".txt");
    }

    @Override
    public Map<String, ColumnValue> read(RangeReader file) throws IOException {
        if (file.getSourceIdentifier().endsWith("broken.txt")) {
            throw new IOException("truncated file");
        }
        if (file.getSourceIdentifier().endsWith("error.txt")) {
            throw new AssertionError("a bug in the reader");
        }
        long length = file.size().orElseThrow(() -> new IOException("no size"));
        ByteBuffer head = file.readRange(0, 1);
        head.flip();
        String first = head.hasRemaining() ? String.valueOf((char) head.get()) : "";
        return Map.of(
                LENGTH.key(), ColumnValue.count(length),
                FIRST.key(), ColumnValue.text(first));
    }
}
