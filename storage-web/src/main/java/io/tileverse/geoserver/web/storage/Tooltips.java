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

import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;

/**
 * The tooltips of the storage panels. A tooltip is the bundle entry {@code <key>.tooltip} in the language of the
 * session: a {@code GeoServerApplication_<language>.properties} file translates it.
 */
final class Tooltips {

    private static final String KEY_SUFFIX = ".tooltip";

    private Tooltips() {}

    /**
     * The tooltip of a field, read at each render.
     *
     * @param key the key of the field in the bundles, without the tooltip suffix
     * @param field the component showing the tooltip; the text is looked up for it, because GeoServer's own loader of
     *     labels answers no lookup made without a component
     * @param fallback the text for a language without the entry, possibly blank
     * @return the text, or null when blank: a null value leaves the {@code title} attribute out
     */
    static IModel<String> of(String key, Component field, String fallback) {
        StringResourceModel entry = new StringResourceModel(key + KEY_SUFFIX, field);
        entry.setDefaultValue(fallback);
        return entry.map(Tooltips::nullIfBlank);
    }

    private static String nullIfBlank(String text) {
        if (text.isBlank()) {
            return null;
        }
        return text;
    }
}
