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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.tileverse.storage.StorageConfig;

import io.tileverse.geoserver.web.storage.browser.BrowseException.Reason;

/**
 * Finds where a browser opens for a typed location: the folder of a file, a folder itself, or the folder before the
 * first glob character of a glob. An empty location, a {@code file:} URI or a bare path opens the local roots; another
 * location opens its bucket or container.
 */
final class BrowseStart {

    private static final String FILE_SCHEME = "file:";

    private BrowseStart() {}

    /**
     * @param location the typed location, trimmed here
     * @param storageParams the typed storage parameters, read for their {@code storage.provider}
     * @param localFiles the local folders open to the user
     * @throws BrowseException if the location names no bucket or container, is no URI, or no local folder is open
     */
    static StartingPoint of(String location, Properties storageParams, LocalFiles localFiles) {
        String typed = location == null ? "" : location.trim();
        if (isLocal(typed)) {
            return localStart(typed, localFiles);
        }
        String providerId = storageParams.getProperty(StorageConfig.PROVIDER_ID_KEY, "");
        return containerStart(typed, providerId);
    }

    /** An empty location, a {@code file:} URI, or a path: a scheme takes two letters at least, unlike a drive. */
    static boolean isLocal(String location) {
        if (location.isEmpty() || startsWithFileScheme(location)) {
            return true;
        }
        int colon = location.indexOf(':');
        return colon < 2 || !isScheme(location.substring(0, colon));
    }

    /**
     * Starts at the folder of the location when it lies under a root and the user may open it, as GeoServer's file
     * chooser does, and at the first root otherwise.
     */
    private static StartingPoint localStart(String location, LocalFiles localFiles) {
        List<File> roots = localFiles.roots();
        if (roots.isEmpty()) {
            throw new BrowseException(Reason.NO_LOCAL_ROOT);
        }
        Optional<File> folder = localFolderOf(location, localFiles.dataDirectory());
        Optional<StartingPoint> underRoots = folder.flatMap(found -> underRoots(found, roots, localFiles));
        return underRoots.orElseGet(() -> new StartingPoint(rootUri(roots.get(0)), "", roots));
    }

    private static Optional<StartingPoint> underRoots(File folder, List<File> roots, LocalFiles localFiles) {
        Path folderPath = onDisk(folder);
        File opened = folderPath.toFile();
        if (!localFiles.allows(opened)) {
            return Optional.empty();
        }
        Optional<File> root = deepestRootHolding(folderPath, roots);
        return root.map(found -> new StartingPoint(rootUri(found), folderKey(onDisk(found), folderPath), roots));
    }

    /** The data directory is a root and lies under a file system root: the deeper one wins. */
    private static Optional<File> deepestRootHolding(Path folderPath, List<File> roots) {
        File deepest = null;
        int deepestNameCount = -1;
        for (File root : roots) {
            Path rootPath = onDisk(root);
            if (folderPath.startsWith(rootPath) && rootPath.getNameCount() > deepestNameCount) {
                deepest = root;
                deepestNameCount = rootPath.getNameCount();
            }
        }
        return Optional.ofNullable(deepest);
    }

    /** The key of {@code folder} under {@code root}: its names joined by {@code /}, ending with {@code /}. */
    private static String folderKey(Path root, Path folder) {
        StringBuilder key = new StringBuilder();
        for (Path name : root.relativize(folder)) {
            String text = name.toString();
            if (!text.isEmpty()) {
                key.append(text).append('/');
            }
        }
        return key.toString();
    }

    /**
     * The path with each name spelled as on disk. A case-insensitive file system finds a path typed in another letter
     * case, and tileverse lists such a folder as empty. Each name is looked up on its own because Java 17 on macOS
     * keeps the typed letter case of a path resolved without following links. Links stay as they are, as GeoServer's
     * sandbox check leaves them.
     */
    private static Path onDisk(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        Path spelled = path.getRoot();
        for (Path name : path) {
            String typed = name.toString();
            Path candidate = spelled.resolve(typed);
            spelled = spelled.resolve(diskName(candidate, typed));
        }
        return spelled;
    }

    /**
     * The name of {@code candidate} as on disk when it differs from {@code typed} only by letter case, and
     * {@code typed} otherwise. A link keeps its typed name because its real path ends with the name of its target. A
     * missing element has no real path to read.
     */
    private static String diskName(Path candidate, String typed) {
        if (Files.isSymbolicLink(candidate)) {
            return typed;
        }
        try {
            return nameOf(candidate.toRealPath(), typed);
        } catch (IOException e) {
            return typed;
        }
    }

