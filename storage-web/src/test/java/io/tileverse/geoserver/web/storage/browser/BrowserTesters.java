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

import org.apache.wicket.settings.ResourceSettings;
import org.apache.wicket.util.tester.WicketTester;
import org.geoserver.web.GeoServerResourceStreamLocator;
import org.geoserver.web.GeoServerStringResourceLoader;

/** WicketTesters for the browser's components, with labels looked up as GeoServer does. */
public final class BrowserTesters {

    private BrowserTesters() {}

    /** Renders a component alone in the default page. */
    public static WicketTester labelledAsGeoServer() {
        return labelled(new WicketTester());
    }

    /** Renders a form holding a component of id {@code field}. */
    public static WicketTester inFormLabelledAsGeoServer() {
        WicketTester tester = new WicketTester() {
            @Override
            protected String createPageMarkup(String componentId) {
                return "<html><head></head><body><form wicket:id='" + componentId
                        + "'><div wicket:id='field'></div></form></body></html>";
            }
        };
        return labelled(tester);
    }

    private static WicketTester labelled(WicketTester tester) {
        ResourceSettings resources = tester.getApplication().getResourceSettings();
        resources.setThrowExceptionOnMissingResource(false);
        resources.setResourceStreamLocator(new GeoServerResourceStreamLocator());
        resources.getStringResourceLoaders().add(0, new GeoServerStringResourceLoader());
        return tester;
    }
}
