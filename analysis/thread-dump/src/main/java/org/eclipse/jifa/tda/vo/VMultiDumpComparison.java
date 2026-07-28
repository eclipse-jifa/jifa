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
 * SPDX-License-Identifier: EPL-2.0 and CC0-1.0
 *
 * AI Disclosure: This file was largely AI-generated with GitHub Copilot.
 * The AI-generated portions are made available under CC0-1.0. The human
 * contributor has reviewed and verified the code.
 * Assisted-by: GitHub Copilot (Claude Sonnet 4.6)
 ********************************************************************************/

package org.eclipse.jifa.tda.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VMultiDumpComparison {

    private List<DumpSummary> dumpOverviews;

    /** One row per unique thread (matched by NID). */
    private List<ThreadRow> threadRows;

    @Data
    public static class DumpSummary {

        /** Original file name. */
        private String name;

        private int threadCount;

        private int deadLockCount;

        /** Java thread state distribution (state → count); only non-zero entries. */
        private Map<String, Integer> stateCounts;
    }

    @Data
    public static class ThreadRow {

        /** Thread name (from the first dump where this NID appears). */
        private String name;

        /** Java state per dump (same order as {@link VMultiDumpComparison#dumpOverviews}); {@code null} = not present. */
        private List<String> states;

        /** {@code true} if blocked ({@code BLOCKED_ON_MONITOR_ENTER}) in every dump. */
        private boolean alwaysBlocked;

        /** {@code true} if the thread state changed in any consecutive dump pair. */
        private boolean stateChanged;
    }
}
