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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProviderDisplayOrderTest {

    @Test
    void ordersTheListedProvidersMostCommonlyUsedFirst() {
        List<String> ids = List.of("file", "azure", "s3", "azure-datalake", "gcs", "http");

        List<String> ordered =
                ids.stream().sorted(ProviderDisplayOrder.comparator()).toList();

        assertThat(ordered).containsExactly("http", "s3", "gcs", "azure", "azure-datalake", "file");
    }

    @Test
    void placesUnlistedProvidersAfterTheListedOnesInIdOrder() {
        List<String> ids = List.of("zeta", "file", "alpha", "http");

        List<String> ordered =
                ids.stream().sorted(ProviderDisplayOrder.comparator()).toList();

        assertThat(ordered).containsExactly("http", "file", "alpha", "zeta");
    }
}
