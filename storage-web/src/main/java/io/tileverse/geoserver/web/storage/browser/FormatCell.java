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

import org.apache.wicket.extensions.ajax.markup.html.AjaxLazyLoadPanel;

/**
 * The cell of one format column for one file: loading until the browser has read the file, then its value. The cells of
 * a page share one {@link FormatCellsTimer}.
 */
final class FormatCell extends AjaxLazyLoadPanel<FormatValue> {

    private static final long serialVersionUID = 1L;

    private final BrowserRef ref;
    private final BrowserEntry row;
    private final FileColumn column;

    FormatCell(String id, BrowserRef ref, BrowserEntry row, FileColumn column) {
        super(id);
        this.ref = ref;
        this.row = row;
        this.column = column;
    }

    /** Ready once the file is read, or once the browser is gone: the cell then stays empty. */
    @Override
    protected boolean isContentReady() {
        Optional<StorageBrowser> browser = ref.browser();
        return browser.isEmpty() || browser.get().details(row).isPresent();
    }

    @Override
    public FormatValue getLazyLoadComponent(String markupId) {
        return new FormatValue(markupId, ref, row, column);
    }

    /** Replaces Wicket's timer of lazy panels: the first tick of that timer comes a second after the page shows. */
    @Override
    protected void initTimer() {
        FormatCellsTimer.installOn(getPage());
    }

    /** Shows the value if the file is read. Returns whether the cell shows it. */
    boolean showIfRead() {
        return isLoaded();
    }
}
