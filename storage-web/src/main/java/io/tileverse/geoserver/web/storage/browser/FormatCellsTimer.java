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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Page;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.util.visit.IVisit;

/**
 * The timer of the loading format cells of a page. Each tick shows the values of the files read since the previous
 * tick, and the timer stops once no cell is loading.
 *
 * <p>Wicket's own timer of lazy panels makes its first tick a second after the page shows. The reads of a page of rows
 * usually end well before: its cells would wait for that tick.
 */
final class FormatCellsTimer extends AbstractAjaxTimerBehavior {

    private static final long serialVersionUID = 1L;

    private static final Duration INTERVAL = Duration.ofMillis(250);

    private FormatCellsTimer() {
        super(INTERVAL);
    }

    /**
     * Adds the timer to {@code page}, unless the page has one. An Ajax request does not render the page again: the
     * timer starts through the Ajax response instead.
     */
    static void installOn(Page page) {
        if (!page.getBehaviors(FormatCellsTimer.class).isEmpty()) {
            return;
        }
        FormatCellsTimer timer = new FormatCellsTimer();
        page.add(timer);
        RequestCycle.get().find(AjaxRequestTarget.class).ifPresent(timer::restart);
    }

    @Override
    protected void onTimer(AjaxRequestTarget target) {
        List<FormatCell> loading = showReadCells();
        if (loading.isEmpty()) {
            stop(target);
            getComponent().remove(this);
        }
    }

    /** Shows the values of the cells whose files are read, and returns the cells still loading. */
    private List<FormatCell> showReadCells() {
        List<FormatCell> loading = new ArrayList<>();
        getComponent().getPage().visitChildren(FormatCell.class, (FormatCell cell, IVisit<Void> visit) -> {
            if (cell.isVisibleInHierarchy() && !cell.showIfRead()) {
                loading.add(cell);
            }
        });
        return loading;
    }
}
