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

/**
 * A store as listed by the migration page. The location has no secrets. The reason is the note explaining the state,
 * null when there is none.
 */
public record MigrationRow(
        String storeId,
        String workspace,
        String store,
        String kindLabelKey,
        String location,
        int layers,
        RowState state,
        MigrationNote reason)
        implements Serializable {

    /** A running job or a migrated store leaves nothing to check. */
    public boolean canCheck() {
        return state != RowState.WORKING && state != RowState.MIGRATED;
    }

    public boolean canMigrate() {
        return state == RowState.READY || state == RowState.READY_WITH_DIFFERENCES;
    }

    public boolean canRevert() {
        return state == RowState.MIGRATED;
    }
}
