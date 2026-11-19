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

import java.util.List;
import java.util.Map;

/**
 * What the migration page shows about one store: its parameters before and after the migration, the notes and layer
 * comparisons of the last check, and the text of the last failure, null when the last job did not fail. The values are
 * already masked.
 */
public record MigrationDetails(
        Map<String, String> before,
        Map<String, String> after,
        List<MigrationNote> notes,
        List<LayerComparison> layers,
        String failure) {}
