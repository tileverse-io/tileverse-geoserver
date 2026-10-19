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

import java.io.Closeable;
import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import io.tileverse.storage.AccessDeniedException;
import io.tileverse.storage.NotFoundException;
import io.tileverse.storage.StorageEntry;
import io.tileverse.storage.UnsupportedCapabilityException;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

/**
 * The browsing logic of one dialog, with no Wicket in it: the storage opened from the typed location and storage
 * parameters, the folder shown and its listings, the choices, the format reads of the rows shown, and the sort by a
 * format column. The dialog's requests call it in turn; the reads finish on the shared pool.
 *
 * <p>A browser opens only what it listed itself: the root, the folder shown, its ancestors, and the folders and files
 * of its listings. Local files also pass the sandbox check of GeoServer, folder by folder and choice by choice.
 */
public final class StorageBrowser implements Closeable {

    private static final String CACHING_PREFIX = "storage.caching.";
    private static final String FILE_PREFIX = "storage.file.";

    private final StorageBrowsing browsing;
    private final Properties storageParams;
    private final BrowserServices services;
    private final FolderLister lister;
    private final FormatReads reads;
    private final List<File> localRoots;

    /** Guarded by this, as are the fields below. */
    private OpenStorage storage;

    private FolderListing listing;

    /** The last search over a truncated listing, kept with its prefix until another folder opens; null without one. */
    private FolderListing searchListing;

    private String searchPrefix;

    /** The sort by a format column being read or done; null without one. */
    private FormatSort sort;

    private boolean closed;

    /** The entries of the listings, readable without the lock of the browser. */
    private volatile int heldEntries;

    private StorageBrowser(
            StorageBrowsing browsing, Properties storageParams, BrowserServices services, StartingPoint start) {
        this.browsing = browsing;
        this.storageParams = storageParams;
        this.services = services;
        this.lister = new FolderLister(services.limits().listedEntries());
        int readsPerBrowser = services.limits().readsPerBrowser();
        this.reads = new FormatReads(
                browsing.columns(), services.loader(), services.cache(), storageParams, readsPerBrowser);
        this.localRoots = start.localRoots();
        this.storage = openStorage(start.root());
        this.listing = FolderListing.empty(start.folderKey());
        this.reads.reset(storage);
    }

    /**
     * Opens a browser over the storage named by a typed location; its folder lists on {@link #openFolder}. The caching
     * parameters are left out: they tune the reads of the store, not browsing. Local browsing keeps the
     * {@code storage.file.*} parameters only: a provider named for another backend would match no {@code file:} root.
     *
     * @throws BrowseException if the location names nothing to browse, or the storage cannot be opened
     */
    static StorageBrowser open(
            String location, Properties typedParams, StorageBrowsing browsing, BrowserServices services) {
        Properties withoutCaching = withoutCachingParameters(typedParams);
        StartingPoint start = BrowseStart.of(location, withoutCaching, services.localFiles());
        Properties openingParams = start.local() ? fileParameters(withoutCaching) : withoutCaching;
        return new StorageBrowser(browsing, openingParams, services, start);
    }

    public StorageBrowsing browsing() {
        return browsing;
    }

    /** Whether the browser lists local files, under one of {@link #localRoots()}. */
    public boolean local() {
        return !localRoots.isEmpty();
    }

    public List<File> localRoots() {
        return localRoots;
    }

    /** The local root shown, or empty on object storage. */
    public synchronized Optional<File> localRoot() {
        if (!local()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(storage.root()).toFile());
    }

    /** The root of the open storage, such as {@code s3://bucket/}. */
    public synchronized URI root() {
        return storage.root();
    }

    /** The key of the folder shown: empty for the root, ending with {@code /} otherwise. */
    public synchronized String folder() {
        return listing.folderKey();
    }

