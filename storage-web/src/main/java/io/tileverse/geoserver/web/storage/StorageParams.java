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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.tileverse.storage.StorageParameter;
import io.tileverse.storage.spi.StorageProvider;

/**
 * The {@code storage.*} parameters and provider ids of the {@link StorageProvider} registry, in display order. A
 * provider added to the classpath appears here with no code change.
 */
public final class StorageParams {

    private StorageParams() {}

    private static final String CACHING_GROUP = "caching";

    /**
     * Caching first when {@code cachingParameters} is true, then the backend groups, each group's parameters in their
     * provider's declared order.
     */
    public static List<StorageParameter<?>> orderedParameters(boolean cachingParameters) {
        Map<String, List<StorageParameter<?>>> byGroup = parametersByGroup();
        List<StorageParameter<?>> ordered = new ArrayList<>();
        if (cachingParameters) {
            ordered.addAll(byGroup.getOrDefault(CACHING_GROUP, List.of()));
        }
        for (String group : StorageParamVisibility.selectableGroups()) {
            ordered.addAll(byGroup.getOrDefault(group, List.of()));
        }
        return List.copyOf(ordered);
    }

    /** In display order, for the provider selector. */
    public static List<String> providerIds() {
        return StorageProvider.getProviders().stream()
                .map(StorageProvider::getId)
                .sorted(ProviderDisplayOrder.comparator())
                .toList();
    }

    /** Deduplicated by key because Azure Data Lake declares the Azure parameters too. */
    private static Map<String, List<StorageParameter<?>>> parametersByGroup() {
        Map<String, List<StorageParameter<?>>> byGroup = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (StorageProvider provider : StorageProvider.getProviders()) {
            for (StorageParameter<?> parameter : provider.getParameters()) {
                if (seen.add(parameter.key())) {
                    byGroup.computeIfAbsent(parameter.group(), group -> new ArrayList<>())
                            .add(parameter);
                }
            }
        }
        return byGroup;
    }
}
