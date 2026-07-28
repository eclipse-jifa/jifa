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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.mcp.dto.McpAnalysisSummary;
import org.eclipse.jifa.server.mcp.dto.McpFileInfo;
import org.eclipse.jifa.server.mcp.dto.McpFinding;
import org.eclipse.jifa.server.mcp.dto.McpListMyFilesResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
final class JifaMcpFileToolService {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 100;

    private static final int MAX_PAGE_SIZE = 100;

    private final JifaMcpFileResolver fileResolver;

    private final JifaMcpResultHelper resultHelper;

    private final JifaMcpGcLogToolService gcLogToolService;

    private final JifaMcpThreadDumpToolService threadDumpToolService;

    private final JifaMcpHeapDumpToolService heapDumpToolService;

    JifaMcpFileToolService(JifaMcpFileResolver fileResolver,
                           JifaMcpResultHelper resultHelper,
                           JifaMcpGcLogToolService gcLogToolService,
                           JifaMcpThreadDumpToolService threadDumpToolService,
                           JifaMcpHeapDumpToolService heapDumpToolService) {
        this.fileResolver = fileResolver;
        this.resultHelper = resultHelper;
        this.gcLogToolService = gcLogToolService;
        this.threadDumpToolService = threadDumpToolService;
        this.heapDumpToolService = heapDumpToolService;
    }

    McpListMyFilesResult listMyFiles(String type, Integer page, Integer pageSize) {
        FileType parsedType = fileResolver.parseFileType(type, true);
        int resolvedPage = validatePage(page);
        int resolvedPageSize = validatePageSize(pageSize);
        PageView<FileView> pageView = fileResolver.getUserFileViews(parsedType, resolvedPage, resolvedPageSize);
        List<McpFileInfo> files = pageView.getData().stream().map(fileResolver::toFileInfo).toList();
        return new McpListMyFilesResult(
                StringUtils.trimToNull(type),
                parsedType == null ? null : parsedType.getApiNamespace(),
                pageView.getPage(),
                pageView.getPageSize(),
                pageView.getTotalSize(),
                files.size(),
                pageView.getTotalSize() > files.size(),
                files
        );
    }

    McpFileInfo getFileInfo(String uniqueName) {
        return fileResolver.toFileInfo(fileResolver.getFileViewByUniqueName(uniqueName));
    }

    McpAnalysisSummary analyzeFileSummary(String uniqueName) {
        FileView fileView = fileResolver.getFileViewByUniqueName(uniqueName);
        return switch (fileView.type()) {
            case GC_LOG -> gcLogToolService.analyzeGcLogSummary(uniqueName);
            case THREAD_DUMP -> threadDumpToolService.analyzeThreadDumpSummary(uniqueName);
            case HEAP_DUMP -> heapDumpToolService.analyzeHeapDumpSummary(uniqueName);
            case JFR_FILE -> unsupportedJfrSummary(fileView);
        };
    }

    private McpAnalysisSummary unsupportedJfrSummary(FileView fileView) {
        return resultHelper.analysisSummary(
                fileView,
                "JFR files are recognized by Jifa, but issue #382 intentionally keeps JFR-specific MCP tools out of scope.",
                List.of(new McpFinding(
                        "low",
                        "JFR MCP tools are not exposed in this PR",
                        "The experimental MCP endpoint only exposes file, GC log, thread dump, and heap dump tools.",
                        resultHelper.orderedMap("fileType", fileView.type().getApiNamespace())
                )),
                List.of("Open this file in the Jifa Web UI for JFR-specific analysis."),
                resultHelper.orderedMap("fileInfo", resultHelper.toMap(fileResolver.toFileInfo(fileView)))
        );
    }

    private int validatePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new IllegalArgumentException("Argument 'page' must be greater than or equal to 1.");
        }
        return page;
    }

    private int validatePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Argument 'pageSize' must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return pageSize;
    }
}
