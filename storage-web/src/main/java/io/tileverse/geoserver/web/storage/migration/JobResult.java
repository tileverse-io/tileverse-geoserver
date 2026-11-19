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

import java.util.Objects;

/**
 * The outcome of a background job on one store. The check is the verification run by the job, null for a revert, a
 * discard and a failure. The failure is a text safe to show, without secrets, and null on success.
 */
public record JobResult(Kind kind, CheckResult check, String failure) {

    /** What the job did to the store. */
    public enum Kind {
        CHECK,
        MIGRATE,
        REVERT,
        DISCARD
    }

    public JobResult {
        Objects.requireNonNull(kind, "kind");
    }

    public static JobResult checked(CheckResult check) {
        return new JobResult(Kind.CHECK, Objects.requireNonNull(check, "check"), null);
    }

    /** A migration written to the catalog, with the verification run just before. */
    public static JobResult migrated(CheckResult check) {
        return new JobResult(Kind.MIGRATE, Objects.requireNonNull(check, "check"), null);
    }

    public static JobResult reverted() {
        return new JobResult(Kind.REVERT, null, null);
    }

    public static JobResult discarded() {
        return new JobResult(Kind.DISCARD, null, null);
    }

    public static JobResult failed(Kind kind, String failure) {
        return new JobResult(kind, null, Objects.requireNonNull(failure, "failure"));
    }

    public boolean isFailed() {
        return failure != null;
    }
}
