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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/** Runs the submitted reads only when told: a test sees how many are running and finishes them one at a time. */
final class ManualExecutor implements Executor {

    private final Deque<Runnable> pending = new ArrayDeque<>();

    @Override
    public synchronized void execute(Runnable task) {
        pending.add(task);
    }

    synchronized int pending() {
        return pending.size();
    }

    void runNext() {
        Runnable task;
        synchronized (this) {
            task = pending.poll();
        }
        task.run();
    }

    void runAll() {
        while (pending() > 0) {
            runNext();
        }
    }
}
