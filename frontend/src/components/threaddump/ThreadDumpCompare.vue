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
import axios from 'axios';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { THREAD_DUMP } from '@/composables/file-types';
import { tdt } from '@/i18n/i18n';
import { prettyTime } from '@/support/utils';
import { deltaClass, deltaText } from '@/components/threaddump/thread-state-colors';
import CpuConsumingThreadsCompare from '@/components/threaddump/CpuConsumingThreadsCompare.vue';
import StateDistributionCompare from '@/components/threaddump/StateDistributionCompare.vue';
import ThreadStateChanges from '@/components/threaddump/ThreadStateChanges.vue';
import PersistentBlockers from '@/components/threaddump/PersistentBlockers.vue';
import DiagnoseCompare from '@/components/threaddump/DiagnoseCompare.vue';
import { ElMessage } from 'element-plus';
import { useRouter, useRoute } from 'vue-router';
import {
  Clock,
  CoffeeCup,
  Connection,
  Delete,
  Histogram,
  Operation,
  Platform,
  Promotion,
  UploadFilled,
} from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();

const { requestWithTarget } = useAnalysisApiRequester();

// ── Types ────────────────────────────────────────────────────────────────────

interface FileEntry {
  id: number;
  uniqueName: string;
  originalName: string;
}

interface Overview {
  timestamp: number;
  vmInfo: string;
  jniRefs: number;
  jniWeakRefs: number;
  deadLockCount: number;
  errorCount: number;
  javaStates: { name: string }[];
  states: { name: string }[];
  javaThreadStat: { javaCounts: number[]; counts: number[] };
  jitThreadStat:  { counts: number[] };
  gcThreadStat:   { counts: number[] };
  otherThreadStat:{ counts: number[] };
  threadGroupStat: Record<string, { counts: number[] }>;
}

interface ThreadRow {
  label: string;
  icon: any;
  count1: number;
  count2: number;
  delta: number;
}

interface GroupRow {
  name: string;
  count1: number;
  count2: number;
  delta: number;
}

// ── File picker state ────────────────────────────────────────────────────────

const filesLoading = ref(false);
const availableFiles = ref<FileEntry[]>([]);
const file1 = ref('');
const file2 = ref('');
const file1Name = computed(() => availableFiles.value.find(f => f.uniqueName === file1.value)?.originalName ?? file1.value);
const file2Name = computed(() => availableFiles.value.find(f => f.uniqueName === file2.value)?.originalName ?? file2.value);

async function loadFiles() {
  filesLoading.value = true;
  try {
    const pageSize = 50;
    let page = 1;
    const all: FileEntry[] = [];
    while (true) {
      const resp = await axios.get('/jifa-api/files', {
        params: { type: 'THREAD_DUMP', page, pageSize }
      });
      const items: FileEntry[] = resp.data.data ?? [];
      all.push(...items);
      if (all.length >= (resp.data.totalSize ?? Infinity) || items.length < pageSize) break;
      page++;
    }
    availableFiles.value = all;
  } catch {
    ElMessage.error('Failed to load thread dump files');
  } finally {
    filesLoading.value = false;
  }
}

// ── Overview data ────────────────────────────────────────────────────────────

const loading = ref(false);
const ov1 = ref<Overview | null>(null);
const ov2 = ref<Overview | null>(null);
const compared = ref(false);

function sum(arr: number[]) {
  return arr.reduce((a, b) => a + b, 0);
}

// Basic info rows
const basicRows = computed(() => {
  if (!ov1.value || !ov2.value) return [];
  const o1 = ov1.value, o2 = ov2.value;
  const timeDiffMs = Math.abs(o2.timestamp - o1.timestamp);
  const timeDiffSec = Math.round(timeDiffMs / 1000);
  return [
    {
      icon: Clock,
      label: tdt('time'),
      v1: o1.timestamp > 0 ? prettyTime(o1.timestamp, 'Y-M-D h:m:s') : '-',
      v2: o2.timestamp > 0 ? prettyTime(o2.timestamp, 'Y-M-D h:m:s') : '-',
      extra: o1.timestamp > 0 && o2.timestamp > 0
        ? `Δ ${timeDiffSec}s`
        : '',
      match: null,
    },
    {
      icon: Platform,
      label: tdt('vmInfo'),
      v1: o1.vmInfo,
      v2: o2.vmInfo,
      extra: '',
      match: o1.vmInfo === o2.vmInfo,
    },
    {
      icon: Connection,
      label: tdt('jniRefs'),
      v1: o1.jniRefs >= 0 ? `${o1.jniRefs} (${o1.jniWeakRefs} weak)` : '-',
      v2: o2.jniRefs >= 0 ? `${o2.jniRefs} (${o2.jniWeakRefs} weak)` : '-',
      extra: '',
      match: null,
    },
  ];
});

