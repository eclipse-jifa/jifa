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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
final class JifaMcpGcLogToolService {

    private final JifaMcpAnalysisInvoker analysisInvoker;

    private final JifaMcpFileResolver fileResolver;

    private final JifaMcpResultHelper resultHelper;

    JifaMcpGcLogToolService(JifaMcpAnalysisInvoker analysisInvoker,
                            JifaMcpFileResolver fileResolver,
                            JifaMcpResultHelper resultHelper) {
        this.analysisInvoker = analysisInvoker;
        this.fileResolver = fileResolver;
        this.resultHelper = resultHelper;
    }

    McpAnalysisSummary analyzeGcLogSummary(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.GC_LOG);
        Map<String, Object> metadata = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "metadata", null);
        Map<String, Object> config = resultHelper.buildGcAnalysisConfig(metadata);
        Map<String, Object> range = resultHelper.resolveGcAnalysisRange(metadata);
        Map<String, Object> pauseStatistics = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "pauseStatistics", range);
        Map<String, Object> diagnoseInfo = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "diagnoseInfo",
                                                                               resultHelper.orderedMap("config", config));

        Map<String, Object> mostSeriousProblem = resultHelper.getMap(diagnoseInfo, "mostSeriousProblem");
        double throughput = resultHelper.getDouble(pauseStatistics, "throughput", -1);
        double pauseMax = resultHelper.getDouble(pauseStatistics, "pauseMax", -1);
        double duration = resultHelper.getDouble(metadata, "endTime", -1) - resultHelper.getDouble(metadata, "startTime", -1);
        String collector = resultHelper.getString(metadata, "collector");
        String summary = "GC log summary for " + fileView.originalName() + ": collector="
                         + resultHelper.defaultIfBlank(collector, "unknown")
                         + ", duration=" + resultHelper.formatMilliseconds(duration)
                         + ", max pause=" + resultHelper.formatMilliseconds(pauseMax)
                         + ", throughput=" + resultHelper.formatPercent(throughput) + ".";
        if (mostSeriousProblem != null) {
            summary += " Most serious problem: " + resultHelper.formatI18n(mostSeriousProblem.get("problem")) + ".";
        }

        List<McpFinding> findings = new ArrayList<>();
        if (mostSeriousProblem != null) {
            findings.add(new McpFinding(
                    "high",
                    resultHelper.formatI18n(mostSeriousProblem.get("problem")),
                    "Jifa identified the most serious GC problem in the default analysis range.",
                    resultHelper.orderedMap("sites", mostSeriousProblem.get("sites"))
            ));
        }
        if (pauseMax >= resultHelper.getDouble(config, "longPauseThreshold", Double.MAX_VALUE)) {
            findings.add(new McpFinding(
                    "medium",
                    "Long GC pauses detected",
                    "The maximum pause exceeds the configured long pause threshold.",
                    resultHelper.orderedMap(
                            "pauseMax", pauseMax,
                            "longPauseThreshold", resultHelper.getDouble(config, "longPauseThreshold", -1)
                    )
            ));
        }
        if (throughput >= 0 && throughput < resultHelper.getDouble(config, "badThroughputThreshold", 100) / 100.0) {
            findings.add(new McpFinding(
                    "medium",
                    "GC throughput is below target",
                    "Application time outside GC is lower than the configured throughput target.",
                    resultHelper.orderedMap(
                            "throughput", throughput,
                            "badThroughputThreshold", resultHelper.getDouble(config, "badThroughputThreshold", -1)
                    )
            ));
        }

        Map<String, Object> seriousProblems = resultHelper.getMap(diagnoseInfo, "seriousProblems");
        if (seriousProblems != null) {
            seriousProblems.entrySet().stream()
                           .sorted(Comparator.comparingInt(entry -> -resultHelper.getList(entry.getValue()).size()))
                           .limit(3)
                           .filter(entry -> mostSeriousProblem == null
                                            || !Objects.equals(resultHelper.formatI18n(mostSeriousProblem.get("problem")), entry.getKey()))
                           .forEach(entry -> findings.add(new McpFinding(
                                   "medium",
                                   entry.getKey(),
                                   "Detected " + resultHelper.getList(entry.getValue()).size() + " occurrence(s) in the default range.",
                                   resultHelper.orderedMap("sites", entry.getValue())
                           )));
        }

        List<String> recommendations = resultHelper.extractRecommendations(diagnoseInfo);
        if (recommendations.isEmpty()) {
            recommendations = List.of(
                    "Use `analyze_gc_log_metrics` to inspect detailed pause, heap, and allocation metrics.",
                    "Open the highlighted time ranges in the Jifa Web UI if you need event-level drill-down."
            );
        }

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "metadata", resultHelper.orderedMap(
                        "collector", collector,
                        "logStyle", resultHelper.getString(metadata, "logStyle"),
                        "startTime", resultHelper.getDouble(metadata, "startTime", -1),
                        "endTime", resultHelper.getDouble(metadata, "endTime", -1),
                        "duration", duration
                ),
                "pauseStatistics", pauseStatistics,
                "mostSeriousProblem", mostSeriousProblem,
                "seriousProblems", seriousProblems
        );
        return resultHelper.analysisSummary(fileView, summary, findings, recommendations, evidence);
    }

    McpAnalysisSummary analyzeGcLogMetrics(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.GC_LOG);
        Map<String, Object> metadata = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "metadata", null);
        Map<String, Object> config = resultHelper.buildGcAnalysisConfig(metadata);
        Map<String, Object> range = resultHelper.resolveGcAnalysisRange(metadata);
        Map<String, Object> pauseStatistics = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "pauseStatistics", range);
        Map<String, Object> memoryStatistics = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "memoryStatistics", range);
        Map<String, Object> objectStatistics = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "objectStatistics", range);

        double throughput = resultHelper.getDouble(pauseStatistics, "throughput", -1);
        double pauseP99 = resultHelper.getDouble(pauseStatistics, "pauseP99", -1);
        double pauseMax = resultHelper.getDouble(pauseStatistics, "pauseMax", -1);
        Map<String, Object> heap = resultHelper.getMap(memoryStatistics, "heap");
        Map<String, Object> old = resultHelper.getMap(memoryStatistics, "old");
        Map<String, Object> metaspace = resultHelper.getMap(memoryStatistics, "metaspace");

        String summary = "GC metrics for " + fileView.originalName()
                         + ": p99 pause=" + resultHelper.formatMilliseconds(pauseP99)
                         + ", max pause=" + resultHelper.formatMilliseconds(pauseMax)
                         + ", throughput=" + resultHelper.formatPercent(throughput)
                         + ", heap peak=" + resultHelper.formatBytes(resultHelper.getLong(heap, "usedMax", -1)) + ".";

        List<McpFinding> findings = new ArrayList<>();
        if (pauseMax >= resultHelper.getDouble(config, "longPauseThreshold", Double.MAX_VALUE)) {
            findings.add(new McpFinding(
                    "high",
                    "Pause time exceeds threshold",
                    "The observed maximum pause is above the configured long pause threshold.",
                    resultHelper.orderedMap("pauseMax", pauseMax, "threshold", resultHelper.getDouble(config, "longPauseThreshold", -1))
            ));
        }
        if (throughput >= 0 && throughput < resultHelper.getDouble(config, "badThroughputThreshold", 100) / 100.0) {
            findings.add(new McpFinding(
                    "high",
                    "Low throughput",
                    "GC consumed more time than the configured throughput target allows.",
                    resultHelper.orderedMap("throughput", throughput, "threshold", resultHelper.getDouble(config, "badThroughputThreshold", -1))
            ));
        }

        resultHelper.addUsageFinding(findings, "Heap usage remains high after GC", heap,
                                     resultHelper.getDouble(config, "highHeapUsageThreshold", -1));
        resultHelper.addUsageFinding(findings, "Old generation usage remains high after GC", old,
                                     resultHelper.getDouble(config, "highOldUsageThreshold", -1));
        resultHelper.addUsageFinding(findings, "Metaspace usage remains high after GC", metaspace,
                                     resultHelper.getDouble(config, "highMetaspaceUsageThreshold", -1));

        List<String> recommendations = new ArrayList<>();
        if (findings.stream().anyMatch(f -> "high".equals(f.severity()))) {
            recommendations.add("Inspect the time range around the highest pause and the most saturated memory areas in the Jifa Web UI.");
        }
        recommendations.add("Compare these metrics with application SLOs before drawing a final conclusion.");

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "metadata", resultHelper.orderedMap(
                        "collector", resultHelper.getString(metadata, "collector"),
                        "logStyle", resultHelper.getString(metadata, "logStyle")
                ),
                "pauseStatistics", pauseStatistics,
                "memoryStatistics", memoryStatistics,
                "objectStatistics", objectStatistics
        );
        return resultHelper.analysisSummary(fileView, summary, findings, recommendations, evidence);
    }
}
