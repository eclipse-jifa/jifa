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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.jifa.common.util.GsonHolder;
import org.eclipse.jifa.server.domain.dto.AnalysisApiRequest;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
final class JifaMcpAnalysisInvoker {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<String>> LIST_OF_STRINGS_TYPE = new TypeReference<>() {};

    private final AnalysisApiService analysisApiService;

    private final ObjectMapper objectMapper;

    JifaMcpAnalysisInvoker(AnalysisApiService analysisApiService, ObjectMapper objectMapper) {
        this.analysisApiService = analysisApiService;
        this.objectMapper = objectMapper;
    }

    Object invokeAnalysisRaw(FileType fileType,
                             String uniqueName,
                             String api,
                             Map<String, Object> parameters) {
        JsonObject json = new JsonObject();
        json.addProperty("namespace", fileType.getApiNamespace());
        json.addProperty("api", api);
        json.addProperty("target", uniqueName);
        if (parameters != null && !parameters.isEmpty()) {
            json.add("parameters", GsonHolder.GSON.toJsonTree(parameters).getAsJsonObject());
        }

        try {
            Object raw = analysisApiService.invoke(new AnalysisApiRequest(json)).get();
            return normalizeAnalysisResult(raw, api);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke analysis API '" + api + "': " + e.getMessage(), e);
        }
    }

    Map<String, Object> invokeAnalysisAsMap(FileType fileType,
                                            String uniqueName,
                                            String api,
                                            Map<String, Object> parameters) {
        Object result = invokeAnalysisRaw(fileType, uniqueName, api, parameters);
        return result == null ? Map.of() : objectMapper.convertValue(result, MAP_TYPE);
    }

    List<Map<String, Object>> invokeAnalysisAsListOfMaps(FileType fileType,
                                                         String uniqueName,
                                                         String api,
                                                         Map<String, Object> parameters) {
        Object result = invokeAnalysisRaw(fileType, uniqueName, api, parameters);
        return result == null ? List.of() : objectMapper.convertValue(result, LIST_OF_MAPS_TYPE);
    }

    List<String> invokeAnalysisAsListOfStrings(FileType fileType,
                                               String uniqueName,
                                               String api,
                                               Map<String, Object> parameters) {
        Object result = invokeAnalysisRaw(fileType, uniqueName, api, parameters);
        return result == null ? List.of() : objectMapper.convertValue(result, LIST_OF_STRINGS_TYPE);
    }

    private Object normalizeAnalysisResult(Object raw, String api) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return parseJsonPayload(bytes, api);
        }
        if (raw instanceof JsonElement element) {
            return parseJsonPayload(element.toString().getBytes(), api);
        }
        return raw;
    }

    private Object parseJsonPayload(byte[] payload, String api) {
        if (payload.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode analysis API '" + api + "' response", e);
        }
    }
}