    /**
     * The last name of {@code realPath} when it differs from {@code typed} only by letter case, and {@code typed}
     * otherwise. A file system root has no name, and Windows resolves a junction to a drive root to that root.
     * Package-private for the tests.
     */
    static String nameOf(Path realPath, String typed) {
        Path last = realPath.getFileName();
        if (last == null) {
            return typed;
        }
        String realName = last.toString();
        return realName.equalsIgnoreCase(typed) ? realName : typed;
    }

    static URI rootUri(File root) {
        return root.toPath().toAbsolutePath().normalize().toUri();
    }

    /** The local folder named by {@code location}, or the nearest existing one above it; empty for no location. */
    private static Optional<File> localFolderOf(String location, File dataDirectory) {
        if (location.isEmpty()) {
            return Optional.empty();
        }
        String folderText = folderText(localPath(location));
        File folder = resolve(folderText, dataDirectory);
        if (!endsWithSeparator(folderText) && !folder.isDirectory()) {
            folder = folder.getParentFile();
        }
        return Optional.ofNullable(nearestExisting(folder));
    }

    /** The decoded path of a {@code file:} URI, or the location itself for a bare path. */
    private static String localPath(String location) {
        if (!startsWithFileScheme(location)) {
            return location;
        }
        String rest = location.substring(FILE_SCHEME.length());
        if (!rest.startsWith("/")) {
            return decodeQuietly(rest);
        }
        try {
            return Path.of(new URI(location)).toString();
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            return "/" + rest.replaceFirst("^/+", "");
        }
    }

    /** Decodes the text of a relative {@code file:} URL; a malformed escape leaves the text as typed. */
    private static String decodeQuietly(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return text;
        }
    }

    /** The folder text of a path: up to the separator before its first glob character, if any. */
    private static String folderText(String path) {
        int glob = GlobCharacters.firstIn(path);
        if (glob < 0) {
            return path;
        }
        int slash = path.lastIndexOf('/', glob);
        int separator = Math.max(slash, path.lastIndexOf(File.separatorChar, glob));
        if (separator < 0) {
            return "";
        }
        return path.substring(0, separator + 1);
    }

    private static File resolve(String folderText, File dataDirectory) {
        if (folderText.isEmpty()) {
            return dataDirectory;
        }
        File typed = new File(folderText);
        if (typed.isAbsolute()) {
            return typed;
        }
        return new File(dataDirectory, folderText);
    }

    private static File nearestExisting(File folder) {
        File existing = folder;
        while (existing != null && !existing.isDirectory()) {
            existing = existing.getParentFile();
        }
        return existing;
    }

    private static StartingPoint containerStart(String location, String providerId) {
        URI uri = parseUri(location);
        Optional<URI> root = ContainerRoots.rootOf(uri, providerId);
        if (root.isEmpty()) {
            throw new BrowseException(Reason.NO_CONTAINER, location);
        }
        String folderPath = folderPath(uri);
        String rootPath = root.get().getPath();
        String key = folderPath.startsWith(rootPath) ? folderPath.substring(rootPath.length()) : "";
        return new StartingPoint(root.get(), key, List.of());
    }

    /** A URI such as {@code s3://} fails to parse for its missing authority: it names no bucket. */
    private static URI parseUri(String location) {
        try {
            return new URI(location);
        } catch (URISyntaxException e) {
            if (location.endsWith("://")) {
                throw new BrowseException(Reason.NO_CONTAINER, location, e);
            }
            throw new BrowseException(Reason.INVALID_LOCATION, location, e);
        }
    }

    /** The decoded path of the folder of {@code uri}, ending with {@code /}. */
    private static String folderPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int glob = GlobCharacters.firstIn(path);
        if (glob < 0 && path.endsWith("/")) {
            return path;
        }
        int end = glob < 0 ? path.length() : glob;
        return path.substring(0, path.lastIndexOf('/', end - 1) + 1);
    }

    private static boolean endsWithSeparator(String text) {
        return text.endsWith("/") || text.endsWith(File.separator);
    }

    private static boolean startsWithFileScheme(String location) {
        return location.regionMatches(true, 0, FILE_SCHEME, 0, FILE_SCHEME.length());
    }

    private static boolean isScheme(String text) {
        if (!Character.isLetter(text.charAt(0))) {
            return false;
        }
        return text.chars().allMatch(BrowseStart::isSchemeCharacter);
    }

    private static boolean isSchemeCharacter(int character) {
        return Character.isLetterOrDigit(character) || "+-.".indexOf(character) >= 0;
    }
}
