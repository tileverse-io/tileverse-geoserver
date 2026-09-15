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

import java.util.Comparator;
import java.util.List;

/**
 * The display order of storage providers and backend groups, most commonly used first: HTTP leads because most
 * cloud-optimized formats are read straight from a URL, and local files trail because a plain path already selects the
 * file provider. Unlisted ids follow in id order.
 */
final class ProviderDisplayOrder {

    private static final List<String> LISTED_IDS = List.of("http", "s3", "gcs", "azure", "azure-datalake", "file");

    private ProviderDisplayOrder() {}

    static Comparator<String> comparator() {
        Comparator<String> byListedPosition = Comparator.comparingInt(ProviderDisplayOrder::listedPosition);
        return byListedPosition.thenComparing(Comparator.naturalOrder());
    }

    private static int listedPosition(String id) {
        int position = LISTED_IDS.indexOf(id);
        if (position < 0) {
            return LISTED_IDS.size();
        }
        return position;
    }
}
