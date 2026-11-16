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
package io.tileverse.geoserver.parquetry.migration;

import java.util.List;

import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

/**
 * The layer names published by the community GeoParquet store. The community store turns the name of each dataset into
 * a valid XML name; the migration needs the same names to match the layers of a migrated store.
 */
final class CommunityNames {

    private static final String PLACEHOLDER = "_";

    private static final String PARQUET_EXTENSION = ".parquet";

    private CommunityNames() {}

    /**
     * The layer name of a file read through a URI with a query string. The community store appends the query string, as
     * written in the URI, to the file name with its extension, and applies {@link #layerName(String)} to the result.
     */
    static String layerNameWithQuery(String typeName, String writtenQuery) {
        String fileName = typeName + PARQUET_EXTENSION;
        return layerName(fileName + "?" + writtenQuery);
    }

    /**
     * Applies the community rule: invalid characters become {@code _}, runs of {@code _} and {@code -} collapse to one
     * {@code _}, a name not starting with a letter or {@code _} gets a {@code _} prefix, and one trailing {@code _} is
     * dropped.
     */
    static String layerName(String typeName) {
        String trimmed = typeName.trim();
        String replaced = replaceInvalidCharacters(trimmed);
        String collapsed = collapseSeparators(replaced);
        if (collapsed.isEmpty() || collapsed.equals(PLACEHOLDER)) {
            return PLACEHOLDER;
        }
        String prefixed = prefixInvalidStart(collapsed);
        return dropTrailingUnderscore(prefixed);
    }

    /**
     * The native name as shown on the page, with the given secrets masked. A secret from the query of a uri appears in
     * a layer name in the form given by the community rule: the name is matched against that form.
     */
    static String shownName(String nativeName, List<String> writtenSecrets) {
        String shown = nativeName;
        for (String secret : writtenSecrets) {
            String communityForm = communityForm(secret);
            if (!communityForm.isEmpty()) {
                shown = shown.replace(communityForm, MigrationPlan.MASK);
            }
        }
        return shown;
    }

    private static String replaceInvalidCharacters(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.-]", PLACEHOLDER);
    }

    private static String collapseSeparators(String name) {
        return name.replaceAll("[_-]+", PLACEHOLDER);
    }

    private static String prefixInvalidStart(String name) {
        char first = name.charAt(0);
        if (Character.isLetter(first) || first == '_') {
            return name;
        }
        return PLACEHOLDER + name;
    }

    /**
     * A value as it appears inside a layer name. Its separators at both ends merge with the separators next to it in
     * the name, and are left out.
     */
    private static String communityForm(String value) {
        String replaced = replaceInvalidCharacters(value);
        String collapsed = collapseSeparators(replaced);
        return stripPlaceholders(collapsed);
    }

    private static String stripPlaceholders(String name) {
        int start = name.startsWith(PLACEHOLDER) ? 1 : 0;
        int end = name.endsWith(PLACEHOLDER) ? name.length() - 1 : name.length();
        if (start >= end) {
            return "";
        }
        return name.substring(start, end);
    }

    private static String dropTrailingUnderscore(String name) {
        if (name.length() > 1 && name.endsWith(PLACEHOLDER)) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }
}
