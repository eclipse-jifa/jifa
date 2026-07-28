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

import lombok.Data;

/**
 * Configuration controlling which heuristics the {@link ThreadDumpDiagnoser}
 * applies and at what thresholds warnings are emitted.
 * <p>
 * A threshold value of {@code 0} or less disables the corresponding check.
 */
@Data
public class ThreadDumpAnalysisConfig {

    /** Issue a warning if the total thread count reaches at least this value. */
    private int highThreadsThreshold = 500;

    /** Issue a warning if at least that many threads are blocked. */
    private int highBlockedThreadsThreshold = 3;

    /** Issue a warning if a thread's stack depth reaches at least this value. */
    private int highStackSizeThreshold = 200;

    /**
     * Issue a warning if the ratio between a thread's cpu time and its elapsed
     * time is at least this value (range 0.0 – 1.0).
     */
    private double highCpuConsumedRatio = 0.5;

    /** Issue a warning if a thread is currently throwing an exception. */
    private boolean reportThrowingException = true;
}
