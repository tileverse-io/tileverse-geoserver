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

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.GeoServerSecuredPage;

/**
 * The page of the Tools menu listing the stores of GeoServer community modules, to check them against the tileverse
 * stores, migrate them in place and revert them. Only administrators open it.
 */
public class StoreMigrationPage extends GeoServerSecuredPage {

    private static final long serialVersionUID = 1L;

    public StoreMigrationPage() {
        IModel<StoreMigrations> migrations = LoadableDetachableModel.of(StoreMigrationPage::applicationMigrations);
        add(new StoreMigrationPanel("migrations", migrations));
    }

    /** The service holds the catalog and the beans of the application: a page in the page store must not. */
    private static StoreMigrations applicationMigrations() {
        return StoreMigrations.of(GeoServerApplication.get());
    }
}
