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

/** Maps JavaThreadState and OSThreadState enum names to display colors. */
export const STATE_COLORS: Record<string, string> = {
  // JavaThreadState enum names (backend serialization)
  RUNNABLE:                '#67c23a',   // green
  SLEEPING:                '#e6a23c',   // orange
  IN_OBJECT_WAIT:          '#409eff',   // blue
  IN_OBJECT_WAIT_TIMED:    '#79bbff',   // light blue
  PARKED:                  '#a855f7',   // purple
  PARKED_TIMED:            '#7c3aed',   // dark purple
  BLOCKED_ON_MONITOR_ENTER:'#f56c6c',   // red
  NEW:                     '#95d475',   // light green
  TERMINATED:              '#909399',   // gray
  // OSThreadState enum names
  MONITOR_WAIT:            '#f56c6c',   // red
  COND_VAR_WAIT:           '#5c8ee6',   // steel blue
  OBJECT_WAIT:             '#3b82f6',   // blue
  BREAK_POINTED:           '#f0c419',   // yellow
  ALLOCATED:               '#95d475',   // light green
  INITIALIZED:             '#67c23a',   // green
  ZOMBIE:                  '#606266',   // dark gray
  UNKNOWN:                 '#606266',   // dark gray
};

export function stateColor(state: string): string {
  return STATE_COLORS[state] ?? '#8892a4';
}
