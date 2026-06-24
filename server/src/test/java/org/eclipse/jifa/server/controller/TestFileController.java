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

import org.apache.commons.io.FileUtils;
import org.eclipse.jifa.common.domain.request.PagingRequest;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.common.util.PageViewBuilder;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.domain.dto.FileTransferProgress;
import org.eclipse.jifa.server.domain.dto.FileTransferRequest;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.domain.dto.NamedResource;
import org.eclipse.jifa.server.enums.FileTransferMethod;
import org.eclipse.jifa.server.enums.FileTransferState;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.service.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.eclipse.jifa.common.util.GsonHolder.GSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = FileController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class TestFileController {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FileService fileService;

    @Test
    public void testGetFiles() throws Exception {
        FileView fv = new FileView(7,
                                   "uniqueName",
                                   "originalName",
                                   FileType.GC_LOG,
                                   1024,
                                   LocalDateTime.now());
        PageView<FileView> pv = PageViewBuilder.build(List.of(fv), new PagingRequest(1, 16));

        Mockito.when(fileService.getUserFileViews(FileType.GC_LOG, 1, 16))
               .thenReturn(pv);

        mvc.perform(get(Constant.HTTP_API_PREFIX + "/files")
                            .queryParam("type", FileType.GC_LOG.name())
                            .queryParam("page", "1")
                            .queryParam("pageSize", "16"))
           .andExpect(status().isOk())
           .andExpect(content().json(GSON.toJson(pv)));
    }

    @Test
    public void testGetFile() throws Exception {
        FileView fv = new FileView(1,
                                   "uniqueName",
                                   "originalName",
                                   FileType.GC_LOG,
                                   1024,
                                   LocalDateTime.now());

        Mockito.when(fileService.getFileViewById(1)).thenReturn(fv);

        mvc.perform(get(Constant.HTTP_API_PREFIX + "/files/1"))
           .andExpect(status().isOk())
           .andExpect(content().json(GSON.toJson(fv)));
    }

    @Test
    public void testDeleteFile() throws Exception {
        Mockito.doNothing().when(fileService).deleteById(1);

        mvc.perform(delete(Constant.HTTP_API_PREFIX + "/files/1"))
           .andExpect(status().isOk());
    }

    @Test
    public void testTransfer() throws Throwable {
        Mockito.doReturn(1L).when(fileService).handleTransferRequest(Mockito.any());

        FileTransferRequest request = new FileTransferRequest();
        request.setType(FileType.THREAD_DUMP);
        request.setMethod(FileTransferMethod.URL);
        request.setUrl("https://example.org/data.txt");
        mvc.perform(post(Constant.HTTP_API_PREFIX + "/files/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(GSON.toJson(request)))
           .andExpect(status().isOk());
    }

    @Test
    public void testGetTransferProgress() throws Exception {
        FileTransferProgress progress = new FileTransferProgress(FileTransferState.SUCCESS, 1024, 1024, "", 1L);
        Mockito.doReturn(progress).when(fileService).getTransferProgress(1);

        mvc.perform(get(Constant.HTTP_API_PREFIX + "/files/transfer/1"))
           .andExpect(status().isOk())
           .andExpect(content().json(GSON.toJson(progress)));
    }

    @Test
    public void testUpload() throws Throwable {
        Mockito.when(fileService.handleUploadRequest(Mockito.eq(FileType.THREAD_DUMP),
                                                     Mockito.any(MultipartFile.class))).thenReturn(1L);
        mvc.perform(multipart(Constant.HTTP_API_PREFIX + "/files/upload")
                            .file(new MockMultipartFile("file", "filename", null, new byte[]{1, 2, 3}))
                            .queryParam("type", FileType.THREAD_DUMP.name()))
           .andExpect(status().isOk())
           .andExpect(content().json(Long.toString(1L)));
    }

    @Test
    public void testDownload() throws Throwable {
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        String content = UUID.randomUUID().toString();
        FileUtils.writeStringToFile(tempFile, content, StandardCharsets.UTF_8);
        Mockito.when(fileService.handleDownloadRequest(Mockito.eq(1L)))
               .thenReturn(new NamedResource("test.txt", new FileSystemResource(tempFile.toPath().toAbsolutePath())));

        mvc.perform(get(Constant.HTTP_API_PREFIX + "/files/1/download"))
           .andExpect(content().string(content))
           .andReturn();
    }
}
