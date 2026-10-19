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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.tileverse.storage.Storage;

class OpenStorageTest {

    private final FakeObjectStorage fake = new FakeObjectStorage("s3://bucket/");
    private final AtomicInteger closes = new AtomicInteger();

    @Test
    void releaseAloneKeepsItOpen() {
        OpenStorage storage = openStorage();

        assertThat(storage.acquire()).isTrue();
        storage.release();

        assertThat(fake.closed()).isFalse();
    }

    @Test
    void idleStorageClosesAtOnce() {
        OpenStorage storage = openStorage();

        storage.close();

        assertThat(fake.closed()).isTrue();
        assertThat(closes).hasValue(1);
    }

    @Test
    void acquireFailsAfterClose() {
        OpenStorage storage = openStorage();
        storage.close();

        assertThat(storage.acquire()).isFalse();
    }

    // a read still holds the storage, but a new read must not start on a storage about to close
    @Test
    void acquireFailsWhileClosing() {
        OpenStorage storage = openStorage();
        storage.acquire();
        storage.close();

        assertThat(storage.acquire()).isFalse();
    }

    @Test
    void closeWaitsForARead() {
        OpenStorage storage = openStorage();
        storage.acquire();

        storage.close();
        assertThat(fake.closed()).as("a read holds it").isFalse();
        storage.release();

        assertThat(fake.closed()).isTrue();
        assertThat(closes).hasValue(1);
    }

    @Test
    void closeWaitsForTheLastRead() {
        OpenStorage storage = openStorage();
        storage.acquire();
        storage.acquire();
        storage.close();

        storage.release();
        assertThat(fake.closed()).as("a read still holds it").isFalse();
        storage.release();

        assertThat(fake.closed()).isTrue();
        assertThat(closes).hasValue(1);
    }

    @Test
    void aSecondCloseIsHarmless() {
        OpenStorage storage = openStorage();
        storage.acquire();
        storage.close();
        storage.close();
        storage.release();

        storage.close();

        assertThat(closes).hasValue(1);
    }

    // a failure closing the storage must not stop the end of a read: the end frees the read slot
    @ParameterizedTest
    @MethodSource("closeFailures")
    void closeFailuresStayQuiet(Throwable failure) {
        OpenStorage storage = openStorageFailingToClose(failure);
        storage.acquire();
        storage.close();

        assertThatCode(storage::release).doesNotThrowAnyException();
        storage.close();

        assertThat(closes).as("a failed close is not tried again").hasValue(1);
    }

    static Stream<Throwable> closeFailures() {
        return Stream.of(new IOException("connection reset"), new IllegalStateException("already closed"));
    }

    private OpenStorage openStorage() {
        return new OpenStorage(forwardingToFake(null));
    }

    private OpenStorage openStorageFailingToClose(Throwable failure) {
        return new OpenStorage(forwardingToFake(failure));
    }

    /**
     * The fake keeps a flag, not a count, and a second close would go unseen. This storage forwards the calls to the
     * fake, counts the close calls, and throws {@code closeFailure} from a close when it is not null.
     */
    private Storage forwardingToFake(Throwable closeFailure) {
        InvocationHandler handler = new CloseCounter(fake, closes, closeFailure);
        ClassLoader loader = Storage.class.getClassLoader();
        return (Storage) Proxy.newProxyInstance(loader, new Class<?>[] {Storage.class}, handler);
    }

    private static final class CloseCounter implements InvocationHandler {

        private final FakeObjectStorage fake;
        private final AtomicInteger closes;
        private final Throwable closeFailure;

        CloseCounter(FakeObjectStorage fake, AtomicInteger closes, Throwable closeFailure) {
            this.fake = fake;
            this.closes = closes;
            this.closeFailure = closeFailure;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close")) {
                return close();
            }
            return forward(method, args);
        }

        private Object close() throws Throwable {
            closes.incrementAndGet();
            fake.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
            return null;
        }

        private Object forward(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(fake, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
