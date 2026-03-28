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
import org.eclipse.jifa.server.mcp.dto.McpFileInfo;
import org.eclipse.jifa.server.service.FileService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
final class JifaMcpFileResolver {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FileService fileService;

    private final JifaMcpResultHelper resultHelper;

    JifaMcpFileResolver(FileService fileService, JifaMcpResultHelper resultHelper) {
        this.fileService = fileService;
        this.resultHelper = resultHelper;
    }

    PageView<FileView> getUserFileViews(FileType type, int page, int pageSize) {
        return fileService.getUserFileViews(type, page, pageSize);
    }

    FileView getFileViewByUniqueName(String uniqueName) {
        return fileService.getFileViewByUniqueName(uniqueName);
    }

    FileView requireFile(String uniqueName, FileType expectedType) {
        FileView fileView = getFileViewByUniqueName(uniqueName);
        if (expectedType != null && fileView.type() != expectedType) {
            throw new IllegalArgumentException(
                    "File '" + uniqueName + "' is of type " + fileView.type().getApiNamespace()
                    + ", expected " + expectedType.getApiNamespace()
            );
        }
        return fileView;
    }

    McpFileInfo toFileInfo(FileView fileView) {
        return new McpFileInfo(
                fileView.id(),
                fileView.uniqueName(),
                fileView.originalName(),
                fileView.type().getApiNamespace(),
                resultHelper.displayName(fileView.type()),
                fileView.size(),
                fileView.createdTime() == null ? null : FILE_TIME_FORMATTER.format(fileView.createdTime()),
                supportedAnalysisCapabilities(fileView.type())
        );
    }

    FileType parseFileType(String type, boolean allowNull) {
        String normalized = StringUtils.trimToNull(type);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new IllegalArgumentException("File type is required");
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        for (FileType fileType : FileType.values()) {
            if (lower.equals(fileType.getApiNamespace())
                || lower.equals(fileType.name().toLowerCase(Locale.ROOT))
                || lower.equals(fileType.getAnalysisUrlPath())) {
                return fileType;
            }
        }
        throw new IllegalArgumentException("Unsupported file type: " + type);
    }

    private List<String> supportedAnalysisCapabilities(FileType fileType) {
        List<String> capabilities = new ArrayList<>();
        capabilities.add("analyze_file_summary");
        switch (fileType) {
            case GC_LOG -> {
                capabilities.add("analyze_gc_log_summary");
                capabilities.add("analyze_gc_log_metrics");
            }
            case THREAD_DUMP -> {
                capabilities.add("analyze_thread_dump_summary");
                capabilities.add("analyze_thread_dump_details");
                capabilities.add("analyze_thread_dump_blocking_chains");
                capabilities.add("get_thread_dump_thread_content");
            }
            case HEAP_DUMP -> {
                capabilities.add("analyze_heap_dump_summary");
                capabilities.add("analyze_heap_dump_hotspots");
                capabilities.add("analyze_heap_dump_thread_details");
            }
            case JFR_FILE -> {
            }
        }
        return capabilities;
    }
}
