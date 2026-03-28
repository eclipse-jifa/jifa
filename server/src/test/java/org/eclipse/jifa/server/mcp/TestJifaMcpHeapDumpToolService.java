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

public class TestJifaMcpHeapDumpToolService extends JifaMcpToolServiceTestSupport {

    @Test
    public void testAnalyzeHeapDumpSummary() {
        registerHeapDumpResponses();

        McpAnalysisSummary result = heapDumpToolService.analyzeHeapDumpSummary(HEAP_FILE.uniqueName());

        assertThat(result.summary()).contains("used heap");
        assertThat(result.evidence()).containsKeys("details", "classLoaderSummary", "largestObjects");
    }

    @Test
    public void testAnalyzeHeapDumpHotspots() {
        registerHeapDumpResponses();

        McpAnalysisSummary result = heapDumpToolService.analyzeHeapDumpHotspots(HEAP_FILE.uniqueName());

        assertThat(result.summary()).contains("top retained class=com.example.Cache");
        assertThat(result.findings()).isNotEmpty();
    }

    @Test
    public void testAnalyzeHeapDumpThreadDetails() {
        registerHeapDumpResponses();

        McpAnalysisSummary result = heapDumpToolService.analyzeHeapDumpThreadDetails(HEAP_FILE.uniqueName());

        assertThat(result.summary()).contains("thread objects=2");
        assertThat(result.evidence()).containsKeys("threadSummary", "topThreads", "stackSamples");
    }
}
