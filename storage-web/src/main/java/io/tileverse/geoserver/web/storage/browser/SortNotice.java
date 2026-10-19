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
import java.util.Optional;

import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;

/**
 * The notice of a sort by a format column: the progress of its reads with a Cancel link, or the request to narrow the
 * list first. It polls while the reads run, and tells the dialog once they end.
 */
final class SortNotice extends Panel {

    private static final long serialVersionUID = 1L;
    private static final Duration POLL = Duration.ofSeconds(1);

    private final BrowserRef ref;
    private final AbstractAjaxTimerBehavior timer;

    SortNotice(String id, BrowserRef ref) {
        super(id);
        this.ref = ref;
        setOutputMarkupPlaceholderTag(true);
        IModel<String> reading = this::readingText;
        IModel<String> tooMany = this::tooManyText;
        add(new Label("reading", reading));
        add(cancelLink());
        add(new Label("tooMany", tooMany));
        this.timer = new AbstractAjaxTimerBehavior(POLL) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onTimer(AjaxRequestTarget target) {
                poll(target);
            }
        };
        add(timer);
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
        boolean reading = isReading();
        boolean tooMany = progress().map(SortProgress::tooMany).orElse(false);
        get("reading").setVisible(reading);
        get("cancel").setVisible(reading);
        get("tooMany").setVisible(tooMany);
        setVisible(reading || tooMany);
    }

    /** Repaints the notice after a change of sort, polling again while its reads run. */
    void follow(AjaxRequestTarget target) {
        if (isReading() && timer.isStopped()) {
            timer.restart(target);
        }
        target.add(this);
    }

    private void poll(AjaxRequestTarget target) {
        boolean complete = progress().map(SortProgress::complete).orElse(false);
        if (!isReading()) {
            timer.stop(target);
        }
        if (complete) {
            send(this, Broadcast.BUBBLE, new SortReady(target));
        }
        target.add(this);
    }

    private boolean isReading() {
        return progress().map(found -> !found.complete() && !found.tooMany()).orElse(false);
    }

    private Optional<SortProgress> progress() {
        return ref.browser().flatMap(StorageBrowser::sortProgress);
    }

    private String readingText() {
        Optional<SortProgress> progress = progress();
        if (progress.isEmpty()) {
            return "";
        }
        SortProgress found = progress.get();
        return new StringResourceModel("StorageBrowserPanel.sortReading", this)
                .setParameters(found.read(), found.total(), titleOf(found.column()))
                .getString();
    }

    private String tooManyText() {
        Optional<SortProgress> progress = progress();
        if (progress.isEmpty()) {
            return "";
        }
        SortProgress found = progress.get();
        return new StringResourceModel("StorageBrowserPanel.sortTooMany", this)
                .setParameters(titleOf(found.column()), found.limit())
                .getString();
    }

    private String titleOf(FileColumn column) {
        return getString(column.titleKey(), null, column.key());
    }

    private AjaxLink<Void> cancelLink() {
        return new AjaxLink<>("cancel") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ref.browser().ifPresent(StorageBrowser::cancelSort);
                send(this, Broadcast.BUBBLE, new SortCancelled(target));
            }
        };
    }
}
