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
import Thread from '@/components/threaddump/Thread.vue';
import { deltaClass, deltaText } from '@/components/threaddump/thread-state-colors';
import type { PageView } from '@/components/threaddump/thread-state-colors';

const props = defineProps<{
  file1: string;
  file2: string;
  dump1Name: string;
  dump2Name: string;
}>();

const { requestWithTarget } = useAnalysisApiRequester();

interface BlockedThread {
  id: number;
  name: string;
  cpu?: number;
  elapsed?: number;
}

interface SummaryRow {
  label: string;
  count1: number;
  count2: number;
  delta: number;
}

const loading = ref(false);
const blockedThreads = ref<BlockedThread[]>([]);
const blockedDump1 = ref<BlockedThread[]>([]);
const blockedDump2 = ref<BlockedThread[]>([]);
const dialogVisible  = ref(false);
const selectedId     = ref<number | null>(null);

const summaryRows = computed((): SummaryRow[] => {
  const blocked1 = blockedDump1.value.length;
  const blocked2 = blockedDump2.value.length;
  const persistent = blockedThreads.value.length;
  const resolved = Math.max(0, blocked1 - persistent);
  const introduced = Math.max(0, blocked2 - persistent);

  return [
    {
      label: tdt('persistentBlockers.allBlocked') ?? 'Blocked threads (all)',
      count1: blocked1,
      count2: blocked2,
      delta: blocked2 - blocked1,
    },
    {
      label: tdt('persistentBlockers.persistentOnly') ?? 'Blocked in both dumps',
      count1: persistent,
      count2: persistent,
      delta: 0,
    },
    {
      label: tdt('persistentBlockers.resolved') ?? 'Resolved blockers',
      count1: resolved,
      count2: 0,
      delta: -resolved,
    },
    {
      label: tdt('persistentBlockers.introduced') ?? 'New blockers in dump 2',
      count1: 0,
      count2: introduced,
      delta: introduced,
    },
  ];
});

function openThread(id: number) {
  selectedId.value = id;
  dialogVisible.value = true;
}

async function loadBlockedThreads(target: string) {
  const pageView: PageView<BlockedThread> = await requestWithTarget(
    'threads',
    THREAD_DUMP,
    target,
    { threadState: 'BLOCKED_ON_MONITOR_ENTER', page: 1, pageSize: 10000 }
  );
  return pageView?.data ?? [];
}

async function load() {
  if (!props.file1 || !props.file2) return;
  loading.value = true;
  blockedThreads.value = [];
  blockedDump1.value = [];
  blockedDump2.value = [];
  try {
    const [result, dump1Blocked, dump2Blocked] = await Promise.all([
      requestWithTarget(
        'persistentBlockers',
        THREAD_DUMP,
        props.file1,
        { other: props.file2 }
      ),
      loadBlockedThreads(props.file1),
      loadBlockedThreads(props.file2),
    ]);
    blockedThreads.value = result ?? [];
    blockedDump1.value = dump1Blocked;
    blockedDump2.value = dump2Blocked;
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
        <el-table-column :label="tdt('persistentBlockers.summaryType') ?? 'Type'" prop="label" min-width="200" />
        <el-table-column :label="dump1Name" prop="count1" align="right" min-width="220" />
        <el-table-column :label="tdt('persistentBlockers.delta') ?? 'Δ'" align="center" width="130">
          <template #default="{ row }">
            <span :class="deltaClass(row.delta)">{{ deltaText(row.delta) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="dump2Name" prop="count2" align="right" min-width="220" />
      </el-table>

      <el-alert
        v-if="blockedThreads.length > 0"
        :title="tdt('persistentBlockers.warningTitle', { count: blockedThreads.length })"
        type="warning"
        :closable="false"
        show-icon
        style="margin: 12px 0"
      />

      <template v-if="blockedThreads.length > 0">
        <h4>{{ tdt('persistentBlockers.detailsTitle') ?? 'Threads blocked in both dumps' }}</h4>
        <el-table :data="blockedThreads" stripe :max-height="320">
        <el-table-column :label="tdt('persistentBlockers.thread')" prop="name" min-width="320" show-overflow-tooltip />
        <el-table-column :label="tdt('persistentBlockers.actions')" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openThread(row.id)">
              {{ tdt('persistentBlockers.inspect') }}
            </el-button>
          </template>
        </el-table-column>
        </el-table>
      </template>
    </template>
    <el-dialog v-model="dialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedId != null ? [selectedId] : []" />
    </el-dialog>
  </div>
</template>

<style scoped>
/* delta colors come from ThreadDumpCompare parent's :deep rules */
h4 {
  margin: 6px 0 10px;
  font-size: 0.95rem;
  color: var(--el-text-color-secondary);
}
</style>
