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

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import io.tileverse.storage.StorageParameter;
import io.tileverse.storage.spi.StorageProvider;

/**
 * Identifies the connection-parameter keys whose value is a {@code java.time.Duration} rendered as an ISO-8601 string.
 * The engine declares those params as plain strings (GeoTools has no Duration binding); the underlying type is
 * recovered here from the tileverse-storage provider registry, keeping the set free of hardcoded keys - a new provider
 * with a Duration param is recognized with no change here.
 */
final class StorageParamTypes {

    private static final Set<String> DURATION_KEYS = durationKeys();

    private StorageParamTypes() {}

    /** Whether {@code paramKey} is declared by a storage provider with a {@code Duration} type. */
    static boolean isDuration(String paramKey) {
        return DURATION_KEYS.contains(paramKey);
    }

    private static Set<String> durationKeys() {
        return StorageProvider.getProviders().stream()
                .flatMap(provider -> provider.getParameters().stream())
                .filter(parameter -> parameter.type() == Duration.class)
                .map(StorageParameter::key)
                .collect(Collectors.toUnmodifiableSet());
    }
}
