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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CheckResultTest {

    private static final MigrationNote INFO = MigrationNote.info("info");
    private static final MigrationNote DIFFERENCE = MigrationNote.difference("difference");
    private static final MigrationNote BLOCKER = MigrationNote.blocker("blocker");

    @ParameterizedTest(name = "{0}")
    @MethodSource("notesAndOutcomes")
    void outcomeFollowsTheWorstNote(
            String description,
            List<MigrationNote> planNotes,
            List<MigrationNote> resultNotes,
            List<MigrationNote> layerNotes,
            CheckOutcome expected) {
        CheckResult result = new CheckResult(planWith(planNotes), List.of(layerWith(layerNotes)), resultNotes);

        assertThat(result.outcome()).isEqualTo(expected);
    }

    static Stream<Arguments> notesAndOutcomes() {
        return Stream.of(
                Arguments.of("no notes", List.of(), List.of(), List.of(), CheckOutcome.READY),
                Arguments.of("info only", List.of(INFO), List.of(INFO), List.of(INFO), CheckOutcome.READY),
                Arguments.of(
                        "a difference in a layer",
                        List.of(INFO),
                        List.of(),
                        List.of(DIFFERENCE),
                        CheckOutcome.READY_WITH_DIFFERENCES),
                Arguments.of("a blocker in the plan", List.of(BLOCKER), List.of(), List.of(), CheckOutcome.BLOCKED),
                Arguments.of(
                        "a blocker in the result and a difference",
                        List.of(),
                        List.of(BLOCKER),
                        List.of(DIFFERENCE),
                        CheckOutcome.BLOCKED));
    }

    @Test
    void allNotesListsThePlanNotesFirst() {
        CheckResult result =
                new CheckResult(planWith(List.of(INFO)), List.of(layerWith(List.of(BLOCKER))), List.of(DIFFERENCE));

        assertThat(result.allNotes()).containsExactly(INFO, DIFFERENCE, BLOCKER);
    }

    @Test
    void worseThanFollowsTheOrder() {
        assertThat(CheckOutcome.BLOCKED.worseThan(CheckOutcome.READY)).isTrue();
        assertThat(CheckOutcome.READY.worseThan(CheckOutcome.READY)).isFalse();
        assertThat(CheckOutcome.READY_WITH_DIFFERENCES.worseThan(CheckOutcome.BLOCKED))
                .isFalse();
    }

    private static MigrationPlan planWith(List<MigrationNote> notes) {
        MigrationPlan.Builder builder = MigrationPlan.to("X");
        notes.forEach(builder::note);
        return builder.build();
    }

    private static LayerComparison layerWith(List<MigrationNote> notes) {
        return new LayerComparison("FeatureTypeInfoImpl-1", "roads", "roads", "roads", notes);
    }
}
