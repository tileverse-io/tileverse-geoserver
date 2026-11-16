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
package io.tileverse.geoserver.web.storage.migration;

import java.util.List;
import java.util.Optional;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.geoserver.web.wicket.GeoServerDataProvider;

/**
 * The rows of the store migration page, read from the service on each request. A row model keeps the store id only: a
 * row read again in a later request shows the state reached by the jobs meanwhile.
 */
final class MigrationRowProvider extends GeoServerDataProvider<MigrationRow> {

    private static final long serialVersionUID = 1L;

    static final String WORKSPACE = "workspace";
    static final String STORE = "store";
    static final String KIND = "kind";
    static final String LOCATION = "location";
    static final String LAYERS = "layers";
    static final String STATUS = "status";

    /**
     * The rows of the service, listed once per request. Listing scans the catalog and reads the backups of the migrated
     * stores: the table asks for the rows several times per repaint.
     */
    private final ListedRows rows;

    /** The columns, in the order of the table. */
    private final List<Property<MigrationRow>> properties;

    /**
     * The Origin and Status columns sort and filter on the texts shown by the page, translated in the language of the
     * administrator: a search for "Ready" or "Bereit" finds the ready stores.
     */
    MigrationRowProvider(
            IModel<StoreMigrations> migrations,
            SerializableFunction<MigrationRow, String> kindText,
            SerializableFunction<MigrationRow, String> statusText) {
        this.rows = new ListedRows(migrations);
        this.properties = List.of(
                new RowProperty(WORKSPACE, MigrationRow::workspace),
                new RowProperty(STORE, MigrationRow::store),
                new RowProperty(KIND, kindText::apply),
                new RowProperty(LOCATION, MigrationRow::location),
                new RowProperty(LAYERS, MigrationRow::layers),
                new RowProperty(STATUS, statusText::apply));
    }

    /** Lists the rows again on the next read: a job or an action changed them within this request. */
    void refresh() {
        rows.detach();
    }

    @Override
    protected List<Property<MigrationRow>> getProperties() {
        return properties;
    }

    @Override
    protected List<MigrationRow> getItems() {
        return rows.getObject();
    }

    @Override
    protected IModel<MigrationRow> newModel(MigrationRow row) {
        return new RowModel(rows, row);
    }

    @Override
    public void detach() {
        super.detach();
        rows.detach();
    }

    /** A column showing one value of a row; the value also sorts and filters the rows. */
    private static final class RowProperty extends AbstractProperty<MigrationRow> {

        private static final long serialVersionUID = 1L;

        /** Reads the value of the column from a row. */
        private final SerializableFunction<MigrationRow, Object> value;

        RowProperty(String name, SerializableFunction<MigrationRow, Object> value) {
            super(name);
            this.value = value;
        }

        @Override
        public Object getPropertyValue(MigrationRow row) {
            return value.apply(row);
        }
    }

    /** The rows of the service, kept until the end of the request. */
    private static final class ListedRows extends LoadableDetachableModel<List<MigrationRow>> {

        private static final long serialVersionUID = 1L;

        /** The service listing the rows, loaded again on each request. */
        private final IModel<StoreMigrations> migrations;

        ListedRows(IModel<StoreMigrations> migrations) {
            this.migrations = migrations;
        }

        @Override
        protected List<MigrationRow> load() {
            return migrations.getObject().rows();
        }

        @Override
        protected void onDetach() {
            migrations.detach();
        }
    }

    /**
     * A row listed by the provider, read again by store id once detached. The row is null when the store left the list
     * in between.
     */
    private static final class RowModel extends LoadableDetachableModel<MigrationRow> {

        private static final long serialVersionUID = 1L;

        /** The rows of the request, holding the row read again. */
        private final ListedRows rows;

        /** The id of the store of the row. */
        private final String storeId;

        RowModel(ListedRows rows, MigrationRow row) {
            super(row);
            this.rows = rows;
            this.storeId = row.storeId();
        }

        @Override
        protected MigrationRow load() {
            List<MigrationRow> listed = rows.getObject();
            Optional<MigrationRow> row = listed.stream()
                    .filter(each -> each.storeId().equals(storeId))
                    .findFirst();
            return row.orElse(null);
        }

        @Override
        protected void onDetach() {
            rows.detach();
        }
    }
}
