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

import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.eclipse.jifa.server.mcp.dto.McpFinding;
import org.eclipse.jifa.server.mcp.dto.McpThreadContentResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
final class JifaMcpThreadDumpToolService {

    private static final int MAX_THREAD_SAMPLE_SIZE = 20;

    private static final int MAX_MONITOR_SAMPLE_SIZE = 50;

    private static final int BLOCKED_ON_MONITOR_ENTER_INDEX = 7;

    private final JifaMcpAnalysisInvoker analysisInvoker;

    private final JifaMcpFileResolver fileResolver;

    private final JifaMcpResultHelper resultHelper;

    JifaMcpThreadDumpToolService(JifaMcpAnalysisInvoker analysisInvoker,
                                 JifaMcpFileResolver fileResolver,
                                 JifaMcpResultHelper resultHelper) {
        this.analysisInvoker = analysisInvoker;
        this.fileResolver = fileResolver;
        this.resultHelper = resultHelper;
    }

    McpAnalysisSummary analyzeThreadDumpSummary(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.THREAD_DUMP);
        Map<String, Object> overview = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "overview", null);
        int totalThreads = resultHelper.sum(resultHelper.getList(overview.get("threadStat"), "counts"));
        int javaThreads = resultHelper.sum(resultHelper.getList(overview.get("javaThreadStat"), "javaCounts"));
        int daemonThreads = resultHelper.getInt(resultHelper.getMap(overview, "javaThreadStat"), "daemonCount", -1);
        int deadLockCount = resultHelper.getInt(overview, "deadLockCount", 0);
        int errorCount = resultHelper.getInt(overview, "errorCount", 0);
        int blockedJavaThreads = resultHelper.getCountAt(
                resultHelper.getList(resultHelper.getMap(overview, "javaThreadStat").get("javaCounts")),
                BLOCKED_ON_MONITOR_ENTER_INDEX
        );

        String summary = "Thread dump summary for " + fileView.originalName()
                         + ": total threads=" + totalThreads
                         + ", java threads=" + javaThreads
                         + ", daemon threads=" + daemonThreads
                         + ", deadlocks=" + deadLockCount
                         + ", parser errors=" + errorCount + ".";

        List<McpFinding> findings = new ArrayList<>();
        if (deadLockCount > 0) {
            findings.add(new McpFinding(
                    "critical",
                    "Deadlock detected",
                    "Jifa reported one or more deadlocked threads in this dump.",
                    resultHelper.orderedMap("deadLockCount", deadLockCount)
            ));
        }
        if (blockedJavaThreads > 0) {
            findings.add(new McpFinding(
                    "medium",
                    "Blocked Java threads detected",
                    "Some Java threads are blocked on monitor enter.",
                    resultHelper.orderedMap("blockedJavaThreads", blockedJavaThreads)
            ));
        }
        if (errorCount > 0) {
            findings.add(new McpFinding(
                    "medium",
                    "Thread dump parsing reported errors",
                    "The analyzer reported parse errors, so some details may be incomplete.",
                    resultHelper.orderedMap("errorCount", errorCount)
            ));
        }

        List<String> recommendations = new ArrayList<>();
        if (deadLockCount > 0 || blockedJavaThreads > 0) {
            recommendations.add("Use `analyze_thread_dump_blocking_chains` to inspect monitor owners and waiters.");
        }
        recommendations.add("Use `get_thread_dump_thread_content` for any suspicious thread ID to inspect its raw stack.");

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "overview", overview,
                "computed", resultHelper.orderedMap(
                        "totalThreads", totalThreads,
                        "javaThreads", javaThreads,
                        "daemonThreads", daemonThreads,
                        "blockedJavaThreads", blockedJavaThreads
                )
        );
        return resultHelper.analysisSummary(fileView, summary, findings, recommendations, evidence);
    }

    McpAnalysisSummary analyzeThreadDumpDetails(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.THREAD_DUMP);
        Map<String, Object> overview = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "overview", null);
        Map<String, Object> threads = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "threads",
                                                                          resultHelper.orderedMap("page", 1, "pageSize", MAX_THREAD_SAMPLE_SIZE));
        Map<String, Object> monitors = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "monitors",
                                                                           resultHelper.orderedMap("page", 1, "pageSize", MAX_THREAD_SAMPLE_SIZE));

        List<Map<String, Object>> threadData = resultHelper.getListOfMaps(threads.get("data"));
        List<Map<String, Object>> monitorData = resultHelper.getListOfMaps(monitors.get("data"));

        String summary = "Thread dump details for " + fileView.originalName()
                         + ": sampled " + threadData.size() + " thread(s) and "
                         + monitorData.size() + " monitor(s) from the current dump.";

        List<McpFinding> findings = new ArrayList<>();
        if (resultHelper.getInt(threads, "totalSize", 0) > threadData.size()) {
            findings.add(new McpFinding(
                    "low",
                    "Thread list was sampled",
                    "Only the first page of threads is returned to keep MCP responses compact.",
                    resultHelper.orderedMap("sampled", threadData.size(), "total", resultHelper.getInt(threads, "totalSize", 0))
            ));
        }
        if (resultHelper.getInt(monitors, "totalSize", 0) > monitorData.size()) {
            findings.add(new McpFinding(
                    "low",
                    "Monitor list was sampled",
                    "Only the first page of monitors is returned to keep MCP responses compact.",
                    resultHelper.orderedMap("sampled", monitorData.size(), "total", resultHelper.getInt(monitors, "totalSize", 0))
            ));
        }

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "overview", overview,
                "sampleThreads", threadData,
                "sampleMonitors", monitorData
        );
        return resultHelper.analysisSummary(
                fileView,
                summary,
                findings,
                List.of(
                        "Use `analyze_thread_dump_blocking_chains` to focus on monitors with waiters.",
                        "Use `get_thread_dump_thread_content` when a sampled thread looks suspicious."
                ),
                evidence
        );
    }

    McpAnalysisSummary analyzeThreadDumpBlockingChains(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.THREAD_DUMP);
        Map<String, Object> monitors = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "monitors",
                                                                           resultHelper.orderedMap("page", 1, "pageSize", MAX_MONITOR_SAMPLE_SIZE));
        List<Map<String, Object>> monitorData = resultHelper.getListOfMaps(monitors.get("data"));
        List<Map<String, Object>> chains = new ArrayList<>();
        List<McpFinding> findings = new ArrayList<>();

        for (Map<String, Object> monitor : monitorData) {
            int monitorId = resultHelper.getInt(monitor, "id", -1);
            Map<String, Object> stateCounts = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "threadCountsByMonitor",
                                                                                  resultHelper.orderedMap("id", monitorId));
            int blockedCount = resultHelper.monitorStateCount(stateCounts, "WAITING_TO_LOCK")
                               + resultHelper.monitorStateCount(stateCounts, "WAITING_TO_RE_LOCK");
            int waitingCount = resultHelper.monitorStateCount(stateCounts, "WAITING_ON")
                               + resultHelper.monitorStateCount(stateCounts, "WAITING_ON_CLASS_INITIALIZATION")
                               + resultHelper.monitorStateCount(stateCounts, "WAITING_ON_NO_OBJECT_REFERENCE_AVAILABLE")
                               + resultHelper.monitorStateCount(stateCounts, "PARKING");
            int ownerCount = resultHelper.monitorStateCount(stateCounts, "LOCKED");
            if (blockedCount == 0 && waitingCount == 0) {
                continue;
            }

            Map<String, Object> chain = resultHelper.orderedMap(
                    "monitor", resultHelper.orderedMap(
                            "id", monitorId,
                            "reference", resultHelper.formatMonitorReference(monitor)
                    ),
                    "counts", stateCounts,
                    "owners", loadThreadsByMonitorState(fileView, uniqueName, monitorId, "LOCKED", ownerCount),
                    "blocked", resultHelper.mergeLists(List.of(
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId, "WAITING_TO_LOCK",
                                                      resultHelper.monitorStateCount(stateCounts, "WAITING_TO_LOCK")),
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId, "WAITING_TO_RE_LOCK",
                                                      resultHelper.monitorStateCount(stateCounts, "WAITING_TO_RE_LOCK"))
                    )),
                    "waiting", resultHelper.mergeLists(List.of(
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId, "WAITING_ON",
                                                      resultHelper.monitorStateCount(stateCounts, "WAITING_ON")),
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId, "PARKING",
                                                      resultHelper.monitorStateCount(stateCounts, "PARKING")),
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId,
                                                      "WAITING_ON_CLASS_INITIALIZATION",
                                                      resultHelper.monitorStateCount(stateCounts, "WAITING_ON_CLASS_INITIALIZATION")),
                            loadThreadsByMonitorState(fileView, uniqueName, monitorId,
                                                      "WAITING_ON_NO_OBJECT_REFERENCE_AVAILABLE",
                                                      resultHelper.monitorStateCount(stateCounts, "WAITING_ON_NO_OBJECT_REFERENCE_AVAILABLE"))
                    ))
            );
            chains.add(chain);
            findings.add(new McpFinding(
                    blockedCount > 0 ? "high" : "medium",
                    "Blocking chain on " + resultHelper.formatMonitorReference(monitor),
                    "This monitor has " + blockedCount + " blocked thread(s) and " + waitingCount + " waiting thread(s).",
                    resultHelper.orderedMap("counts", stateCounts)
            ));
        }

        chains.sort(Comparator.comparingInt(this::blockingChainScore).reversed());
        findings.sort(Comparator.comparing((McpFinding finding) -> resultHelper.severityScore(finding.severity())).reversed());

        String summary = "Thread dump blocking chains for " + fileView.originalName()
                         + ": detected " + chains.size() + " monitor(s) with blocked or waiting threads.";
        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "chains", chains
        );
        return resultHelper.analysisSummary(
                fileView,
                summary,
                findings,
                List.of("Use `get_thread_dump_thread_content` with the returned thread IDs to inspect raw stacks."),
                evidence
        );
    }

    McpThreadContentResult getThreadDumpThreadContent(String uniqueName, int threadId) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.THREAD_DUMP);
        List<String> content = analysisInvoker.invokeAnalysisAsListOfStrings(fileView.type(), uniqueName, "rawContentOfThread",
                                                                             resultHelper.orderedMap("id", threadId));
        return new McpThreadContentResult(
                fileView.uniqueName(),
                fileView.originalName(),
                threadId,
                content.size(),
                String.join("\n", content)
        );
    }

    private List<Map<String, Object>> loadThreadsByMonitorState(FileView fileView,
                                                                String uniqueName,
                                                                int monitorId,
                                                                String state,
                                                                int totalSize) {
        if (totalSize <= 0) {
            return List.of();
        }
        Map<String, Object> pageView = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "threadsByMonitor",
                                                                           resultHelper.orderedMap(
                                                                                   "id", monitorId,
                                                                                   "state", state,
                                                                                   "page", 1,
                                                                                   "pageSize", Math.min(totalSize, 10)
                                                                           ));
        return resultHelper.getListOfMaps(pageView.get("data"));
    }

    private int blockingChainScore(Map<String, Object> chain) {
        Map<String, Object> counts = resultHelper.getMap(chain, "counts");
        return resultHelper.monitorStateCount(counts, "WAITING_TO_LOCK")
               + resultHelper.monitorStateCount(counts, "WAITING_TO_RE_LOCK")
               + resultHelper.monitorStateCount(counts, "WAITING_ON")
               + resultHelper.monitorStateCount(counts, "PARKING");
    }
}
