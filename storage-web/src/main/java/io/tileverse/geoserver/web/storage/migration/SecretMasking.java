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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hides the secrets of a store in the texts shown by the migration page. The plan of a check knows the secrets of the
 * store and masks them. Without a plan, for example after a restart of the server, the query of a url is left out:
 * signed urls and access tokens sit in the query.
 */
final class SecretMasking {

    /** The parts of a parameter key naming a secret, in lower case. */
    private static final List<String> SECRET_KEY_PARTS = List.of("password", "secret", "key", "token");

    private SecretMasking() {}

    /**
     * The parameters as text, sorted by key. A key naming a secret hides its value. The other values are masked as
     * {@link #text(String, MigrationPlan)} does. The plan may be null.
     */
    static Map<String, String> parameters(Map<String, ? extends Serializable> parameters, MigrationPlan plan) {
        Map<String, String> masked = new TreeMap<>();
        parameters.forEach((key, value) -> masked.put(key, shownValue(key, value, plan)));
        return Collections.unmodifiableMap(masked);
    }

    /**
     * The location of a store without its query, with the secrets of the plan masked. The query goes even with a plan:
     * the values of unknown query keys are not secrets of the plan. The plan may be null.
     */
    static String location(String location, MigrationPlan plan) {
        String withoutQuery = withoutQuery(location);
        if (plan == null) {
            return withoutQuery;
        }
        return plan.scrub(withoutQuery);
    }

    /** The text with the secrets of the plan masked, or without its query when the plan is null. */
    static String text(String text, MigrationPlan plan) {
        if (plan == null) {
            return withoutQuery(text);
        }
        return plan.scrub(text);
    }

    private static String shownValue(String key, Serializable value, MigrationPlan plan) {
        if (isSecretKey(key)) {
            return MigrationPlan.MASK;
        }
        String text = value == null ? "" : String.valueOf(value);
        return text(text, plan);
    }

    private static boolean isSecretKey(String key) {
        String lowerCase = key.toLowerCase(Locale.ROOT);
        return SECRET_KEY_PARTS.stream().anyMatch(lowerCase::contains);
    }

    /** The text up to its first "?". It cuts any text there, even a text that is not a URL. */
    private static String withoutQuery(String text) {
        int queryStart = text.indexOf('?');
        if (queryStart < 0) {
            return text;
        }
        return text.substring(0, queryStart);
    }
}
