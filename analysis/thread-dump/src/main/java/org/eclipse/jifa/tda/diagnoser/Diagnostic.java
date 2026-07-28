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

import java.util.List;
import java.util.Map;

import org.eclipse.jifa.tda.vo.VThread;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a single diagnostic finding produced by {@link ThreadDumpDiagnoser}.
 * <p>
 * Messages and suggestions are intentionally kept out of this class – they are
 * resolved on the frontend via the {@link Type} key so that they can be
 * localised without re-deploying the backend.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Diagnostic {

    private Severity severity;

    /** Identifies the kind of issue; used as i18n key on the frontend. */
    private Type type;

    /**
     * Named parameters that are interpolated into the i18n message on the
     * frontend (e.g. {@code count}, {@code name}, {@code threshold}).
     */
    private Map<String, Object> params;

    /** Affected threads, may be {@code null} when not applicable. */
    private List<VThread> threads;

    public enum Severity {
        OK, INFO, WARNING, ERROR
    }

    public enum Type {
        HIGH_THREAD_COUNT,
        DEADLOCK,
        HIGH_BLOCKED_THREAD_COUNT,
        HIGH_STACK_SIZE,
        HIGH_CPU_RATIO,
        THREAD_THROWING_EXCEPTION
    }
}
