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

import java.io.Serializable;

/**
 * One format column of the browser.
 *
 * @param key the key of the column's values in the map returned by {@link FileColumns#read}
 * @param titleKey the i18n key of the column title
 * @param sort how the table sorts the column
 */
public record FileColumn(String key, String titleKey, ColumnSort sort) implements Serializable {}
