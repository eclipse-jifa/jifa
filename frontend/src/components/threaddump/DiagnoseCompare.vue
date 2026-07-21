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
import {
  CircleCheckFilled,
  CircleCloseFilled,
  InfoFilled,
  WarningFilled,
} from '@element-plus/icons-vue';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { THREAD_DUMP } from '@/composables/file-types';
import { tdt } from '@/i18n/i18n';
import { useI18n } from 'vue-i18n';
import Thread from '@/components/threaddump/Thread.vue';

const props = defineProps<{
  file1: string;
  file2: string;
  dump1Name: string;
  dump2Name: string;
}>();

const { requestWithTarget } = useAnalysisApiRequester();
const { t } = useI18n();

// ── Types ─────────────────────────────────────────────────────────────────────

interface DiagnosticEntry {
  severity: 'OK' | 'INFO' | 'WARNING' | 'ERROR';
  type: string;
  params: Record<string, any>;
  threads?: { id: number; name: string }[];
}

/**
 * A row in the merged comparison table.
 * One row per unique diagnostic type found in either dump.
 */
interface CompareRow {
  type: string;
  dump1: DiagnosticEntry | null;
  dump2: DiagnosticEntry | null;
  /** Issue appeared in dump 2 but not dump 1 → new problem */
  isNew: boolean;
  /** Issue existed in dump 1 but not dump 2 → resolved */
  isResolved: boolean;
}

// ── State ─────────────────────────────────────────────────────────────────────

const loading = ref(false);
const rows = ref<CompareRow[]>([]);

const threadDialogVisible = ref(false);
const selectedIds = ref<number[]>([]);
const selectedFile = ref('');

// ── Helpers ───────────────────────────────────────────────────────────────────

const SEVERITY_ORDER: Record<string, number> = { ERROR: 3, WARNING: 2, INFO: 1, OK: 0 };

function severityIcon(entry: DiagnosticEntry | null) {
  if (!entry || entry.severity === 'OK') return CircleCheckFilled;
  if (entry.severity === 'ERROR')         return CircleCloseFilled;
  if (entry.severity === 'WARNING')       return WarningFilled;
  return InfoFilled;
}

function severityStyle(entry: DiagnosticEntry | null): string {
  if (!entry || entry.severity === 'OK') return 'color: var(--el-color-success)';
  if (entry.severity === 'ERROR')        return 'color: var(--el-color-danger)';
  if (entry.severity === 'WARNING')      return 'color: var(--el-color-warning)';
  return 'color: var(--el-color-info)';
}

function messageText(entry: DiagnosticEntry | null): string {
  if (!entry) return tdt('diagnoseCompare.noIssue') ?? '✓ No issue';
  if (entry.type === 'NO_ISSUES') return tdt('diagnosis.type.NO_ISSUES') ?? 'No issues found';
  const count: number = entry.params?.count ?? 0;
  return t(`jifa.threadDump.diagnosis.type.${entry.type}`, count, entry.params);
}

function hasThreads(entry: DiagnosticEntry | null): boolean {
  return !!(entry?.threads?.length);
}

function openThreads(entry: DiagnosticEntry, file: string) {
  if (!hasThreads(entry)) return;
  selectedIds.value = entry.threads!.map(th => th.id);
  selectedFile.value = file;
  threadDialogVisible.value = true;
}

/**
 * Merges two diagnostic arrays by `type`.
 * NO_ISSUES entries are filtered – they only appear if *both* dumps have no issues.
 */
function mergeResults(d1: DiagnosticEntry[], d2: DiagnosticEntry[]): CompareRow[] {
  const d1Map = new Map(d1.map(e => [e.type, e]));
  const d2Map = new Map(d2.map(e => [e.type, e]));

  // If both are NO_ISSUES, return a single OK row
  if (d1Map.size === 1 && d1Map.has('NO_ISSUES') && d2Map.size === 1 && d2Map.has('NO_ISSUES')) {
    return [{ type: 'NO_ISSUES', dump1: d1Map.get('NO_ISSUES')!, dump2: d2Map.get('NO_ISSUES')!, isNew: false, isResolved: false }];
  }

  // Collect all real issue types (exclude NO_ISSUES)
  const allTypes = new Set([...d1Map.keys(), ...d2Map.keys()].filter(t => t !== 'NO_ISSUES'));

  const result: CompareRow[] = Array.from(allTypes).map(type => {
    const e1 = d1Map.get(type) ?? null;
    const e2 = d2Map.get(type) ?? null;
    return {
      type,
      dump1: e1,
      dump2: e2,
      isNew: !e1 && !!e2,
      isResolved: !!e1 && !e2,
    };
  });

  // Sort: worsened/new first (highest combined severity), resolved last
  result.sort((a, b) => {
    const sA = Math.max(SEVERITY_ORDER[a.dump1?.severity ?? 'OK'], SEVERITY_ORDER[a.dump2?.severity ?? 'OK']);
    const sB = Math.max(SEVERITY_ORDER[b.dump1?.severity ?? 'OK'], SEVERITY_ORDER[b.dump2?.severity ?? 'OK']);
    if (sB !== sA) return sB - sA;
    if (a.isResolved !== b.isResolved) return a.isResolved ? 1 : -1;
    return 0;
  });

  return result;
}

