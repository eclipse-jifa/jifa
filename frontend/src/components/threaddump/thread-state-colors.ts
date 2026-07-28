/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/**
 * Maps JavaThreadState and OSThreadState enum names to display colors.
 *
 * Hues are grouped by semantics:
 *   green  = running / healthy      red    = blocked / lock contention
 *   blue   = waiting on something   purple = parked (thread pool idle)
 *   amber  = timed sleep / debug    teal   = just created
 *   gray   = terminated / unknown
 */
export const STATE_COLORS: Record<string, string> = {
  // JavaThreadState enum names (backend serialization)
  RUNNABLE:                 '#3f9e4d', // green
  SLEEPING:                 '#c07f1a', // amber
  IN_OBJECT_WAIT:           '#3a76c4', // blue
  IN_OBJECT_WAIT_TIMED:     '#5b8fd9', // light blue
  PARKED:                   '#7e57c2', // purple
  PARKED_TIMED:             '#9575cd', // light purple
  BLOCKED_ON_MONITOR_ENTER: '#d05252', // red
  NEW:                      '#12999a', // teal
  TERMINATED:               '#8a8f99', // gray
  // OSThreadState enum names
  MONITOR_WAIT:             '#d05252', // red (same contention semantics)
  COND_VAR_WAIT:            '#54739e', // steel blue
  OBJECT_WAIT:              '#3a76c4', // blue
  BREAK_POINTED:            '#b8860b', // dark amber
  ALLOCATED:                '#12999a', // teal
  INITIALIZED:              '#3f9e4d', // green
  ZOMBIE:                   '#6b7280', // dark gray
  UNKNOWN:                  '#909399', // gray
};

const FALLBACK_COLOR = '#8892a4';

export function stateColor(state: string): string {
  return STATE_COLORS[state] ?? FALLBACK_COLOR;
}

/**
 * Soft tag style: light tinted background with colored text and a subtle
 * border of the same hue. Easier on the eyes than solid color blocks and
 * works in both light and dark themes.
 */
export function stateTagStyle(state: string): Record<string, string> {
  const c = stateColor(state);
  return {
    color: c,
    backgroundColor: c + '1f', // ~12% alpha tint
    borderColor: c + '52', // ~32% alpha
    fontWeight: '500'
  };
}