    /** The root, then the folders down to the one shown. */
    public synchronized List<Crumb> breadcrumbs() {
        List<Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Crumb(rootLabel(), ""));
        StringBuilder key = new StringBuilder();
        for (String segment : folder().split("/")) {
            if (!segment.isEmpty()) {
                key.append(segment).append('/');
                crumbs.add(new Crumb(segment, key.toString()));
            }
        }
        return crumbs;
    }

    public synchronized List<BrowserEntry> rows() {
        return listing.rows();
    }

    /** Whether the listing of the folder shown stopped at {@link #listingLimit()}. */
    public synchronized boolean truncated() {
        return listing.truncated();
    }

    public int listingLimit() {
        return services.limits().listedEntries();
    }

    /** The listed entries held by this browser, bounded together for the browsers of the server. */
    public int heldEntries() {
        return heldEntries;
    }

    /**
     * Lists {@code key} and shows it: the root, the folder shown, one of its ancestors, or a folder of its listings.
     *
     * @throws BrowseException if the browser was closed, or the folder is none of those, lies outside the sandbox,
     *     holds a glob character in its name, or cannot be listed
     */
    public synchronized void openFolder(String key) {
        requireNotClosed();
        requireOpenable(key);
        FolderListing opened = list(key);
        listing = opened;
        clearSearch();
        sort = null;
        reads.reset(storage);
        countHeldEntries();
    }

    /** Lists the folder shown again, and reads its values again. */
    public synchronized void refresh() {
        openFolder(folder());
    }

    /** Opens the local root at {@code index} of {@link #localRoots()}, at its top folder. */
    public synchronized void openRoot(int index) {
        requireNotClosed();
        if (index < 0 || index >= localRoots.size()) {
            throw new BrowseException(Reason.FORBIDDEN, Integer.toString(index));
        }
        File root = localRoots.get(index);
        requireAllowed(root);
        OpenStorage opened = openStorage(BrowseStart.rootUri(root));
        storage.close();
        storage = opened;
        listing = FolderListing.empty("");
        clearSearch();
        countHeldEntries();
        reads.reset(storage);
        openFolder("");
    }

    /**
     * The rows of the folder shown with names starting with {@code prefix}, in the same letter case, listed again: over
     * a truncated listing, a search must reach the entries past its limit. The last search stays until another folder
     * opens.
     */
    public synchronized List<BrowserEntry> search(String prefix) {
        requireNotClosed();
        if (searchListing == null || !prefix.equals(searchPrefix)) {
            searchListing = searchOf(prefix);
            searchPrefix = prefix;
            countHeldEntries();
        }
        return searchListing.rows();
    }

    /** The location of the file {@code key}, listed in the folder shown, for a store picking files. */
    public synchronized URI chooseFile(String key) {
        requireNotClosed();
        if (!browsing.pickMode().allowsFiles()) {
            throw new BrowseException(Reason.NOT_PICKABLE);
        }
        requireSafeKey(key);
        if (listedFile(key).isEmpty()) {
            throw new BrowseException(Reason.FORBIDDEN, key);
        }
        return chosen(key);
    }

    /** The location of the folder shown, ending with {@code /}, for a store picking folders. */
    public synchronized URI chooseFolder() {
        requireNotClosed();
        if (!browsing.pickMode().allowsFolders()) {
            throw new BrowseException(Reason.NOT_PICKABLE);
        }
        String shown = folder();
        requireListableFolder(shown);
        return chosen(shown);
    }

    /**
     * Queues the format reads of the rows shown by the page, and cancels the queued reads of the rows no longer shown.
     */
    public synchronized void showRows(List<BrowserEntry> shown) {
        reads.show(listedFiles(shown));
    }

    /** Whether the format columns read {@code row}: a file of their format. */
    public boolean reads(BrowserEntry row) {
        return !row.folder() && browsing.columns().reads(row.name());
    }

    /** The format values of {@code row}, once read. */
    public Optional<FileDetails> details(BrowserEntry row) {
        return reads.details(row.key());
    }

    /**
     * Starts the reads needed to sort {@code rows} by a format column, for the file rows read by the columns and still
     * missing a value. No read starts if they exceed the limit of a sort ({@link SortProgress#tooMany()}), and a later
     * call counts them again. The rows can sort once {@link SortProgress#complete()} is true. Another column or other
     * rows start the sort over and keep the values already read.
     */
    public synchronized SortProgress sortBy(FileColumn column, List<BrowserEntry> rows) {
        List<StorageEntry.File> files = filesRead(rows);
        Set<String> keys = keysOf(files);
        if (startsOver(column, keys)) {
            sort = startSort(column, files, keys);
        }
        return sort.progress(reads, sortLimit());
    }

    /** The progress of the sort by a format column, or empty without one. */
    public synchronized Optional<SortProgress> sortProgress() {
        if (sort == null) {
            return Optional.empty();
        }
        return Optional.of(sort.progress(reads, sortLimit()));
    }

    /** Cancels the queued reads of the sort; the values read stay. */
    public synchronized void cancelSort() {
        reads.cancelSortReads();
        sort = null;
    }

    /**
     * The order of a sort by {@code column}: folders first, then the files with a value, then those without one. The
     * direction applies to the values only; ties keep the name order.
     */
    public Comparator<BrowserEntry> formatOrder(FileColumn column, boolean ascending) {
        return BrowserOrder.byValues(column, ascending, row -> valueOf(row, column));
    }

    /**
     * Cancels the queued reads; the storage closes once the running ones end. Afterwards, {@link #openFolder},
     * {@link #refresh}, {@link #openRoot}, {@link #search}, {@link #chooseFile} and {@link #chooseFolder} fail as
     * {@link Reason#EXPIRED}. A request racing the close of an idle browser meets that failure.
     */
    @Override
    public synchronized void close() {
        closed = true;
        reads.close();
        storage.close();
    }

    private void requireNotClosed() {
        if (closed) {
            throw new BrowseException(Reason.EXPIRED);
        }
    }

    private void requireOpenable(String key) {
        if (!isKnownFolder(key)) {
            throw new BrowseException(Reason.FORBIDDEN, key);
        }
        requireListableFolder(key);
        if (local()) {
            requireAllowed(localFile(key));
        }
    }

    /** The root, the folder shown, one of its ancestors, or a folder of its listings. */
    private boolean isKnownFolder(String key) {
        String shown = folder();
        boolean ancestor = key.endsWith("/") && shown.startsWith(key);
        boolean searched = searchListing != null && searchListing.hasFolder(key);
        return key.isEmpty() || key.equals(shown) || ancestor || listing.hasFolder(key) || searched;
    }

    /**
     * The key rules for a folder: no dot segment, and no glob character because a listing would read the key as a
     * pattern. They apply to a folder listed, searched or chosen, including a typed start folder not listed yet.
     */
    private static void requireListableFolder(String key) {
        requireSafeKey(key);
        if (GlobCharacters.firstIn(key) >= 0) {
            throw new BrowseException(Reason.UNLISTABLE_NAME, BrowserEntry.nameOf(key));
        }
    }

    /** A key names a row or an ancestor: a {@code .} or {@code ..} segment, even percent-encoded, names neither. */
    private static void requireSafeKey(String key) {
        if (key.startsWith("/")) {
            throw new BrowseException(Reason.FORBIDDEN, key);
        }
        for (String segment : key.split("/")) {
            String decoded = decodeQuietly(segment);
            if (".".equals(decoded) || "..".equals(decoded)) {
                throw new BrowseException(Reason.FORBIDDEN, key);
            }
        }
    }

    private static String decodeQuietly(String segment) {
        try {
            return URLDecoder.decode(segment, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return segment;
        }
    }

    private FolderListing list(String key) {
        requireListing();
        try {
            return lister.list(storage.storage(), key);
        } catch (RuntimeException e) {
            throw failureOf(e, key);
        }
    }

    private FolderListing searchOf(String prefix) {
        requireListableFolder(folder());
        requireListing();
        try {
            return lister.search(storage.storage(), folder(), prefix);
        } catch (RuntimeException e) {
            throw failureOf(e, folder());
        }
    }

    private void requireListing() {
        if (!storage.storage().capabilities().list()) {
            throw new BrowseException(Reason.CANNOT_LIST);
        }
    }

    private BrowseException failureOf(RuntimeException failure, String key) {
        if (failure instanceof UnsupportedCapabilityException) {
            return new BrowseException(Reason.CANNOT_LIST, "", failure);
        }
        if (failure instanceof AccessDeniedException) {
            return new BrowseException(Reason.ACCESS_DENIED, "", failure);
        }
        if (failure instanceof NotFoundException) {
            return new BrowseException(Reason.NOT_FOUND, locationOf(key).toString(), failure);
        }
        return new BrowseException(Reason.FAILED, FailureMessages.of(failure, storageParams), failure);
    }

    private void clearSearch() {
        searchListing = null;
        searchPrefix = null;
    }

    private void countHeldEntries() {
        int searched = searchListing == null ? 0 : searchListing.size();
        heldEntries = listing.size() + searched;
    }

    private URI chosen(String key) {
        if (local()) {
            requireAllowed(localFile(key));
        }
        return locationOf(key);
    }

    private URI locationOf(String key) {
        return ChosenLocations.of(storage.root(), key, local());
    }

    private void requireAllowed(File file) {
        if (!services.localFiles().allows(file)) {
            throw new BrowseException(Reason.FORBIDDEN, file.getPath());
        }
    }

    private File localFile(String key) {
        return Path.of(storage.root()).resolve(key).toFile();
    }

    private String rootLabel() {
        if (local()) {
            return Path.of(storage.root()).toString();
        }
        return storage.root().toString();
    }

    private List<StorageEntry.File> listedFiles(List<BrowserEntry> rows) {
        List<StorageEntry.File> files = new ArrayList<>();
        for (BrowserEntry row : rows) {
            listedFile(row.key()).ifPresent(files::add);
        }
        return files;
    }

    private Optional<StorageEntry.File> listedFile(String key) {
        Optional<StorageEntry.File> file = listing.file(key);
        if (file.isEmpty() && searchListing != null) {
            return searchListing.file(key);
        }
        return file;
    }

    /** A sort found too many starts over on each call: the rows shown meanwhile may have read some of its values. */
    private boolean startsOver(FileColumn column, Set<String> keys) {
        return sort == null || sort.tooMany() || !sort.sorts(column, keys);
    }

    private FormatSort startSort(FileColumn column, List<StorageEntry.File> files, Set<String> keys) {
        reads.cancelSortReads();
        boolean tooMany = reads.missingCount(files) > sortLimit();
        if (!tooMany) {
            reads.queueForSort(files);
        }
        return new FormatSort(column, keys, tooMany);
    }

    private int sortLimit() {
        return services.limits().sortReads();
    }

    private List<StorageEntry.File> filesRead(List<BrowserEntry> rows) {
        List<StorageEntry.File> files = new ArrayList<>();
        for (BrowserEntry row : rows) {
            if (reads(row)) {
                listedFile(row.key()).ifPresent(files::add);
            }
        }
        return files;
    }

    private static Set<String> keysOf(List<StorageEntry.File> files) {
        Set<String> keys = new HashSet<>();
        for (StorageEntry.File file : files) {
            keys.add(file.key());
        }
        return keys;
    }

    private Optional<ColumnValue> valueOf(BrowserEntry row, FileColumn column) {
        Optional<FileDetails> details = reads.details(row.key());
        if (details.isPresent() && details.get() instanceof FileDetails.Values values) {
            ColumnValue value = values.byColumn().get(column.key());
            return Optional.ofNullable(value);
        }
        return Optional.empty();
    }

    private OpenStorage openStorage(URI root) {
        try {
            return new OpenStorage(services.opener().open(root, storageParams));
        } catch (RuntimeException e) {
            throw new BrowseException(Reason.CANNOT_OPEN, FailureMessages.of(e, storageParams), e);
        }
    }

    private static Properties withoutCachingParameters(Properties typed) {
        Properties kept = new Properties();
        for (String key : typed.stringPropertyNames()) {
            if (!key.startsWith(CACHING_PREFIX)) {
                kept.setProperty(key, typed.getProperty(key));
            }
        }
        return kept;
    }

    private static Properties fileParameters(Properties typed) {
        Properties kept = new Properties();
        for (String key : typed.stringPropertyNames()) {
            if (key.startsWith(FILE_PREFIX)) {
                kept.setProperty(key, typed.getProperty(key));
            }
        }
        return kept;
    }
}
