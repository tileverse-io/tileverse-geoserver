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

import org.apache.wicket.Component;
import org.apache.wicket.model.StringResourceModel;

/**
 * The text of a {@link BrowseException} in the language of the page. Only a message showing a detail goes through
 * {@code MessageFormat}: an apostrophe in the others stays as written.
 */
final class BrowseMessages {

    private static final String KEY_PREFIX = "BrowseException.";

    private BrowseMessages() {}

    static String of(BrowseException failure, Component component) {
        String key = KEY_PREFIX + failure.reason().name();
        if (!failure.reason().takesDetail()) {
            return component.getString(key);
        }
        return new StringResourceModel(key, component)
                .setParameters(failure.detail())
                .getString();
    }
}
