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
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import io.tileverse.storage.RangeReader;

/**
 * The format columns of a store: which files they read, and what they read from one file. A store panel supplies its
 * own, and storage-web needs no engine for them. Implementations are serializable, as kept by the store page, and
 * stateless, as concurrent reads share them.
 */
public interface FileColumns extends Serializable {

    /** No columns, for a store showing names, sizes and dates only. */
    FileColumns NONE = NoFileColumns.INSTANCE;

    /** The columns, in display order. */
    List<FileColumn> columns();

    /** Whether {@link #read} reads a file of this name; the cells of other files stay empty. */
    boolean reads(String fileName);

    /**
     * Reads the values of one file. A file rejected by the store is no failure: the values say why, as the COG status
     * does.
     *
     * @param file the reader of the file, closed by the caller after the read; an implementation may close it too
     * @return the values by column key, leaving out the columns without a value
     * @throws IOException if the file cannot be read; its cells then show "unavailable"
     */
    Map<String, ColumnValue> read(RangeReader file) throws IOException;
}
