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

import org.geoserver.web.data.resource.DataStorePanelInfo;
import org.geoserver.web.data.store.StoreEditPanel;

/**
 * Builds the {@link DataStorePanelInfo} beans the per-store {@code @Configuration} classes share, centralizing the one
 * unchecked cast that {@link DataStorePanelInfo#setComponentClass} forces on a concrete panel class.
 */
final class DataStorePanels {

    private DataStorePanels() {}

    static DataStorePanelInfo panel(
            String id, Class<?> factoryClass, Class<? extends StoreEditPanel> componentClass, String icon) {

        DataStorePanelInfo info = new DataStorePanelInfo();
        info.setId(id);
        info.setFactoryClass(factoryClass);
        info.setComponentClass(asPanelType(componentClass));
        info.setIconBase(componentClass);
        info.setIcon(icon);
        return info;
    }

    // setComponentClass wants the erased StoreEditPanel type; any concrete panel subclass is a valid runtime value.
    @SuppressWarnings("unchecked")
    private static Class<StoreEditPanel> asPanelType(Class<? extends StoreEditPanel> componentClass) {
        return (Class<StoreEditPanel>) componentClass;
    }
}
