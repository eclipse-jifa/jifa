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
 * Represents the CPU delta of a thread between two thread dumps,
 * matched by native thread id (nid).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VThreadDelta {

    /** Internal thread id (from the first dump). */
    private int id;

    private String name;

    /** CPU time in the first (earlier) dump, in milliseconds. */
    private double cpuFirst;

    /** CPU time in the second (later) dump, in milliseconds. */
    private double cpuSecond;

    /** Delta: cpuSecond - cpuFirst, in milliseconds. Positive means more CPU consumed. */
    private double cpuDelta;
}
