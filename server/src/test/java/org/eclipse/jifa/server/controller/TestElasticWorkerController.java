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

import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.enums.ElasticWorkerState;
import org.eclipse.jifa.server.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        properties = {
                "jifa.role=master",
                "jifa.storage-pvc-name=storage-pvc",
                "jifa.service-account-name=service-account",
                "jifa.elastic-worker-image=image"
        },
        value = ElasticWorkerController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
public class TestElasticWorkerController {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkerService workerService;

    @BeforeEach
    public void before() {
        Mockito.when(workerService.getElasticWorkerState(1)).thenReturn(ElasticWorkerState.READY);
    }

    @Test
    public void test() throws Exception {
        mvc.perform(get(Constant.HTTP_API_PREFIX + "/elastic-workers/1/state"))
           .andExpect(status().isOk())
           .andExpect(content().json("\"READY\""));
    }
}
