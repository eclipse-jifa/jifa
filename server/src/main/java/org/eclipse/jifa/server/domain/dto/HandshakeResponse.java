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
package org.eclipse.jifa.server.domain.dto;

import org.eclipse.jifa.server.enums.FileTransferMethod;
import org.eclipse.jifa.server.enums.Role;

import java.util.Map;
import java.util.Set;

public record HandshakeResponse(Role serverRole,
                                boolean allowLogin,
                                Map<String, String> oauth2LoginLinks,
                                boolean allowAnonymousAccess,
                                boolean allowRegistration,
                                PublicKey publicKey,
                                Set<FileTransferMethod> disabledFileTransferMethods,
                                User user,
                                long maxFileSize) {
}
