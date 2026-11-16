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

/**
 * Describes the failure of a migration job for the page and for the server log, without secrets. With the plan of the
 * store, the messages of the exception and of its causes are kept with the secrets of the plan masked. Without a plan,
 * only the class names are kept: a message may echo a stored location with its secret.
 */
final class JobFailure {

    /** The exception thrown by the job. */
    private final Throwable failure;

    /** Masks the secrets of the store. Null when the job failed before the store was translated. */
    private final MigrationPlan plan;

    JobFailure(Throwable failure, MigrationPlan plan) {
        this.failure = Objects.requireNonNull(failure, "failure");
        this.plan = plan;
    }

    /**
     * The text kept for the page: the message of the exception, then the message of its root cause. A wrapping
     * exception names the store, and the root cause tells what went wrong.
     */
    String text() {
        String text = messageOf(failure);
        Throwable root = rootCause();
        if (root == failure) {
            return text;
        }
        return text + ": " + messageOf(root);
    }

    /** The exception and its causes, each with its class name, for a log line. */
    String causeChain() {
        List<String> descriptions = causes().stream().map(this::withClassName).toList();
        return String.join("; caused by ", descriptions);
    }

    /** The stack traces of the exception and of its causes, with the messages masked as in the log line. */
    String stackTrace() {
        StringBuilder trace = new StringBuilder();
        for (Throwable cause : causes()) {
            trace.append(withClassName(cause)).append(System.lineSeparator());
            for (StackTraceElement frame : cause.getStackTrace()) {
                trace.append("\tat ").append(frame).append(System.lineSeparator());
            }
        }
        return trace.toString();
    }

    /** The exception, then its causes. A cause seen before ends the list: a chain may loop. */
    private List<Throwable> causes() {
        List<Throwable> causes = new ArrayList<>();
        Throwable cause = failure;
        while (cause != null && !causes.contains(cause)) {
            causes.add(cause);
            cause = cause.getCause();
        }
        return causes;
    }

    private Throwable rootCause() {
        List<Throwable> causes = causes();
        return causes.get(causes.size() - 1);
    }

    /** The masked message, or the class name without a plan or without a message. */
    private String messageOf(Throwable cause) {
        String message = cause.getMessage();
        if (plan == null || message == null) {
            return cause.getClass().getName();
        }
        return plan.scrub(message);
    }

    /** The class name, then the masked message when there is one to show. */
    private String withClassName(Throwable cause) {
        String className = cause.getClass().getName();
        String message = cause.getMessage();
        if (plan == null || message == null) {
            return className;
        }
        return className + ": " + plan.scrub(message);
    }
}
