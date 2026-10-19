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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AjaxRequestHandler;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.WicketTester;
import org.apache.wicket.util.visit.IVisit;
import org.geoserver.web.wicket.GSModalWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

class StorageLocationFieldTest {

    private static final StorageBrowsing FILES_OR_FOLDERS =
            StorageBrowsing.of(PickMode.FILE_OR_FOLDER, FileColumns.NONE);
    private static final String TEXT = "field:border:border_body:paramValue";
    private static final String BROWSE = "form:field:border:border_body:browse";

    @TempDir
    Path folder;

    private final IModel<String> location = new Model<>();
    private WicketTester tester;

    @BeforeEach
    void startTester() {
        GeoServerFixture.sandboxedTo(folder, folder);
        tester = BrowserTesters.inFormLabelledAsGeoServer();
    }

    @AfterEach
    void stopTester() {
        tester.destroy();
        GeoServerFixture.clear();
    }

    @Test
    void browseOpensTheTypedFolder() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        browse(folder.resolve("scenes").toString());

        assertThat(dialog().isShown()).isTrue();
        assertThat(browserPanels()).hasSize(1);
        assertThat(location.getObject()).isNull();
    }

    @Test
    void aChoiceWritesTheUri() throws IOException {
        Path file = createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        int openBefore = StorageBrowserSessions.shared().size();
        browse(folder.resolve("scenes").toString());

        click("a.tif");

        assertThat(location.getObject()).isEqualTo(file.toUri().toString());
        assertThat(dialog().isShown()).isFalse();
        assertThat(StorageBrowserSessions.shared().size()).isEqualTo(openBefore);
    }

    @Test
    void localPathsWriteAPath() throws IOException {
        Path file = createFiles("my dir/a#b%.tif");
        render(FILES_OR_FOLDERS.withLocalPaths(), () -> Optional.of(new Properties()));
        browse(file.getParent().toString());

        click("a#b%.tif");

        assertThat(location.getObject()).isEqualTo(file.toString());
    }

    @Test
    void aFolderPathEndsWithTheSeparator() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS.withLocalPaths(), () -> Optional.of(new Properties()));
        browse(folder.resolve("scenes").toString());

        tester.clickLink(browserPath() + ":content:chooseFolder", true);

        assertThat(location.getObject()).isEqualTo(folder.resolve("scenes") + File.separator);
    }

    // a file system root already ends with the separator
    @Test
    void aRootChoiceKeepsOneSeparator() {
        render(FILES_OR_FOLDERS.withLocalPaths(), () -> Optional.of(new Properties()));
        URI root = URI.create("file:///");

        choose(root);

        assertThat(location.getObject()).isEqualTo(Path.of(root).toString());
    }

    @Test
    void noContainerShowsAMessage() {
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        browse("s3://");

        assertThat(dialog().isShown()).isFalse();
        assertThat(tester.getLastResponseAsString())
                .contains("Type a location naming a bucket or container to browse it.");
    }

    @Test
    void aFailedConversionKeepsItClosed() {
        render(FILES_OR_FOLDERS, Optional::empty);

        browse(folder.toString());

        assertThat(dialog().isShown()).isFalse();
    }

    @Test
    void trimsTheTypedLocation() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        browse("  " + folder.resolve("scenes") + "  ");

        assertThat(linkLabelled("a.tif")).isPresent();
    }

    @Test
    void cancelClosesTheBrowser() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        int openBefore = StorageBrowserSessions.shared().size();
        browse(folder.resolve("scenes").toString());
        assertThat(StorageBrowserSessions.shared().size()).isEqualTo(openBefore + 1);

        tester.clickLink(browserPath() + ":content:cancel", true);

        assertThat(dialog().isShown()).isFalse();
        assertThat(StorageBrowserSessions.shared().size()).isEqualTo(openBefore);
    }

    // The model already holds the choice and does not change: only clearing the typed input shows the choice
    @Test
    void aChoiceReplacesTheTypedText() throws IOException {
        Path file = createFiles("scenes/a.tif");
        location.setObject(file.toUri().toString());
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        browse(folder.resolve("scenes").toString());

        click("a.tif");

        tester.assertComponentOnAjaxResponse("form:" + TEXT);
        assertThat(textField().getValue()).isEqualTo(file.toUri().toString());
    }

    @Test
    void aRemoteChoiceKeepsItsUri() {
        render(FILES_OR_FOLDERS.withLocalPaths(), () -> Optional.of(new Properties()));

        choose(URI.create("s3://bucket/scenes/a.tif"));

        assertThat(location.getObject()).isEqualTo("s3://bucket/scenes/a.tif");
    }

    @Test
    void theDialogIsTitledByTheLabel() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        browse(folder.resolve("scenes").toString());

        assertThat(dialog().getTitle().getObject()).isEqualTo("URL");
    }

    // The store pages put the tooltip on the tag of the field, an ancestor of the dialog
    @Test
    void tooltipStaysOutOfTheDialog() throws Exception {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        addTooltip("Where the data is");

        browse(folder.resolve("scenes").toString());
        tester.startPage(tester.getLastRenderedPage());

        assertThat(valueInMarkup("count(//div[@class='wicket-modal'])")).isEqualTo("1");
        assertThat(valueInMarkup(tooltipOf("input[@class='longtext']"))).isEqualTo("Where the data is");
        assertThat(valueInMarkup(tooltipOf("div[@class='wicket-modal']"))).isEmpty();
    }

    // A slow listing invites a second click before the dialog shows
    @Test
    void aSecondBrowseClosesTheFirst() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        int openBefore = StorageBrowserSessions.shared().size();

        browse(folder.resolve("scenes").toString());
        browse(folder.resolve("scenes").toString());

        assertThat(StorageBrowserSessions.shared().size()).isEqualTo(openBefore + 1);
    }

    @Test
    void aFailedReadRepaintsFeedback() {
        render(FILES_OR_FOLDERS, Optional::empty);

        browse(folder.toString());

        tester.assertComponentOnAjaxResponse("form:field:feedback");
    }

    // Some store pages build a feedback panel without a markup id: a target cannot repaint it
    @Test
    void feedbackWithoutAnIdIsSkipped() {
        render(FILES_OR_FOLDERS, Optional::empty);
        addHiddenFeedbackWithoutId();

        browse(folder.toString());

        assertThat(dialog().isShown()).isFalse();
    }

    @Test
    void theButtonIsTranslated() {
        tester.getSession().setLocale(Locale.FRENCH);

        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        assertThat(tester.getLastResponseAsString()).contains("Parcourir...");
    }

    @Test
    void aRequiredFieldIsMarked() {
        render(true, FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        assertThat(textField().isRequired()).isTrue();
        assertThat(tester.getLastResponseAsString()).contains("URL *");
    }

    @Test
    void aMissingValueNamesTheField() {
        render(true, FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        tester.newFormTester("form", false).submit();

        assertThat(tester.getMessages(FeedbackMessage.ERROR))
                .anyMatch(message -> message.toString().contains("'URL'"));
    }

    // GeoServer's stylesheet grows only the last child of a form group
    @Test
    void inputGroupIsInTheLastChild() throws Exception {
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        String inputGroupInLastChild = "//div[@class='gs-form-group']/*[last()]//div[@class='gs-input-group']";
        assertThat(valueInMarkup("count(" + inputGroupInLastChild + ")")).isEqualTo("1");
    }

    // GeoServer's FileInput gives its text field a size of 100
    @Test
    void inputHasFileInputSize() throws Exception {
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));

        assertThat(valueInMarkup("//input[@class='longtext']/@size")).isEqualTo("100");
    }

    // The page goes to GeoServer's page store: the field and its open dialog must serialize
    @Test
    void pageStaysSerializable() throws IOException {
        createFiles("scenes/a.tif");
        render(FILES_OR_FOLDERS, () -> Optional.of(new Properties()));
        browse(folder.resolve("scenes").toString());

        byte[] stored =
                tester.getApplication().getFrameworkSettings().getSerializer().serialize(tester.getLastRenderedPage());

        assertThat(stored).isNotNull();
    }

    private void render(StorageBrowsing browsing, TypedStorageParameters typed) {
        render(false, browsing, typed);
    }

    private void render(boolean required, StorageBrowsing browsing, TypedStorageParameters typed) {
        Form<Void> form = new Form<>("form");
        form.add(new StorageLocationField("field", location, Model.of("URL"), required, browsing, typed));
        tester.startComponentInPage(form);
    }

    private void browse(String typed) {
        FormTester form = tester.newFormTester("form", false);
        form.setValue(TEXT, typed);
        tester.executeAjaxEvent(BROWSE, "click");
    }

    private FormComponent<?> textField() {
        return (FormComponent<?>) tester.getComponentFromLastRenderedPage("form:" + TEXT);
    }

    /** Gives the field a tooltip as GeoServer's parameter list does: a title on the tag of the field. */
    private void addTooltip(String tooltip) {
        Component field = tester.getComponentFromLastRenderedPage("form:field");
        field.add(AttributeModifier.replace("title", tooltip));
    }

    /**
     * The value of an XPath expression over the markup of the last response. The parser is not namespace-aware: the
     * rendered page uses the {@code wicket:} prefix without declaring it, and a namespace-aware parse fails because the
     * prefix "wicket" is not bound.
     */
    private String valueInMarkup(String expression) throws Exception {
        String markup = tester.getLastResponseAsString();
        InputSource source = new InputSource(new StringReader(markup));
        DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = parser.parse(source);
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.evaluate(expression, document);
    }

    /** The tooltip shown by a browser for an element: its own title, or the title of its nearest ancestor with one. */
    private static String tooltipOf(String element) {
        return "//" + element + "/ancestor-or-self::*[@title][1]/@title";
    }

    /** What the dialog sends up when the user picks a row. */
    private void choose(URI chosen) {
        Page page = tester.getLastRenderedPage();
        StorageLocationField field = (StorageLocationField) page.get("form:field");
        field.send(field, Broadcast.EXACT, new LocationChosen(chosen, new AjaxRequestHandler(page)));
    }

    /** Hidden, as the markup of the test page has no tag for it. */
    private void addHiddenFeedbackWithoutId() {
        FeedbackPanel withoutId = new FeedbackPanel("withoutId");
        withoutId.setVisible(false);
        MarkupContainer form = (MarkupContainer) tester.getLastRenderedPage().get("form");
        form.add(withoutId);
    }

    private GSModalWindow dialog() {
        return (GSModalWindow) tester.getComponentFromLastRenderedPage("form:field:dialog");
    }

    private List<StorageBrowserPanel> browserPanels() {
        List<StorageBrowserPanel> found = new ArrayList<>();
        tester.getLastRenderedPage()
                .visitChildren(
                        StorageBrowserPanel.class, (StorageBrowserPanel panel, IVisit<Void> visit) -> found.add(panel));
        return found;
    }

    private String browserPath() {
        return browserPanels().get(0).getPageRelativePath();
    }

    private void click(String label) {
        EventLink link = linkLabelled(label).orElseThrow(() -> new AssertionError("no link " + label));
        tester.executeAjaxEvent(link.get("link"), "click");
    }

    private Optional<EventLink> linkLabelled(String label) {
        List<EventLink> found = new ArrayList<>();
        tester.getLastRenderedPage().visitChildren(EventLink.class, (EventLink link, IVisit<Void> visit) -> {
            if (label.equals(link.getDefaultModelObject())) {
                found.add(link);
            }
        });
        return found.stream().findFirst();
    }

    private Path createFiles(String... relativePaths) throws IOException {
        Path last = null;
        for (String relativePath : relativePaths) {
            last = folder.resolve(relativePath);
            Files.createDirectories(last.getParent());
            Files.writeString(last, relativePath);
        }
        return last;
    }
}
