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

/**
 * Result of a multi-dump comparison (2–4 dumps).
 * <p>
 * {@code dumpOverviews} is ordered: index 0 = primary dump, 1..N = others.
 * {@code threadRows} contains one entry per unique NID seen across all dumps.
 */
@Data
public class VMultiDumpComparison {

    /**
     * One summary per dump, in the same order as the input.
     */
    private List<DumpSummary> dumpOverviews;

    /**
     * One row per unique thread (matched by native thread ID).
     * Rows are sorted by "most interesting" first:
     * threads that appear in all dumps and changed state, then partial matches.
     */
    private List<ThreadRow> threadRows;

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Lightweight overview of a single dump used in multi-dump comparisons.
     */
    @Data
    public static class DumpSummary {

        /** Display name (original file name). */
        private String name;

        /** Total Java thread count. */
        private int threadCount;

        /** Number of deadlocked threads (0 if none detected). */
        private int deadLockCount;

        /**
         * Java thread state distribution: state name → count.
         * Only states with count > 0 are included.
         */
        private Map<String, Integer> stateCounts;
    }

    /**
     * One row per unique thread across all compared dumps.
     */
    @Data
    public static class ThreadRow {

        /** Thread name (from the first dump where this NID appears). */
        private String name;

        /**
         * Java state per dump, in the same order as {@link #dumpOverviews}.
         * {@code null} means the thread was not present in that dump.
         */
        private List<String> states;

        /**
         * {@code true} if the thread appears in every dump with state
         * {@code BLOCKED_ON_MONITOR_ENTER} – i.e. persistently blocked.
         */
        private boolean alwaysBlocked;

        /**
         * {@code true} if the thread changed its Java state at least once
         * across consecutive dump pairs.
         */
        private boolean stateChanged;
    }
}
