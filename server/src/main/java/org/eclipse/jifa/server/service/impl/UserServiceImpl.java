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
package org.eclipse.jifa.server.service.impl;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jifa.common.domain.exception.ShouldNotReachHereException;
import org.eclipse.jifa.common.util.Validate;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.domain.entity.shared.user.ExternalLoginDataEntity;
import org.eclipse.jifa.server.domain.entity.shared.user.LoginDataEntity;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.domain.security.JifaAuthenticationToken;
import org.eclipse.jifa.server.enums.ExternalLoginMethod;
import org.eclipse.jifa.server.repository.ExternalLoginDataRepo;
import org.eclipse.jifa.server.repository.LoginDataRepo;
import org.eclipse.jifa.server.repository.UserRepo;
import org.eclipse.jifa.server.service.CipherService;
import org.eclipse.jifa.server.service.JwtService;
import org.eclipse.jifa.server.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.eclipse.jifa.common.domain.exception.CommonException.CE;
import static org.eclipse.jifa.server.enums.ServerErrorCode.INCORRECT_PASSWORD;
import static org.eclipse.jifa.server.enums.ServerErrorCode.USERNAME_EXISTS;
import static org.eclipse.jifa.server.enums.ServerErrorCode.USER_NOT_FOUND;

@Service
public class UserServiceImpl extends ConfigurationAccessor implements UserService {

    private final TransactionTemplate transactionTemplate;
    private final UserRepo userRepo;
    private final LoginDataRepo loginDataRepo;
    private final ExternalLoginDataRepo externalLoginDataRepo;
    private final CipherService cipherService;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public UserServiceImpl(TransactionTemplate transactionTemplate,
                           UserRepo userRepo,
                           LoginDataRepo loginDataRepo,
                           ExternalLoginDataRepo externalLoginDataRepo,
                           CipherService cipherService,
                           PasswordEncoder encoder,
                           JwtService jwtService) {
        this.transactionTemplate = transactionTemplate;
        this.userRepo = userRepo;
        this.loginDataRepo = loginDataRepo;
        this.externalLoginDataRepo = externalLoginDataRepo;
        this.cipherService = cipherService;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @PostConstruct
    private void init() {
        if (userRepo.count() == 0 && StringUtils.isNotBlank(config.getAdminUsername())) {
            Optional<LoginDataEntity> defaultAdmin = loginDataRepo.findByUsername(config.getAdminUsername());
            if (defaultAdmin.isPresent()) {
                return;
            }
            try {
                register("Admin", config.getAdminUsername(), cipherService.encrypt(config.getAdminPassword()), true);
            } catch (Throwable t) {
                if (loginDataRepo.findByUsername(config.getAdminUsername()).isEmpty()) {
                    throw t;
                }
            }
        }
    }

    @Override
    public JifaAuthenticationToken login(String username, String password) {
        LoginDataEntity loginData = loginDataRepo.findByUsername(username).orElse(null);
        Validate.notNull(loginData, USER_NOT_FOUND);
        Validate.isTrue(encoder.matches(cipherService.decrypt(password), loginData.getPasswordHash()),
                        INCORRECT_PASSWORD);
        return jwtService.generateToken(loginData.getUser());
    }

    @Override
    public JifaAuthenticationToken handleOauth2Login(OAuth2AuthenticationToken token) {
        String provider = token.getAuthorizedClientRegistrationId();
        String principalName = token.getPrincipal().getName();

        Optional<ExternalLoginDataEntity> optional =
                externalLoginDataRepo.findByMethodAndProviderAndAndPrincipalName(ExternalLoginMethod.OAUTH2, provider, principalName);

        if (optional.isPresent()) {
            return jwtService.generateToken(optional.get().getUser());
        }

        // Derive a display name from common OIDC claims (Keycloak: preferred_username / name / sub)
        Object nameClaim = token.getPrincipal().getAttribute("name");
        if (nameClaim == null) {
            nameClaim = token.getPrincipal().getAttribute("preferred_username");
        }
        String name = nameClaim != null ? nameClaim.toString() : principalName;

        ExternalLoginDataEntity externalLoginData = new ExternalLoginDataEntity();
        externalLoginData.setMethod(ExternalLoginMethod.OAUTH2);
        externalLoginData.setProvider(provider);
        externalLoginData.setPrincipalName(principalName);

        return jwtService.generateToken(transactionTemplate.execute(status -> {
            UserEntity user = userRepo.save(createUser(name, false));
            externalLoginData.setUser(user);
            externalLoginDataRepo.save(externalLoginData);
            return user;
        }));
    }

    @Override
    public JifaAuthenticationToken register(String name, String username, String password, boolean admin) {
        Validate.isTrue(loginDataRepo.findByUsername(username).isEmpty(), USERNAME_EXISTS);

        LoginDataEntity loginData = new LoginDataEntity();
        loginData.setUsername(username);
        loginData.setPasswordHash(encoder.encode(cipherService.decrypt(password)));

        UserEntity user = transactionTemplate.execute(status -> {
            loginData.setUser(userRepo.save(createUser(name, admin)));
            loginDataRepo.save(loginData);
            return loginData.getUser();
        });

        return jwtService.generateToken(user);
    }

    @Override
    public Long getCurrentUserId() {
        if (!config.isAllowLogin()) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JifaAuthenticationToken token) {
            return token.getUserId();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        throw new ShouldNotReachHereException();
    }

    @Override
    public boolean isCurrentUserAdmin() {
        if (!config.isAllowLogin()) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JifaAuthenticationToken token) {
            return token.isAdmin();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        throw new ShouldNotReachHereException();
    }

    @Override
    public String getCurrentUserJwtTokenOrNull() {
        if (!config.isAllowLogin()) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        if (authentication instanceof JifaAuthenticationToken token) {
            return token.getToken();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        throw new ShouldNotReachHereException();
    }

    @Override
    public UserEntity getCurrentUser() {
        if (!config.isAllowLogin()) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JifaAuthenticationToken token) {
            return userRepo.findById(token.getUserId()).orElseThrow(() -> CE(USER_NOT_FOUND));
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        throw new ShouldNotReachHereException();
    }

    @Override
    public UserEntity getCurrentUserRef() {
        if (!config.isAllowLogin()) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JifaAuthenticationToken token) {
            return userRepo.getReferenceById(token.getUserId());
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        throw new ShouldNotReachHereException();
    }

    private UserEntity createUser(String name, boolean admin) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setAdmin(admin);
        return user;
    }
}