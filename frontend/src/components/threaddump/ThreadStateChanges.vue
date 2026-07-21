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
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { THREAD_DUMP } from '@/composables/file-types';
import { tdt } from '@/i18n/i18n';
import { deltaClass, deltaText, stateTagStyle } from '@/components/threaddump/thread-state-colors';

const props = defineProps<{
  file1: string;
  file2: string;
  dump1Name: string;
  dump2Name: string;
}>();

const { requestWithTarget } = useAnalysisApiRequester();

interface ThreadStateChange {
  id: number;
  name: string;
  stateBefore: string | null;
  stateAfter: string | null;
}

const loading  = ref(false);
const changes  = ref<ThreadStateChange[]>([]);

// Split into three categories for display
const stateChanged   = computed(() => changes.value.filter(e => e.stateBefore && e.stateAfter))
const disappeared    = computed(() => changes.value.filter(e => e.stateBefore && !e.stateAfter))
const newThreads     = computed(() => changes.value.filter(e => !e.stateBefore && e.stateAfter))

interface SummaryRow {
  label: string;
  count1: number;
  count2: number;
  delta: number;
}

const summaryRows = computed((): SummaryRow[] => {
  const disappearedCount = disappeared.value.length;
  const newCount = newThreads.value.length;
  return [
    {
      label: tdt('threadStateChanges.summaryDisappeared') ?? 'Disappeared',
      count1: disappearedCount,
      count2: 0,
      delta: -disappearedCount,
    },
    {
      label: tdt('threadStateChanges.summaryNew') ?? 'New in second dump',
      count1: 0,
      count2: newCount,
      delta: newCount,
    },
  ];
});

const transitionRows = computed(() => {
  const grouped = new Map<string, { from: string; to: string; count: number }>();
  for (const item of stateChanged.value) {
    const from = item.stateBefore ?? '-';
    const to = item.stateAfter ?? '-';
    const key = `${from}>>>${to}`;
    if (!grouped.has(key)) {
      grouped.set(key, { from, to, count: 0 });
    }
    grouped.get(key)!.count += 1;
  }
  return Array.from(grouped.values()).sort((a, b) => b.count - a.count);
});

// State-tag style helper – uses shared stateTagStyle for consistent colors
function tagStyle(state: string | null): Record<string, string> {
  return state ? stateTagStyle(state) : {};
}

async function load() {
  if (!props.file1 || !props.file2) return;
  loading.value = true;
  changes.value = [];
  try {
    const result: ThreadStateChange[] = await requestWithTarget(
      'threadStateChanges',
      THREAD_DUMP,
      props.file1,
      { other: props.file2 }
    );
    changes.value = result ?? [];
  } finally {
    loading.value = false;
  }
}

watch([() => props.file1, () => props.file2], load);
onMounted(load);
</script>

<template>
  <div v-loading="loading" style="min-height: 80px">
    <template v-if="!loading">
      <el-table :data="summaryRows" stripe>
        <el-table-column :label="tdt('threadStateChanges.changeType') ?? 'Change type'" prop="label" min-width="200" />
        <el-table-column :label="dump1Name" prop="count1" align="right" min-width="220" />
        <el-table-column :label="tdt('threadStateChanges.delta') ?? 'Δ'" align="center" width="130">
          <template #default="{ row }">
            <span :class="deltaClass(row.delta)">{{ deltaText(row.delta) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="dump2Name" prop="count2" align="right" min-width="220" />
      </el-table>

      <el-divider v-if="stateChanged.length > 0 || changes.length > 0" />

      <template v-if="stateChanged.length > 0">
        <h4>{{ tdt('threadStateChanges.transitionsTitle') ?? 'State transition breakdown' }}</h4>
        <el-table :data="transitionRows" stripe :max-height="280">
        <el-table-column :label="tdt('threadStateChanges.stateBefore')" min-width="220">
          <template #default="{ row }">
            <el-tag :style="tagStyle(row.from)" size="small" disable-transitions>{{ row.from }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column width="50" align="center"><template #default>→</template></el-table-column>
        <el-table-column :label="tdt('threadStateChanges.stateAfter')" min-width="220">
          <template #default="{ row }">
            <el-tag :style="tagStyle(row.to)" size="small" disable-transitions>{{ row.to }}</el-tag>
          </template>
        </el-table-column>
          <el-table-column :label="tdt('threadStateChanges.count') ?? 'Count'" prop="count" width="120" align="right" />
        </el-table>
      </template>
      <el-empty v-else :description="tdt('threadStateChanges.noChanges')" />
    </template>
  </div>
</template>

<style scoped>
/* delta colors come from ThreadDumpCompare parent's :deep rules */
h4 {
  margin: 0 0 10px;
  font-size: 0.95rem;
  color: var(--el-text-color-secondary);
}
</style>
