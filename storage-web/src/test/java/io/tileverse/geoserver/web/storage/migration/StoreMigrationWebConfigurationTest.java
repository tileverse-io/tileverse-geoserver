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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.geoserver.web.AdminComponentAuthorizer;
import org.geoserver.web.ToolLinkInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class StoreMigrationWebConfigurationTest {

    @Test
    void declaresTheToolsEntry() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(StoreMigrationWebConfiguration.class)) {
            ToolLinkInfo link = context.getBean("storeMigrationToolLink", ToolLinkInfo.class);

            assertThat(link.getId()).isEqualTo("storeMigrationToolLink");
            assertThat(link.getComponentClass()).isEqualTo(StoreMigrationPage.class);
            assertThat(link.getTitleKey()).isEqualTo("StoreMigrationPage.page.title");
            assertThat(link.getDescriptionKey()).isEqualTo("StoreMigrationPage.page.description");
            assertThat(link.getAuthorizer()).isInstanceOf(AdminComponentAuthorizer.class);
        }
    }

    // Two plugins import the configuration: the Tools menu shows the entry once
    @Test
    void twoImportsGiveOneEntry() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(FirstPlugin.class, SecondPlugin.class)) {
            Map<String, ToolLinkInfo> links = context.getBeansOfType(ToolLinkInfo.class);

            assertThat(links).hasSize(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(StoreMigrationWebConfiguration.class)
    static class FirstPlugin {}

    @Configuration(proxyBeanMethods = false)
    @Import(StoreMigrationWebConfiguration.class)
    static class SecondPlugin {}
}
