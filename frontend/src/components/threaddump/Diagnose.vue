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
    Assisted-by: GitHub Copilot (Claude Sonnet 4.5)
 -->
<script setup lang="ts">
import { CircleCheckFilled, CircleCloseFilled, InfoFilled, WarningFilled } from '@element-plus/icons-vue';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { tdt } from '@/i18n/i18n';
import { useI18n } from 'vue-i18n';
import Thread from '@/components/threaddump/Thread.vue';
import { TABLE_HEADER_CELL_STYLE } from '@/components/styles';

const { request } = useAnalysisApiRequester();
const { t } = useI18n();

const loading = ref(false);

interface DiagnosticEntry {
  severity: 'OK' | 'INFO' | 'WARNING' | 'ERROR';
  type: string;
  params: Record<string, any>;
  threads?: { id: number; name: string }[];
  file?: string;
}

const diagnostics = ref<DiagnosticEntry[]>([]);

// Dialog for examining affected threads
const threadDialogVisible = ref(false);
const selectedThreadIds = ref<number[]>([]);

function loadData() {
  loading.value = true;
  diagnostics.value = [];
  request('diagnose', {}).then((data: DiagnosticEntry[]) => {
    if (!data || data.length === 0) {
      diagnostics.value = [{ severity: 'OK', type: 'NO_ISSUES', params: {} }];
    } else {
      diagnostics.value = data;
    }
  }).finally(() => {
    loading.value = false;
  });
}

function severityColor(severity: string): string {
  if (severity === 'ERROR')   return 'color: var(--el-color-danger)';
  if (severity === 'WARNING') return 'color: var(--el-color-warning)';
  if (severity === 'OK')      return 'color: var(--el-color-success)';
  return 'color: var(--el-color-info)';
}

function messageText(row: DiagnosticEntry): string {
  if (!row.type) return '';
  const key = `jifa.threadDump.diagnosis.type.${row.type}`;
  const count: number = row.params?.count ?? 0;
  return t(key, count, row.params);
}

function suggestionText(row: DiagnosticEntry): string {
  if (!row.type) return '';
  return t(`jifa.threadDump.diagnosis.type.${row.type}_SUGGESTION`);
}

function examineThreads(row: DiagnosticEntry) {
  if (Array.isArray(row.threads) && row.threads.length > 0) {
    selectedThreadIds.value = row.threads.map((th) => th.id);
    threadDialogVisible.value = true;
  }
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div v-loading="loading">
    <el-table
      :data="diagnostics"
      stripe
      :header-cell-style="TABLE_HEADER_CELL_STYLE"
      style="width: 100%"
    >
      <!-- Message column -->
      <el-table-column :label="tdt('diagnosis.messageColumn')">
        <template #default="{ row }">
          <div style="display: flex; align-items: center">
            <el-icon
              style="flex-shrink: 0"
              :style="severityColor(row.severity)"
              :title="row.severity"
            >
              <CircleCloseFilled v-if="row.severity === 'ERROR'" />
              <WarningFilled v-else-if="row.severity === 'WARNING'" />
              <CircleCheckFilled v-else-if="row.severity === 'OK'" />
              <InfoFilled v-else />
            </el-icon>
            <span style="margin-left: 10px">{{ messageText(row) }}</span>
          </div>
        </template>
      </el-table-column>

      <!-- Suggestion column -->
      <el-table-column :label="tdt('diagnosis.suggestionColumn')">
        <template #default="{ row }">
          <span>{{ suggestionText(row) }}</span>
          <el-button
            v-if="Array.isArray(row.threads) && row.threads.length > 0"
            type="primary"
            link
            style="margin-left: 8px"
            @click="examineThreads(row)"
          >
            {{ tdt('diagnosis.examine') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Thread detail dialog -->
    <el-dialog v-model="threadDialogVisible" width="80%">
      <Thread :ids="selectedThreadIds" />
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.el-table .cell) {
  word-break: break-word !important;
}
</style>
