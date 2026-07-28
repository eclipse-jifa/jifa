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

import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.eclipse.jifa.server.mcp.dto.McpFileInfo;
import org.eclipse.jifa.server.mcp.dto.McpListMyFilesResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class TestJifaMcpFileToolService extends JifaMcpToolServiceTestSupport {

    @Test
    public void testListMyFiles() {
        when(fileService.getUserFileViews(null, 2, 1))
                .thenReturn(new PageView<>(2, 1, 3, List.of(THREAD_FILE)));

        McpListMyFilesResult result = fileToolService.listMyFiles(null, 2, 1);

        assertThat(result.totalSize()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(1);
        assertThat(result.returnedSize()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
        assertThat(result.files()).extracting(McpFileInfo::uniqueName)
                                  .containsExactly("thread-file");
        assertThat(result.files().get(0).analysisCapabilities()).contains("analyze_thread_dump_summary");
    }

    @Test
    public void testGetFileInfo() {
        McpFileInfo result = fileToolService.getFileInfo(GC_FILE.uniqueName());

        assertThat(result.uniqueName()).isEqualTo("gc-file");
        assertThat(result.typeId()).isEqualTo("gc-log");
        assertThat(result.analysisCapabilities()).contains("analyze_gc_log_metrics");
    }

    @Test
    public void testAnalyzeFileSummaryDispatchesToGcLogSummary() {
        registerGcResponses();

        McpAnalysisSummary result = fileToolService.analyzeFileSummary(GC_FILE.uniqueName());

        assertThat(result.file()).isEqualTo("gc-file");
        assertThat(result.summary()).contains("GC log summary");
        assertThat(result.findings()).isNotEmpty();
    }

    @Test
    public void testAnalyzeFileSummaryForJfrFallsBackToUnsupportedMessage() {
        McpAnalysisSummary result = fileToolService.analyzeFileSummary(JFR_FILE.uniqueName());

        assertThat(result.fileTypeId()).isEqualTo("jfr-file");
        assertThat(result.summary()).contains("JFR");
        assertThat(result.findings()).hasSize(1);
    }
}
