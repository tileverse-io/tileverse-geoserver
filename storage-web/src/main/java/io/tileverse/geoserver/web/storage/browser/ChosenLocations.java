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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/** The location written into the field for a choice: an absolute {@code file:} URI, or the storage root and the key. */
final class ChosenLocations {

    private ChosenLocations() {}

    /**
     * The URI of {@code key} under {@code root}. The characters not allowed in a URI path are percent-encoded, a space,
     * {@code #}, {@code ?} and {@code %} among them. On object storage a star is encoded too, because a store would
     * read a raw star in its location as a glob. A local URI is written by {@link Path#toUri()} and keeps the star. A
     * folder ends with {@code /}.
     */
    static URI of(URI root, String key, boolean local) {
        if (local) {
            return localUri(root, key);
        }
        return URI.create(root + encodedKey(key));
    }

    private static URI localUri(URI root, String key) {
        URI uri = Path.of(root).resolve(key).toUri();
        boolean folder = key.isEmpty() || key.endsWith("/");
        if (folder && !uri.toString().endsWith("/")) {
            return URI.create(uri + "/");
        }
        return uri;
    }

    private static String encodedKey(String key) {
        String[] segments = key.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                encoded.append('/');
            }
            encoded.append(encodedSegment(segments[index]));
        }
        return encoded.toString();
    }

    /** Encoded as the path of an absolute URI: a relative one would read a segment holding a colon as a scheme. */
    private static String encodedSegment(String segment) {
        if (segment.isEmpty()) {
            return "";
        }
        try {
            String rawPath = new URI(null, null, "/" + segment, null).getRawPath();
            return withStarsEncoded(rawPath.substring(1));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a key segment: " + segment, e);
        }
    }

    /** The URI class leaves the star of a path as it is, unlike the other glob characters. */
    private static String withStarsEncoded(String segment) {
        return segment.replace("*", "%2A");
    }
}
