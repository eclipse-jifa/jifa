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

import jakarta.annotation.Nullable;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.domain.dto.HandshakeResponse;
import org.eclipse.jifa.server.domain.dto.User;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.service.CipherService;
import org.eclipse.jifa.server.service.UserService;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * System information controller
 */
@RestController
public class HandshakeController extends ConfigurationAccessor {

    private final CipherService cipherService;

    private final UserService userService;

    private final Map<String, String> oauth2LoginLinks = new HashMap<>();

    private final MultipartProperties multipartProperties;

    public HandshakeController(CipherService cipherService,
                               UserService userService,
                               @Nullable OAuth2ClientProperties oauth2ClientProperties,
                               @Nullable MultipartProperties multipartProperties) {
        this.cipherService = cipherService;
        this.userService = userService;
        this.multipartProperties = multipartProperties;
        if (oauth2ClientProperties != null) {
            Map<String, OAuth2ClientProperties.Registration> registration = oauth2ClientProperties.getRegistration();
            registration.keySet()
                        .forEach(key -> oauth2LoginLinks.put(key.substring(0, 1).toUpperCase() + key.substring(1),
                                                             OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/" + key));
        }
    }

    /**
     * @return Handshake result
     */
    @GetMapping(Constant.HTTP_HANDSHAKE_MAPPING)
    public HandshakeResponse handshake() {
        UserEntity userEntity = userService.getCurrentUser();
        User user = userEntity == null ? null : new User(userEntity.getName(), userEntity.isAdmin());
        
        // Get file size limit configuration
        long maxFileSizeBytes;
        if (multipartProperties != null) {
            DataSize maxFileSize = multipartProperties.getMaxFileSize();
            maxFileSizeBytes = maxFileSize.toBytes();
        } else {
            maxFileSizeBytes = Constant.DEFAULT_MAX_FILE_SIZE_BYTES;
        }
        
        return new HandshakeResponse(getRole(),
                                     config.isAllowLogin(),
                                     config.isAllowLogin() ? oauth2LoginLinks : Collections.emptyMap(),
                                     config.isAllowAnonymousAccess(),
                                     config.isAllowRegistration(),
                                     cipherService.getPublicKeyString(),
                                     config.getDisabledFileTransferMethods(),
                                     user,
                                     maxFileSizeBytes);
    }
}
