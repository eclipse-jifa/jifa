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
import java.util.Map;

public record McpAnalysisSummary(String file,
                                 String originalName,
                                 String fileTypeId,
                                 String fileType,
                                 String summary,
                                 List<McpFinding> findings,
                                 List<String> recommendations,
                                 Map<String, Object> evidence) {
}
