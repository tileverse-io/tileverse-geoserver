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
package io.tileverse.geoserver.web.storage.migration;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.danekja.java.util.function.serializable.SerializableConsumer;
import org.danekja.java.util.function.serializable.SerializablePredicate;
import org.geoserver.web.wicket.GSModalWindow;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;
import org.geoserver.web.wicket.GeoServerTablePanel;

/**
 * The content of the store migration page: GeoServer's table of the stores of community modules, the buttons acting on
 * the selected stores, the details of one store and the confirmation dialog. The panel keeps store ids only. It reads
 * the rows and the details from the service on each request, because the jobs change them in the background and their
 * check results are not serializable.
 */
public class StoreMigrationPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(2);
    private static final String TITLE_PREFIX = "StoreMigrationPanel.th.";
    private static final String ITEMS = "listContainer:items";
    private static final String CONFIRM_DIFFERENCES = "StoreMigrationPanel.confirmDifferences";
    private static final String CONFIRM_REVERT = "StoreMigrationPanel.confirmRevert";
    private static final String CONFIRM_DISCARD = "StoreMigrationPanel.confirmDiscard";
    private static final String MIGRATE = "StoreMigrationPanel.migrate";
    private static final String REVERT = "StoreMigrationPanel.revert";
    private static final String DISCARD = "StoreMigrationPanel.discard";

    /** The service behind the page, loaded again on each request. */
    private final IModel<StoreMigrations> migrations;

    /** The rows of the table, listed once per request. */
    private final MigrationRowProvider provider;

    /** The stores, selectable, sortable and filterable. */
    private final GeoServerTablePanel<MigrationRow> table;

    /** Shown when no store is listed. */
    private final Label empty;

    /** Check, migrate, revert and discard, enabled by the selected rows. */
    private final List<AjaxLink<Void>> buttons;

    /** The details of the store of the last status link clicked. */
    private final MigrationDetailsPanel details;

    /** Asks before a migration with differences, a revert or a discard. */
    private final GSModalWindow dialog;

    /** The id of the store shown by the details, null until a status link is clicked. */
    private final IModel<String> shownStoreId = new Model<>();

    public StoreMigrationPanel(String id, IModel<StoreMigrations> migrations) {
        super(id, migrations);
        this.migrations = migrations;
        setOutputMarkupId(true);
        this.provider = new MigrationRowProvider(migrations, this::kindText, this::statusText);
        this.table = new MigrationTable("table", provider);
        add(table);
        this.empty = emptyLabel();
        add(empty);
        this.buttons = List.of(
                button("check", MigrationRow::canCheck, this::check),
                button("migrate", MigrationRow::canMigrate, this::migrate),
                button("revert", MigrationRow::canRevert, this::revert),
                button("discard", MigrationRow::canRevert, this::discard));
        buttons.forEach(this::add);
        this.details = new MigrationDetailsPanel("details", LoadableDetachableModel.of(this::shownDetails));
        add(details);
        this.dialog = new GSModalWindow("dialog");
        add(dialog);
    }

    /** An administrator coming back to the page sees the jobs started before. */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        if (service().isWorking()) {
            add(new JobsTimer());
        }
    }

    private Label emptyLabel() {
        Label label = new Label("empty", new ResourceModel("StoreMigrationPanel.empty")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(table.getDataProvider().fullSize() == 0);
            }
        };
        label.setOutputMarkupPlaceholderTag(true);
        return label;
    }

    /** A button acts on the selected rows allowing its action, and is enabled when there is one. */
    private AjaxLink<Void> button(String id, SerializablePredicate<MigrationRow> allows, RowAction action) {
        AjaxLink<Void> button = new AjaxLink<>(id) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                action.run(target, selectedRows(allows));
            }

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setEnabled(!selectedRows(allows).isEmpty());
            }
        };
        button.setOutputMarkupId(true);
        return button;
    }

    /** The selected rows still listed and allowing the action. */
    private List<MigrationRow> selectedRows(SerializablePredicate<MigrationRow> allows) {
        List<MigrationRow> selection = listedRows(table.getSelection());
        return selection.stream().filter(allows).toList();
    }

    private void check(AjaxRequestTarget target, List<MigrationRow> rows) {
        service().check(idsOf(rows));
        showChanges(target);
    }

    private void migrate(AjaxRequestTarget target, List<MigrationRow> rows) {
        List<String> storeIds = idsOf(rows);
        List<MigrationRow> withDifferences =
                rows.stream().filter(StoreMigrationPanel::hasDifferences).toList();
        if (withDifferences.isEmpty()) {
            startMigration(target, storeIds);
            return;
        }
        confirm(
                target,
                CONFIRM_DIFFERENCES,
                withDifferences,
                MIGRATE,
                confirmed -> startMigration(confirmed, storeIds));
    }

    private void revert(AjaxRequestTarget target, List<MigrationRow> rows) {
        List<String> storeIds = idsOf(rows);
        confirm(target, CONFIRM_REVERT, rows, REVERT, confirmed -> startRevert(confirmed, storeIds));
    }

    private void discard(AjaxRequestTarget target, List<MigrationRow> rows) {
        List<String> storeIds = idsOf(rows);
        confirm(target, CONFIRM_DISCARD, rows, DISCARD, confirmed -> discardBackups(confirmed, storeIds));
    }

    private void startMigration(AjaxRequestTarget target, List<String> storeIds) {
        service().migrate(storeIds);
        showChanges(target);
    }

    private void startRevert(AjaxRequestTarget target, List<String> storeIds) {
        service().revert(storeIds);
        showChanges(target);
    }

    /** The discarded stores leave the list: the selection would point at the rows moved into their places. */
    private void discardBackups(AjaxRequestTarget target, List<String> storeIds) {
        service().discardBackup(storeIds);
        table.clearSelection();
        showChanges(target);
    }

    private void showDetails(AjaxRequestTarget target, String storeId) {
        shownStoreId.setObject(storeId);
        target.add(details);
    }

    /** Repaints the rows, the buttons and the details, and refreshes them until the jobs end. */
    private void showChanges(AjaxRequestTarget target) {
        repaint(target);
        if (service().isWorking() && timers().isEmpty()) {
            JobsTimer timer = new JobsTimer();
            add(timer);
            timer.restart(target);
        }
    }

    private void repaint(AjaxRequestTarget target) {
        provider.refresh();
        target.add(table, empty, details);
        repaintButtons(target);
    }

    private void repaintButtons(AjaxRequestTarget target) {
        for (AjaxLink<Void> button : buttons) {
            target.add(button);
        }
    }

    /**
     * Shows the question in the dialog. The confirm link, labelled as the button clicked, runs the action; the cancel
     * link only closes the dialog.
     */
    private void confirm(
            AjaxRequestTarget target,
            String questionKey,
            List<MigrationRow> named,
            String actionKey,
            SerializableConsumer<AjaxRequestTarget> action) {
        StringResourceModel question = new StringResourceModel(questionKey, this).setParameters(namesOf(named));
        Fragment confirmation = new Fragment(dialog.getContentId(), "confirmation", this);
        confirmation.add(new Label("question", question.getString()));
        confirmation.add(confirmLink(actionKey, action));
        confirmation.add(link("cancel", dialog::close));
        dialog.setTitle(new ResourceModel(actionKey));
        dialog.setContent(confirmation);
        dialog.show(target);
    }

    private AjaxLink<Void> confirmLink(String actionKey, SerializableConsumer<AjaxRequestTarget> action) {
        AjaxLink<Void> link = link("confirm", target -> {
            dialog.close(target);
            action.accept(target);
        });
        link.setBody(new ResourceModel(actionKey));
        return link;
    }

    private MigrationDetails shownDetails() {
        String storeId = shownStoreId.getObject();
        if (storeId == null) {
            return null;
        }
        Optional<MigrationDetails> found = service().details(storeId);
        return found.orElse(null);
    }

    /** The label of the kind of the row, its label key when the bundles have no label. */
    private String kindText(MigrationRow row) {
        String kindKey = row.kindLabelKey();
        return getString(kindKey, null, kindKey);
    }

    /** The label of the state, then the label of the reason of the row when it has one. */
    private String statusText(MigrationRow row) {
        String stateKey = "RowState." + row.state().name();
        String state = getString(stateKey, null, stateKey);
        MigrationNote reason = row.reason();
        if (reason == null) {
            return state;
        }
        return state + ": " + MigrationDetailsPanel.textOf(reason, this);
    }

    private List<AbstractAjaxTimerBehavior> timers() {
        return getBehaviors(AbstractAjaxTimerBehavior.class);
    }

    private StoreMigrations service() {
        return migrations.getObject();
    }

    private static AjaxLink<Void> link(String id, SerializableConsumer<AjaxRequestTarget> onClick) {
        return new AjaxLink<>(id) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                onClick.accept(target);
            }
        };
    }

    /** The table answers null for a selected row whose store left the list. */
    private static List<MigrationRow> listedRows(List<MigrationRow> selection) {
        return selection.stream().filter(Objects::nonNull).toList();
    }

    private static boolean hasDifferences(MigrationRow row) {
        return row.state() == RowState.READY_WITH_DIFFERENCES;
    }

    private static List<String> idsOf(List<MigrationRow> rows) {
        return rows.stream().map(MigrationRow::storeId).toList();
    }

    /** The stores as workspace:store, as GeoServer names them elsewhere. */
    private static String namesOf(List<MigrationRow> rows) {
        return rows.stream().map(row -> row.workspace() + ":" + row.store()).collect(Collectors.joining(", "));
    }

    /** What a button does with the selected rows allowing its action. */
    @FunctionalInterface
    private interface RowAction extends Serializable {
        void run(AjaxRequestTarget target, List<MigrationRow> rows);
    }

    /** GeoServer's table over the rows, with a translated kind and a status link opening the details. */
    private final class MigrationTable extends GeoServerTablePanel<MigrationRow> {

        private static final long serialVersionUID = 1L;

        MigrationTable(String id, MigrationRowProvider provider) {
            super(id, provider, true);
            setOutputMarkupId(true);
        }

        @Override
        protected IModel<String> getPropertyTitle(Property<MigrationRow> property) {
            String name = property.getName();
            return new ResourceModel(TITLE_PREFIX + name, name);
        }

        @Override
        protected Component getComponentForProperty(
                String id, IModel<MigrationRow> itemModel, Property<MigrationRow> property) {
            MigrationRow row = itemModel.getObject();
            String name = property.getName();
            if (MigrationRowProvider.KIND.equals(name)) {
                return new Label(id, kindText(row));
            }
            if (MigrationRowProvider.STATUS.equals(name)) {
                return statusCell(id, row);
            }
            return null;
        }

        @Override
        protected void onSelectionUpdate(AjaxRequestTarget target) {
            repaintButtons(target);
        }

        /**
         * Keeps the selection on the same stores when the rows move. GeoServer's table selects positions, and a repaint
         * sorts the rows again: a store changing state may change position, and the buttons would act on the store
         * moved into its place. A sort, a search or another page clears the selection before the repaint.
         */
        @Override
        protected void onBeforeRender() {
            List<MigrationRow> selected = getSelection();
            List<String> selectedIds = idsOf(listedRows(selected));
            super.onBeforeRender();
            selectAgain(selectedIds);
        }

        /** Ticks the rows of the given stores among the rows built by this render. */
        private void selectAgain(List<String> storeIds) {
            clearSelection();
            MarkupContainer items = (MarkupContainer) get(ITEMS);
            int position = 0;
            for (Component item : items) {
                MigrationRow row = (MigrationRow) item.getDefaultModelObject();
                if (row != null && storeIds.contains(row.storeId())) {
                    selectIndex(position);
                }
                position++;
            }
        }

        private Fragment statusCell(String id, MigrationRow row) {
            String storeId = row.storeId();
            AjaxLink<Void> link = link(MigrationRowProvider.STATUS, target -> showDetails(target, storeId));
            link.add(new Label("label", statusText(row)));
            Fragment cell = new Fragment(id, "statusCell", StoreMigrationPanel.this);
            cell.add(link);
            return cell;
        }
    }

    /** Repaints the table, the buttons and the details while jobs run, and removes itself once they end. */
    private final class JobsTimer extends AbstractAjaxTimerBehavior {

        private static final long serialVersionUID = 1L;

        JobsTimer() {
            super(REFRESH_INTERVAL);
        }

        @Override
        protected void onTimer(AjaxRequestTarget target) {
            repaint(target);
            if (!service().isWorking()) {
                stop(target);
                getComponent().remove(this);
            }
        }
    }
}
