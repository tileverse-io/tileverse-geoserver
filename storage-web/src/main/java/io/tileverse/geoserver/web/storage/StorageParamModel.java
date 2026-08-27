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
package io.tileverse.geoserver.web.storage;

import java.io.Serializable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.wicket.model.IModel;
import org.geotools.util.Converters;
import org.geotools.util.logging.Logging;

/**
 * The model of one storage parameter field. A String stored behind a typed parameter, by REST or an older version,
 * reads as the declared type and is written back typed: Wicket skips the model write when the submitted value equals
 * the one read back. An unconvertible value reads as {@code null}.
 */
final class StorageParamModel<T extends Serializable> implements IModel<T> {

    private static final Logger LOGGER = Logging.getLogger(StorageParamModel.class);

    private final IModel<Map<String, Serializable>> connectionParameters;
    private final String key;
    private final Class<T> type;

    StorageParamModel(IModel<Map<String, Serializable>> connectionParameters, String key, Class<T> type) {
        this.connectionParameters = connectionParameters;
        this.key = key;
        this.type = type;
    }

    @Override
    public T getObject() {
        Serializable raw = connectionParameters.getObject().get(key);
        if (raw == null) {
            return null;
        }
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        T converted = Converters.convert(raw, type);
        if (converted == null) {
            LOGGER.log(Level.FINE, () -> key + ": ignoring " + raw + ", not a " + type.getSimpleName());
            return null;
        }
        connectionParameters.getObject().put(key, converted);
        return converted;
    }

    @Override
    public void setObject(T value) {
        connectionParameters.getObject().put(key, value);
    }
}
