/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.eclipse.jifa.server.configurer.McpConfigurer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMcpConfigurerCondition {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(McpConfigurer.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(JifaMcpToolRegistry.class, () -> {
                JifaMcpToolRegistry registry = Mockito.mock(JifaMcpToolRegistry.class);
                Mockito.when(registry.definitions()).thenReturn(List.of());
                return registry;
            });

    @Test
    public void testMcpBeansEnabledForStandaloneWorker() {
        contextRunner.withPropertyValues("jifa.mcp-enabled=true", "jifa.role=STANDALONE_WORKER")
                     .run(context -> {
                         assertThat(context).hasSingleBean(HttpServletStreamableServerTransportProvider.class);
                         assertThat(context).hasSingleBean(McpSyncServer.class);
                     });
    }

    @Test
    public void testMcpBeansEnabledForMaster() {
        contextRunner.withPropertyValues("jifa.mcp-enabled=true", "jifa.role=MASTER")
                     .run(context -> {
                         assertThat(context).hasSingleBean(HttpServletStreamableServerTransportProvider.class);
                         assertThat(context).hasSingleBean(McpSyncServer.class);
                     });
    }

    @Test
    public void testMcpBeansDisabledForStaticWorker() {
        contextRunner.withPropertyValues("jifa.mcp-enabled=true", "jifa.role=STATIC_WORKER")
                     .run(context -> {
                         assertThat(context).doesNotHaveBean(HttpServletStreamableServerTransportProvider.class);
                         assertThat(context).doesNotHaveBean(McpSyncServer.class);
                     });
    }
}
