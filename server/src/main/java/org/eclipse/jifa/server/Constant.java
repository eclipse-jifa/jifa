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
package org.eclipse.jifa.server;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface Constant extends org.eclipse.jifa.common.Constant {

    int DEFAULT_PORT = 8102;

    String COOKIE_JIFA_TOKEN_KEY = "jifa-token";

    String HTTP_API_PREFIX = "/jifa-api";

    String HTTP_ANALYSIS_API_MAPPING = "/analysis";

    String HTTP_HEALTH_CHECK_MAPPING = "/health-check";

    String HTTP_HANDSHAKE_MAPPING = "/handshake";

    String HTTP_LOGIN_MAPPING = "/login";

    String HTTP_USER_MAPPING = "/user";

    String STOMP_ENDPOINT = "jifa-stomp";

    String STOMP_APPLICATION_DESTINATION_PREFIX = "/ad";

    String STOMP_USER_DESTINATION_PREFIX = "/ud";

    String STOMP_ANALYSIS_API_MAPPING = "analysis";

    Charset CHARSET = StandardCharsets.UTF_8;

    String APPLICATION_JSON = "application/json;charset=utf-8";

    long JWT_EXPIRY = 604800;

    long JWT_REFRESH_WINDOW = 86400;

    String STOMP_ANALYSIS_API_REQUEST_ID_KEY = "request-id";

    String STOMP_ANALYSIS_API_RESPONSE_SUCCESS_KEY = "response-success";

    String HTTP_HEADER_ENABLE_SSE = "X-Enable-SSE";

    String SSE_EVENT_PING = "ping";

    String SSE_EVENT_SUCCESS_RESPONSE = "response";

    String SSE_EVENT_ERROR_RESPONSE = "error";

    String ROLE_PREFIX = "ROLE_";

    String ROLE_ADMIN = "ADMIN";

    String ANONYMOUS_USERNAME = "Anonymous User";

    String ANONYMOUS_KEY = "Anonymous";

    byte[] EMPTY_BYTE_ARRAY = new byte[0];

    String DEFAULT_CLUSTER_NAMESPACE = "jifa";

    String POD_NAME_PREFIX = "jifa-elastic-worker-";

    String ELASTIC_WORKER_IDENTITY_ENV_KEY = "JIFA_ELASTIC_WORKER_IDENTITY";

    String WORKER_CONTAINER_NAME = "main-container";

    String ANALYSIS_API_REQUEST_NAMESPACE_KEY = "namespace";

    String ANALYSIS_API_REQUEST_API_KEY = "api";

    String ANALYSIS_API_REQUEST_TARGET_KEY = "target";

    String ANALYSIS_API_REQUEST_PARAMETERS_KEY = "parameters";

    String CONFIGURATION_PUBLIC_KEY = "public-key";

    String CONFIGURATION_PRIVATE_KEY = "private-key";

    String DEFAULT_FILENAME = "file";

    /**
     * Default maximum upload size limit - unlimited by default
     */
    long DEFAULT_MAX_UPLOAD_SIZE = Long.MAX_VALUE;
}
