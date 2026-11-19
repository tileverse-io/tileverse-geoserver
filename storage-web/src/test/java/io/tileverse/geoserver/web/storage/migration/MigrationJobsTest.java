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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.tileverse.geoserver.web.storage.browser.ManualExecutor;
import io.tileverse.geoserver.web.storage.migration.JobResult.Kind;

class MigrationJobsTest {

    @Test
    void keepsTheLastResult() {
        MigrationJobs jobs = new MigrationJobs(Runnable::run);
        JobResult reverted = JobResult.reverted();

        jobs.submit("s1", () -> JobResult.failed(Kind.CHECK, "first"));
        jobs.submit("s1", () -> reverted);

        assertThat(jobs.lastResult("s1")).contains(reverted);
        assertThat(jobs.lastResult("s2")).isEmpty();
        assertThat(jobs.isWorking("s1")).isFalse();
        assertThat(jobs.anyWorking()).isFalse();
    }

    @Test
    void runHereWaitsForTheJob() {
        ManualExecutor executor = new ManualExecutor();
        MigrationJobs jobs = new MigrationJobs(executor);
        jobs.submit("s1", JobResult::reverted);

        Optional<JobResult> refused = jobs.runHere("s1", JobResult::discarded);

        assertThat(refused).isEmpty();
        executor.runAll();
        assertThat(jobs.runHere("s1", JobResult::discarded)).contains(JobResult.discarded());
        assertThat(jobs.lastResult("s1")).contains(JobResult.discarded());
    }

    @Test
    void forgetDropsTheResult() {
        MigrationJobs jobs = new MigrationJobs(Runnable::run);
        jobs.submit("s1", JobResult::reverted);

        jobs.forget("s1");

        assertThat(jobs.lastResult("s1")).isEmpty();
    }
}
