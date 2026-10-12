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

import org.apache.wicket.model.IModel;

/**
 * The model of the checkbox of a Boolean parameter without a declared default. Such a parameter has three states and
 * its checkbox has two. An unticked box leaves the parameter out of the store. The engine then applies its own rule,
 * and that rule may depend on the other parameters of the store. A ticked box stores {@code true}.
 *
 * <p>The page cannot write {@code false}. A {@code false} stored over REST stays until the box is ticked.
 */
final class OptionalFlagModel implements IModel<Boolean> {

    private final StorageParamModel<Boolean> stored;

    OptionalFlagModel(StorageParamModel<Boolean> stored) {
        this.stored = stored;
    }

    @Override
    public Boolean getObject() {
        return stored.getObject();
    }

    @Override
    public void setObject(Boolean ticked) {
        if (Boolean.TRUE.equals(ticked)) {
            stored.setObject(Boolean.TRUE);
        } else {
            removeAStoredTrue();
        }
    }

    private void removeAStoredTrue() {
        boolean storedTrue = Boolean.TRUE.equals(stored.getObject());
        if (storedTrue) {
            stored.unset();
        }
    }
}
