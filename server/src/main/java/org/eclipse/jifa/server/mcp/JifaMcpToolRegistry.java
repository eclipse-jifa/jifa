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

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class JifaMcpToolRegistry {

    private static final McpSchema.JsonSchema LIST_MY_FILES_SCHEMA = new McpSchema.JsonSchema(
            "object",
            Map.of(
                    "type", Map.of(
                            "type", "string",
                            "description", "Optional file type filter such as gc-log, thread-dump, heap-dump, or jfr-file."
                    ),
                    "page", Map.of(
                            "type", "integer",
                            "description", "Optional page number starting from 1."
                    ),
                    "pageSize", Map.of(
                            "type", "integer",
                            "description", "Optional page size between 1 and 100."
                    )
            ),
            null,
            false,
            null,
            null
    );

    private static final McpSchema.JsonSchema FILE_ARGUMENT_SCHEMA = new McpSchema.JsonSchema(
            "object",
            Map.of(
                    "file", Map.of(
                            "type", "string",
                            "description", "The unique file name returned by list_my_files."
                    )
            ),
            List.of("file"),
            false,
            null,
            null
    );

    private static final McpSchema.JsonSchema THREAD_CONTENT_ARGUMENT_SCHEMA = new McpSchema.JsonSchema(
            "object",
            Map.of(
                    "file", Map.of(
                            "type", "string",
                            "description", "The unique thread dump file name."
                    ),
                    "threadId", Map.of(
                            "type", "integer",
                            "description", "The thread id returned by analyze_thread_dump_details or blocking chains."
                    )
            ),
            List.of("file", "threadId"),
            false,
            null,
            null
    );

    private final List<JifaMcpToolDefinition> definitions;

    public JifaMcpToolRegistry(JifaMcpFileToolService fileToolService,
                               JifaMcpGcLogToolService gcLogToolService,
                               JifaMcpThreadDumpToolService threadDumpToolService,
                               JifaMcpHeapDumpToolService heapDumpToolService) {
        this.definitions = List.of(
                definition("list_my_files",
                           "List the current user's Jifa files. Optionally filter by file type.",
                           LIST_MY_FILES_SCHEMA,
                           request -> fileToolService.listMyFiles(optionalStringArgument(request, "type"),
                                                                  optionalIntArgument(request, "page"),
                                                                  optionalIntArgument(request, "pageSize"))),
                definition("get_file_info",
                           "Get basic metadata and supported MCP analyses for one Jifa file.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> fileToolService.getFileInfo(requiredStringArgument(request, "file"))),
                definition("analyze_file_summary",
                           "Return a high-level summary for a Jifa file. Dispatches to the supported file-type summary when possible.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> fileToolService.analyzeFileSummary(requiredStringArgument(request, "file"))),
                definition("analyze_gc_log_summary",
                           "Return a high-level GC log summary with findings and recommendations.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> gcLogToolService.analyzeGcLogSummary(requiredStringArgument(request, "file"))),
                definition("analyze_gc_log_metrics",
                           "Return key GC metrics such as pause, throughput, allocation, and memory usage.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> gcLogToolService.analyzeGcLogMetrics(requiredStringArgument(request, "file"))),
                definition("analyze_thread_dump_summary",
                           "Return a high-level thread dump summary including deadlocks and blocking hints.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> threadDumpToolService.analyzeThreadDumpSummary(requiredStringArgument(request, "file"))),
                definition("analyze_thread_dump_details",
                           "Return sampled thread and monitor details from a thread dump.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> threadDumpToolService.analyzeThreadDumpDetails(requiredStringArgument(request, "file"))),
                definition("analyze_thread_dump_blocking_chains",
                           "Return monitors with owners, blocked threads, and waiting threads.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> threadDumpToolService.analyzeThreadDumpBlockingChains(requiredStringArgument(request, "file"))),
                definition("get_thread_dump_thread_content",
                           "Return the raw text content for one thread in a thread dump.",
                           THREAD_CONTENT_ARGUMENT_SCHEMA,
                           request -> threadDumpToolService.getThreadDumpThreadContent(
                                   requiredStringArgument(request, "file"),
                                   requiredIntArgument(request, "threadId")
                           )),
                definition("analyze_heap_dump_summary",
                           "Return a high-level heap dump summary including object and classloader overview.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> heapDumpToolService.analyzeHeapDumpSummary(requiredStringArgument(request, "file"))),
                definition("analyze_heap_dump_hotspots",
                           "Return retained-heap hotspots based on the biggest objects and class histogram.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> heapDumpToolService.analyzeHeapDumpHotspots(requiredStringArgument(request, "file"))),
                definition("analyze_heap_dump_thread_details",
                           "Return retained-heap details for thread objects in a heap dump.",
                           FILE_ARGUMENT_SCHEMA,
                           request -> heapDumpToolService.analyzeHeapDumpThreadDetails(requiredStringArgument(request, "file")))
        );
    }

    public List<JifaMcpToolDefinition> definitions() {
        return definitions;
    }

    private JifaMcpToolDefinition definition(String name,
                                             String description,
                                             McpSchema.JsonSchema inputSchema,
                                             Function<McpSchema.CallToolRequest, Object> handler) {
        return new JifaMcpToolDefinition(name, description, inputSchema, handler);
    }

    private String optionalStringArgument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private String requiredStringArgument(McpSchema.CallToolRequest request, String name) {
        String value = optionalStringArgument(request, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private int requiredIntArgument(McpSchema.CallToolRequest request, String name) {
        Integer value = optionalIntArgument(request, name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required integer argument: " + name);
        }
        return value;
    }

    private Integer optionalIntArgument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Argument '" + name + "' must be an integer.", e);
            }
        }
        throw new IllegalArgumentException("Argument '" + name + "' must be an integer.");
    }
}
