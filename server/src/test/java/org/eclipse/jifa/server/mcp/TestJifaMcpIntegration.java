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
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.server.domain.dto.AnalysisApiRequest;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.repository.LoginDataRepo;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.eclipse.jifa.server.service.FileService;
import org.eclipse.jifa.server.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "jifa.role=standalone-worker",
                "jifa.storage-path=${java.io.tmpdir}/jifa-mcp-test-storage",
                "jifa.mcp-enabled=true",
                "jifa.allow-login=true",
                "jifa.allow-anonymous-access=false",
                "jifa.database-host=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
public class TestJifaMcpIntegration {

    private static final int TEST_PORT = findAvailablePort();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new TypeReference<>() {};

    private static final FileView GC_FILE = new FileView(
            1L,
            "gc-file",
            "app.gc.log",
            FileType.GC_LOG,
            1024L,
            LocalDateTime.of(2026, 3, 27, 11, 0)
    );

    private static final FileView THREAD_FILE = new FileView(
            2L,
            "thread-file",
            "threads.txt",
            FileType.THREAD_DUMP,
            2048L,
            LocalDateTime.of(2026, 3, 27, 11, 5)
    );

    private static final FileView HEAP_FILE = new FileView(
            3L,
            "heap-file",
            "dump.hprof",
            FileType.HEAP_DUMP,
            4096L,
            LocalDateTime.of(2026, 3, 27, 11, 10)
    );

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginDataRepo loginDataRepo;

    @MockBean
    private FileService fileService;

    @MockBean
    private AnalysisApiService analysisApiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Map<String, FileView> filesByUniqueName;

