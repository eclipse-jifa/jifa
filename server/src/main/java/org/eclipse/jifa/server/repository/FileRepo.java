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
package org.eclipse.jifa.server.repository;

import org.eclipse.jifa.server.domain.entity.shared.file.FileEntity;
import org.eclipse.jifa.server.enums.FileType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepo extends CrudRepository<FileEntity, Long> {
    Page<FileEntity> findByUserIdOrderByCreatedTimeDesc(Long userId, Pageable pageable);

    Page<FileEntity> findByUserIdAndTypeOrderByCreatedTimeDesc(Long userId, FileType type, Pageable pageable);

    Optional<FileEntity> findByUniqueName(String uniqueName);

    Optional<FileEntity> findFirstByOrderByCreatedTimeAsc();
}
