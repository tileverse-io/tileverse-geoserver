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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;

import io.tileverse.storage.StorageConfig;
import io.tileverse.storage.StorageFactory;

/**
 * Checks, without any I/O, that a store URL resolves to a tileverse-storage provider. The provider registry is not
 * consulted for an {@code http(s):} URI because choosing between its candidate backends can send a network HEAD
 * request, while resolving any other scheme stays local.
 */
public class StorageUriValidator implements IValidator<String> {

    private static final String ERROR_KEY = "StorageUriValidator.invalidUri";

    /** Accepted without the registry, like a plain path, because their backends always exist. */
    private static final Set<String> REGISTRY_EXEMPT_SCHEMES = Set.of("file", "http", "https");

    @Override
    public void validate(IValidatable<String> validatable) {
        if (!namesAStorageLocation(validatable.getValue())) {
            validatable.error(new ValidationError().addKey(ERROR_KEY));
        }
    }

    private static boolean namesAStorageLocation(String location) {
        URI uri = tryParse(location);
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || isRegistryExempt(scheme)) {
            return true;
        }
        return resolvesToAProvider(location);
    }

    private static URI tryParse(String location) {
        try {
            return new URI(location);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isRegistryExempt(String scheme) {
        return REGISTRY_EXEMPT_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
    }

    private static boolean resolvesToAProvider(String location) {
        Properties propsWithUri = new Properties();
        propsWithUri.setProperty(StorageConfig.URI_KEY, location);
        try {
            StorageFactory.findProvider(StorageConfig.fromProperties(propsWithUri));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
