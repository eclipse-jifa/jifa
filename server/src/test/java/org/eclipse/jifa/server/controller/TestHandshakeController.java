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

import org.eclipse.jifa.common.util.GsonHolder;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.domain.dto.HandshakeResponse;
import org.eclipse.jifa.server.domain.dto.PublicKey;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.service.CipherService;
import org.eclipse.jifa.server.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HandshakeController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class TestHandshakeController {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CipherService cipherService;

    @MockitoBean
    private UserService userService;

    @Test
    public void test() throws Exception {
        PublicKey publicKey = new PublicKey("pkcs8", "ssh2");
        Mockito.when(cipherService.getPublicKeyString()).thenReturn(publicKey);
        UserEntity user = new UserEntity();
        user.setName("Jifa");
        user.setAdmin(true);
        Mockito.when(userService.getCurrentUser()).thenReturn(user);

        MvcResult result = mvc.perform(get(Constant.HTTP_API_PREFIX + Constant.HTTP_HANDSHAKE_MAPPING))
                              .andExpect(status().isOk())
                              .andReturn();
        HandshakeResponse handshakeResponse = GsonHolder.GSON.fromJson(result.getResponse().getContentAsString(), HandshakeResponse.class);
        assertNotNull(handshakeResponse);
        assertTrue(handshakeResponse.allowAnonymousAccess());
        assertEquals(publicKey, handshakeResponse.publicKey());
        assertNotNull(handshakeResponse.user());
        assertEquals("Jifa", handshakeResponse.user().name());
        assertTrue(handshakeResponse.user().admin());
    }
}
