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

import java.util.Objects;

import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.password.ConfigurationPasswordEncryptionHelper;

/**
 * Encrypts backups with the password encoder configured for store passwords in GeoServer. The backup text stays in
 * clear when GeoServer has no such encoder, as the store passwords do.
 */
public final class GeoServerBackupCipher implements BackupCipher {

    /** Provides the encoder configured for store passwords. */
    private final GeoServerSecurityManager securityManager;

    public GeoServerBackupCipher(GeoServerSecurityManager securityManager) {
        this.securityManager = Objects.requireNonNull(securityManager, "securityManager");
    }

    @Override
    public String encode(String plain) {
        return helper().encode(plain);
    }

    @Override
    public String decode(String stored) {
        return helper().decode(stored);
    }

    private ConfigurationPasswordEncryptionHelper helper() {
        return securityManager.getConfigPasswordEncryptionHelper();
    }
}
