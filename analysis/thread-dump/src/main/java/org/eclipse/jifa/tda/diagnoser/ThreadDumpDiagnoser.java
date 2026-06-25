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

package org.eclipse.jifa.tda.diagnoser;

import org.eclipse.jifa.tda.diagnoser.Diagnostic.Severity;
import org.eclipse.jifa.tda.enums.JavaThreadState;
import org.eclipse.jifa.tda.model.Frame;
import org.eclipse.jifa.tda.model.JavaThread;
import org.eclipse.jifa.tda.model.Snapshot;
import org.eclipse.jifa.tda.model.Thread;
import org.eclipse.jifa.tda.vo.VThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Analyses a parsed thread dump {@link Snapshot} against a
 * {@link ThreadDumpAnalysisConfig} and returns a list of {@link Diagnostic}
 * findings.
 * <p>
 * All human-readable messages and suggestions are intentionally absent from
 * this class; they are resolved on the frontend via the {@link Diagnostic.Type}
 * key so that they can be localised without redeploying the backend.
 */
public class ThreadDumpDiagnoser {

    /** Parameter key: number of affected threads. */
    private static final String KEY_COUNT     = "count";
    /** Parameter key: name of the single affected thread (only set when count == 1). */
    private static final String KEY_NAME      = "name";
    /** Parameter key: a numeric threshold used in the diagnostic message. */
    private static final String KEY_THRESHOLD = "threshold";

    /**
     * Runs all configured heuristics against the snapshot and returns a
     * (potentially empty) list of findings.
     *
     * @param snapshot the parsed thread dump
     * @param config   the analysis configuration
     * @return list of diagnostic findings, never {@code null}
     */
    public List<Diagnostic> analyze(Snapshot snapshot, ThreadDumpAnalysisConfig config) {
        List<Diagnostic> results = new ArrayList<>();
        analyzeDeadlock(snapshot, config, results);
        analyzeBlockedThreads(snapshot, config, results);
        analyzeThreadCount(snapshot, config, results);
        analyzeLargeStackSize(snapshot, config, results);
        analyzeCpuRatio(snapshot, config, results);
        analyzeExceptionThread(snapshot, config, results);
        return results;
    }

    // ------------------------------------------------------------------
    // individual checks
    // ------------------------------------------------------------------

    private void analyzeDeadlock(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                  List<Diagnostic> results) {
        if (snapshot.getDeadLockThreads() == null) {
            return;
        }
        List<JavaThread> deadlockThreads = new ArrayList<>();
        snapshot.getDeadLockThreads().forEach(deadlockThreads::addAll);
        if (!deadlockThreads.isEmpty()) {
            results.add(new Diagnostic(Severity.ERROR, Diagnostic.Type.DEADLOCK,
                    createParams(deadlockThreads), toVThread(deadlockThreads)));
        }
    }

    private void analyzeBlockedThreads(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                        List<Diagnostic> results) {
        if (config.getHighBlockedThreadsThreshold() <= 0) {
            return;
        }
        List<JavaThread> blockedThreads = snapshot.getJavaThreads().stream()
                .filter(t -> t.getJavaThreadState() == JavaThreadState.BLOCKED_ON_MONITOR_ENTER)
                .collect(Collectors.toList());
        if (blockedThreads.size() >= config.getHighBlockedThreadsThreshold()) {
            results.add(new Diagnostic(Severity.ERROR, Diagnostic.Type.HIGH_BLOCKED_THREAD_COUNT,
                    createParams(blockedThreads), toVThread(blockedThreads)));
        }
    }

    private void analyzeThreadCount(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                     List<Diagnostic> results) {
        if (config.getHighThreadsThreshold() <= 0) {
            return;
        }
        Collection<Thread> allThreads = snapshot.getThreadMap().values();
        if (allThreads.size() >= config.getHighThreadsThreshold()) {
            results.add(new Diagnostic(Severity.WARNING, Diagnostic.Type.HIGH_THREAD_COUNT,
                    createParams(allThreads), null));
        }
    }

    private void analyzeLargeStackSize(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                        List<Diagnostic> results) {
        if (config.getHighStackSizeThreshold() <= 0) {
            return;
        }
        List<JavaThread> threads = snapshot.getJavaThreads().stream()
                .filter(t -> Optional.ofNullable(t.getTrace())
                        .map(trace -> trace.getFrames())
                        .map(frames -> frames.length)
                        .orElse(0) > config.getHighStackSizeThreshold())
                .collect(Collectors.toList());
        if (!threads.isEmpty()) {
            Map<String, Object> params = createParams(threads);
            params.put(KEY_THRESHOLD, config.getHighStackSizeThreshold());
            results.add(new Diagnostic(Severity.WARNING, Diagnostic.Type.HIGH_STACK_SIZE,
                    params, toVThread(threads)));
        }
    }

    private void analyzeCpuRatio(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                  List<Diagnostic> results) {
        if (config.getHighCpuConsumedRatio() <= 0) {
            return;
        }
        List<JavaThread> threads = snapshot.getJavaThreads().stream()
                .filter(t -> t.getCpu() > 0 && t.getElapsed() > 0)
                .filter(t -> (t.getCpu() / t.getElapsed()) >= config.getHighCpuConsumedRatio())
                .collect(Collectors.toList());
        if (!threads.isEmpty()) {
            results.add(new Diagnostic(Severity.WARNING, Diagnostic.Type.HIGH_CPU_RATIO,
                    createParams(threads), toVThread(threads)));
        }
    }

    private void analyzeExceptionThread(Snapshot snapshot, ThreadDumpAnalysisConfig config,
                                         List<Diagnostic> results) {
        if (!config.isReportThrowingException()) {
            return;
        }
        List<JavaThread> threads = snapshot.getJavaThreads().stream()
                .filter(this::isThrowingException)
                .collect(Collectors.toList());
        if (!threads.isEmpty()) {
            results.add(new Diagnostic(Severity.WARNING, Diagnostic.Type.THREAD_THROWING_EXCEPTION,
                    createParams(threads), toVThread(threads)));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if the top frames of the thread's stack trace indicate
     * that a {@link Throwable} is currently being constructed (i.e. the thread is
     * inside {@code fillInStackTrace}).  Only the first five frames are inspected
     * to avoid false positives from deep exception-handling code.
     */
    private boolean isThrowingException(JavaThread thread) {
        if (thread.getTrace() == null || thread.getTrace().getFrames() == null) {
            return false;
        }
        Frame[] frames = thread.getTrace().getFrames();
        String throwableName = Throwable.class.getName();
        for (int i = 0; i < Math.min(frames.length, 5); i++) {
            Frame frame = frames[i];
            if (throwableName.equals(frame.getClazz())
                    && frame.getMethod() != null
                    && frame.getMethod().contains("fillInStackTrace")) {
                return true;
            }
        }
        return false;
    }

    private List<VThread> toVThread(Collection<? extends Thread> threads) {
        return threads.stream()
                .map(t -> new VThread(t.getId(), t.getName(), null, null))
                .collect(Collectors.toList());
    }

    /**
     * Builds a parameter map for diagnostic messages. Always sets {@link #KEY_COUNT}.
     * When there is exactly one thread, also sets {@link #KEY_NAME} for singular
     * message variants.
     */
    private Map<String, Object> createParams(Collection<? extends Thread> threads) {
        Map<String, Object> params = new HashMap<>();
        params.put(KEY_COUNT, threads.size());
        if (threads.size() == 1) {
            params.put(KEY_NAME, threads.stream().findFirst().get().getName());
        }
        return params;
    }
}
