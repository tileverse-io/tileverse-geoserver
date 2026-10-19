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

import java.nio.ByteBuffer;
import java.util.OptionalLong;

import io.tileverse.storage.RangeReader;

/** A range reader over bytes held in memory, as handed out by {@link FakeObjectStorage}. */
final class BytesRangeReader implements RangeReader {

    private final String identifier;
    private final byte[] bytes;

    BytesRangeReader(String identifier, byte[] bytes) {
        this.identifier = identifier;
        this.bytes = bytes;
    }

    @Override
    public int readRange(long offset, int length, ByteBuffer target) {
        if (offset >= bytes.length) {
            return 0;
        }
        int count = (int) Math.min(length, bytes.length - offset);
        target.put(bytes, (int) offset, count);
        return count;
    }

    @Override
    public OptionalLong size() {
        return OptionalLong.of(bytes.length);
    }

    @Override
    public String getSourceIdentifier() {
        return identifier;
    }

    @Override
    public void close() {
        // nothing to release
    }
}
