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
package org.eclipse.jifa.server.configurer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jifa.common.domain.exception.ErrorCode;
import org.eclipse.jifa.common.domain.exception.ErrorCodeAccessor;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.condition.ConditionalOnRole;
import org.eclipse.jifa.server.enums.Role;
import org.eclipse.jifa.server.mcp.JifaMcpToolDefinition;
import org.eclipse.jifa.server.mcp.JifaMcpToolRegistry;
import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.eclipse.jifa.server.mcp.dto.McpFileInfo;
import org.eclipse.jifa.server.mcp.dto.McpListMyFilesResult;
import org.eclipse.jifa.server.mcp.dto.McpThreadContentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
@ConditionalOnRole({Role.MASTER, Role.STANDALONE_WORKER})
@ConditionalOnProperty(value = "jifa.mcp-enabled", havingValue = "true")
public class McpConfigurer {

    private static final String AUTHENTICATION_CONTEXT_KEY = "authentication";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Bean(destroyMethod = "closeGracefully")
    public HttpServletStreamableServerTransportProvider jifaMcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                                                           .mcpEndpoint(Constant.HTTP_MCP_MAPPING)
                                                           .contextExtractor(this::extractTransportContext)
                                                           .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> jifaMcpServletRegistration(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registrationBean =
                new ServletRegistrationBean<>(transport, Constant.HTTP_API_PREFIX + Constant.HTTP_MCP_MAPPING);
        registrationBean.setName("jifaMcpServlet");
        registrationBean.setLoadOnStartup(1);
        return registrationBean;
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer jifaMcpServer(HttpServletStreamableServerTransportProvider transport,
                                       JifaMcpToolRegistry toolRegistry,
                                       ObjectMapper objectMapper) {
        return McpServer.sync(transport)
                        .serverInfo("Eclipse Jifa MCP", "dev")
                        .instructions("""
                                Experimental MCP endpoint for Eclipse Jifa.
                                Use list_my_files first, then choose a high-level analysis tool that matches the file type.
                                The tools intentionally expose summarized diagnostics instead of mirroring the full internal HTTP API.
                                """.strip())
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                        .tools(buildTools(toolRegistry, objectMapper))
                        .build();
    }

    private List<McpServerFeatures.SyncToolSpecification> buildTools(JifaMcpToolRegistry toolRegistry,
                                                                     ObjectMapper objectMapper) {
        return toolRegistry.definitions().stream()
                           .map(definition -> tool(objectMapper, definition))
                           .toList();
    }

    private McpTransportContext extractTransportContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return McpTransportContext.EMPTY;
        }
        return McpTransportContext.create(Map.of(AUTHENTICATION_CONTEXT_KEY, authentication));
    }

    private <T> T withAuthentication(McpTransportContext transportContext, Supplier<T> supplier) {
        SecurityContext previous = SecurityContextHolder.getContext();
        Authentication authentication = transportContext == null
                ? null
                : (Authentication) transportContext.get(AUTHENTICATION_CONTEXT_KEY);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (authentication != null) {
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private McpServerFeatures.SyncToolSpecification tool(ObjectMapper objectMapper,
                                                         JifaMcpToolDefinition definition) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                                            .name(definition.name())
                                            .description(definition.description())
                                            .inputSchema(definition.inputSchema())
                                            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                withAuthentication(exchange.transportContext(), () -> {
                    try {
                        Object result = definition.handler().apply(request);
                        return McpSchema.CallToolResult.builder()
                                                       .structuredContent(toStructuredContent(objectMapper, result))
                                                       .addTextContent(defaultTextContent(result))
                                                       .isError(false)
                                                       .build();
                    } catch (Exception e) {
                        Map<String, Object> errorContent = errorStructuredContent(e);
                        return McpSchema.CallToolResult.builder()
                                                       .structuredContent(errorContent)
                                                       .addTextContent("Tool execution failed: " + errorContent.get("error"))
                                                       .isError(true)
                                                       .build();
                    }
                }));
    }

    private Map<String, Object> toStructuredContent(ObjectMapper objectMapper, Object result) {
        return objectMapper.convertValue(result, MAP_TYPE);
    }

    private String defaultTextContent(Object result) {
        if (result instanceof McpAnalysisSummary analysisSummary) {
            return analysisSummary.summary();
        }
        if (result instanceof McpListMyFilesResult listResult) {
            return "Returned " + listResult.returnedSize() + " file(s).";
        }
        if (result instanceof McpFileInfo fileInfo) {
            return "Resolved file " + fileInfo.originalName() + " as " + fileInfo.type() + ".";
        }
        if (result instanceof McpThreadContentResult threadContent) {
            return "Returned " + threadContent.lineCount() + " line(s) for thread " + threadContent.threadId() + ".";
        }
        return "Tool execution completed.";
    }

    private Map<String, Object> errorStructuredContent(Throwable throwable) {
        LinkedHashMap<String, Object> content = new LinkedHashMap<>();
        ErrorCode errorCode = findErrorCode(throwable);
        if (errorCode != null) {
            content.put("errorCode", errorCode.identifier());
        }
        content.put("error", errorMessage(throwable));
        return content;
    }

    private ErrorCode findErrorCode(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ErrorCodeAccessor accessor) {
                return accessor.getErrorCode();
            }
        }
        return null;
    }

    private String errorMessage(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
        }
        return "Unknown error";
    }
}