    private Map<String, Function<AnalysisApiRequest, Object>> analysisReplies;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("jifa.port", () -> TEST_PORT);
    }

    @BeforeEach
    public void setUp() {
        filesByUniqueName = new HashMap<>();
        analysisReplies = new HashMap<>();

        filesByUniqueName.put(GC_FILE.uniqueName(), GC_FILE);
        filesByUniqueName.put(THREAD_FILE.uniqueName(), THREAD_FILE);
        filesByUniqueName.put(HEAP_FILE.uniqueName(), HEAP_FILE);

        Mockito.when(fileService.getFileViewByUniqueName(Mockito.anyString())).thenAnswer(invocation ->
                filesByUniqueName.get(invocation.getArgument(0, String.class)));
        Mockito.when(fileService.getUserFileViews(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(invocation -> pageViewFor(invocation.getArgument(0, FileType.class),
                                                     invocation.getArgument(1, Integer.class),
                                                     invocation.getArgument(2, Integer.class)));
        Mockito.when(analysisApiService.invoke(Mockito.any())).thenAnswer(invocation -> {
            AnalysisApiRequest request = invocation.getArgument(0, AnalysisApiRequest.class);
            Function<AnalysisApiRequest, Object> reply = analysisReplies.get(request.api());
            return CompletableFuture.completedFuture(reply == null ? null : reply.apply(request));
        });

        registerGcResponses();
        registerThreadDumpResponses();
        registerHeapDumpResponses();
    }

    @Test
    public void testUnauthenticatedAccessRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(baseUrl() + "/jifa-api/mcp"))
                                         .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                         .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                                         .POST(HttpRequest.BodyPublishers.ofString("""
                                                 {"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}
                                                 """))
                                         .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                                                  .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    public void testListToolsContainsExactlyIssue382Tools() {
        try (McpSyncClient client = initializeClient(true)) {
            List<Map<String, Object>> tools = asListOfMaps(client.listTools().tools());

            List<String> toolNames = tools.stream().map(tool -> tool.get("name").toString()).toList();

            assertThat(toolNames).containsExactlyInAnyOrder(
                    "list_my_files",
                    "get_file_info",
                    "analyze_file_summary",
                    "analyze_gc_log_summary",
                    "analyze_gc_log_metrics",
                    "analyze_thread_dump_summary",
                    "analyze_thread_dump_details",
                    "analyze_thread_dump_blocking_chains",
                    "get_thread_dump_thread_content",
                    "analyze_heap_dump_summary",
                    "analyze_heap_dump_hotspots",
                    "analyze_heap_dump_thread_details"
            );
            assertThat(client.getServerInfo().version()).isEqualTo("dev");
        }
    }

    @Test
    public void testListMyFilesToolCall() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("list_my_files", Map.of()));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("returnedSize")).isEqualTo(3);
            assertThat(asList(structured.get("files"))).hasSize(3);
        }
    }

    @Test
    public void testListMyFilesSupportsPagination() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("list_my_files", Map.of(
                    "page", 2,
                    "pageSize", 1
            )));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("page")).isEqualTo(2);
            assertThat(structured.get("pageSize")).isEqualTo(1);
            assertThat(structured.get("returnedSize")).isEqualTo(1);
            assertThat(structured.get("truncated")).isEqualTo(true);
            assertThat(asMap(asList(structured.get("files")).get(0)).get("uniqueName")).isEqualTo(THREAD_FILE.uniqueName());
        }
    }

    @Test
    public void testAnalyzeGcLogSummaryToolCall() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "analyze_gc_log_summary",
                    Map.of("file", GC_FILE.uniqueName())
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("summary").toString()).contains("GC log summary");
            assertThat(asList(structured.get("findings"))).isNotEmpty();
        }
    }

    @Test
    public void testAnalyzeThreadDumpSummaryToolCall() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "analyze_thread_dump_summary",
                    Map.of("file", THREAD_FILE.uniqueName())
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("summary").toString()).contains("Thread dump summary");
            assertThat(asList(structured.get("findings"))).isNotEmpty();
        }
    }

    @Test
    public void testAnalyzeHeapDumpSummaryToolCall() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "analyze_heap_dump_summary",
                    Map.of("file", HEAP_FILE.uniqueName())
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("summary").toString()).contains("Heap dump summary");
            assertThat(asMap(structured.get("evidence"))).containsKey("details");
        }
    }

    @Test
    public void testGetThreadDumpThreadContentToolCall() {
        try (McpSyncClient client = initializeClient(true)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "get_thread_dump_thread_content",
                    Map.of(
                            "file", THREAD_FILE.uniqueName(),
                            "threadId", 101
                    )
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(false);
            assertThat(structured.get("threadId")).isEqualTo(101);
            assertThat(structured.get("content").toString()).contains("RUNNABLE");
        }
    }

    private String baseUrl() {
        return "http://localhost:" + TEST_PORT;
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate a test port", e);
        }
    }

    private Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private List<Object> asList(Object value) {
        return objectMapper.convertValue(value, LIST_TYPE);
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        return objectMapper.convertValue(value, LIST_OF_MAPS_TYPE);
    }

    private String bearerToken() {
        return jwtService.generateToken(loginDataRepo.findByUsername("admin").orElseThrow().getUser()).getToken();
    }

    private McpSyncClient initializeClient(boolean authenticated) {
        McpSyncClient client = createClient(authenticated);
        client.initialize();
        return client;
    }

    private McpSyncClient createClient(boolean authenticated) {
        HttpClientStreamableHttpTransport.Builder transportBuilder =
                HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/jifa-api/mcp");
        if (authenticated) {
            transportBuilder.customizeRequest(request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken()));
        }
        return McpClient.sync(transportBuilder.build()).build();
    }

    private PageView<FileView> pageViewFor(FileType type, int page, int pageSize) {
        List<FileView> filtered = new ArrayList<>(filesByUniqueName.values().stream()
                                                                   .filter(file -> type == null || file.type() == type)
                                                                   .sorted((left, right) -> Long.compare(left.id(), right.id()))
                                                                   .toList());
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        int toIndex = Math.min(filtered.size(), fromIndex + pageSize);
        List<FileView> pageData = fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);
        return new PageView<>(page, pageSize, filtered.size(), pageData);
    }

    private void registerGcResponses() {
        reply("metadata", request -> Map.of(
                "collector", "G1",
                "logStyle", "UNIFIED",
                "startTime", 0.0,
                "endTime", 5000.0,
                "analysisConfig", Map.of(
                        "timeRange", Map.of("start", 0.0, "end", 5000.0),
                        "longPauseThreshold", 400.0,
                        "badThroughputThreshold", 90.0,
                        "highHeapUsageThreshold", 60.0,
                        "highOldUsageThreshold", 80.0,
                        "highMetaspaceUsageThreshold", 80.0
                )
        ));
        reply("pauseStatistics", request -> Map.of(
                "throughput", 0.82,
                "pauseAvg", 180.0,
                "pauseMedian", 120.0,
                "pauseP99", 520.0,
                "pauseP999", 540.0,
                "pauseMax", 610.0
        ));
        reply("diagnoseInfo", request -> Map.of(
                "mostSeriousProblem", Map.of(
                        "problem", Map.of("name", "longYoungGCPause"),
                        "sites", List.of(Map.of("start", 1000.0, "end", 2000.0)),
                        "suggestions", List.of(Map.of("name", "checkPauseTime"))
                ),
                "seriousProblems", Map.of("longYoungGCPause", List.of(1000.0))
        ));
        reply("memoryStatistics", request -> Map.of(
                "heap", Map.of(
                        "capacityAvg", 1024L * 1024L * 1024L,
                        "usedMax", 900L * 1024L * 1024L,
                        "usedAvgAfterFullGC", 850L * 1024L * 1024L
                ),
                "old", Map.of(
                        "capacityAvg", 700L * 1024L * 1024L,
                        "usedMax", 600L * 1024L * 1024L,
                        "usedAvgAfterFullGC", 590L * 1024L * 1024L
                ),
                "metaspace", Map.of(
                        "capacityAvg", 200L * 1024L * 1024L,
                        "usedMax", 170L * 1024L * 1024L,
                        "usedAvgAfterFullGC", 165L * 1024L * 1024L
                )
        ));
        reply("objectStatistics", request -> Map.of(
                "objectCreationSpeed", 2048.0,
                "objectPromotionSpeed", 1024.0,
                "objectPromotionAvg", 4096L,
                "objectPromotionMax", 8192L
        ));
    }

    private void registerThreadDumpResponses() {
        reply("overview", request -> Map.of(
                "threadStat", Map.of("counts", List.of(3, 2, 1)),
                "javaThreadStat", Map.of(
                        "javaCounts", List.of(0, 2, 0, 0, 0, 0, 0, 1, 0, 0),
                        "daemonCount", 1
                ),
                "deadLockCount", 1,
                "errorCount", 0
        ));
        reply("threads", request -> new PageView<>(1, 20, 3, List.of(
                Map.of("id", 101, "name", "main"),
                Map.of("id", 102, "name", "worker")
        )));
        reply("monitors", request -> new PageView<>(1, 20, 1, List.of(
                Map.of("id", 11, "clazz", "java.lang.Object", "address", 42L)
        )));
        reply("threadCountsByMonitor", request -> Map.of(
                "LOCKED", 1,
                "WAITING_TO_LOCK", 1,
                "WAITING_ON", 1
        ));
        reply("threadsByMonitor", request -> {
            String state = request.parameters().get("state").getAsString();
            return switch (state) {
                case "LOCKED" -> new PageView<>(1, 10, 1, List.of(Map.of("id", 101, "name", "main")));
                case "WAITING_TO_LOCK" -> new PageView<>(1, 10, 1, List.of(Map.of("id", 102, "name", "worker")));
                case "WAITING_ON" -> new PageView<>(1, 10, 1, List.of(Map.of("id", 103, "name", "waiter")));
                default -> new PageView<>(1, 10, 0, List.of());
            };
        });
        reply("rawContentOfThread", request -> List.of(
                "\"main\" #101 prio=5 os_prio=31 tid=0x1 nid=0x2 waiting on condition",
                "   java.lang.Thread.State: RUNNABLE"
        ));
    }

    private void registerHeapDumpResponses() {
        reply("details", request -> Map.of(
                "usedHeapSize", 2L * 1024L * 1024L * 1024L,
                "numberOfObjects", 12345,
                "numberOfClasses", 321,
                "numberOfClassLoaders", 12
        ));
        reply("biggestObjects", request -> List.of(
                Map.of("label", "com.example.Cache", "value", 12.5, "description", "Retained by cache root"),
                Map.of("label", "byte[]", "value", 7.2, "description", "Large buffer")
        ));
        reply("classLoaderExplorer.summary", request -> Map.of(
                "totalSize", 12,
                "definedClasses", 300,
                "numberOfInstances", 1000
        ));
        reply("histogram", request -> new PageView<>(1, 20, 2, List.of(
                Map.of(
                        "label", "com.example.Cache",
                        "retainedSize", 900L * 1024L * 1024L,
                        "numberOfObjects", 20
                ),
                Map.of(
                        "label", "byte[]",
                        "retainedSize", 300L * 1024L * 1024L,
                        "numberOfObjects", 50
                )
        )));
        reply("threadsSummary", request -> Map.of(
                "totalSize", 2,
                "shallowHeap", 1024L,
                "retainedHeap", 20L * 1024L * 1024L
        ));
        reply("threads", request -> new PageView<>(1, 10, 2, List.of(
                Map.of(
                        "objectId", 2001,
                        "name", "main",
                        "retainedSize", 12L * 1024L * 1024L,
                        "contextClassLoader", "app-loader",
                        "hasStack", true
                ),
                Map.of(
                        "objectId", 2002,
                        "name", "worker",
                        "retainedSize", 8L * 1024L * 1024L,
                        "contextClassLoader", "app-loader",
                        "hasStack", true
                )
        )));
        reply("stackTrace", request -> List.of(
                Map.of("stack", "com.example.Work.run(Work.java:10)", "hasLocal", true),
                Map.of("stack", "java.lang.Thread.run(Thread.java:840)", "hasLocal", false)
        ));
    }

    private void reply(String api, Function<AnalysisApiRequest, Object> resolver) {
        analysisReplies.put(api, resolver);
    }
}
