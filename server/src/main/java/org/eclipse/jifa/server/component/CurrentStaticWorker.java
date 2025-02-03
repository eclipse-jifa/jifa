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
package org.eclipse.jifa.server.component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.condition.StaticWorker;
import org.eclipse.jifa.server.domain.entity.cluster.StaticWorkerEntity;
import org.eclipse.jifa.server.repository.StaticWorkerRepo;
import org.eclipse.jifa.server.service.StorageService;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.net.*;
import java.util.Enumeration;

@StaticWorker
@Component
@Slf4j
public class CurrentStaticWorker extends ConfigurationAccessor {

    private final StaticWorkerRepo staticWorkerRepo;

    private final StorageService storageService;

    private final TaskScheduler taskScheduler;

    private StaticWorkerEntity current;

    public CurrentStaticWorker(StaticWorkerRepo staticWorkerRepo,
                               StorageService storageService,
                               TaskScheduler taskScheduler) {
        this.staticWorkerRepo = staticWorkerRepo;
        this.storageService = storageService;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    private void init() throws IOException {
        String hostAddress = getLocalHostExactAddress();
        current = this.staticWorkerRepo.findByHostAddress(hostAddress).orElseGet(() -> {
            StaticWorkerEntity worker = new StaticWorkerEntity();
            worker.setHostAddress(hostAddress);
            return worker;
        });
        current.setPort(config.getPort());
        updateStorageSpace();

        taskScheduler.scheduleAtFixedRate(() -> {
            try {
                updateStorageSpace();
            } catch (Throwable t) {
                log.warn("Failed to update static worker space value", t);
            }
        }, Instant.now().plusSeconds(60), Duration.ofSeconds(60));
    }

    public StaticWorkerEntity getEntity() {
        return current;
    }

    private void updateStorageSpace() throws IOException {
        current.setAvailableSpace(storageService.getAvailableSpace());
        current.setTotalSpace(storageService.getTotalSpace());
        current = staticWorkerRepo.save(current);
    }

    private String getLocalHostExactAddress() throws IOException {
        Enumeration<NetworkInterface> allNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (allNetworkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = allNetworkInterfaces.nextElement();
            if (!networkInterface.isLoopback() && !networkInterface.isVirtual() && networkInterface.isUp()) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        }
        return InetAddress.getLocalHost().getHostAddress();
    }
}
