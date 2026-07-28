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

import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestJifaMcpGcLogToolService extends JifaMcpToolServiceTestSupport {

    @Test
    public void testAnalyzeGcLogSummary() {
        registerGcResponses();

        McpAnalysisSummary result = gcLogToolService.analyzeGcLogSummary(GC_FILE.uniqueName());

        assertThat(result.summary()).contains("collector=G1");
        assertThat(result.findings()).extracting(f -> f.title()).contains("longYoungGCPause");
        assertThat(result.recommendations()).isNotEmpty();
    }

    @Test
    public void testAnalyzeGcLogMetrics() {
        registerGcResponses();

        McpAnalysisSummary result = gcLogToolService.analyzeGcLogMetrics(GC_FILE.uniqueName());

        assertThat(result.summary()).contains("p99 pause");
        assertThat(result.findings()).extracting(f -> f.title())
                                     .contains("Pause time exceeds threshold", "Low throughput");
        assertThat(result.evidence()).containsKeys("pauseStatistics", "memoryStatistics", "objectStatistics");
    }

    @Test
    public void testAnalyzeGcLogSummaryWithWorkerStyleJsonBytes() {
        registerGcResponsesAsJsonBytes();

        McpAnalysisSummary result = gcLogToolService.analyzeGcLogSummary(GC_FILE.uniqueName());

        assertThat(result.summary()).contains("collector=G1");
        assertThat(result.findings()).extracting(f -> f.title()).contains("longYoungGCPause");
    }
}
