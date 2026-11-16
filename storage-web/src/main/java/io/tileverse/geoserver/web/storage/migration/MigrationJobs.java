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

import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.geotools.util.logging.Logging;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Runs the migration jobs in the background, at most one at a time per store, and keeps the last result per store in
 * memory. The page reads the results on later requests: an administrator may leave the page while a job runs. A job
 * runs with the authentication of the thread submitting it, because it writes to the catalog on behalf of the
 * administrator.
 */
public final class MigrationJobs {

    private static final Logger LOGGER = Logging.getLogger(MigrationJobs.class);

    private static final int THREADS = 4;

    private static final MigrationJobs SHARED =
            new MigrationJobs(Executors.newFixedThreadPool(THREADS, daemonThreads()));

    /** Runs the jobs. */
    private final Executor executor;

    /** The ids of the stores with a job submitted and not finished. */
    private final Set<String> working = ConcurrentHashMap.newKeySet();

    /** The last result by store id. Workers write it while request threads read it. */
    private final Map<String, JobResult> results = new ConcurrentHashMap<>();

    public MigrationJobs(Executor executor) {
        this.executor = executor;
    }

    /** The jobs of the server, run by four daemon threads. */
    public static MigrationJobs shared() {
        return SHARED;
    }

    /**
     * Runs the work in the background and keeps its result as the last result of the store. The work turns its failures
     * into a failed result.
     *
     * @return false, and the work does not run, while a job of the store is working
     */
    public boolean submit(String storeId, Callable<JobResult> work) {
        if (!working.add(storeId)) {
            return false;
        }
        SecurityContext context = copyOfCurrentContext();
        try {
            executor.execute(() -> run(storeId, work, context));
        } catch (RuntimeException e) {
            working.remove(storeId);
            throw e;
        }
        return true;
    }

    /**
     * Runs the work on the calling thread and keeps its result as the last result of the store. No job of the store
     * starts while the work runs. The work turns its failures into a failed result.
     *
     * @return the result of the work, empty when it did not run because a job of the store is working
     */
    public Optional<JobResult> runHere(String storeId, Callable<JobResult> work) {
        if (!working.add(storeId)) {
            return Optional.empty();
        }
        try {
            return callAndKeep(storeId, work);
        } finally {
            working.remove(storeId);
        }
    }

    public boolean isWorking(String storeId) {
        return working.contains(storeId);
    }

    public boolean anyWorking() {
        return !working.isEmpty();
    }

    public Optional<JobResult> lastResult(String storeId) {
        return Optional.ofNullable(results.get(storeId));
    }

    public void forget(String storeId) {
        results.remove(storeId);
    }

    /**
     * A context of its own for the job. The context of the request thread is cleared or replaced when the request ends,
     * maybe before the job runs.
     */
    private static SecurityContext copyOfCurrentContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        copy.setAuthentication(authentication);
        return copy;
    }

    /**
     * Keeps the result before it marks the store as idle: a reader seeing the store idle finds its new result. The
     * previous context of the thread is restored rather than cleared because a direct executor runs the job on the
     * submitting thread.
     */
    private void run(String storeId, Callable<JobResult> work, SecurityContext context) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(context);
        try {
            callAndKeep(storeId, work);
        } finally {
            SecurityContextHolder.setContext(previous);
            working.remove(storeId);
        }
    }

    /** The result of the work, kept as the last result of the store. Empty when the work threw. */
    private Optional<JobResult> callAndKeep(String storeId, Callable<JobResult> work) {
        try {
            JobResult result = work.call();
            results.put(storeId, result);
            return Optional.of(result);
        } catch (Exception | LinkageError | ServiceConfigurationError e) {
            logUnexpected(e);
            return Optional.empty();
        }
    }

    /** The log names the exception class only, because its message may hold a secret of the store. */
    private static void logUnexpected(Throwable e) {
        LOGGER.warning("A store migration job ended with " + e.getClass().getName());
    }

    /** Daemon threads never keep a stopping server waiting. */
    private static ThreadFactory daemonThreads() {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "store-migration-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
