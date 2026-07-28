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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.io.FileUtils;
import org.eclipse.jifa.server.enums.FileType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "jifa.role=standalone-worker",
                "jifa.mcp-enabled=true",
                "jifa.allow-login=false",
                "jifa.allow-anonymous-access=true",
                "jifa.database-host=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
public class TestJifaMcpRealIntegration {

    private static final int TEST_PORT = findAvailablePort();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

    private static final Path TEST_STORAGE = createTempDirectory("jifa-mcp-real-storage");

    private static final Path GC_LOG = createGcLogFile();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("jifa.port", () -> TEST_PORT);
        registry.add("jifa.storage-path", () -> TEST_STORAGE.toString());
        registry.add("jifa.input-files[0]", () -> GC_LOG.toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        FileUtils.deleteDirectory(TEST_STORAGE.toFile());
        Files.deleteIfExists(GC_LOG);
    }

    @Test
    public void testListMyFilesUsesRealFileService() {
        try (McpSyncClient client = initializeClient()) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "list_my_files",
                    Map.of("type", FileType.GC_LOG.getApiNamespace())
            ));
            Map<String, Object> structured = asMap(result.structuredContent());
            List<Object> files = asList(structured.get("files"));

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("totalSize")).isEqualTo(1);
            assertThat(files).hasSize(1);
            assertThat(asMap(files.get(0)).get("typeId")).isEqualTo(FileType.GC_LOG.getApiNamespace());
        }
    }

    @Test
    public void testAnalyzeGcLogSummaryUsesRealAnalysisApi() {
        try (McpSyncClient client = initializeClient()) {
            String uniqueName = findImportedGcLogUniqueName(client);
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "analyze_gc_log_summary",
                    Map.of("file", uniqueName)
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("summary").toString()).contains("GC log summary");
            assertThat(asMap(structured.get("evidence")).get("metadata")).isNotNull();
        }
    }

    private String findImportedGcLogUniqueName(McpSyncClient client) {
        for (int attempt = 0; attempt < 20; attempt++) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "list_my_files",
                    Map.of("type", FileType.GC_LOG.getApiNamespace())
            ));
            List<Object> files = asList(asMap(result.structuredContent()).get("files"));
            if (!files.isEmpty()) {
                return asMap(files.get(0)).get("uniqueName").toString();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for imported GC log", e);
            }
        }
        throw new IllegalStateException("GC log imported by input-files was not visible to MCP");
    }

    private McpSyncClient initializeClient() {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + TEST_PORT)
                                                                               .endpoint("/jifa-api/mcp")
                                                                               .build())
                                        .build();
        client.initialize();
        return client;
    }

    private Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private List<Object> asList(Object value) {
        return objectMapper.convertValue(value, LIST_TYPE);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate a test port", e);
        }
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory", e);
        }
    }

    private static Path createGcLogFile() {
        try {
            Path path = Files.createTempFile("jifa-mcp-real-gc", ".log");
            Files.writeString(path, """
                    OpenJDK 64-Bit Server VM (25.472-b08) for bsd-aarch64 JRE (1.8.0_472-b08)
                    CommandLine flags: -XX:+PrintGC -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+UseParallelGC
                    2026-03-23T18:46:22.549+0800: 13.372: [GC (Metadata GC Threshold) [PSYoungGen: 88829K->12434K(114688K)] 88829K->12523K(376832K), 0.0049924 secs] [Times: user=0.01 sys=0.01, real=0.01 secs]
                    2026-03-23T18:46:22.554+0800: 13.377: [Full GC (Metadata GC Threshold) [PSYoungGen: 12434K->0K(114688K)] [ParOldGen: 88K->11844K(110592K)] 12523K->11844K(225280K), [Metaspace: 20756K->20756K(1069056K)], 0.0122272 secs] [Times: user=0.06 sys=0.00, real=0.01 secs]
                    2026-03-23T18:46:27.500+0800: 18.323: [GC (Allocation Failure) [PSYoungGen: 98304K->14423K(114688K)] 110148K->26276K(225280K), 0.0125330 secs] [Times: user=0.02 sys=0.00, real=0.01 secs]
                    Heap
                     PSYoungGen      total 114688K, used 45458K [0x0000000535000000, 0x000000053d000000, 0x00000005b5000000)
                      eden space 98304K, 31% used [0x0000000535000000,0x0000000536e4e850,0x000000053b000000)
                     ParOldGen       total 110592K, used 11852K [0x0000000435000000, 0x000000043bc00000, 0x0000000535000000)
                     Metaspace       used 33225K, capacity 33940K, committed 34048K, reserved 1079296K
                    """);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test GC log", e);
        }
    }
}
