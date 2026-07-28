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
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.server.domain.dto.AnalysisApiRequest;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.eclipse.jifa.server.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class JifaMcpToolServiceTestSupport {

    protected static final FileView GC_FILE = new FileView(
            1L,
            "gc-file",
            "app.gc.log",
            FileType.GC_LOG,
            1024L,
            LocalDateTime.of(2026, 3, 27, 10, 0)
    );

    protected static final FileView THREAD_FILE = new FileView(
            2L,
            "thread-file",
            "threads.txt",
            FileType.THREAD_DUMP,
            2048L,
            LocalDateTime.of(2026, 3, 27, 10, 5)
    );

    protected static final FileView HEAP_FILE = new FileView(
            3L,
            "heap-file",
            "dump.hprof",
            FileType.HEAP_DUMP,
            4096L,
            LocalDateTime.of(2026, 3, 27, 10, 10)
    );

    protected static final FileView JFR_FILE = new FileView(
            4L,
            "jfr-file",
            "recording.jfr",
            FileType.JFR_FILE,
            512L,
            LocalDateTime.of(2026, 3, 27, 10, 15)
    );

    @Mock
    protected FileService fileService;

    @Mock
    protected AnalysisApiService analysisApiService;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected JifaMcpFileToolService fileToolService;

    protected JifaMcpGcLogToolService gcLogToolService;

    protected JifaMcpThreadDumpToolService threadDumpToolService;

    protected JifaMcpHeapDumpToolService heapDumpToolService;

    protected Map<String, FileView> filesByUniqueName;

    protected Map<String, Function<AnalysisApiRequest, Object>> analysisReplies;

    @BeforeEach
    public void setUp() {
        JifaMcpResultHelper resultHelper = new JifaMcpResultHelper(objectMapper);
        JifaMcpAnalysisInvoker analysisInvoker = new JifaMcpAnalysisInvoker(analysisApiService, objectMapper);
        JifaMcpFileResolver fileResolver = new JifaMcpFileResolver(fileService, resultHelper);
        gcLogToolService = new JifaMcpGcLogToolService(analysisInvoker, fileResolver, resultHelper);
        threadDumpToolService = new JifaMcpThreadDumpToolService(analysisInvoker, fileResolver, resultHelper);
        heapDumpToolService = new JifaMcpHeapDumpToolService(analysisInvoker, fileResolver, resultHelper);
        fileToolService = new JifaMcpFileToolService(fileResolver,
                                                     resultHelper,
                                                     gcLogToolService,
                                                     threadDumpToolService,
                                                     heapDumpToolService);
        filesByUniqueName = new HashMap<>();
        analysisReplies = new HashMap<>();

        filesByUniqueName.put(GC_FILE.uniqueName(), GC_FILE);
        filesByUniqueName.put(THREAD_FILE.uniqueName(), THREAD_FILE);
        filesByUniqueName.put(HEAP_FILE.uniqueName(), HEAP_FILE);
        filesByUniqueName.put(JFR_FILE.uniqueName(), JFR_FILE);

        lenient().when(fileService.getFileViewByUniqueName(anyString())).thenAnswer(invocation ->
                filesByUniqueName.get(invocation.getArgument(0, String.class)));
        lenient().when(analysisApiService.invoke(any())).thenAnswer(invocation -> {
            AnalysisApiRequest request = invocation.getArgument(0, AnalysisApiRequest.class);
            Function<AnalysisApiRequest, Object> reply = analysisReplies.get(request.api());
            return CompletableFuture.completedFuture(reply == null ? null : reply.apply(request));
        });
    }

    protected void registerGcResponses() {
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
                        "suggestions", List.of(
                                Map.of("name", "checkPauseTime"),
                                Map.of("name", "inspectYoungGen")
                        )
                ),
                "seriousProblems", Map.of(
                        "longYoungGCPause", List.of(1000.0, 2000.0),
                        "frequentYoungGC", List.of(2500.0)
                )
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

    protected void registerThreadDumpResponses() {
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

    protected void registerHeapDumpResponses() {
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

    protected void reply(String api, Object value) {
        reply(api, request -> value);
    }

    protected void reply(String api, Function<AnalysisApiRequest, Object> resolver) {
        analysisReplies.put(api, resolver);
    }

    protected void replyAsJsonBytes(String api, Function<AnalysisApiRequest, Object> resolver) {
        analysisReplies.put(api, request -> {
            try {
                return objectMapper.writeValueAsBytes(resolver.apply(request));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected void registerGcResponsesAsJsonBytes() {
        registerGcResponses();
        convertRepliesToJsonBytes("metadata", "pauseStatistics", "diagnoseInfo", "memoryStatistics", "objectStatistics");
    }

    protected void registerThreadDumpResponsesAsJsonBytes() {
        registerThreadDumpResponses();
        convertRepliesToJsonBytes("overview", "threads", "monitors", "threadCountsByMonitor", "threadsByMonitor", "rawContentOfThread");
    }

    protected void convertRepliesToJsonBytes(String... apis) {
        for (String api : apis) {
            Function<AnalysisApiRequest, Object> resolver = analysisReplies.get(api);
            replyAsJsonBytes(api, resolver);
        }
    }
}
