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
package io.tileverse.geoserver.parquetry.config;

import org.geoserver.config.GeoServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.tileverse.geoserver.parquetry.wfs.ArrowIpcOutputFormat;

/**
 * Registers the {@code arrow-ipc} WFS GetFeature output format ({@link ArrowIpcOutputFormat}). GeoServer discovers any
 * {@code Response} bean in the application context; declaring the bean is the whole of the registration.
 *
 * <p>Each output format has its own configuration class, letting GeoServer Cloud auto-configuration enable them
 * independently. GeoServer Cloud imports this class directly; vanilla GeoServer reaches the same bean through the
 * plugin's {@code applicationContext.xml}.
 */
@Configuration(proxyBeanMethods = false)
public class ArrowIpcWfsOutputFormatConfiguration {

    @Bean
    ArrowIpcOutputFormat arrowIpcOutputFormat(GeoServer geoServer) {
        return new ArrowIpcOutputFormat(geoServer);
    }
}
