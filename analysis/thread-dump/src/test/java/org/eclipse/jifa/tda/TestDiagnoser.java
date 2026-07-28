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
 * Assisted-by: GitHub Copilot (Claude Sonnet 4.5)
 ********************************************************************************/

package org.eclipse.jifa.tda;

import org.eclipse.jifa.analysis.listener.DefaultProgressListener;
import org.eclipse.jifa.tda.diagnoser.Diagnostic;
import org.eclipse.jifa.tda.diagnoser.Diagnostic.Severity;
import org.eclipse.jifa.tda.diagnoser.Diagnostic.Type;
import org.eclipse.jifa.tda.diagnoser.ThreadDumpAnalysisConfig;
import org.eclipse.jifa.tda.vo.VBlockingThread;
import org.eclipse.jifa.tda.vo.VThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new Phase-2 analyzer features:
 * blockingThreads(), cpuConsumingThreads(), analyze() / ThreadDumpDiagnoser.
 * <p>
 * Uses jstack_17_with_blocked.log which contains:
 *  - German-locale decimal commas in cpu/elapsed (e.g. "360,22ms", "0,74s")
 *  - 1 blocking thread (pool-1-thread-2) holding a monitor
 *  - 4 blocked threads (pool-1-thread-1/3/4/5) waiting on that monitor
 */
public class TestDiagnoser extends TestBase {

    private ThreadDumpAnalyzer tda;

    @BeforeEach
    void setup() throws Exception {
        tda = new ThreadDumpAnalyzer(
                pathOfResource("jstack_17_with_blocked.log"),
                new DefaultProgressListener());
    }

    // ------------------------------------------------------------------
    // blockingThreads()
    // ------------------------------------------------------------------

    @Test
    public void testBlockingThreads_count() {
        List<VBlockingThread> blocking = tda.blockingThreads();
        // pool-1-thread-2 holds the lock; 4 threads are blocked on it
        assertEquals(1, blocking.size());
    }

    @Test
    public void testBlockingThreads_blockerName() {
        VBlockingThread bt = tda.blockingThreads().get(0);
        assertEquals("pool-1-thread-2", bt.getBlockingThread().getName());
    }

    @Test
    public void testBlockingThreads_blockedCount() {
        VBlockingThread bt = tda.blockingThreads().get(0);
        assertEquals(4, bt.getBlockedThreads().size());
    }

    @Test
    public void testBlockingThreads_heldLockPresent() {
        VBlockingThread bt = tda.blockingThreads().get(0);
        assertNotNull(bt.getHeldLock());
    }

    // ------------------------------------------------------------------
    // cpuConsumingThreads() – also validates German-locale decimal parsing
    // ------------------------------------------------------------------

    @Test
    public void testCpuConsumingThreads_parsesGermanLocale() {
        // "main" has cpu=360,22ms in the log – Converter must parse the comma
        List<VThread> threads = tda.cpuConsumingThreads(null, 1);
        assertEquals(1, threads.size());
        assertEquals("main", threads.get(0).getName());
        assertNotNull(threads.get(0).getCpu());
        assertTrue(threads.get(0).getCpu() > 0,
                "CPU time should be > 0 after parsing German-locale comma");
    }

    @Test
    public void testCpuConsumingThreads_limitRespected() {
        List<VThread> top3 = tda.cpuConsumingThreads(null, 3);
        assertTrue(top3.size() <= 3);
    }

    @Test
    public void testCpuConsumingThreads_sortedDescending() {
        List<VThread> threads = tda.cpuConsumingThreads(null, -1);
        for (int i = 0; i < threads.size() - 1; i++) {
            double a = threads.get(i).getCpu() != null ? threads.get(i).getCpu() : 0;
            double b = threads.get(i + 1).getCpu() != null ? threads.get(i + 1).getCpu() : 0;
            assertTrue(a >= b, "List must be sorted descending by CPU");
        }
    }

    // ------------------------------------------------------------------
    // analyze() / ThreadDumpDiagnoser
    // ------------------------------------------------------------------

    @Test
    public void testAnalyze_defaultConfig_findsBlockedThreads() {
        // Default threshold is 3; the dump has 4 blocked threads → should fire
        List<Diagnostic> diagnostics = tda.diagnose(null);
        assertTrue(diagnostics.stream()
                .anyMatch(d -> d.getType() == Type.HIGH_BLOCKED_THREAD_COUNT),
                "Expected HIGH_BLOCKED_THREAD_COUNT diagnostic");
    }

    @Test
    public void testAnalyze_blockedDiagnosticSeverity() {
        List<Diagnostic> diagnostics = tda.diagnose(null);
        Diagnostic d = diagnostics.stream()
                .filter(x -> x.getType() == Type.HIGH_BLOCKED_THREAD_COUNT)
                .findFirst().orElseThrow();
        assertEquals(Severity.ERROR, d.getSeverity());
    }

    @Test
    public void testAnalyze_blockedDiagnosticParams() {
        List<Diagnostic> diagnostics = tda.diagnose(null);
        Diagnostic d = diagnostics.stream()
                .filter(x -> x.getType() == Type.HIGH_BLOCKED_THREAD_COUNT)
                .findFirst().orElseThrow();
        assertEquals(4, d.getParams().get("count"));
    }

    @Test
    public void testAnalyze_raisedThreshold_noBlockedDiagnostic() {
        ThreadDumpAnalysisConfig config = new ThreadDumpAnalysisConfig();
        config.setHighBlockedThreadsThreshold(10); // higher than 4 blocked threads
        List<Diagnostic> diagnostics = tda.diagnose(config);
        assertTrue(diagnostics.stream()
                .noneMatch(d -> d.getType() == Type.HIGH_BLOCKED_THREAD_COUNT),
                "No HIGH_BLOCKED_THREAD_COUNT expected when threshold is raised");
    }

    @Test
    public void testAnalyze_disabledCheck_returnsEmpty() {
        ThreadDumpAnalysisConfig config = new ThreadDumpAnalysisConfig();
        // Use very high thresholds so no check fires on this small dump,
        // and disable exception/CPU checks explicitly
        config.setHighBlockedThreadsThreshold(Integer.MAX_VALUE);
        config.setHighThreadsThreshold(Integer.MAX_VALUE);
        config.setHighStackSizeThreshold(Integer.MAX_VALUE);
        config.setHighCpuConsumedRatio(2.0);           // ratio > 1.0 is impossible
        config.setReportThrowingException(false);
        List<Diagnostic> diagnostics = tda.diagnose(config);
        assertTrue(diagnostics.isEmpty(),
                "All checks suppressed – list must be empty, but got: " + diagnostics);
    }
}