// Thread summary rows
const threadRows = computed((): ThreadRow[] => {
  if (!ov1.value || !ov2.value) return [];
  const o1 = ov1.value, o2 = ov2.value;
  const rows: ThreadRow[] = [
    { label: tdt('javaThread') ?? 'Java', icon: CoffeeCup,
      count1: sum(o1.javaThreadStat.javaCounts), count2: sum(o2.javaThreadStat.javaCounts), delta: 0 },
    { label: tdt('jitThread')  ?? 'JIT',  icon: Promotion,
      count1: sum(o1.jitThreadStat.counts),      count2: sum(o2.jitThreadStat.counts),      delta: 0 },
    { label: tdt('gcThread')   ?? 'GC',   icon: Delete,
      count1: sum(o1.gcThreadStat.counts),        count2: sum(o2.gcThreadStat.counts),        delta: 0 },
    { label: tdt('otherThread') ?? 'Other', icon: Operation,
      count1: sum(o1.otherThreadStat.counts),    count2: sum(o2.otherThreadStat.counts),    delta: 0 },
  ];
  rows.forEach(r => r.delta = r.count2 - r.count1);
  const total1 = rows.reduce((a, r) => a + r.count1, 0);
  const total2 = rows.reduce((a, r) => a + r.count2, 0);
  rows.push({ label: tdt('total') ?? 'Total', icon: Histogram,
    count1: total1, count2: total2, delta: total2 - total1 });
  return rows;
});

// Thread group rows — union of both dumps, sorted by max count desc
const groupRows = computed((): GroupRow[] => {
  if (!ov1.value || !ov2.value) return [];
  const o1 = ov1.value, o2 = ov2.value;
  const allKeys = new Set([...Object.keys(o1.threadGroupStat), ...Object.keys(o2.threadGroupStat)]);
  const rows: GroupRow[] = [];
  allKeys.forEach(k => {
    const c1 = o1.threadGroupStat[k]?.counts ? sum(o1.threadGroupStat[k].counts) : 0;
    const c2 = o2.threadGroupStat[k]?.counts ? sum(o2.threadGroupStat[k].counts) : 0;
    rows.push({ name: k, count1: c1, count2: c2, delta: c2 - c1 });
  });
  rows.sort((a, b) => Math.max(b.count1, b.count2) - Math.max(a.count1, a.count2));
  return rows.slice(0, 20);
});

async function runCompare() {
  if (!file1.value || !file2.value) return;
  loading.value = true;
  ov1.value = null;
  ov2.value = null;
  compared.value = false;
  try {
    [ov1.value, ov2.value] = await Promise.all([
      requestWithTarget('overview', THREAD_DUMP, file1.value),
      requestWithTarget('overview', THREAD_DUMP, file2.value),
    ]);
    compared.value = true;
  } finally {
    loading.value = false;
  }
}

// ── Init ─────────────────────────────────────────────────────────────────────

onMounted(async () => {
  // Capture query param before async load to avoid race with route changes
  const preselect = route.query.file1 as string | undefined;
  await loadFiles();
  if (preselect && availableFiles.value.some(f => f.uniqueName === preselect)) {
    file1.value = preselect;
  }
});
</script>

