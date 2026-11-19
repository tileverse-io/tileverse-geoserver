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
import java.util.List;
import java.util.Objects;

/**
 * A fact about a migration, shown to the administrator as a translated message. A BLOCKER note stops the migration; a
 * DIFFERENCE note tells what the migrated store does differently.
 */
public record MigrationNote(Severity severity, String messageKey, List<String> arguments) implements Serializable {

    /** How much a note weighs on the decision to migrate, from the least to the most. */
    public enum Severity {
        INFO,
        DIFFERENCE,
        BLOCKER
    }

    public MigrationNote {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }

    public static MigrationNote info(String messageKey, String... arguments) {
        return new MigrationNote(Severity.INFO, messageKey, List.of(arguments));
    }

    public static MigrationNote difference(String messageKey, String... arguments) {
        return new MigrationNote(Severity.DIFFERENCE, messageKey, List.of(arguments));
    }

    public static MigrationNote blocker(String messageKey, String... arguments) {
        return new MigrationNote(Severity.BLOCKER, messageKey, List.of(arguments));
    }
}
