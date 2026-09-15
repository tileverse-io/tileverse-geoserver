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
package io.tileverse.geoserver.web.storage;

import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;

/** Applies {@link StorageParamVisibility} to components keyed by connection-parameter key. */
final class ComponentVisibility {

    private ComponentVisibility() {}

    /** A null {@code targetOrNull} marks the initial render, which has no Ajax response to update. */
    static void apply(
            Map<String, ? extends Component> componentsByKey,
            Set<String> selectedGroups,
            AjaxRequestTarget targetOrNull) {
        componentsByKey.forEach((key, component) -> {
            component.setVisible(StorageParamVisibility.isVisible(key, selectedGroups));
            if (targetOrNull != null) {
                targetOrNull.add(component);
            }
        });
    }
}
