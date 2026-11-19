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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.geotools.util.logging.Logging;

/**
 * Collects the records of one logger and keeps them off the console, for the tests. {@link #close()} gives the logger
 * back its level and its parent handlers.
 */
public final class CapturedLog extends Handler implements AutoCloseable {

    /** The logger of the class under test. */
    private final Logger logger;

    /** The level of the logger before the capture. */
    private final Level previousLevel;

    /** Whether the logger passed its records to its parents before the capture. */
    private final boolean previousParentHandlers;

    /** The records logged since the capture started. Jobs may log from other threads. */
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private CapturedLog(Logger logger) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        this.previousParentHandlers = logger.getUseParentHandlers();
    }

    /** Captures the records of the logger of the given class, at any level. */
    public static CapturedLog of(Class<?> type) {
        CapturedLog log = new CapturedLog(Logging.getLogger(type));
        log.start();
        return log;
    }

    public List<LogRecord> records() {
        return List.copyOf(records);
    }

    /** The messages of the records at the given level. */
    public List<String> messages(Level level) {
        return records.stream()
                .filter(logged -> logged.getLevel().equals(level))
                .map(LogRecord::getMessage)
                .toList();
    }

    @Override
    public void publish(LogRecord logged) {
        records.add(logged);
    }

    @Override
    public void flush() {
        // the records stay in memory
    }

    @Override
    public void close() {
        logger.removeHandler(this);
        logger.setUseParentHandlers(previousParentHandlers);
        logger.setLevel(previousLevel);
    }

    private void start() {
        setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(this);
    }
}
