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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a thread whose Java state changed between two thread dumps,
 * matched by native thread id (nid).
 * <p>
 * {@code stateBefore} or {@code stateAfter} can be {@code null} to indicate
 * that the thread was absent in that dump (new or disappeared thread).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VThreadStateChange {

    /** Internal thread id (from the first dump; -1 for threads only in dump 2). */
    private int id;

    private String name;

    /**
     * Java state in the first (earlier) dump.
     * {@code null} if the thread did not exist in dump 1 (newly appeared thread).
     */
    private String stateBefore;

    /**
     * Java state in the second (later) dump.
     * {@code null} if the thread no longer exists in dump 2 (disappeared thread).
     */
    private String stateAfter;

    /**
     * Convenience: returns {@code true} if this entry represents a thread that
     * actually changed state (as opposed to appearing or disappearing).
     */
    public boolean isStateChanged() {
        return stateBefore != null && stateAfter != null && !stateBefore.equals(stateAfter);
    }
}
