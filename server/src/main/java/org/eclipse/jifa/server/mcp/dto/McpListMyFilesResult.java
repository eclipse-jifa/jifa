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
package org.eclipse.jifa.server.mcp.dto;

import java.util.List;

public record McpListMyFilesResult(String requestedType,
                                   String normalizedType,
                                   int page,
                                   int pageSize,
                                   int totalSize,
                                   int returnedSize,
                                   boolean truncated,
                                   List<McpFileInfo> files) {
}
