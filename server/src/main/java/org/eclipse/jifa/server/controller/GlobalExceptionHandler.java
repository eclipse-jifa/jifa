/********************************************************************************
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.jifa.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.common.domain.exception.ErrorCodeAccessor;
import org.eclipse.jifa.common.domain.exception.ValidationException;
import org.eclipse.jifa.server.domain.exception.ElasticWorkerNotReadyException;
import org.eclipse.jifa.server.enums.ServerErrorCode;
import org.eclipse.jifa.server.util.ErrorUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler
    @ResponseBody
    public void handleHttpRequestException(Throwable throwable, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log(throwable, request);

        // Handle WebClient response exceptions
        if (throwable instanceof WebClientResponseException e) {
            response.setStatus(e.getStatusCode().value());
            response.getOutputStream().write(e.getResponseBodyAsByteArray());
            return;
        }
        
        // Handle file upload size exceeded exceptions
        if (throwable instanceof MaxUploadSizeExceededException e) {
            handleFileUploadSizeExceeded(e, response);
            return;
        }
        
        // Handle other exceptions
        response.setStatus(getStatusOf(throwable));
        response.getOutputStream().write(ErrorUtil.toJson(throwable));
    }

    /**
     * Handle file upload size exceeded exceptions with detailed error message
     */
    private void handleFileUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletResponse response) throws IOException {
        String maxSize = formatFileSize(e.getMaxUploadSize());
        String actualSize = extractActualFileSize(e.getMessage(), maxSize);
        String errorMessage = buildFileSizeErrorMessage(actualSize, maxSize);
        
        response.setStatus(getStatusOf(e));
        response.getOutputStream().write(ErrorUtil.toJson(ServerErrorCode.FILE_TOO_LARGE, errorMessage));
    }

    /**
     * Extract actual file size from exception message
     */
    private String extractActualFileSize(String exceptionMessage, String fallbackSize) {
        if (exceptionMessage == null || !exceptionMessage.contains("size (")) {
            return fallbackSize;
        }
        
        try {
            int start = exceptionMessage.indexOf("size (") + 6;
            int end = exceptionMessage.indexOf(")", start);
            if (start > 6 && end > start) {
                long actualSizeBytes = Long.parseLong(exceptionMessage.substring(start, end));
                return formatFileSize(actualSizeBytes);
            }
        } catch (Exception ignored) {
            // Parsing failed, use fallback
        }
        
        return fallbackSize;
    }

    /**
     * Build file size error message with actual and maximum sizes
     */
    private String buildFileSizeErrorMessage(String actualSize, String maxSize) {
        if (actualSize.equals(maxSize)) {
            return String.format("File size exceeds limit. Maximum allowed size: %s", maxSize);
        }
        return String.format("File size exceeds limit. Actual size: %s, Maximum allowed size: %s", actualSize, maxSize);
    }

    private void log(Throwable throwable, HttpServletRequest request) {
        if (throwable instanceof ElasticWorkerNotReadyException) {
            return;
        }

        if (throwable instanceof MissingServletRequestParameterException ||
            throwable instanceof IllegalArgumentException ||
            throwable instanceof AuthenticationException ||
            throwable instanceof ValidationException ||
            throwable instanceof WebClientResponseException) {
            log.error(throwable.getMessage());
        } else {
            log.error("Error occurred when handling http request '{}'", request.getRequestURI(), throwable);
        }
    }

    private int getStatusOf(Throwable throwable) {
        if (throwable instanceof MissingServletRequestParameterException) {
            return 400;
        }
        if (throwable instanceof AuthenticationException || throwable instanceof AccessDeniedException) {
            return 401;
        }
        if (throwable instanceof MaxUploadSizeExceededException) {
            return 413; // Payload Too Large
        }
        if (throwable instanceof ErrorCodeAccessor errorCodeAccessor) {
            if (ServerErrorCode.ACCESS_DENIED == errorCodeAccessor.getErrorCode()) {
                return 401;
            }
        }
        return 500;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "Unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