// ── Data loading ──────────────────────────────────────────────────────────────

async function load() {
  if (!props.file1 || !props.file2) return;
  loading.value = true;
  rows.value = [];
  try {
    const [d1, d2] = await Promise.all([
      requestWithTarget('diagnose', THREAD_DUMP, props.file1, {}),
      requestWithTarget('diagnose', THREAD_DUMP, props.file2, {}),
    ]);
    const r1: DiagnosticEntry[] = d1?.length ? d1 : [{ severity: 'OK', type: 'NO_ISSUES', params: {} }];
    const r2: DiagnosticEntry[] = d2?.length ? d2 : [{ severity: 'OK', type: 'NO_ISSUES', params: {} }];
    rows.value = mergeResults(r1, r2);
  } finally {
    loading.value = false;
  }
}

watch([() => props.file1, () => props.file2], load);
onMounted(load);
</script>

<template>
  <div v-loading="loading" style="min-height: 60px">
    <el-table v-if="rows.length > 0" :data="rows" stripe>

      <!-- Issue type label – derived from whichever dump has it -->
      <el-table-column :label="tdt('diagnoseCompare.issue') ?? 'Issue'" min-width="200">
        <template #default="{ row }">
          <span
            class="issue-label"
            :class="{
              'is-new':      row.isNew,
              'is-resolved': row.isResolved,
            }"
          >
            {{ row.type === 'NO_ISSUES'
              ? (tdt('diagnosis.type.NO_ISSUES') ?? 'No issues')
              : (tdt(`diagnoseCompare.issueType.${row.type}`) ?? row.type) }}
          </span>
        </template>
      </el-table-column>

      <!-- Dump 1 cell -->
      <el-table-column :label="dump1Name" min-width="300">
        <template #default="{ row }">
          <div class="cell-content">
            <el-icon :style="severityStyle(row.dump1)" style="flex-shrink:0">
              <component :is="severityIcon(row.dump1)" />
            </el-icon>
            <span class="cell-text">{{ messageText(row.dump1) }}</span>
            <el-button
              v-if="hasThreads(row.dump1)"
              type="primary" link size="small"
              @click="openThreads(row.dump1, file1)"
            >{{ tdt('diagnosis.examine') }}</el-button>
          </div>
        </template>
      </el-table-column>

      <!-- Dump 2 cell -->
      <el-table-column :label="dump2Name" min-width="300">
        <template #default="{ row }">
          <div class="cell-content">
            <el-icon :style="severityStyle(row.dump2)" style="flex-shrink:0">
              <component :is="severityIcon(row.dump2)" />
            </el-icon>
            <span class="cell-text" :class="{ 'text-new': row.isNew, 'text-resolved': row.isResolved }">
              {{ messageText(row.dump2) }}
            </span>
            <el-button
              v-if="hasThreads(row.dump2)"
              type="primary" link size="small"
              @click="openThreads(row.dump2, file2)"
            >{{ tdt('diagnosis.examine') }}</el-button>
          </div>
          <!-- Badge for state changes -->
          <el-tag
            v-if="row.isNew"
            type="danger" size="small" disable-transitions
            style="margin-left: 6px; vertical-align: middle"
          >{{ tdt('diagnoseCompare.new') ?? 'New' }}</el-tag>
          <el-tag
            v-else-if="row.isResolved"
            type="success" size="small" disable-transitions
            style="margin-left: 6px; vertical-align: middle"
          >{{ tdt('diagnoseCompare.resolved') ?? 'Resolved' }}</el-tag>
        </template>
      </el-table-column>

    </el-table>

    <!-- Thread detail dialog – uses the file that was clicked -->
    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedIds" :target="selectedFile || undefined" />
    </el-dialog>
  </div>
</template>

<style scoped>
.cell-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.cell-text {
  flex: 1;
  word-break: break-word;
}

.issue-label {
  font-weight: 500;
}

.is-new {
  color: var(--el-color-danger);
}

.is-resolved {
  color: var(--el-color-success);
}

.text-new {
  color: var(--el-color-danger);
  font-weight: 500;
}

.text-resolved {
  color: var(--el-color-success);
}

:deep(.el-table .cell) {
  word-break: break-word !important;
}
</style>
