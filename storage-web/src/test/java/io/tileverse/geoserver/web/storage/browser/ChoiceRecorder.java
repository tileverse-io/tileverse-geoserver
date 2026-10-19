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

import java.net.URI;

import org.apache.wicket.event.IEvent;
import org.apache.wicket.markup.html.panel.Panel;

/** Holds a browser panel and records the location sent up by the panel, as the location field would write it. */
public class ChoiceRecorder extends Panel {

    private static final long serialVersionUID = 1L;

    private URI chosen;

    public ChoiceRecorder(String id, String token, StorageBrowsing browsing) {
        super(id);
        add(new StorageBrowserPanel("browser", token, browsing));
    }

    @Override
    public void onEvent(IEvent<?> event) {
        if (event.getPayload() instanceof LocationChosen location) {
            chosen = location.location();
        }
    }

    URI chosen() {
        return chosen;
    }
}
