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
import java.util.List;
import java.util.Map;

@Component
final class JifaMcpHeapDumpToolService {

    /**
     * Treat retained slices at or above 10% of the dump view as worth surfacing in the MCP summary.
     */
    private static final double LARGE_RETAINED_SLICE_THRESHOLD_PERCENT = 10.0;

    private final JifaMcpAnalysisInvoker analysisInvoker;

    private final JifaMcpFileResolver fileResolver;

    private final JifaMcpResultHelper resultHelper;

    JifaMcpHeapDumpToolService(JifaMcpAnalysisInvoker analysisInvoker,
                               JifaMcpFileResolver fileResolver,
                               JifaMcpResultHelper resultHelper) {
        this.analysisInvoker = analysisInvoker;
        this.fileResolver = fileResolver;
        this.resultHelper = resultHelper;
    }

    McpAnalysisSummary analyzeHeapDumpSummary(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.HEAP_DUMP);
        Map<String, Object> details = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "details", null);
        List<Map<String, Object>> biggestObjects = analysisInvoker.invokeAnalysisAsListOfMaps(fileView.type(), uniqueName,
                                                                                              "biggestObjects", null);
        Map<String, Object> classLoaderSummary = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName,
                                                                                     "classLoaderExplorer.summary", null);

        String summary = "Heap dump summary for " + fileView.originalName()
                         + ": used heap=" + resultHelper.formatBytes(resultHelper.getLong(details, "usedHeapSize", -1))
                         + ", objects=" + resultHelper.getLong(details, "numberOfObjects", -1)
                         + ", classes=" + resultHelper.getLong(details, "numberOfClasses", -1)
                         + ", classloaders=" + resultHelper.getLong(details, "numberOfClassLoaders", -1) + ".";

        List<McpFinding> findings = new ArrayList<>();
        Map<String, Object> biggestObject = biggestObjects.isEmpty() ? null : biggestObjects.get(0);
        if (biggestObject != null
            && resultHelper.getDouble(biggestObject, "value", 0) >= LARGE_RETAINED_SLICE_THRESHOLD_PERCENT) {
            findings.add(new McpFinding(
                    "medium",
                    "Large retained object slice detected",
                    "A single retained slice occupies a noticeable portion of the dump.",
                    resultHelper.orderedMap(
                            "label", resultHelper.getString(biggestObject, "label"),
                            "value", resultHelper.getDouble(biggestObject, "value", -1),
                            "description", resultHelper.getString(biggestObject, "description")
                    )
            ));
        }

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "details", details,
                "classLoaderSummary", classLoaderSummary,
                "largestObjects", biggestObjects.stream().limit(10).toList()
        );
        return resultHelper.analysisSummary(
                fileView,
                summary,
                findings,
                List.of(
                        "Use `analyze_heap_dump_hotspots` to inspect the largest retained classes and objects.",
                        "Use `analyze_heap_dump_thread_details` if you want to inspect retained heap around thread objects."
                ),
                evidence
        );
    }

    McpAnalysisSummary analyzeHeapDumpHotspots(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.HEAP_DUMP);
        Map<String, Object> details = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "details", null);
        List<Map<String, Object>> biggestObjects = analysisInvoker.invokeAnalysisAsListOfMaps(fileView.type(), uniqueName,
                                                                                              "biggestObjects", null);
        Map<String, Object> histogram = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "histogram",
                                                                            resultHelper.orderedMap(
                                                                                    "groupBy", "BY_CLASS",
                                                                                    "sortBy", "retainedSize",
                                                                                    "ascendingOrder", false,
                                                                                    "searchText", "",
                                                                                    "searchType", "BY_NAME",
                                                                                    "page", 1,
                                                                                    "pageSize", 20
                                                                            ));
        List<Map<String, Object>> topClasses = resultHelper.getListOfMaps(histogram.get("data"));

        String topClassName = topClasses.isEmpty() ? "n/a" : resultHelper.getString(topClasses.get(0), "label");
        String summary = "Heap hotspots for " + fileView.originalName()
                         + ": top retained class=" + topClassName
                         + ", used heap=" + resultHelper.formatBytes(resultHelper.getLong(details, "usedHeapSize", -1))
                         + ", sampled classes=" + topClasses.size() + ".";

        List<McpFinding> findings = new ArrayList<>();
        Map<String, Object> topClass = topClasses.isEmpty() ? null : topClasses.get(0);
        if (topClass != null) {
            findings.add(new McpFinding(
                    "medium",
                    "Largest retained class slice",
                    "The first histogram entry is the class currently retaining the most heap in the sampled results.",
                    resultHelper.orderedMap(
                            "label", resultHelper.getString(topClass, "label"),
                            "retainedSize", resultHelper.getLong(topClass, "retainedSize", -1),
                            "numberOfObjects", resultHelper.getLong(topClass, "numberOfObjects", -1)
                    )
            ));
        }

        Map<String, Object> largestObject = biggestObjects.isEmpty() ? null : biggestObjects.get(0);
        if (largestObject != null
            && resultHelper.getDouble(largestObject, "value", 0) >= LARGE_RETAINED_SLICE_THRESHOLD_PERCENT) {
            findings.add(new McpFinding(
                    "medium",
                    "Largest single retained object slice",
                    "The retained slice reported by `biggestObjects` is large enough to be worth manual inspection.",
                    resultHelper.orderedMap(
                            "label", resultHelper.getString(largestObject, "label"),
                            "value", resultHelper.getDouble(largestObject, "value", -1)
                    )
            ));
        }

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "topClasses", topClasses,
                "largestObjects", biggestObjects.stream().limit(10).toList()
        );
        return resultHelper.analysisSummary(
                fileView,
                summary,
                findings,
                List.of("Inspect the top classes and object slices in the Jifa Web UI to confirm reachability and ownership."),
                evidence
        );
    }

    McpAnalysisSummary analyzeHeapDumpThreadDetails(String uniqueName) {
        FileView fileView = fileResolver.requireFile(uniqueName, FileType.HEAP_DUMP);
        Map<String, Object> threadSummary = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "threadsSummary",
                                                                                resultHelper.orderedMap("searchText", "", "searchType", "BY_NAME"));
        Map<String, Object> threads = analysisInvoker.invokeAnalysisAsMap(fileView.type(), uniqueName, "threads",
                                                                          resultHelper.orderedMap(
                                                                                  "sortBy", "retainedHeap",
                                                                                  "ascendingOrder", false,
                                                                                  "searchText", "",
                                                                                  "searchType", "BY_NAME",
                                                                                  "page", 1,
                                                                                  "pageSize", 10
                                                                          ));
        List<Map<String, Object>> topThreads = resultHelper.getListOfMaps(threads.get("data"));
        List<Map<String, Object>> stackSamples = new ArrayList<>();
        for (Map<String, Object> thread : topThreads.stream().limit(3).toList()) {
            if (!resultHelper.getBoolean(thread, "hasStack", false)) {
                continue;
            }
            int objectId = resultHelper.getInt(thread, "objectId", -1);
            List<Map<String, Object>> stackTrace = analysisInvoker.invokeAnalysisAsListOfMaps(fileView.type(), uniqueName,
                                                                                              "stackTrace",
                                                                                              resultHelper.orderedMap("objectId", objectId));
            stackSamples.add(resultHelper.orderedMap(
                    "thread", resultHelper.orderedMap(
                            "objectId", objectId,
                            "name", resultHelper.getString(thread, "name")
                    ),
                    "frames", stackTrace.stream().limit(5).toList()
            ));
        }

        String summary = "Heap thread details for " + fileView.originalName()
                         + ": thread objects=" + resultHelper.getLong(threadSummary, "totalSize", -1)
                         + ", retained heap=" + resultHelper.formatBytes(resultHelper.getLong(threadSummary, "retainedHeap", -1))
                         + ", sampled top threads=" + topThreads.size() + ".";

        List<McpFinding> findings = new ArrayList<>();
        Map<String, Object> firstThread = topThreads.isEmpty() ? null : topThreads.get(0);
        if (firstThread != null && resultHelper.getLong(firstThread, "retainedSize", 0) > 0) {
            findings.add(new McpFinding(
                    "medium",
                    "Top thread object by retained heap",
                    "The first sampled thread retains the most heap among thread objects in the current view.",
                    resultHelper.orderedMap(
                            "name", resultHelper.getString(firstThread, "name"),
                            "retainedSize", resultHelper.getLong(firstThread, "retainedSize", -1),
                            "contextClassLoader", resultHelper.getString(firstThread, "contextClassLoader")
                    )
            ));
        }

        Map<String, Object> evidence = resultHelper.orderedMap(
                "fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)),
                "threadSummary", threadSummary,
                "topThreads", topThreads,
                "stackSamples", stackSamples
        );
        return resultHelper.analysisSummary(
                fileView,
                summary,
                findings,
                List.of("Inspect the sampled thread stacks and locals in the Jifa Web UI if you suspect thread-local retention."),
                evidence
        );
    }
}
