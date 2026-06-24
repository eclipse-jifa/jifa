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
package org.eclipse.jifa.server.configurer;

import jakarta.annotation.Nullable;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.condition.ConditionalOnRole;
import org.eclipse.jifa.server.filter.JwtTokenRefreshFilter;
import org.eclipse.jifa.server.service.JwtService;
import org.eclipse.jifa.server.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.eclipse.jifa.server.Constant.COOKIE_JIFA_TOKEN_KEY;
import static org.eclipse.jifa.server.Constant.HTTP_API_PREFIX;
import static org.eclipse.jifa.server.Constant.HTTP_HANDSHAKE_MAPPING;
import static org.eclipse.jifa.server.Constant.HTTP_HEALTH_CHECK_MAPPING;
import static org.eclipse.jifa.server.Constant.HTTP_LOGIN_MAPPING;
import static org.eclipse.jifa.server.Constant.HTTP_USER_MAPPING;
import static org.eclipse.jifa.server.enums.Role.MASTER;
import static org.eclipse.jifa.server.enums.Role.STANDALONE_WORKER;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(value = "jifa.security-filters-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityFilterConfigurer extends ConfigurationAccessor {

    @Bean
    public SecurityFilterChain configure(HttpSecurity hs, UserService userService, JwtService jwtService,
                                         @Nullable OAuth2ClientProperties oauth2ClientProperties) throws Exception {
        hs.cors(cors -> {
          })
          .csrf(AbstractHttpConfigurer::disable)
          .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .requestCache(cache -> cache.requestCache(new NullRequestCache()));

        hs.anonymous(customizer -> customizer.principal(Constant.ANONYMOUS_USERNAME).key(Constant.ANONYMOUS_KEY));

        hs.authorizeHttpRequests(requests -> {
            String prefix = HTTP_API_PREFIX;
            requests.requestMatchers(prefix + HTTP_HEALTH_CHECK_MAPPING).permitAll();
            requests.requestMatchers(prefix + HTTP_HANDSHAKE_MAPPING).permitAll();
            requests.requestMatchers(prefix + HTTP_LOGIN_MAPPING).permitAll();
            requests.requestMatchers(prefix + HTTP_USER_MAPPING).permitAll();
            String apiMatchers = prefix + "/**";
            if (!config.isAllowAnonymousAccess()) {
                requests.requestMatchers(apiMatchers).authenticated();
            }
            requests.anyRequest().permitAll();
        });

        hs.authenticationProvider(new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = (String) authentication.getPrincipal();
                String password = (String) authentication.getCredentials();
                try {
                    return userService.login(username, password);
                } catch (Throwable t) {
                    throw new AuthenticationServiceException(t.getMessage(), t);
                }
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class == authentication;
            }
        });

        if (oauth2ClientProperties != null && !oauth2ClientProperties.getRegistration().isEmpty()) {
            hs.oauth2Login(oauth2 -> oauth2.successHandler((request, response, authentication) -> {
                Cookie jifaToken = new Cookie(COOKIE_JIFA_TOKEN_KEY, userService.handleOauth2Login((OAuth2AuthenticationToken) authentication).getToken());
                jifaToken.setPath("/");
                jifaToken.setHttpOnly(false);
                response.addCookie(jifaToken);
                response.sendRedirect("/");
            }));
        }

        DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
        hs.oauth2ResourceServer(rs -> rs.jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwtService::convert))
                                        .bearerTokenResolver(request -> {
                                            String token = defaultBearerTokenResolver.resolve(request);
                                            if (token == null) {
                                                if (request.getRequestURI().matches(HTTP_API_PREFIX + "/files/\\d+/download")) {
                                                    Cookie[] cookies = request.getCookies();
                                                    if (cookies != null) {
                                                        for (Cookie cookie : cookies) {
                                                            if (COOKIE_JIFA_TOKEN_KEY.equals(cookie.getName())) {
                                                                token = cookie.getValue();
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            return token;
                                        }));

        hs.exceptionHandling(eh -> {
            eh.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint());
            eh.accessDeniedHandler(new BearerTokenAccessDeniedHandler());
        });

        return hs.build();
    }

    @ConditionalOnRole({MASTER, STANDALONE_WORKER})
    @Bean
    public FilterRegistrationBean<Filter> refreshJwtTokenFilter(JwtService jwtService) {
        FilterRegistrationBean<Filter> frb = new FilterRegistrationBean<>();
        frb.setFilter(new JwtTokenRefreshFilter(jwtService));
        frb.addUrlPatterns(HTTP_API_PREFIX + "/*");
        // must be after spring security filter chain
        frb.setOrder(Integer.MAX_VALUE);
        return frb;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("*");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.applyPermitDefaultValues();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