<template>
  <div class="ej-common-view-div">
    <el-scrollbar>
      <div class="compare-root">

        <!-- ── File picker ──────────────────────────────────────────────── -->
        <div class="picker-bar">
          <el-select
            v-model="file1"
            :placeholder="tdt('threadDumpCompare.selectPlaceholder')"
            :loading="filesLoading"
            filterable
            class="picker-select"
          >
            <el-option
              v-for="f in availableFiles"
              :key="f.uniqueName"
              :label="f.originalName || f.uniqueName"
              :value="f.uniqueName"
            />
          </el-select>

          <span class="picker-vs">vs</span>

          <el-select
            v-model="file2"
            :placeholder="tdt('threadDumpCompare.selectPlaceholder')"
            :loading="filesLoading"
            filterable
            class="picker-select"
          >
            <el-option
              v-for="f in availableFiles"
              :key="f.uniqueName"
              :label="f.originalName || f.uniqueName"
              :value="f.uniqueName"
            />
          </el-select>

          <el-button
            type="primary"
            :disabled="!file1 || !file2 || file1 === file2"
            :loading="loading"
            @click="runCompare"
          >
            {{ tdt('threadDumpCompare.compareButton') }}
          </el-button>
        </div>

        <!-- ── Empty state ──────────────────────────────────────────────── -->
        <template v-if="!compared && !loading">
          <!-- Files still loading -->
          <el-empty v-if="filesLoading" :description="tdt('threadDumpCompare.loadingFiles')" style="margin-top: 60px" />
          <!-- No files at all -->
          <div v-else-if="availableFiles.length === 0" class="empty-state">
            <el-icon class="empty-icon"><UploadFilled /></el-icon>
            <p class="empty-title">{{ tdt('threadDumpCompare.noFilesAvailable') }}</p>
            <p class="empty-hint">{{ tdt('threadDumpCompare.noFilesAvailableHint') }}</p>
            <el-button type="primary" @click="router.push('/')">
              {{ tdt('threadDumpCompare.uploadNow') }}
            </el-button>
          </div>
          <!-- Files available, nothing selected yet -->
          <el-empty
            v-else
            :description="tdt('threadDumpCompare.noFilesSelected')"
            style="margin-top: 60px"
          />
        </template>

        <!-- ── Results ──────────────────────────────────────────────────── -->
        <div v-if="compared" v-loading="loading">

          <!-- All sections in one collapse -->
          <el-collapse :model-value="['basic', 'diagnosis', 'summary', 'groups', 'states', 'stateChanges', 'blockers', 'cpu']">

            <!-- Basic Information -->
            <el-collapse-item name="basic" :title="tdt('threadDumpCompare.basicInfo')">
              <el-table :data="basicRows" :show-header="true" stripe>
                <el-table-column :label="tdt('threadDumpCompare.basicInfo') ?? ''" min-width="200">
                  <template #default="{ row }">
                    <div style="display:flex;align-items:center;gap:6px">
                      <el-icon><component :is="row.icon" /></el-icon>
                      {{ row.label }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column :label="file1Name" prop="v1" min-width="220" align="right" />
                <el-table-column :label="tdt('threadDumpCompare.deltaLabel') ?? 'Δ'" width="130" align="center">
                  <template #default="{ row }">
                    <span v-if="row.extra" class="delta-zero">{{ row.extra }}</span>
                    <el-tag v-else-if="row.match === true"  type="success" size="small" disable-transitions>{{ tdt('threadDumpCompare.vmInfoMatch') }}</el-tag>
                    <el-tag v-else-if="row.match === false" type="warning" size="small" disable-transitions>{{ tdt('threadDumpCompare.vmInfoMismatch') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="file2Name" prop="v2" min-width="220" align="right" />
              </el-table>
            </el-collapse-item>

            <!-- Diagnosis -->
            <el-collapse-item name="diagnosis" :title="tdt('threadDumpCompare.diagnosis')">
              <DiagnoseCompare
                :file1="file1"
                :file2="file2"
                :dump1-name="file1Name"
                :dump2-name="file2Name"
              />
            </el-collapse-item>

          <!-- Thread Summary -->
            <el-collapse-item name="summary" :title="tdt('threadDumpCompare.threadSummary')">
              <el-table :data="threadRows" :show-header="true" stripe>
                <el-table-column :label="tdt('threadDumpCompare.threadType') ?? 'Type'" min-width="200">
                  <template #default="{ row }">
                    <div style="display:flex;align-items:center;gap:6px">
                      <el-icon><component :is="row.icon" /></el-icon>
                      {{ row.label }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column :label="file1Name" prop="count1" align="right" min-width="220" />
                <el-table-column :label="tdt('threadDumpCompare.deltaLabel') ?? 'Δ'" align="center" width="130">
                  <template #default="{ row }">
                    <span :class="deltaClass(row.delta, true)">{{ deltaText(row.delta) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="file2Name" prop="count2" align="right" min-width="220" />
              </el-table>
            </el-collapse-item>

            <!-- Thread Groups -->
            <el-collapse-item name="groups" :title="tdt('threadDumpCompare.threadGroupSummary')">
              <el-table :data="groupRows" :show-header="true" stripe>
                <el-table-column :label="tdt('threadDumpCompare.groupName') ?? 'Group'" prop="name" min-width="200" />
                <el-table-column :label="file1Name" prop="count1" align="right" min-width="220" />
                <el-table-column :label="tdt('threadDumpCompare.deltaLabel') ?? 'Δ'" align="center" width="130">
                  <template #default="{ row }">
                    <span :class="deltaClass(row.delta, true)">{{ deltaText(row.delta) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="file2Name" prop="count2" align="right" min-width="220">
                  <template #default="{ row }">
                    <span :class="row.count1 === 0 ? 'new-group' : ''">{{ row.count2 }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>

            <!-- State Distribution -->
            <el-collapse-item name="states" :title="tdt('threadDumpCompare.stateDistribution')">
              <StateDistributionCompare
                :java-states="ov1!.javaStates.map(s => s.name ?? String(s))"
                :java-counts1="ov1!.javaThreadStat.javaCounts"
                :java-counts2="ov2!.javaThreadStat.javaCounts"
                :dump1-name="file1Name"
                :dump2-name="file2Name"
              />
            </el-collapse-item>

            <!-- Thread State Changes -->
            <el-collapse-item name="stateChanges" :title="tdt('threadDumpCompare.stateChanges')">
              <ThreadStateChanges :file1="file1" :file2="file2" :dump1-name="file1Name" :dump2-name="file2Name" />
            </el-collapse-item>

            <!-- Persistent Blockers -->
            <el-collapse-item name="blockers" :title="tdt('threadDumpCompare.persistentBlockers')">
              <PersistentBlockers :file1="file1" :file2="file2" :dump1-name="file1Name" :dump2-name="file2Name" />
            </el-collapse-item>

            <!-- CPU Delta (last – often empty) -->
            <el-collapse-item name="cpu" :title="tdt('threadDumpCompare.cpuDelta')">
              <CpuConsumingThreadsCompare :file1="file1" :file2="file2" />
            </el-collapse-item>
          </el-collapse>

        </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.compare-root {
  padding: 0 10px 20px;
}

.picker-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  flex-wrap: wrap;
}

.picker-select {
  width: 340px;
}

.picker-vs {
  font-size: 1rem;
  font-family: var(--el-font-family);
  font-weight: bold;
  color: var(--el-text-color-secondary);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  gap: 12px;
  text-align: center;
}
.empty-icon {
  font-size: 64px;
  font-family: var(--el-font-family);
  color: var(--el-text-color-placeholder);
}
.empty-title {
  font-size: 1.1rem;
  font-family: var(--el-font-family);
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0;
}
.empty-hint {
  color: var(--el-text-color-secondary);
  margin: 0;
}

/* Delta colors */
:deep(.delta-pos) { color: var(--el-color-danger); font-weight: bold; }
:deep(.delta-neg) { color: var(--el-color-success); font-weight: bold; }
:deep(.delta-neutral-pos) { color: var(--el-color-primary); font-weight: bold; }
:deep(.delta-neutral-neg) { color: var(--el-color-warning); font-weight: bold; }

/* New group highlight */
:deep(.new-group) { color: var(--el-color-primary); font-style: italic; }



:deep(.el-collapse-item__content) {
  padding-bottom: 12px !important;
}
</style>
