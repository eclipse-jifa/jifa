<!--
    Copyright (c) 2026 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0 and CC0-1.0

    AI Disclosure: This file was largely AI-generated with GitHub Copilot.
    The AI-generated portions are made available under CC0-1.0. The human
    contributor has reviewed and verified the code.
    Assisted-by: GitHub Copilot (Claude Sonnet 4.6)
 -->
<script setup lang="ts">
import { tdt } from '@/i18n/i18n';
import { deltaClass, deltaText } from '@/components/threaddump/thread-state-colors';

/**
 * Renders a side-by-side grouped bar chart comparing the Java thread state
 * distribution from two already-loaded Overview objects.
 *
 * Both props are plain arrays as returned by the /overview endpoint:
 *   javaStates  – the ordered list of JavaThreadState enum names
 *   javaCounts1 – counts for dump 1 (same index order as javaStates)
 *   javaCounts2 – counts for dump 2
 */
const props = defineProps<{
  javaStates: string[];
  javaCounts1: number[];
  javaCounts2: number[];
  dump1Name: string;
  dump2Name: string;
}>();

interface StateRow {
  state: string;
  count1: number;
  count2: number;
  delta: number;
}

const rows = computed((): StateRow[] => {
  const all = props.javaStates.map((state, i) => {
    const count1 = props.javaCounts1[i] ?? 0;
    const count2 = props.javaCounts2[i] ?? 0;
    return {
      state,
      count1,
      count2,
      delta: count2 - count1,
    };
  });

  return all
    .filter(r => r.count1 > 0 || r.count2 > 0)
    .sort((a, b) => Math.max(b.count1, b.count2) - Math.max(a.count1, a.count2));
});

</script>

<template>
  <div>
    <el-table v-if="rows.length > 0" :data="rows" stripe>
      <el-table-column :label="tdt('stateDistributionCompare.state') ?? 'State'" prop="state" min-width="200" />
      <el-table-column :label="dump1Name" prop="count1" align="right" min-width="220" />
      <el-table-column :label="tdt('stateDistributionCompare.delta') ?? 'Δ'" align="center" width="130">
        <template #default="{ row }">
          <span :class="deltaClass(row.delta)">{{ deltaText(row.delta) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="dump2Name" prop="count2" align="right" min-width="220" />
    </el-table>
    <el-empty v-else :description="tdt('stateDistributionCompare.noData')" />
  </div>
</template>

<style scoped>
/* delta colors come from ThreadDumpCompare parent's :deep rules */
</style>
