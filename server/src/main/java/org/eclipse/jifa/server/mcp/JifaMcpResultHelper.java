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
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.eclipse.jifa.server.mcp.dto.McpFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * responsible for MCP result assembly, Map/List reading, and display formatting.
 */
@Component
final class JifaMcpResultHelper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    JifaMcpResultHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    McpAnalysisSummary analysisSummary(FileView fileView,
                                       String summary,
                                       List<McpFinding> findings,
                                       List<String> recommendations,
                                       Map<String, Object> evidence) {
        return new McpAnalysisSummary(
                fileView.uniqueName(),
                fileView.originalName(),
                fileView.type().getApiNamespace(),
                displayName(fileView.type()),
                summary,
                findings,
                recommendations,
                evidence
        );
    }

    Map<String, Object> buildGcAnalysisConfig(Map<String, Object> metadata) {
        Map<String, Object> metadataConfig = getMap(metadata, "analysisConfig");
        if (metadataConfig != null) {
            return metadataConfig;
        }

        boolean pauseless = getBoolean(metadata, "pauseless", false);
        boolean generational = getBoolean(metadata, "generational", true);
        return orderedMap(
                "timeRange", resolveGcAnalysisRange(metadata),
                "longPauseThreshold", pauseless ? 30 : 400,
                "longConcurrentThreshold", 30000,
                "youngGCFrequentIntervalThreshold", 1000,
                "oldGCFrequentIntervalThreshold", 15000,
                "fullGCFrequentIntervalThreshold", generational ? 60000 : 2000,
                "highOldUsageThreshold", 80,
                "highHumongousUsageThreshold", 50,
                "highHeapUsageThreshold", 60,
                "highMetaspaceUsageThreshold", 80,
                "smallGenerationThreshold", 10,
                "highPromotionThreshold", 3,
                "badThroughputThreshold", 90,
                "tooManyOldGCThreshold", 20,
                "highSysThreshold", 50,
                "lowUsrThreshold", 100
        );
    }

    Map<String, Object> resolveGcAnalysisRange(Map<String, Object> metadata) {
        Map<String, Object> metadataConfig = getMap(metadata, "analysisConfig");
        Map<String, Object> timeRange = metadataConfig == null ? null : getMap(metadataConfig, "timeRange");
        if (timeRange != null) {
            return timeRange;
        }
        return orderedMap(
                "start", getDouble(metadata, "startTime", 0),
                "end", getDouble(metadata, "endTime", 0)
        );
    }

    void addUsageFinding(List<McpFinding> findings,
                         String title,
                         Map<String, Object> area,
                         double thresholdPercent) {
        if (area == null || thresholdPercent < 0) {
            return;
        }
        long capacity = getLong(area, "capacityAvg", -1);
        if (capacity <= 0) {
            return;
        }
        long used = getLong(area, "usedAvgAfterFullGC", -1);
        if (used < 0) {
            used = getLong(area, "usedMax", -1);
        }
        if (used < 0) {
            return;
        }

        double percent = used * 100.0 / capacity;
        if (percent >= thresholdPercent) {
            findings.add(new McpFinding(
                    "medium",
                    title,
                    "Observed usage is above the configured threshold.",
                    orderedMap(
                            "used", used,
                            "capacity", capacity,
                            "usagePercent", percent,
                            "thresholdPercent", thresholdPercent
                    )
            ));
        }
    }

    List<Map<String, Object>> mergeLists(List<List<Map<String, Object>>> lists) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<Map<String, Object>> list : lists) {
            result.addAll(list);
        }
        return result;
    }

    int monitorStateCount(Map<String, Object> counts, String state) {
        if (counts == null) {
            return 0;
        }
        return getInt(counts, state, 0);
    }

    int severityScore(String severity) {
        return switch (severity) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    String formatMonitorReference(Map<String, Object> monitor) {
        long address = getLong(monitor, "address", -1);
        String clazz = getString(monitor, "class");
        if (StringUtils.isBlank(clazz)) {
            clazz = getString(monitor, "clazz");
        }
        String suffix = address >= 0 ? "@0x" + Long.toHexString(address) : "";
        return defaultIfBlank(clazz, "monitor") + suffix;
    }

    String displayName(FileType fileType) {
        return switch (fileType) {
            case HEAP_DUMP -> "Heap Dump";
            case GC_LOG -> "GC Log";
            case THREAD_DUMP -> "Thread Dump";
            case JFR_FILE -> "JFR";
        };
    }

    List<String> extractRecommendations(Map<String, Object> diagnoseInfo) {
        Map<String, Object> mostSeriousProblem = getMap(diagnoseInfo, "mostSeriousProblem");
        if (mostSeriousProblem == null) {
            return List.of();
        }
        return getList(mostSeriousProblem.get("suggestions")).stream()
                                                             .map(this::formatI18n)
                                                             .filter(StringUtils::isNotBlank)
                                                             .toList();
    }

    Map<String, Object> orderedMap(Object... items) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i += 2) {
            Object value = items[i + 1];
            if (value != null) {
                map.put(String.valueOf(items[i]), value);
            }
        }
        return map;
    }

    Map<String, Object> toMap(Object source) {
        return objectMapper.convertValue(source, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    List<Object> getList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    List<Map<String, Object>> getListOfMaps(Object value) {
        return objectMapper.convertValue(value == null ? List.of() : value, LIST_OF_MAPS_TYPE);
    }

    Map<String, Object> getMap(Map<String, Object> source, String key) {
        return getMap(source == null ? null : source.get(key));
    }

    Map<String, Object> getMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    List<Object> getList(Object value, String key) {
        Map<String, Object> map = getMap(value);
        return map == null ? List.of() : getList(map.get(key));
    }

    int sum(List<Object> values) {
        return values.stream()
                     .filter(Objects::nonNull)
                     .mapToInt(value -> ((Number) value).intValue())
                     .sum();
    }

    int getCountAt(List<Object> values, int index) {
        if (index < 0 || index >= values.size()) {
            return 0;
        }
        Object value = values.get(index);
        return value instanceof Number number ? number.intValue() : 0;
    }

    String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    int getInt(Map<String, Object> map, String key, int defaultValue) {
        Number number = getNumber(map, key);
        return number == null ? defaultValue : number.intValue();
    }

    long getLong(Map<String, Object> map, String key, long defaultValue) {
        Number number = getNumber(map, key);
        return number == null ? defaultValue : number.longValue();
    }

    double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Number number = getNumber(map, key);
        return number == null ? defaultValue : number.doubleValue();
    }

    boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    Number getNumber(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof Number number ? number : null;
    }

    String formatI18n(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return String.valueOf(value);
        }
        Object name = map.get("name");
        Object params = map.get("params");
        if (!(params instanceof Map<?, ?> paramMap) || paramMap.isEmpty()) {
            return name == null ? null : String.valueOf(name);
        }
        return String.valueOf(name) + "(" + paramMap.entrySet().stream()
                                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                                    .collect(Collectors.joining(", "))
                + ")";
    }

    String formatBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

    String formatMilliseconds(double milliseconds) {
        if (milliseconds < 0) {
            return "unknown";
        }
        if (milliseconds >= 1000) {
            return String.format(Locale.ROOT, "%.2f s", milliseconds / 1000.0);
        }
        return String.format(Locale.ROOT, "%.0f ms", milliseconds);
    }

    String formatPercent(double value) {
        if (value < 0) {
            return "unknown";
        }
        double normalized = value <= 1.0 ? value * 100.0 : value;
        return String.format(Locale.ROOT, "%.1f%%", normalized);
    }

    String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
