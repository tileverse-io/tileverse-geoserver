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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FailureMessagesTest {

    // four characters is the shortest value hidden, and the symbols of a regular expression are plain text
    @ParameterizedTest
    @MethodSource("hiddenValues")
    void hidesParameterValues(String value) {
        Properties storageParams = params("storage.s3.aws-secret-access-key", value);
        IOException failure = new IOException("denied for " + value + " on bucket");

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("denied for *** on bucket");
    }

    // flags and short values are too common to tell a secret
    @ParameterizedTest
    @MethodSource("keptValues")
    void keepsFlagsAndShortValues(String value) {
        Properties storageParams = params("storage.s3.some-parameter", value);
        IOException failure = new IOException("bad " + value + " here");

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("bad " + value + " here");
    }

    @Test
    void hidesRepeatedValues() {
        Properties storageParams = params("storage.azure.account-key", "abcd1234");
        IOException failure = new IOException("abcd1234 rejected, retry with abcd1234");

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("*** rejected, retry with ***");
    }

    @Test
    void hidesSeveralValues() {
        Properties storageParams = params(
                "storage.s3.aws-access-key-id", "AKIA1234",
                "storage.s3.aws-secret-access-key", "s3cr3t-key");
        IOException failure = new IOException("AKIA1234 and s3cr3t-key");

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("*** and ***");
    }

    // the parameters are read in no fixed order, and the vectors swap the values to cover both orders
    @ParameterizedTest
    @MethodSource("nestedValues")
    void hidesTheWholeLongerValue(String first, String second) {
        Properties storageParams = params("storage.http.username", first, "storage.http.password", second);
        IOException failure = new IOException("rejected admin with admin123");

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("rejected *** with ***");
    }

    @ParameterizedTest
    @MethodSource("missingMessages")
    void missingMessageNamesTheClass(String missing) {
        IllegalStateException failure = new IllegalStateException(missing);

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("IllegalStateException");
    }

    // the engine reports a failed read with the reason in the cause
    @Test
    void appendsTheCauseReason() {
        IOException reason = new IOException("403 Forbidden");
        UncheckedIOException failure = new UncheckedIOException("Read failed at offset 0 of s3://b/x", reason);

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("Read failed at offset 0 of s3://b/x: 403 Forbidden");
    }

    // a wrapper built from its cause quotes the cause in its own message
    @Test
    void skipsARepeatedCauseMessage() {
        IOException reason = new IOException("403 Forbidden");
        UncheckedIOException failure = new UncheckedIOException(reason);

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("java.io.IOException: 403 Forbidden");
    }

    @Test
    void hidesValuesInACause() {
        Properties storageParams = params("storage.s3.aws-secret-access-key", "s3cr3t-key");
        IOException reason = new IOException("denied for s3cr3t-key");
        UncheckedIOException failure = new UncheckedIOException("Read failed at offset 0", reason);

        String message = FailureMessages.of(failure, storageParams);

        assertThat(message).isEqualTo("Read failed at offset 0: denied for ***");
    }

    @Test
    void followsFiveCausesAtMost() {
        Throwable failure = new IOException("c7");
        for (int level = 6; level >= 0; level--) {
            failure = new IOException("c" + level, failure);
        }

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("c0: c1: c2: c3: c4: c5");
    }

    @Test
    void aCauseFillsAMissingMessage() {
        IOException reason = new IOException("403 Forbidden");
        IllegalStateException failure = new IllegalStateException();
        failure.initCause(reason);

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("403 Forbidden");
    }

    @Test
    void noMessageInTheChainNamesTheClass() {
        IOException cause = new IOException(" ");
        IllegalStateException failure = new IllegalStateException(null, cause);

        String message = FailureMessages.of(failure, new Properties());

        assertThat(message).isEqualTo("IllegalStateException");
    }

    static Stream<String> hiddenValues() {
        return Stream.of("abcd", "s3cr3t-key", "a.b*c+d", "(x|y)[z]$^", "back\\slash");
    }

    static Stream<Arguments> nestedValues() {
        return Stream.of(Arguments.of("admin", "admin123"), Arguments.of("admin123", "admin"));
    }

    static Stream<String> keptValues() {
        return Stream.of("true", "TRUE", "false", "False", "abc", "443", "a", "");
    }

    static Stream<String> missingMessages() {
        return Stream.of(null, "", " ", "\t\n");
    }

    private static Properties params(String... keysAndValues) {
        Properties storageParams = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            storageParams.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return storageParams;
    }
}
