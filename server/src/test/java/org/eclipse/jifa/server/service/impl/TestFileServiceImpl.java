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
package org.eclipse.jifa.server.service.impl;

import org.eclipse.jifa.server.Configuration;
import org.eclipse.jifa.server.domain.entity.shared.file.FileEntity;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.enums.Role;
import org.eclipse.jifa.server.repository.DeletedFileRepo;
import org.eclipse.jifa.server.repository.FileRepo;
import org.eclipse.jifa.server.repository.TransferringFileRepo;
import org.eclipse.jifa.server.service.StorageService;
import org.eclipse.jifa.server.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TestFileServiceImpl {

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private UserService userService;

    @Mock
    private FileRepo fileRepo;

    @Mock
    private TransferringFileRepo transferringFileRepo;

    @Mock
    private DeletedFileRepo deletedFileRepo;

    @Mock
    private StorageService storageService;

    @Mock
    private TaskScheduler taskScheduler;

    private FileServiceImpl fileService;

    @BeforeEach
    public void setUp() {
        fileService = new FileServiceImpl(transactionTemplate,
                                          userService,
                                          fileRepo,
                                          transferringFileRepo,
                                          deletedFileRepo,
                                          null,
                                          null,
                                          storageService,
                                          null,
                                          taskScheduler);

        Configuration configuration = new Configuration();
        configuration.setRole(Role.STANDALONE_WORKER);
        ReflectionTestUtils.setField(fileService, "config", configuration);
    }

    @Test
    public void testDeleteOldestFile() {
        FileEntity oldestFile = createFile(1L, "old-file", LocalDateTime.of(2026, 3, 20, 10, 0));

        when(fileRepo.findFirstByOrderByCreatedTimeAsc()).thenReturn(Optional.of(oldestFile));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        fileService.deleteOldestFile();

        verify(fileRepo).findFirstByOrderByCreatedTimeAsc();
        verify(fileRepo).deleteById(1L);
        verify(deletedFileRepo).save(any());
        verify(storageService).scavenge(FileType.GC_LOG, "old-file");
    }

    @Test
    public void testDeleteOldestFileNoopWhenNoFileExists() {
        when(fileRepo.findFirstByOrderByCreatedTimeAsc()).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> fileService.deleteOldestFile());

        verify(fileRepo).findFirstByOrderByCreatedTimeAsc();
        verify(fileRepo, never()).deleteById(any());
        verify(storageService, never()).scavenge(any(), any());
    }

    private static FileEntity createFile(long id, String uniqueName, LocalDateTime createdTime) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setUniqueName(uniqueName);
        file.setOriginalName(uniqueName + ".hprof");
        file.setType(FileType.GC_LOG);
        file.setSize(1024L);
        file.setCreatedTime(createdTime);
        return file;
    }
}
