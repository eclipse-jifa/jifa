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
package org.eclipse.jifa.server.configurer;

import org.eclipse.jifa.server.condition.Cluster;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan("org.eclipse.jifa.server.domain.entity.shared")
public class EntityConfigurer {

    @Cluster
    @Configuration
    @EntityScan("org.eclipse.jifa.server.domain.entity.cluster")
    public static class ClusterEntityConfigurer {
    }
}
