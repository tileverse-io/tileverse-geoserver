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

import java.util.Locale;

import org.apache.wicket.Component;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.util.tester.WicketTester;

/**
 * Stands in for an entry of a GeoServer bundle. Resolves one key, and only for a component, as GeoServer's own loader
 * of labels does.
 */
record BundleEntry(String key, String text) implements IStringResourceLoader {

    /** Makes the application of {@code tester} resolve {@code key} as {@code text}. */
    static void define(WicketTester tester, String key, String text) {
        tester.getApplication().getResourceSettings().getStringResourceLoaders().add(0, new BundleEntry(key, text));
    }

    @Override
    public String loadStringResource(Class<?> clazz, String requested, Locale locale, String style, String variation) {
        return textOf(requested);
    }

    @Override
    public String loadStringResource(
            Component component, String requested, Locale locale, String style, String variation) {
        if (component == null) {
            return null;
        }
        return textOf(requested);
    }

    private String textOf(String requested) {
        if (key.equals(requested)) {
            return text;
        }
        return null;
    }
}
