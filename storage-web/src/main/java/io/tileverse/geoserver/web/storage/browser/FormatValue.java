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

import java.util.Optional;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 * The value of one format column for one file, as read by the browser. A file that could not be read shows as
 * unavailable, with the reason as its tooltip. The label stays empty once the browser is gone.
 */
final class FormatValue extends Label {

    private static final long serialVersionUID = 1L;
    private static final String TITLE = "title";

    private final BrowserRef ref;
    private final BrowserEntry row;
    private final FileColumn column;

    FormatValue(String id, BrowserRef ref, BrowserEntry row, FileColumn column) {
        super(id);
        this.ref = ref;
        this.row = row;
        this.column = column;
        setDefaultModel(LoadableDetachableModel.of(this::text));
        // A null title adds no attribute: only a file that could not be read gets a tooltip.
        add(AttributeModifier.replace(TITLE, LoadableDetachableModel.of(this::reason)));
    }

    private String text() {
        Optional<FileDetails> details = details();
        if (details.isEmpty()) {
            return "";
        }
        if (details.get() instanceof FileDetails.Values values) {
            ColumnValue value = values.byColumn().get(column.key());
            return value == null ? "" : value.textIn(getLocale());
        }
        return getString("StorageBrowserPanel.unavailable");
    }

    private String reason() {
        Optional<FileDetails> details = details();
        if (details.isPresent() && details.get() instanceof FileDetails.Unavailable unavailable) {
            return unavailable.reason();
        }
        return null;
    }

    private Optional<FileDetails> details() {
        return ref.browser().flatMap(browser -> browser.details(row));
    }
}
