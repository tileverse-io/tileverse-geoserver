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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * The text of a failure as shown to the user: its message, then the messages of its causes, with the values of the
 * storage parameters hidden. A secret quoted by a backend error, in the failure or in a cause, never reaches the page.
 */
final class FailureMessages {

    private static final String HIDDEN = "***";
    private static final String CAUSE_SEPARATOR = ": ";

    /** Shorter values are too common to tell a secret, and hiding them would garble the message. */
    private static final int SHORTEST_HIDDEN_VALUE = 4;

    /** The reason is near the top of a chain, and a chain of causes looping on itself must end. */
    private static final int MAX_CAUSES = 5;

    private FailureMessages() {}

    static String of(Throwable failure, Properties storageParams) {
        String text = messagesOf(failure);
        if (text.isEmpty()) {
            text = failure.getClass().getSimpleName();
        }
        return withoutHiddenValues(text, storageParams);
    }

    /**
     * The message of the failure, followed by the messages of its causes. The engine wraps a failed read in an
     * exception naming the file, and keeps the reason, such as a denied access, in the cause.
     */
    private static String messagesOf(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth <= MAX_CAUSES; depth++) {
            appendMessage(text, current.getMessage());
            current = current.getCause();
        }
        return text.toString();
    }

    /** A wrapper built from its cause quotes the cause in its own message: the text is not repeated. */
    private static void appendMessage(StringBuilder text, String message) {
        if (message == null || message.isBlank() || text.indexOf(message) >= 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(CAUSE_SEPARATOR);
        }
        text.append(message);
    }

    private static String withoutHiddenValues(String text, Properties storageParams) {
        String shown = text;
        for (String value : hiddenValues(storageParams)) {
            shown = shown.replace(value, HIDDEN);
        }
        return shown;
    }

    /**
     * The values to hide, longest first. A value holding another one must be replaced whole: the shorter one would cut
     * it first and leave a piece showing.
     */
    private static List<String> hiddenValues(Properties storageParams) {
        List<String> hidden = new ArrayList<>();
        for (String key : storageParams.stringPropertyNames()) {
            String value = storageParams.getProperty(key);
            if (isHidden(value)) {
                hidden.add(value);
            }
        }
        hidden.sort(Comparator.comparingInt(String::length).reversed());
        return hidden;
    }

    private static boolean isHidden(String value) {
        boolean flag = "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        return value.length() >= SHORTEST_HIDDEN_VALUE && !flag;
    }
}
