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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.tileverse.geoserver.web.storage.migration.MigrationNote.Severity;

/**
 * What the verification of a store found: the plan to apply, possibly refined by the verification, the layer
 * comparisons, and the notes about the store as a whole.
 */
public record CheckResult(MigrationPlan plan, List<LayerComparison> layers, List<MigrationNote> notes) {

    public CheckResult {
        Objects.requireNonNull(plan, "plan");
        layers = List.copyOf(layers);
        notes = List.copyOf(notes);
    }

    /** The notes of the plan, then the notes of this result, then the notes of the layers. */
    public List<MigrationNote> allNotes() {
        List<MigrationNote> all = new ArrayList<>(plan.notes());
        all.addAll(notes);
        layers.forEach(layer -> all.addAll(layer.notes()));
        return List.copyOf(all);
    }

    /** The outcome of the worst note: BLOCKED for a blocker, READY_WITH_DIFFERENCES for a difference. */
    public CheckOutcome outcome() {
        CheckOutcome worst = CheckOutcome.READY;
        for (MigrationNote note : allNotes()) {
            CheckOutcome outcome = outcomeOf(note.severity());
            if (outcome.worseThan(worst)) {
                worst = outcome;
            }
        }
        return worst;
    }

    private static CheckOutcome outcomeOf(Severity severity) {
        return switch (severity) {
            case BLOCKER -> CheckOutcome.BLOCKED;
            case DIFFERENCE -> CheckOutcome.READY_WITH_DIFFERENCES;
            case INFO -> CheckOutcome.READY;
        };
    }
}
