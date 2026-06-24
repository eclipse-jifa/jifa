/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.converter.GsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.util.MimeType;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@SuppressWarnings("NullableProblems")
@Slf4j
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class TestAnalysisApiStompController {

    @MockitoBean
    private AnalysisApiService apiService;

    private WebSocketStompClient webSocketStompClient;

    @BeforeEach
    public void before() {
        Mockito.when(apiService.invoke(Mockito.any())).thenAnswer((Answer<CompletableFuture<?>>) invocation -> CompletableFuture.completedFuture("Hello Jifa"));
        this.webSocketStompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.webSocketStompClient.setMessageConverter(new GsonMessageConverter());
        this.webSocketStompClient.setTaskScheduler(new ConcurrentTaskScheduler());
    }

    @Test
    public void test() throws Exception {
        StompSession session = webSocketStompClient
                .connectAsync(String.format("ws://localhost:%d/%s", Constant.DEFAULT_PORT, Constant.STOMP_ENDPOINT), new StompSessionHandlerAdapter() {
                })
                .get(1000, TimeUnit.SECONDS);

        CountDownLatch countDown = new CountDownLatch(1);

        AtomicReference<String> requestIdInResponse = new AtomicReference<>();
        AtomicReference<Object> payloadInResponse = new AtomicReference<>();
        session.subscribe(Constant.STOMP_USER_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING, new StompSessionHandlerAdapter() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonElement.class;
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                exception.printStackTrace();
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                exception.printStackTrace();
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                requestIdInResponse.set(headers.getFirst(Constant.STOMP_ANALYSIS_API_REQUEST_ID_KEY));
                payloadInResponse.set(payload);
                countDown.countDown();
            }
        });

        MimeType type = new MimeType("application", "json", StandardCharsets.UTF_8);
        String requestId = UUID.randomUUID().toString();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(Constant.STOMP_APPLICATION_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING);
        headers.set(Constant.STOMP_ANALYSIS_API_REQUEST_ID_KEY, requestId);
        headers.setContentType(type);
        session.send(headers, Map.of("namespace", "test-namespace", "api", "test-api", "target", "test-target"));

        assertTrue(countDown.await(2, TimeUnit.SECONDS));
        assertEquals(requestId, requestIdInResponse.get());
        assertEquals("\"Hello Jifa\"", payloadInResponse.get().toString());
    }
}
