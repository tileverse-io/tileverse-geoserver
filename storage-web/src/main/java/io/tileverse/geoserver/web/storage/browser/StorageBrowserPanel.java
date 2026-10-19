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

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.feedback.ContainerFeedbackMessageFilter;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.geoserver.web.wicket.GSModalWindow;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

/**
 * The content of the storage browser dialog: the root picker for local files, the breadcrumbs, the table of the folder
 * shown with its search box, and the buttons. It holds UI code only: its requests go to the {@link StorageBrowser} of
 * its token, and a choice goes up to the field that opened it as a {@link LocationChosen} event.
 */
public class StorageBrowserPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private final BrowserRef ref;
    private final StorageBrowsing browsing;
    private final FeedbackPanel feedback;
    private final WebMarkupContainer retry;
    private final WebMarkupContainer content;
    private final BrowserEntryProvider provider;
    private final SortNotice sortNotice;
    private final BrowserTable table;

    /** The local root picked, written by the root picker. */
    private final IModel<File> pickedRoot = new Model<>();

    /** The sort changes seen by the previous request: a click on a column header changes the sort silently. */
    private int seenSortChanges;

    public StorageBrowserPanel(String id, String token, StorageBrowsing browsing) {
        super(id);
        setOutputMarkupId(true);
        this.ref = new BrowserRef(token);
        this.browsing = browsing;
        this.feedback = new FeedbackPanel("feedback", new ContainerFeedbackMessageFilter(this));
        feedback.setOutputMarkupId(true);
        add(feedback);
        this.retry = retryRow();
        add(retry);
        this.content = new WebMarkupContainer("content");
        content.setOutputMarkupId(true);
        add(content);
        this.provider = new BrowserEntryProvider(ref, browsing.columns().columns());
        this.seenSortChanges = provider.sortChanges();
        this.sortNotice = new SortNotice("sortNotice", ref);
        this.table = new BrowserTable("table", provider, ref, browsing);
        content.add(rootPicker(), breadcrumbs(), truncationNotice(), sortNotice, table);
        content.add(chooseFolderLink(), refreshLink(), cancelLink());
    }

    /** Lists the starting folder: a failure shows in the dialog, with Retry. */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        attempt(browser -> browser.openFolder(browser.folder()));
        table.showFirstPage();
    }

    /** GeoServer's links are block-level: the stylesheet keeps the breadcrumbs on one line. */
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        PackageResourceReference stylesheet =
                new PackageResourceReference(StorageBrowserPanel.class, "StorageBrowserPanel.css");
        response.render(CssHeaderItem.forReference(stylesheet));
    }

    @Override
    public void onEvent(IEvent<?> event) {
        Object payload = event.getPayload();
        if (payload instanceof FolderOpened opened) {
            event.stop();
            openFolder(opened.key(), opened.target());
        } else if (payload instanceof EntryChosen chosen) {
            event.stop();
            choose(chosen.key(), chosen.target());
        } else if (payload instanceof SortReady ready) {
            event.stop();
            table.showSortedFirstPage();
            ready.target().add(content);
        } else if (payload instanceof SortCancelled cancelled) {
            event.stop();
            table.showFirstPage();
            cancelled.target().add(content);
        } else if (payload instanceof AjaxRequestTarget target) {
            followTheTable(target);
        }
    }

    /**
     * Follows the changes made by the table's own links: a search over a truncated listing lists the folder again, a
     * sort by a format column reads its values first, and a closed browser shows its message.
     */
    private void followTheTable(AjaxRequestTarget target) {
        if (ref.browser().isEmpty()) {
            reportExpiryToTheTable(target);
            return;
        }
        searchAgain(target);
        followTheSort(target);
    }

    /**
     * The table of a closed browser has no rows. A request repainting a part of it, as its own links do, shows the
     * expiry message. The polls of the cells and of the sort notice, and the other requests of the store page, say
     * nothing: the user did not act on the table.
     */
    private void reportExpiryToTheTable(AjaxRequestTarget target) {
        if (repaintsTheTable(target)) {
            error(BrowseMessages.of(new BrowseException(Reason.EXPIRED), this));
            target.add(feedback);
        }
    }

    /** Whether the request repaints a part of the table other than its cells, as its sort headers and search box do. */
    private boolean repaintsTheTable(AjaxRequestTarget target) {
        for (Component component : target.getComponents()) {
            boolean inTheTable = table.contains(component, true);
            if (inTheTable && !isCell(component)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCell(Component component) {
        return component instanceof FormatCell || component.findParent(FormatCell.class) != null;
    }

    private void followTheSort(AjaxRequestTarget target) {
        int sortChanges = provider.sortChanges();
        if (sortChanges != seenSortChanges) {
            seenSortChanges = sortChanges;
            sortNotice.follow(target);
        }
    }

    /** Runs the search of a truncated listing before the table renders, for a failure to show here. */
    private void searchAgain(AjaxRequestTarget target) {
        Optional<String> text = provider.searchText();
        Optional<StorageBrowser> browser = ref.browser();
        if (text.isEmpty() || browser.isEmpty() || !browser.get().truncated()) {
            return;
        }
        try {
            browser.get().search(text.get());
        } catch (BrowseException failure) {
            error(BrowseMessages.of(failure, this));
            target.add(feedback);
        }
    }

    private void openFolder(String key, AjaxRequestTarget target) {
        attemptAndShow(browser -> browser.openFolder(key), target);
    }

    private void choose(String key, AjaxRequestTarget target) {
        try {
            StorageBrowser browser = ref.require();
            URI location = key.equals(browser.folder()) ? browser.chooseFolder() : browser.chooseFile(key);
            send(this, Broadcast.BUBBLE, new LocationChosen(location, target));
        } catch (BrowseException failure) {
            error(BrowseMessages.of(failure, this));
            target.add(feedback);
        }
    }

    /** Runs an action on the browser, then shows the first page of its listing and repaints the dialog. */
    private void attemptAndShow(BrowserAction action, AjaxRequestTarget target) {
        attempt(action);
        table.showFirstPage();
        target.add(feedback, retry, content);
    }

    /** Runs an action on the browser. A failure shows in the dialog, with Retry unless the browser expired. */
    private void attempt(BrowserAction action) {
        try {
            action.run(ref.require());
            retry.setVisible(false);
        } catch (BrowseException failure) {
            error(BrowseMessages.of(failure, this));
            retry.setVisible(failure.reason() != Reason.EXPIRED);
        }
    }

    /**
     * The row of the Retry button, shown after a failure. The row is a button group because GeoServer 2.28 draws a link
     * as a button only inside one.
     */
    private WebMarkupContainer retryRow() {
        WebMarkupContainer row = new WebMarkupContainer("retry");
        row.setOutputMarkupPlaceholderTag(true);
        row.setVisible(false);
        row.add(retryLink());
        return row;
    }

    /**
     * Lists the folder shown again. After a failed move to another folder, the dialog stays in the folder shown before.
     */
    private AjaxLink<Void> retryLink() {
        return new AjaxLink<>("retryLink") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                attemptAndShow(browser -> browser.openFolder(browser.folder()), target);
            }
        };
    }

    private DropDownChoice<File> rootPicker() {
        IModel<List<File>> roots = LoadableDetachableModel.of(
                () -> ref.browser().map(StorageBrowser::localRoots).orElse(List.of()));
        // A display value other than a String goes through the converter of the application. GeoServer's converter for
        // File writes a file outside the data directory as null. A "path" expression would make Wicket open the
        // private field of the JDK class, an access refused by Java 17.
        IChoiceRenderer<File> pathAsText = File::getPath;
        DropDownChoice<File> picker = new DropDownChoice<>("root", pickedRoot, roots, pathAsText) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                Optional<StorageBrowser> browser = ref.browser();
                setVisible(browser.map(StorageBrowser::local).orElse(false));
                pickedRoot.setObject(browser.flatMap(StorageBrowser::localRoot).orElse(null));
            }
        };
        picker.add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                File picked = pickedRoot.getObject();
                attemptAndShow(browser -> browser.openRoot(browser.localRoots().indexOf(picked)), target);
            }
        });
        return picker;
    }

    private ListView<Crumb> breadcrumbs() {
        IModel<List<Crumb>> crumbs = LoadableDetachableModel.of(
                () -> ref.browser().map(StorageBrowser::breadcrumbs).orElse(List.of()));
        return new ListView<>("crumbs", crumbs) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<Crumb> item) {
                Crumb crumb = item.getModelObject();
                item.add(new EventLink("crumb", crumb.label(), target -> new FolderOpened(crumb.key(), target)));
            }
        };
    }

    private Label truncationNotice() {
        IModel<String> text = () -> new StringResourceModel("StorageBrowserPanel.truncated", this)
                .setParameters(ref.browser().map(StorageBrowser::listingLimit).orElse(0))
                .getString();
        return new Label("truncated", text) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(ref.browser().map(StorageBrowser::truncated).orElse(false));
            }
        };
    }

    /** Sends the key of the folder shown, as a file row sends its own. */
    private Component chooseFolderLink() {
        AjaxLink<Void> link = new AjaxLink<>("chooseFolder") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                String shown = ref.browser().map(StorageBrowser::folder).orElse("");
                send(this, Broadcast.BUBBLE, new EntryChosen(shown, target));
            }
        };
        link.setVisible(browsing.pickMode().allowsFolders());
        return link;
    }

    private Component refreshLink() {
        return new AjaxLink<Void>("refresh") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                attemptAndShow(StorageBrowser::refresh, target);
            }
        };
    }

    /** Closes the window holding the dialog: the window then closes the browser. */
    private Component cancelLink() {
        return new AjaxLink<Void>("cancel") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                GSModalWindow window = findParent(GSModalWindow.class);
                if (window != null) {
                    window.close(target);
                }
            }
        };
    }

    @FunctionalInterface
    private interface BrowserAction {

        void run(StorageBrowser browser);
    }
}
