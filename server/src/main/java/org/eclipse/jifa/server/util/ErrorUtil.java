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
package org.eclipse.jifa.server.util;

import com.google.gson.JsonObject;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.common.domain.exception.ErrorCode;
import org.eclipse.jifa.common.domain.exception.ErrorCodeAccessor;
import org.eclipse.jifa.common.enums.CommonErrorCode;
import org.eclipse.jifa.server.Constant;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
public class ErrorUtil {

    public static byte[] toJson(Throwable throwable) {
        return buildJsonBytes(getErrorCodeOf(throwable).name(), getMessage(throwable));
    }

    public static byte[] toJson(ErrorCode errorCode, String message) {
        return buildJsonBytes(errorCode.name(), message);
    }
    
    private static byte[] buildJsonBytes(String errorCode, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("errorCode", errorCode);
        json.addProperty("message", message);
        return json.toString().getBytes(Constant.CHARSET);
    }

    private static ErrorCode getErrorCodeOf(Throwable throwable) {
        if (throwable instanceof ErrorCodeAccessor errorCodeAccessor) {
            return errorCodeAccessor.getErrorCode();
        }
        if (throwable instanceof MissingServletRequestParameterException || throwable instanceof IllegalArgumentException) {
            return CommonErrorCode.ILLEGAL_ARGUMENT;
        }
        while ((throwable = throwable.getCause()) != null) {
            if (throwable instanceof ErrorCodeAccessor errorCodeAccessor) {
                return errorCodeAccessor.getErrorCode();
            }
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }

    private static String getMessage(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            return e.getResponseBodyAsString(Constant.CHARSET);
        }

        Throwable cause = throwable;
        String message;
        String fallbackMessage = null;
        do {
            message = map(cause);
            if (cause.getMessage() != null) {
                fallbackMessage = cause.getMessage();
            }
            if (message != null || cause.getCause() == null) {
                break;
            }
            cause = cause.getCause();
        } while (true);

        if (message == null) {
            message = fallbackMessage;
        }

        if (message == null) {
            if (throwable instanceof ErrorCodeAccessor errorCodeAccessor) {
                message = errorCodeAccessor.getErrorCode().name();
            } else {
                message = "Error occurred";
            }
        }

        try {
            return message;
        } catch (Throwable t) {
            log.error("Error occurred while converting throwable to data", t);
            return null;
        }
    }

    private static String map(Throwable t) {
        if (t instanceof ReadTimeoutException) {
            return "Timeout";
        }
        return null;
    }
}
