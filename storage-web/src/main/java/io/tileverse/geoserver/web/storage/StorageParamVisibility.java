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
package io.tileverse.geoserver.web.storage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.tileverse.storage.spi.StorageProvider;

/**
 * Decides which connection parameters show for the selected backend groups, from the key shape
 * {@code storage.<group>[.*]}. Free of Wicket, for testing without a page.
 */
public final class StorageParamVisibility {

    /** The remote backends; only these show the caching parameters. */
    private static final Set<String> CLOUD_GROUPS = Set.of("s3", "azure", "gcs", "http");

    /** Every backend group a store can select, the local file backend included. */
    private static final Set<String> BACKEND_GROUPS = Set.of("s3", "azure", "gcs", "http", "file");

    private static final String STORAGE_PREFIX = "storage.";
    private static final String CACHING_GROUP = "caching";
    private static final String PROVIDER_GROUP = "provider";

    private StorageParamVisibility() {}

    /**
     * Non-{@code storage.*} keys and {@code storage.provider} always show, caching keys with any selected remote
     * backend, backend keys with their group.
     */
    public static boolean isVisible(String paramKey, Set<String> selectedGroups) {
        String group = groupOf(paramKey);
        if (group.isEmpty() || PROVIDER_GROUP.equals(group)) {
            return true;
        }
        if (CACHING_GROUP.equals(group)) {
            return selectedGroups.stream().anyMatch(CLOUD_GROUPS::contains);
        }
        return selectedGroups.contains(group);
    }

    /** In display order: {@code [http, s3, gcs, azure, file]} with the standard providers. */
    public static List<String> selectableGroups() {
        return StorageProvider.getProviders().stream()
                .flatMap(provider -> provider.getParameters().stream())
                .map(parameter -> groupOf(parameter.key()))
                .filter(BACKEND_GROUPS::contains)
                .distinct()
                .sorted(ProviderDisplayOrder.comparator())
                .toList();
    }

    /**
     * The backend groups of the provider's parameters: {@code {azure}} for {@code azure-datalake}, {@code {file}} for
     * {@code file}.
     */
    public static Set<String> groupsForProvider(String providerId) {
        return StorageProvider.getProviders().stream()
                .filter(provider -> provider.getId().equals(providerId))
                .flatMap(provider -> provider.getParameters().stream())
                .map(parameter -> groupOf(parameter.key()))
                .filter(BACKEND_GROUPS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The selectable groups present in an existing store's keys, to pre-select its backends. */
    public static Set<String> selectedGroupsFromParameters(Map<String, ?> connectionParameters) {
        return connectionParameters.keySet().stream()
                .map(StorageParamVisibility::groupOf)
                .filter(BACKEND_GROUPS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The {@code <group>} of a {@code storage.<group>[.*]} key, or {@code ""} for any other key. */
    public static String groupOf(String paramKey) {
        if (!paramKey.startsWith(STORAGE_PREFIX)) {
            return "";
        }
        String rest = paramKey.substring(STORAGE_PREFIX.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
