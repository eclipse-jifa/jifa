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

import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AnalysisApiHttpController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class TestAnalysisApiHttpController {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnalysisApiService apiService;

    @BeforeEach
    public void before() {
        Mockito.when(apiService.invoke(Mockito.any())).thenAnswer((Answer<CompletableFuture<?>>) invocation -> CompletableFuture.completedFuture("Hello Jifa"));
    }

    @Test
    public void testCommon() throws Exception {
        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(content().json("\"Hello Jifa\""))
           .andReturn();
    }

    @Test
    public void testSse() throws Throwable {
        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .header(Constant.HTTP_HEADER_ENABLE_SSE, "true")
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM))
           .andExpect(content().string("""
                                               event:response
                                               data:"Hello Jifa"
                                                                                              
                                               """))
           .andReturn();
    }
}
