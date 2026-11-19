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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.serialize.ISerializer;
import org.apache.wicket.util.tester.WicketTester;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.web.wicket.GSModalWindow;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.geoserver.web.storage.browser.BrowserTesters;
import io.tileverse.geoserver.web.storage.browser.ManualExecutor;

class StoreMigrationPanelTest {

    private static final String PANEL = "panel";
    private static final String ITEMS = PANEL + ":table:listContainer:items";
    private static final String STATUS_HEADER = PANEL + ":table:listContainer:sortableLinks:5:header:link";

    /** Holds the store "vectors" of type "Old", recognized by the fake migration. */
    private MigrationCatalogs catalogs;

    /** The migration offered by the service shown in the panel. */
    private FakeMigration migration;

    /** The service shown in the panel. */
    private StoreMigrations service;

    /** Renders the panel with its labels looked up as GeoServer does. */
    private WicketTester tester;

    @BeforeEach
    void setUp() {
        catalogs = MigrationCatalogs.withStores();
        migration = new FakeMigration();
        tester = BrowserTesters.labelledAsGeoServer();
    }

    @AfterEach
    void tearDown() {
        tester.destroy();
        MigrationCatalogs.clear();
    }

    @Test
    void showsARowPerStore() {
        render(Runnable::run);

        assertThat(tester.getLastResponseAsString())
                .contains("vectors")
                .contains(">ws<")
                .contains("Not checked");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selections")
    void buttonsFollowTheSelection(RowState state, boolean check, boolean migrate, boolean revert) {
        startService(Runnable::run);
        bringTo(state);
        renderService();

        selectTheRow();

        assertThat(isEnabled("check")).as("check").isEqualTo(check);
        assertThat(isEnabled("migrate")).as("migrate").isEqualTo(migrate);
        assertThat(isEnabled("revert")).as("revert").isEqualTo(revert);
        assertThat(isEnabled("discard")).as("discard").isEqualTo(revert);
    }

    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of(RowState.NOT_CHECKED, true, false, false),
                Arguments.of(RowState.READY, true, true, false),
                Arguments.of(RowState.BLOCKED, true, false, false),
                Arguments.of(RowState.MIGRATED, false, false, true));
    }

    @Test
    void noSelectionNoButtons() {
        render(Runnable::run);

        assertThat(isEnabled("check")).isFalse();
        assertThat(isEnabled("migrate")).isFalse();
    }

    @Test
    void checkUpdatesTheRow() {
        render(Runnable::run);
        selectTheRow();

        tester.clickLink(PANEL + ":check", true);

        assertThat(statusText()).isEqualTo("Ready");
    }

    @Test
    void differencesAskFirst() {
        startService(Runnable::run);
        migration.nextNotes(MigrationNote.difference("a.difference"));
        service.check(vectors());
        renderService();
        selectTheRow();

        tester.clickLink(PANEL + ":migrate", true);

        assertThat(dialog().isShown()).isTrue();
        assertThat(isMigrated()).isFalse();
        assertThat(tester.getLastResponseAsString()).contains("ws:vectors");
        tester.clickLink(dialogLink("confirm"), true);
        assertThat(isMigrated()).isTrue();
        assertThat(dialog().isShown()).isFalse();
    }

    @Test
    void cancelKeepsTheStore() {
        startService(Runnable::run);
        migrate();
        renderService();
        selectTheRow();
        tester.clickLink(PANEL + ":revert", true);

        tester.clickLink(dialogLink("cancel"), true);

        assertThat(dialog().isShown()).isFalse();
        assertThat(isMigrated()).isTrue();
    }

    @Test
    void revertWarnsOfLostEdits() {
        startService(Runnable::run);
        migrate();
        renderService();
        selectTheRow();

        tester.clickLink(PANEL + ":revert", true);

        assertThat(tester.getLastResponseAsString()).contains("ws:vectors").contains("are lost");
        tester.clickLink(dialogLink("confirm"), true);
        assertThat(isMigrated()).isFalse();
    }

    @Test
    void detailsMaskSecrets() {
        startService(Runnable::run);
        service.check(vectors());
        renderService();

        tester.clickLink(firstItem() + ":itemProperties:5:component:status", true);

        tester.assertVisible(PANEL + ":details");
        assertThat(tester.getLastResponseAsString())
                .contains(MigrationPlan.MASK)
                .doesNotContain("SK");
    }

    // The label of a note comes from the bundles; a note key without a label shows as is
    @Test
    void unknownNoteShowsItsKey() {
        startService(Runnable::run);
        migration.nextNotes(MigrationNote.blocker("unknown.blocker"));
        service.check(vectors());

        renderService();

        assertThat(statusText()).isEqualTo("Blocked: unknown.blocker");
    }

    @Test
    void migratedRowShowsItsDate() {
        startService(Runnable::run);
        migrate();

        renderService();

        assertThat(statusText()).matches("Migrated: \\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void refreshesWhileWorking() {
        ManualExecutor executor = new ManualExecutor();
        render(executor);
        selectTheRow();

        tester.clickLink(PANEL + ":check", true);

        assertThat(timers()).hasSize(1);
        executor.runAll();
        tester.executeBehavior(timers().get(0));
        assertThat(timers()).isEmpty();
        assertThat(statusText()).isEqualTo("Ready");
    }

    // An administrator coming back to the page sees the jobs still running
    @Test
    void returnRefreshesRunningJobs() {
        ManualExecutor executor = new ManualExecutor();
        startService(executor);
        service.check(vectors());

        renderService();

        assertThat(timers()).hasSize(1);
        assertThat(statusText()).isEqualTo("Working");
    }

    // The failure of a store gone is a label key, translated by the details
    @Test
    void goneStoreShowsItsLabel() {
        ManualExecutor executor = new ManualExecutor();
        render(executor);
        selectTheRow();
        tester.clickLink(PANEL + ":check", true);
        tester.clickLink(firstItem() + ":itemProperties:5:component:status", true);
        removeVectors();

        executor.runAll();
        tester.executeBehavior(timers().get(0));

        assertThat(tester.getLastResponseAsString()).contains("The store no longer exists");
    }

    // Sorted by status, a checked store moves below a ready one: the selection follows the store
    @Test
    void selectionFollowsTheStore() {
        ManualExecutor executor = new ManualExecutor();
        startService(executor);
        String bikes = addStore("bikes");
        service.check(List.of(bikes));
        executor.runAll();
        renderService();
        tester.clickLink(STATUS_HEADER, true);
        selectRow("vectors");

        tester.clickLink(PANEL + ":check", true);

        assertThat(storeOf(firstItem())).isEqualTo("bikes");
        assertThat(selectedStores()).containsExactly("vectors");
        assertThat(isEnabled("migrate")).isFalse();
    }

    // A German administrator searches the status as shown, not the name of the state
    @Test
    void filterMatchesShownStatus() {
        startService(Runnable::run);
        addStore("bikes");
        service.check(vectors());
        tester.getSession().setLocale(Locale.GERMAN);
        renderService();
        GeoServerDataProvider<MigrationRow> provider = migrationTable().getDataProvider();

        provider.setKeywords(new String[] {"Bereit"});

        assertThat(storesOf(provider)).containsExactly("vectors");
    }

    // The page goes to GeoServer's page store: the panel keeps no check result or details
    @Test
    void pageStaysSerializable() {
        startService(Runnable::run);
        service.check(vectors());
        renderService();
        tester.clickLink(firstItem() + ":itemProperties:5:component:status", true);

        ISerializer serializer = tester.getApplication().getFrameworkSettings().getSerializer();
        Page page = tester.getLastRenderedPage();
        byte[] stored = serializer.serialize(page);

        assertThat(stored).isNotNull();
    }

    private void render(Executor executor) {
        startService(executor);
        renderService();
    }

    private void startService(Executor executor) {
        StoreMigrator migrator = new StoreMigrator(catalogs.catalog(), BackupCipher.NONE, Clock.systemUTC());
        MigrationJobs jobs = new MigrationJobs(executor);
        service = new StoreMigrations(catalogs.catalog(), List.of(migration), migrator, jobs);
    }

    private void renderService() {
        tester.startComponentInPage(new StoreMigrationPanel(PANEL, new ServiceModel(service)));
    }

    private void bringTo(RowState state) {
        switch (state) {
            case NOT_CHECKED -> {
                // a new service has checked nothing
            }
            case READY -> service.check(vectors());
            case BLOCKED -> {
                migration.nextNotes(MigrationNote.blocker("a.blocker"));
                service.check(vectors());
            }
            case MIGRATED -> migrate();
            default -> throw new IllegalArgumentException("no setup for " + state);
        }
    }

    private void migrate() {
        service.check(vectors());
        service.migrate(vectors());
    }

    /** Ticks the box of the only row, as a click of the administrator does. */
    private void selectTheRow() {
        selectRow("vectors");
    }

    private void selectRow(String store) {
        String row = itemOf(store);
        CheckBox box = (CheckBox) tester.getComponentFromLastRenderedPage(row + ":selectItemContainer:selectItem");
        tester.getRequest().getPostParameters().setParameterValue(box.getInputName(), "true");
        tester.executeAjaxEvent(box, "click");
    }

    /** The path of the row of a store. */
    private String itemOf(String store) {
        MarkupContainer items = (MarkupContainer) tester.getComponentFromLastRenderedPage(ITEMS);
        for (Component item : items) {
            String path = item.getPageRelativePath();
            if (store.equals(storeOf(path))) {
                return path;
            }
        }
        throw new AssertionError("no row of " + store);
    }

    private String storeOf(String item) {
        MigrationRow row =
                (MigrationRow) tester.getComponentFromLastRenderedPage(item).getDefaultModelObject();
        return row.store();
    }

    @SuppressWarnings("unchecked")
    private GeoServerTablePanel<MigrationRow> migrationTable() {
        return (GeoServerTablePanel<MigrationRow>) tester.getComponentFromLastRenderedPage(PANEL + ":table");
    }

    /** The stores of the rows kept by the filter of the provider, in order. */
    private static List<String> storesOf(GeoServerDataProvider<MigrationRow> provider) {
        List<String> stores = new ArrayList<>();
        Iterator<? extends MigrationRow> rows = provider.iterator(0, provider.size());
        rows.forEachRemaining(row -> stores.add(row.store()));
        return stores;
    }

    private List<String> selectedStores() {
        GeoServerTablePanel<?> table = migrationTable();
        List<?> selection = table.getSelection();
        return selection.stream().map(row -> ((MigrationRow) row).store()).toList();
    }

    /** The path of the first row. The table builds new rows on each repaint, with new ids. */
    private String firstItem() {
        MarkupContainer items = (MarkupContainer) tester.getComponentFromLastRenderedPage(ITEMS);
        Component first = items.iterator().next();
        return first.getPageRelativePath();
    }

    private String statusText() {
        Component label =
                tester.getComponentFromLastRenderedPage(firstItem() + ":itemProperties:5:component:status:label");
        return label.getDefaultModelObjectAsString();
    }

    private boolean isEnabled(String button) {
        return tester.getComponentFromLastRenderedPage(PANEL + ":" + button).isEnabled();
    }

    private GSModalWindow dialog() {
        return (GSModalWindow) tester.getComponentFromLastRenderedPage(PANEL + ":dialog");
    }

    /** The path of a link of the dialog content, nested by Wicket's modal dialog in containers of its own. */
    private String dialogLink(String id) {
        List<String> found = new ArrayList<>();
        dialog().visitChildren(AjaxLink.class, (AjaxLink<?> link, IVisit<Void> visit) -> {
            if (id.equals(link.getId())) {
                found.add(link.getPageRelativePath());
                visit.stop();
            }
        });
        return found.get(0);
    }

    private List<AbstractAjaxTimerBehavior> timers() {
        Component panel = tester.getComponentFromLastRenderedPage(PANEL);
        return panel.getBehaviors(AbstractAjaxTimerBehavior.class);
    }

    private boolean isMigrated() {
        return MigrationMarker.isOn(catalogs.vectors());
    }

    private void removeVectors() {
        Catalog catalog = catalogs.catalog();
        catalog.remove(catalogs.roads());
        catalog.remove(catalogs.vectors());
    }

    /** Adds a second store recognized by the fake migration, and answers its id. */
    private String addStore(String name) {
        Catalog catalog = catalogs.catalog();
        DataStoreInfo store = catalog.getFactory().createDataStore();
        store.setName(name);
        store.setType("Old");
        store.setWorkspace(catalog.getWorkspaceByName(MigrationCatalogs.WORKSPACE));
        store.setEnabled(true);
        store.getConnectionParameters().put("uri", "file:/d/" + name + ".parquet");
        catalog.add(store);
        return catalog.getDataStoreByName(MigrationCatalogs.WORKSPACE, name).getId();
    }

    private List<String> vectors() {
        return List.of(catalogs.vectors().getId());
    }

    /** Serializes without the service: the service stays with the test. */
    private static final class ServiceModel extends LoadableDetachableModel<StoreMigrations> {

        private static final long serialVersionUID = 1L;

        /** Transient: a serialized page leaves the service out. */
        private final transient StoreMigrations service;

        ServiceModel(StoreMigrations service) {
            super(service);
            this.service = service;
        }

        @Override
        protected StoreMigrations load() {
            return service;
        }
    }
}
