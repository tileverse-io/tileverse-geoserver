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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;

import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.ParamResourceModel;

/**
 * GeoServer's table over the rows of the browser, with its search box and pages of 25 rows: links for the folders and
 * for the files pickable by the store, and a lazy cell per format column.
 */
final class BrowserTable extends GeoServerTablePanel<BrowserEntry> {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 25;
    private static final String TITLE_PREFIX = "StorageBrowserPanel.";
    private static final String[] SIZE_UNITS = {"KB", "MB", "GB", "TB"};
    private static final int KILO = 1024;

    /** The id of the label counting the rows, in the markup of GeoServer's pager. */
    private static final String COUNT_ID = "filterMatch";

    private final BrowserRef ref;
    private final StorageBrowsing browsing;

    BrowserTable(String id, BrowserEntryProvider provider, BrowserRef ref, StorageBrowsing browsing) {
        super(id, forgetRememberedSearch(provider));
        this.ref = ref;
        this.browsing = browsing;
        setItemsPerPage(PAGE_SIZE);
        setOutputMarkupId(true);
    }

    /**
     * Shows the first page in name order: another folder, Refresh, or a cancelled sort. The cells of the previous
     * listing are not reused: Refresh reads the values again.
     */
    void showFirstPage() {
        provider().startListing();
        reset();
        countFirstPage();
    }

    /** Shows the first page, keeping the sort: the values of a sort by a format column are read. */
    void showSortedFirstPage() {
        BrowserEntryProvider provider = provider();
        SortParam<Object> sort = provider.getSort();
        reset();
        provider.restoreSort(sort);
        countFirstPage();
    }

    private BrowserEntryProvider provider() {
        return (BrowserEntryProvider) getDataProvider();
    }

    /**
     * GeoServer computes the counts of its pager labels when it builds the table, and again on a search or a page
     * change. Opening another folder changes the rows without any of these events: the labels keep the counts of the
     * previous folder. Showing the first page computes them again.
     */
    private void countFirstPage() {
        GeoServerDataProvider<BrowserEntry> provider = getDataProvider();
        long matched = provider.size();
        long first = matched > 0 ? 1 : 0;
        long last = Math.min(matched, PAGE_SIZE);
        IModel<String> counts = countsOf(provider, first, last, matched);
        showCounts(getTopPager(), counts);
        showCounts(getBottomPager(), counts);
    }

    private IModel<String> countsOf(GeoServerDataProvider<BrowserEntry> provider, long first, long last, long matched) {
        if (provider.getKeywords() == null) {
            return new ParamResourceModel("showingAllRecords", this, first, last, matched);
        }
        return new ParamResourceModel("matchedXOutOfY", this, first, last, matched, provider.fullSize());
    }

    /** GeoServer owns the markup of its pager: a pager without the label keeps the count GeoServer computed. */
    private static void showCounts(Component pager, IModel<String> counts) {
        Component label = pager.get(COUNT_ID);
        if (label != null) {
            label.setDefaultModel(counts);
        }
    }

    @Override
    protected IModel<String> getPropertyTitle(Property<BrowserEntry> property) {
        if (property instanceof BrowserEntryProvider.FormatProperty format) {
            FileColumn column = format.column();
            return new ResourceModel(column.titleKey(), column.key());
        }
        return new ResourceModel(TITLE_PREFIX + property.getName(), property.getName());
    }

    @Override
    protected Component getComponentForProperty(
            String id, IModel<BrowserEntry> itemModel, Property<BrowserEntry> property) {
        BrowserEntry row = itemModel.getObject();
        if (property instanceof BrowserEntryProvider.FormatProperty format) {
            return formatCell(id, row, format.column());
        }
        String name = property.getName();
        if (BrowserEntryProvider.SIZE_PROPERTY.equals(name)) {
            return new Label(id, sizeText(row));
        }
        if (BrowserEntryProvider.LAST_MODIFIED_PROPERTY.equals(name)) {
            return new Label(id, dateText(row));
        }
        return nameCell(id, row);
    }

    /**
     * GeoServer's table remembers its search text and sort in the user session, by provider class: a new dialog would
     * open filtered by the search of an older one.
     */
    private static BrowserEntryProvider forgetRememberedSearch(BrowserEntryProvider provider) {
        Session session = Session.get();
        forget(session, GeoServerTablePanel.FILTER_INPUTS);
        forget(session, GeoServerTablePanel.SORT_INPUTS);
        return provider;
    }

    private static void forget(Session session, String attribute) {
        if (session.getAttribute(attribute) instanceof MetadataMap remembered) {
            remembered.remove(BrowserEntryProvider.class.getName());
        }
    }

    private Component nameCell(String id, BrowserEntry row) {
        if (row.folder()) {
            return new EventLink(id, row.name() + "/", target -> new FolderOpened(row.key(), target));
        }
        if (browsing.pickMode().allowsFiles()) {
            return new EventLink(id, row.name(), target -> new EntryChosen(row.key(), target));
        }
        return new Label(id, row.name());
    }

    /** A file read before its row shows needs no loading cell: its value shows at once. */
    private Component formatCell(String id, BrowserEntry row, FileColumn column) {
        if (row.folder() || !browsing.columns().reads(row.name())) {
            return new Label(id, "");
        }
        if (alreadyRead(row)) {
            return new FormatValue(id, ref, row, column);
        }
        return new FormatCell(id, ref, row, column);
    }

    private boolean alreadyRead(BrowserEntry row) {
        Optional<FileDetails> details = ref.browser().flatMap(browser -> browser.details(row));
        return details.isPresent();
    }

    /** Bytes, then KB, MB, GB and TB of 1,024 with one decimal, written in the language of the user. */
    private String sizeText(BrowserEntry row) {
        if (row.folder()) {
            return "";
        }
        Locale language = getLocale();
        long bytes = row.size();
        if (bytes < KILO) {
            return String.format(language, "%,d B", bytes);
        }
        double size = bytes;
        int unit = -1;
        while (size >= KILO && unit < SIZE_UNITS.length - 1) {
            size /= KILO;
            unit++;
        }
        return String.format(language, "%,.1f %s", size, SIZE_UNITS[unit]);
    }

    private String dateText(BrowserEntry row) {
        if (row.lastModified() == null) {
            return "";
        }
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(getLocale())
                .withZone(ZoneId.systemDefault());
        return dates.format(row.lastModified());
    }
}
