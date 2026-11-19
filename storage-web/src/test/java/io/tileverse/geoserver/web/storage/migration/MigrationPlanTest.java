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
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

class MigrationPlanTest {

    @Test
    void masksSecretsForDisplay() {
        MigrationPlan plan = MigrationPlan.to("Parquet")
                .parameter("geoparquet", "s3://b/f.parquet")
                .secret("storage.s3.aws-secret-access-key", "SK")
                .build();

        assertThat(plan.targetParameters()).containsEntry("storage.s3.aws-secret-access-key", "SK");
        assertThat(plan.targetDisplay())
                .containsExactly(
                        entry("geoparquet", "s3://b/f.parquet"), entry("storage.s3.aws-secret-access-key", "********"));
    }

    @Test
    void scrubsSecretsFromMessages() {
        MigrationPlan plan = MigrationPlan.to("Parquet")
                .secret("k", "SK")
                .secretValue("TOKEN")
                .build();

        assertThat(plan.scrub("denied for SK with TOKEN")).isEqualTo("denied for ******** with ********");
    }

    // A secret contained in a longer one must not leave the rest of the longer one in clear text.
    @Test
    void scrubsTheLongerSecretFirst() {
        MigrationPlan plan =
                MigrationPlan.to("X").secretValue("SK").secretValue("SKXYZ").build();

        assertThat(plan.scrub("key SKXYZ")).isEqualTo("key ********");
    }

    @Test
    void scrubIgnoresBlankSecrets() {
        MigrationPlan plan =
                MigrationPlan.to("X").secretValue(" ").secretValue("").build();

        assertThat(plan.scrub("a b")).isEqualTo("a b");
    }

    @Test
    void displayScrubsEmbeddedSecrets() {
        MigrationPlan plan = MigrationPlan.to("X")
                .parameter("url", "https://h/f?token=TOKEN")
                .secretValue("TOKEN")
                .build();

        assertThat(plan.targetDisplay()).containsExactly(entry("url", "https://h/f?token=********"));
    }

    @Test
    void nullSecretIsNotScrubbed() {
        MigrationPlan plan = MigrationPlan.to("X").secret("k", null).build();

        assertThat(plan.secretValues()).isEmpty();
        assertThat(plan.scrub("null value")).isEqualTo("null value");
    }

    @Test
    void blockerBlocksThePlan() {
        MigrationPlan blocked =
                MigrationPlan.to("X").note(MigrationNote.blocker("k")).build();
        MigrationPlan different =
                MigrationPlan.to("X").note(MigrationNote.difference("k")).build();

        assertThat(blocked.isBlocked()).isTrue();
        assertThat(different.isBlocked()).isFalse();
    }

    @Test
    void refinedCopyKeepsTheOriginal() {
        MigrationPlan plan = MigrationPlan.to("X").parameter("a", "1").build();

        MigrationPlan refined = plan.toBuilder().parameter("b", "2").build();

        assertThat(plan.targetParameters()).containsOnlyKeys("a");
        assertThat(refined.targetParameters()).containsOnlyKeys("a", "b");
    }
}
