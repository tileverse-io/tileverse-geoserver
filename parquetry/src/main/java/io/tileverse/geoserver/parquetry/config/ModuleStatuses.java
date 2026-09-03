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
package io.tileverse.geoserver.parquetry.config;

import org.geoserver.platform.ModuleStatus;
import org.geoserver.platform.ModuleStatusImpl;

/**
 * Builds the {@link ModuleStatusImpl} beans the feature {@code @Configuration} classes declare. Each feature (store
 * type or WFS output format) reports its own community-module row under About > Server Status > Modules, keeping the
 * list accurate when GeoServer Cloud auto-configuration enables features independently.
 */
final class ModuleStatuses {

    private ModuleStatuses() {}

    static ModuleStatusImpl community(String module, String name, String component) {
        ModuleStatusImpl status = new ModuleStatusImpl();
        status.setModule(module);
        status.setName(name);
        status.setComponent(component);
        status.setAvailable(true);
        status.setEnabled(true);
        status.setCategory(ModuleStatus.Category.COMMUNITY);
        return status;
    }
}
