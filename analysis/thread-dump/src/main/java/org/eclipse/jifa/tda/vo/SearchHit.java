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

@Data
public class SearchHit {

    private int id;

    private String name;

    private String javaState;

    private String osState;

    private double cpu;

    private double elapsed;

    /**
     * Raw content lines of the thread's stack trace entry.
     * Line 0 is the thread header; subsequent lines are stack frames.
     */
    private List<String> lines;
}
