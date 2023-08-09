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
package org.eclipse.jifa.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.server.domain.security.JifaAuthenticationToken;
import org.eclipse.jifa.server.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtTokenRefreshFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtTokenRefreshFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            JifaAuthenticationToken newToken = jwtService.refreshToken();
            if (newToken != null) {
                response.addHeader(HttpHeaders.AUTHORIZATION, newToken.getToken());
            }
        } catch (Throwable t) {
            log.error("Failed to refresh jwt token: {}", t.getMessage());
        }
        chain.doFilter(request, response);
    }
}
