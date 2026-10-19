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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.tileverse.storage.RangeReader;

/** {@link LengthColumns} with reads held until the test releases them, to see the dialog while it reads. */
final class HeldLengthColumns implements FileColumns {

    private static final long serialVersionUID = 1L;

    private static volatile CountDownLatch gate = new CountDownLatch(0);

    private final LengthColumns lengths = new LengthColumns();

    /** Holds the reads starting from now on. */
    static void hold() {
        gate = new CountDownLatch(1);
    }

    static void release() {
        gate.countDown();
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
        try {
            gate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return lengths.read(file);
    }
}
