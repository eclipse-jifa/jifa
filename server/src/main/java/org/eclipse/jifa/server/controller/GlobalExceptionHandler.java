/********************************************************************************
 * Copyright (c) 2023, 2025 Contributors to the Eclipse Foundation
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
import org.apache.commons.io.FileUtils;
import org.eclipse.jifa.common.domain.exception.ErrorCodeAccessor;
import org.eclipse.jifa.common.domain.exception.ValidationException;
import org.eclipse.jifa.server.domain.exception.ElasticWorkerNotReadyException;
import org.eclipse.jifa.server.enums.ServerErrorCode;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.util.ErrorUtil;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.lang.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.unit.DataSize;
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

    private final MultipartProperties multipartProperties;

    public GlobalExceptionHandler(@Nullable MultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

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
     * Handle file upload size exceeded exceptions by reading actual configuration
     * Uses the same logic as HandshakeController to ensure consistency
     */
    private void handleFileUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletResponse response) throws IOException {
        // Use the same configuration logic as HandshakeController
        long actualMaxSize;
        if (multipartProperties != null) {
            DataSize maxFileSize = multipartProperties.getMaxFileSize();
            if (maxFileSize != null) {
                actualMaxSize = maxFileSize.toBytes();
            } else {
                // This should rarely happen, but fallback to unlimited if DataSize is null
                actualMaxSize = Constant.DEFAULT_MAX_UPLOAD_SIZE; // Long.MAX_VALUE (unlimited)
            }
        } else {
            // This should rarely happen in a Spring Boot app, but fallback to unlimited
            actualMaxSize = Constant.DEFAULT_MAX_UPLOAD_SIZE; // Long.MAX_VALUE (unlimited)
        }
        
        String maxSizeFormatted;
        if (actualMaxSize == Long.MAX_VALUE) {
            maxSizeFormatted = "unlimited";
        } else {
            maxSizeFormatted = FileUtils.byteCountToDisplaySize(actualMaxSize);
        }
        
        // Log for debugging - show both the exception's maxUploadSize (-1) and actual config
        log.warn("File upload size exceeded. Exception.getMaxUploadSize(): {} (unreliable), Actual configured max-file-size: {} bytes ({})", 
                 e.getMaxUploadSize(), actualMaxSize == Long.MAX_VALUE ? "unlimited" : actualMaxSize, maxSizeFormatted);
        
        response.setStatus(200); // Use 200 to simplify frontend handling
        // Return clear error message with actual configured limit
        String errorMessage = "File size exceeds the maximum allowed size of " + maxSizeFormatted;
        response.getOutputStream().write(ErrorUtil.toJson(ServerErrorCode.FILE_TOO_LARGE, errorMessage));
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
        if (throwable instanceof ErrorCodeAccessor errorCodeAccessor) {
            if (ServerErrorCode.ACCESS_DENIED == errorCodeAccessor.getErrorCode()) {
                return 401;
            }
        }
        return 500;
    }

}
