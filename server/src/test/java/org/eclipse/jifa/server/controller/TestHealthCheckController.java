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

import org.eclipse.jifa.common.util.GsonHolder;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.domain.dto.InstanceView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HealthCheckController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class TestHealthCheckController {

    @Autowired
    private MockMvc mvc;

    @Test
    public void test() throws Exception {
        MvcResult result = mvc.perform(get(Constant.HTTP_API_PREFIX + Constant.HTTP_HEALTH_CHECK_MAPPING))
                              .andExpect(status().isOk())
                              .andReturn();
        InstanceView iv = GsonHolder.GSON.fromJson(result.getResponse().getContentAsString(), InstanceView.class);
        assertNotNull(iv);
        assertNotNull(iv.role());
        assertTrue(iv.uptime() >= 0);
    }
}
