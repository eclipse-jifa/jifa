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
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.io.FileUtils;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.domain.entity.shared.user.UserEntity;
import org.eclipse.jifa.server.domain.security.JifaAuthenticationToken;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.repository.UserRepo;
import org.eclipse.jifa.server.service.FileService;
import org.eclipse.jifa.server.service.JwtService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "jifa.role=standalone-worker",
                "jifa.mcp-enabled=true",
                "jifa.allow-login=true",
                "jifa.allow-anonymous-access=false",
                "jifa.database-host=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
public class TestJifaMcpAuthenticatedIntegration {

    private static final int TEST_PORT = findAvailablePort();

    private static final Path TEST_STORAGE = createTempDirectory("jifa-mcp-auth-storage");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("jifa.port", () -> TEST_PORT);
        registry.add("jifa.storage-path", () -> TEST_STORAGE.toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        FileUtils.deleteDirectory(TEST_STORAGE.toFile());
    }

    @Test
    public void testOwnerCanListOnlyOwnFiles() throws Throwable {
        UserEntity owner = createUser("owner-" + UUID.randomUUID());
        String uniqueName = uploadGcLogAs(owner, "owner.gc.log");

        try (McpSyncClient client = initializeClient(owner)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "list_my_files",
                    Map.of("type", FileType.GC_LOG.getApiNamespace())
            ));
            Map<String, Object> structured = asMap(result.structuredContent());
            List<Object> files = asList(structured.get("files"));

            assertThat(result.isError()).isEqualTo(false);
            assertThat(files).hasSize(1);
            assertThat(asMap(files.get(0)).get("uniqueName")).isEqualTo(uniqueName);
        }
    }

    @Test
    public void testAccessDeniedReturnsStructuredErrorCode() throws Throwable {
        UserEntity owner = createUser("owner-" + UUID.randomUUID());
        UserEntity outsider = createUser("outsider-" + UUID.randomUUID());
        String uniqueName = uploadGcLogAs(owner, "private.gc.log");

        try (McpSyncClient client = initializeClient(outsider)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "get_file_info",
                    Map.of("file", uniqueName)
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(true);
            assertThat(structured.get("errorCode")).isEqualTo("ACCESS_DENIED");
        }
    }

    @Test
    public void testMissingFileReturnsStructuredErrorCode() {
        UserEntity owner = createUser("owner-" + UUID.randomUUID());

        try (McpSyncClient client = initializeClient(owner)) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "get_file_info",
                    Map.of("file", "missing-file")
            ));
            Map<String, Object> structured = asMap(result.structuredContent());

            assertThat(result.isError()).isEqualTo(true);
            assertThat(structured.get("errorCode")).isEqualTo("FILE_NOT_FOUND");
        }
    }

    private UserEntity createUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setAdmin(false);
        return userRepo.save(user);
    }

    private String uploadGcLogAs(UserEntity user, String originalName) throws Throwable {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalName,
                "text/plain",
                """
                        OpenJDK 64-Bit Server VM (25.472-b08) for bsd-aarch64 JRE (1.8.0_472-b08)
                        CommandLine flags: -XX:+PrintGC -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+UseParallelGC
                        2026-03-23T18:46:22.549+0800: 13.372: [GC (Metadata GC Threshold) [PSYoungGen: 88829K->12434K(114688K)] 88829K->12523K(376832K), 0.0049924 secs] [Times: user=0.01 sys=0.01, real=0.01 secs]
                        2026-03-23T18:46:22.554+0800: 13.377: [Full GC (Metadata GC Threshold) [PSYoungGen: 12434K->0K(114688K)] [ParOldGen: 88K->11844K(110592K)] 12523K->11844K(225280K), [Metaspace: 20756K->20756K(1069056K)], 0.0122272 secs] [Times: user=0.06 sys=0.00, real=0.01 secs]
                        """.getBytes(StandardCharsets.UTF_8)
        );

        return withAuthentication(jwtService.generateToken(user), () -> {
            long fileId = fileService.handleUploadRequest(FileType.GC_LOG, file);
            FileView fileView = fileService.getFileViewById(fileId);
            return fileView.uniqueName();
        });
    }

    private McpSyncClient initializeClient(UserEntity user) {
        McpSyncClient client = createClient(user);
        client.initialize();
        return client;
    }

    private McpSyncClient createClient(UserEntity user) {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + TEST_PORT)
                                                               .endpoint("/jifa-api/mcp")
                                                               .customizeRequest(request -> request.header(HttpHeaders.AUTHORIZATION,
                                                                                                          "Bearer " + jwtService.generateToken(user).getToken()))
                                                               .build())
                        .build();
    }

    private Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private List<Object> asList(Object value) {
        return objectMapper.convertValue(value, LIST_TYPE);
    }

    private <T> T withAuthentication(JifaAuthenticationToken authentication, ThrowingSupplier<T> supplier) throws Throwable {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate a test port", e);
        }
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {

        T get() throws Throwable;
    }
}
