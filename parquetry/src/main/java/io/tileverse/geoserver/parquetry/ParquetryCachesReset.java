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
package io.tileverse.geoserver.parquetry;

import org.geoserver.config.impl.GeoServerLifecycleHandler;

import io.tileverse.parquetry.data.ParquetCaches;

/**
 * Clears the caches of parquetry when GeoServer resets: the footers, the learned page indexes and the byte ranges kept
 * in the shared tileverse caches. A reload resets first. A file replaced in place is then read afresh, without waiting
 * for its cached entries to expire.
 *
 * <p>The plugin's {@code applicationContext.xml} declares it as the {@code parquetryCachesReset} bean. A GeoServer
 * Cloud service imports that bean by name to clear its caches on a reset.
 */
public final class ParquetryCachesReset implements GeoServerLifecycleHandler {

    @Override
    public void onReset() {
        ParquetCaches.clearAll();
    }

    @Override
    public void onDispose() {
        // the caches belong to the process, not to the configuration
    }

    @Override
    public void beforeReload() {
        // a reload resets first
    }

    @Override
    public void onReload() {
        // a reload resets first
    }
}
