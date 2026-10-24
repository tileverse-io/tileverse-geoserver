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

import java.util.Objects;

/** Why the browser stopped, shown to the user as the message of its {@link Reason}. */
public final class BrowseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The causes known to the dialog, each with a message of its own. */
    public enum Reason {
        /** The location names no bucket or container, such as {@code s3://}. */
        NO_CONTAINER(false),
        /** No local folder is open to the user. */
        NO_LOCAL_ROOT(false),
        /** The location is neither a URI nor a path. */
        INVALID_LOCATION(true),
        /** The storage cannot be opened. */
        CANNOT_OPEN(true),
        /** The backend cannot list, as plain HTTP cannot. */
        CANNOT_LIST(false),
        /** The name of a folder holds a character read as a listing pattern. */
        UNLISTABLE_NAME(true),
        /** The credentials do not allow listing. */
        ACCESS_DENIED(false),
        /** The bucket, container or folder does not exist. */
        NOT_FOUND(true),
        /** The folder or file is outside the folders open to the user, or not one listed by the browser. */
        FORBIDDEN(false),
        /** The store cannot use a choice of that kind: a folder for a store picking files, or the other way round. */
        NOT_PICKABLE(false),
        /** The browser closed after its idle timeout. */
        EXPIRED(false),
        /** Any other failure, described by the storage error. */
        FAILED(true);

        private final boolean takesDetail;

        Reason(boolean takesDetail) {
            this.takesDetail = takesDetail;
        }

        /** Whether the message of this reason shows the {@link BrowseException#detail()}. */
        public boolean takesDetail() {
            return takesDetail;
        }
    }

    private final Reason reason;
    private final String detail;

    public BrowseException(Reason reason) {
        this(reason, "");
    }

    public BrowseException(Reason reason, String detail) {
        super(reason + " " + detail);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public BrowseException(Reason reason, String detail, Throwable cause) {
        super(reason + " " + detail, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public Reason reason() {
        return reason;
    }

    /** The name, location or storage message completing the message; empty for a reason taking none. */
    public String detail() {
        return detail;
    }
}
