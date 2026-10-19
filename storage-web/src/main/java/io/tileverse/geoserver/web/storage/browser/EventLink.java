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

import java.io.Serializable;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.model.Model;
import org.geoserver.web.wicket.SimpleAjaxLink;

/** A link sending an event up its parents when clicked: the rows and the breadcrumbs of the browser use it. */
final class EventLink extends SimpleAjaxLink<String> {

    private static final long serialVersionUID = 1L;

    /** Builds the event of a click. */
    @FunctionalInterface
    interface EventFactory extends Serializable {

        Object eventOf(AjaxRequestTarget target);
    }

    private final EventFactory events;

    EventLink(String id, String label, EventFactory events) {
        super(id, Model.of(label), Model.of(label));
        this.events = events;
    }

    @Override
    protected void onClick(AjaxRequestTarget target) {
        send(this, Broadcast.BUBBLE, events.eventOf(target));
    }
}
