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
package io.tileverse.geoserver.web.storage.browser;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** The pool running the format reads of the browsers of a server: a fixed number of daemon threads. */
final class FileDetailsLoader {

    private static final int THREADS = 8;

    private final Executor executor;

    FileDetailsLoader(Executor executor) {
        this.executor = executor;
    }

    static FileDetailsLoader defaults() {
        return new FileDetailsLoader(Executors.newFixedThreadPool(THREADS, daemonThreads()));
    }

    void submit(Runnable read) {
        executor.execute(read);
    }

    /** Daemon threads never keep a stopping server waiting. */
    private static ThreadFactory daemonThreads() {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "storage-browser-read-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
