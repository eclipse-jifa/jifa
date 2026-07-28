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

package org.eclipse.jifa.tda.vo;

import lombok.Data;

import java.util.List;

/**
 * A single search result containing a thread that matched the search query,
 * along with the raw content lines of that thread's stack trace.
 */
@Data
public class SearchHit {

    /** Internal thread id (maps to thread detail view). */
    private int id;

    /** Display name of the thread. */
    private String name;

    /** Java thread state string (may be null for non-Java threads). */
    private String javaState;

    /** OS thread state string. */
    private String osState;

    /** CPU time in milliseconds (0 if not available). */
    private double cpu;

    /** Elapsed time in milliseconds (0 if not available). */
    private double elapsed;

    /**
     * Raw content lines of the thread's stack trace entry.
     * Line 0 is the thread header; subsequent lines are stack frames.
     */
    private List<String> lines;
}
