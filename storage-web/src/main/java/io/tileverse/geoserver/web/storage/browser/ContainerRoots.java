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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The bucket or container root of an object storage URI, read from the URI alone: tileverse names no such root in its
 * API, and each provider parses its URLs internally. The rules follow those parsers. An HTTP(S) URL read through an
 * object storage provider names its bucket or container in its first path segment, unless its host names an S3 bucket
 * in the virtual-hosted style.
 */
final class ContainerRoots {

    /** The schemes naming the bucket or container in their authority, as {@code s3://bucket/key} does. */
    private static final Set<String> AUTHORITY_SCHEMES = Set.of("s3", "gs", "abfs", "abfss");

    /** The providers reading HTTP(S) URLs of object storage, as named by {@code storage.provider}. */
    private static final Set<String> OBJECT_STORAGE_PROVIDERS = Set.of("s3", "azure", "azure-datalake", "gcs");

    private static final String AMAZON_DOMAIN = "amazonaws.com";

    private ContainerRoots() {}

    /**
     * The root of {@code uri}: its bucket or container, ending with {@code /}.
     *
     * @param uri an absolute object storage URI
     * @param providerId the provider named by the store, or empty
     * @return the root, or empty when the URI names no bucket or container
     */
    static Optional<URI> rootOf(URI uri, String providerId) {
        String scheme = lowerCase(uri.getScheme());
        if (AUTHORITY_SCHEMES.contains(scheme)) {
            return authorityRoot(uri);
        }
        if ("az".equals(scheme)) {
            return firstSegmentRoot(uri);
        }
        if (isHttp(scheme)) {
            return httpRoot(uri, lowerCase(providerId));
        }
        return authorityRoot(uri);
    }

    /** Plain HTTP cannot list, as the dialog then says: its root only bounds the breadcrumbs. */
    private static Optional<URI> httpRoot(URI uri, String providerId) {
        boolean objectStorage = OBJECT_STORAGE_PROVIDERS.contains(providerId);
        if (!objectStorage || isVirtualHostedS3(uri.getHost())) {
            return authorityRoot(uri);
        }
        return firstSegmentRoot(uri);
    }

    /** As read by tileverse's S3 URL parser: a host holding {@code .s3.}, or {@code .s3-} under amazonaws.com. */
    private static boolean isVirtualHostedS3(String host) {
        if (host == null) {
            return false;
        }
        boolean amazonDashedRegion = host.contains(".s3-") && host.contains(AMAZON_DOMAIN);
        return host.contains(".s3.") || amazonDashedRegion;
    }

    private static Optional<URI> authorityRoot(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(uri.getScheme() + "://" + authority + "/"));
    }

    private static Optional<URI> firstSegmentRoot(URI uri) {
        Optional<URI> authorityRoot = authorityRoot(uri);
        Optional<String> container = firstSegment(uri.getRawPath());
        if (authorityRoot.isEmpty() || container.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(authorityRoot.get() + container.get() + "/"));
    }

    private static Optional<String> firstSegment(String rawPath) {
        if (rawPath == null) {
            return Optional.empty();
        }
        for (String segment : rawPath.split("/")) {
            if (!segment.isEmpty()) {
                return Optional.of(segment);
            }
        }
        return Optional.empty();
    }

    private static boolean isHttp(String scheme) {
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static String lowerCase(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT);
    }
}
