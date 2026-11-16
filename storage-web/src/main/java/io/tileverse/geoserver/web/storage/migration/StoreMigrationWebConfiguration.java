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

import org.geoserver.web.AdminComponentAuthorizer;
import org.geoserver.web.GeoServerBasePage;
import org.geoserver.web.ToolLinkInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the store migration page to GeoServer's Tools menu, for administrators. A plugin offering a migration imports
 * this configuration; Spring declares the entry once when several plugins import it.
 */
@Configuration(proxyBeanMethods = false)
public class StoreMigrationWebConfiguration {

    @Bean
    ToolLinkInfo storeMigrationToolLink() {
        ToolLinkInfo link = new ToolLinkInfo();
        link.setId("storeMigrationToolLink");
        link.setComponentClass(pageClass());
        link.setTitleKey("StoreMigrationPage.page.title");
        link.setDescriptionKey("StoreMigrationPage.page.description");
        link.setAuthorizer(new AdminComponentAuthorizer());
        return link;
    }

    /** A tool link takes its page as a class of the base page type. */
    @SuppressWarnings("unchecked")
    private static Class<GeoServerBasePage> pageClass() {
        Class<? extends GeoServerBasePage> page = StoreMigrationPage.class;
        return (Class<GeoServerBasePage>) page;
    }
}
