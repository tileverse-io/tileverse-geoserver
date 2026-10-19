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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.model.IModel;
import org.geoserver.web.wicket.GeoServerDataProvider;

/**
 * The rows of the folder shown, read from the browser whenever asked, sorted with folders first, and told back to the
 * browser as the rows shown for their format reads. Properties compare by name: a page restored from its store holds
 * copies of them.
 */
final class BrowserEntryProvider extends GeoServerDataProvider<BrowserEntry> {

    private static final long serialVersionUID = 1L;

    static final String NAME_PROPERTY = "name";
    static final String SIZE_PROPERTY = "size";
    static final String LAST_MODIFIED_PROPERTY = "lastModified";

    private static final Comparator<BrowserEntry> BY_SIZE = Comparator.comparingLong(BrowserEntry::size);
    private static final Comparator<BrowserEntry> BY_DATE =
            Comparator.comparing(BrowserEntry::lastModified, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final BrowserRef ref;
    private final List<Property<BrowserEntry>> properties;

    /**
     * Counts the changes the sort notice must follow: a click on a column header changes the sort, and a search changes
     * its rows, both without telling the dialog.
     */
    private int sortChanges;

    /** Counts the listings shown: the rows of another listing get models of their own, and new cells. */
    private int listings;

    /**
     * The count of the rows last found. The pager disables its links without rows, and Wicket answers a click on a
     * disabled link with its access denied page. Once the browser is gone, the provider keeps answering the last
     * counts, and the pager keeps its links. A click on one reaches the dialog, and the dialog reports the expiry.
     */
    private long lastSize;

    /** The count of the rows last listed, apart from the search, remembered with {@link #lastSize}. */
    private int lastFullSize;

    BrowserEntryProvider(BrowserRef ref, List<FileColumn> columns) {
        this.ref = ref;
        List<Property<BrowserEntry>> all = new ArrayList<>();
        all.add(new BaseProperty(NAME_PROPERTY));
        all.add(new BaseProperty(SIZE_PROPERTY));
        all.add(new BaseProperty(LAST_MODIFIED_PROPERTY));
        for (FileColumn column : columns) {
            all.add(new FormatProperty(column));
        }
        this.properties = List.copyOf(all);
    }

    int sortChanges() {
        return sortChanges;
    }

    /**
     * Starts another listing: Refresh reads the values again, and a cell of the previous listing would not show them.
     */
    void startListing() {
        listings++;
    }

    /** The text of the search box, when it holds one. */
    Optional<String> searchText() {
        String[] keywords = getKeywords();
        if (keywords == null || keywords.length == 0) {
            return Optional.empty();
        }
        return Optional.of(String.join(" ", keywords));
    }

    @Override
    protected List<Property<BrowserEntry>> getProperties() {
        return properties;
    }

    @Override
    protected List<BrowserEntry> getItems() {
        return ref.browser().map(StorageBrowser::rows).orElse(List.of());
    }

    /** Over a truncated listing, a search lists the folder again: the entries past the limit must be found too. */
    @Override
    protected List<BrowserEntry> getFilteredItems() {
        Optional<StorageBrowser> browser = ref.browser();
        Optional<String> text = searchText();
        if (browser.isEmpty() || text.isEmpty() || !browser.get().truncated()) {
            return super.getFilteredItems();
        }
        return new ArrayList<>(searchRows(browser.get(), text.get()));
    }

    /**
     * The count of the rows found, or of the rows last found once the browser is gone. The count of {@link #fullSize()}
     * is remembered with it: the pager labels show both. The browser is checked after counting: a browser still open
     * then was open during the count, as a closed browser never opens again.
     */
    @Override
    public long size() {
        long size = super.size();
        int fullSize = super.fullSize();
        if (ref.browser().isPresent()) {
            lastSize = size;
            lastFullSize = fullSize;
        }
        return lastSize;
    }

    /** The count of the rows listed, or of the rows last listed once the browser is gone. */
    @Override
    public int fullSize() {
        int fullSize = super.fullSize();
        if (ref.browser().isPresent()) {
            lastFullSize = fullSize;
        }
        return lastFullSize;
    }

    /** Sorts with folders first, then pages, and tells the browser the rows shown by the page. */
    @Override
    public Iterator<BrowserEntry> iterator(long first, long count) {
        List<BrowserEntry> rows = getFilteredItems();
        rows.sort(orderOf(rows));
        List<BrowserEntry> page = pageOf(rows, first, count);
        ref.browser().ifPresent(browser -> browser.showRows(page));
        return page.iterator();
    }

    @Override
    public void setSort(SortParam<Object> param) {
        SortParam<Object> previous = getSort();
        super.setSort(param);
        sortChanges++;
        ref.browser().ifPresent(browser -> prepareSort(browser, param, previous));
    }

    /** Puts a sort back without its side effects: the table clears its sort to show its first page. */
    void restoreSort(SortParam<Object> param) {
        super.setSort(param);
    }

    /** A search keeps the sort by a format column: the reads of its values start for the rows found, as for a click. */
    @Override
    public void setKeywords(String[] keywords) {
        super.setKeywords(keywords);
        ref.browser().ifPresent(this::sortFoundRows);
    }

    @Override
    protected IModel<BrowserEntry> newModel(BrowserEntry row) {
        return new RowModel(row, listings);
    }

    /** Returns no rows for a failed search: the dialog runs the same search before the table renders, and shows it. */
    private static List<BrowserEntry> searchRows(StorageBrowser browser, String text) {
        try {
            return browser.search(text);
        } catch (BrowseException failure) {
            return List.of();
        }
    }

    /**
     * Starts the reads of the sort for the rows found. Over the limit of a sort, the rows stay in name order and the
     * notice asks to narrow the list: a narrower search then sorts them.
     */
    private void sortFoundRows(StorageBrowser browser) {
        Property<BrowserEntry> property = getProperty(getSort());
        if (property instanceof FormatProperty format) {
            sortChanges++;
            browser.sortBy(format.column(), getFilteredItems());
        }
    }

    /**
     * A sort by a format column starts its reads; over the limit of a sort the table keeps its order, and the notice
     * asks to narrow the list. Another sort cancels the reads of a previous one.
     */
    private void prepareSort(StorageBrowser browser, SortParam<Object> param, SortParam<Object> previous) {
        Property<BrowserEntry> property = getProperty(param);
        if (!(property instanceof FormatProperty format)) {
            browser.cancelSort();
            return;
        }
        SortProgress progress = browser.sortBy(format.column(), getFilteredItems());
        if (progress.tooMany()) {
            super.setSort(previous);
        }
    }

    private Comparator<BrowserEntry> orderOf(List<BrowserEntry> rows) {
        SortParam<Object> sort = getSort();
        Property<BrowserEntry> property = getProperty(sort);
        boolean ascending = sort == null || sort.isAscending();
        if (property instanceof FormatProperty format) {
            return formatOrder(format.column(), ascending, rows);
        }
        String name = property == null ? NAME_PROPERTY : property.getName();
        if (SIZE_PROPERTY.equals(name)) {
            return BrowserOrder.by(BY_SIZE, ascending);
        }
        if (LAST_MODIFIED_PROPERTY.equals(name)) {
            return BrowserOrder.by(BY_DATE, ascending);
        }
        return BrowserOrder.byName(ascending);
    }

    /** By name until the values of the sort are read: the notice shows the progress meanwhile. */
    private Comparator<BrowserEntry> formatOrder(FileColumn column, boolean ascending, List<BrowserEntry> rows) {
        Optional<StorageBrowser> browser = ref.browser();
        if (browser.isEmpty()) {
            return BrowserOrder.byName(true);
        }
        SortProgress progress = browser.get().sortBy(column, rows);
        if (!progress.complete()) {
            return BrowserOrder.byName(true);
        }
        return browser.get().formatOrder(column, ascending);
    }

    private static List<BrowserEntry> pageOf(List<BrowserEntry> rows, long first, long count) {
        int from = (int) Math.min(first, rows.size());
        int to = (int) Math.min(first + count, rows.size());
        return new ArrayList<>(rows.subList(from, to));
    }

    /** A column of name, size or date; the name is the only searchable one. */
    private static final class BaseProperty extends AbstractProperty<BrowserEntry> {

        private static final long serialVersionUID = 1L;

        BaseProperty(String name) {
            super(name);
        }

        @Override
        public Object getPropertyValue(BrowserEntry row) {
            String name = getName();
            if (SIZE_PROPERTY.equals(name)) {
                return row.size();
            }
            if (LAST_MODIFIED_PROPERTY.equals(name)) {
                return row.lastModified();
            }
            return row.name();
        }

        @Override
        public boolean isSearchable() {
            return NAME_PROPERTY.equals(getName());
        }
    }

    /** A format column: its cells come from the reads of the browser, and it sorts unless its column says otherwise. */
    static final class FormatProperty extends AbstractProperty<BrowserEntry> {

        private static final long serialVersionUID = 1L;

        private final FileColumn column;

        FormatProperty(FileColumn column) {
            super("format." + column.key());
            this.column = column;
        }

        FileColumn column() {
            return column;
        }

        @Override
        public Object getPropertyValue(BrowserEntry row) {
            return null;
        }

        /** The table makes a header sortable by its comparator; the provider sorts with the order of the browser. */
        @Override
        public Comparator<BrowserEntry> getComparator() {
            if (column.sort() == ColumnSort.NONE) {
                return null;
            }
            return BrowserOrder.byName(true);
        }

        @Override
        public boolean isSearchable() {
            return false;
        }
    }

    /**
     * A row of one listing. GeoServer's table reuses the item of an equal model, and an item keeps its loaded cells.
     * The listing is part of the model: a row listed again gets another model and new cells, showing the values read
     * again.
     */
    private record RowModel(BrowserEntry row, int listing) implements IModel<BrowserEntry> {

        @Override
        public BrowserEntry getObject() {
            return row;
        }
    }
}
