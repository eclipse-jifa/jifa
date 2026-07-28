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
import org.eclipse.jifa.server.mcp.dto.McpThreadContentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestJifaMcpThreadDumpToolService extends JifaMcpToolServiceTestSupport {

    @Test
    public void testAnalyzeThreadDumpSummary() {
        registerThreadDumpResponses();

        McpAnalysisSummary result = threadDumpToolService.analyzeThreadDumpSummary(THREAD_FILE.uniqueName());

        assertThat(result.summary()).contains("deadlocks=1");
        assertThat(result.findings()).extracting(f -> f.title())
                                     .contains("Deadlock detected", "Blocked Java threads detected");
    }

    @Test
    public void testAnalyzeThreadDumpDetails() {
        registerThreadDumpResponses();

        McpAnalysisSummary result = threadDumpToolService.analyzeThreadDumpDetails(THREAD_FILE.uniqueName());

        assertThat(result.summary()).contains("sampled 2 thread(s)");
        assertThat(result.evidence()).containsKeys("sampleThreads", "sampleMonitors");
    }

    @Test
    public void testAnalyzeThreadDumpBlockingChains() {
        registerThreadDumpResponses();

        McpAnalysisSummary result = threadDumpToolService.analyzeThreadDumpBlockingChains(THREAD_FILE.uniqueName());

        assertThat(result.summary()).contains("detected 1 monitor");
        assertThat(result.findings()).extracting(f -> f.title())
                                     .contains("Blocking chain on java.lang.Object@0x2a");
    }

    @Test
    public void testGetThreadDumpThreadContent() {
        registerThreadDumpResponses();

        McpThreadContentResult result = threadDumpToolService.getThreadDumpThreadContent(THREAD_FILE.uniqueName(), 101);

        assertThat(result.threadId()).isEqualTo(101);
        assertThat(result.lineCount()).isEqualTo(2);
        assertThat(result.content()).contains("java.lang.Thread.State");
    }

    @Test
    public void testAnalyzeThreadDumpDetailsWithWorkerStyleJsonBytes() {
        registerThreadDumpResponsesAsJsonBytes();

        McpAnalysisSummary result = threadDumpToolService.analyzeThreadDumpDetails(THREAD_FILE.uniqueName());

        assertThat(result.summary()).contains("sampled 2 thread(s)");
        assertThat(result.evidence()).containsKeys("sampleThreads", "sampleMonitors");
    }

    @Test
    public void testGetThreadDumpThreadContentWithWorkerStyleJsonBytes() {
        registerThreadDumpResponsesAsJsonBytes();

        McpThreadContentResult result = threadDumpToolService.getThreadDumpThreadContent(THREAD_FILE.uniqueName(), 101);

        assertThat(result.threadId()).isEqualTo(101);
        assertThat(result.content()).contains("RUNNABLE");
    }
}
