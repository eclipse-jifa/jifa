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
import { tdt } from '@/i18n/i18n';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import ThreadDumpSearchForm from './ThreadDumpSearchForm.vue';
import type { SearchModel } from './ThreadDumpSearchForm.vue';
import { stateTagStyle } from '@/components/threaddump/thread-state-colors';
import { TABLE_HEADER_CELL_STYLE } from '@/components/styles';

const { request } = useAnalysisApiRequester();

interface SearchHit {
  id: number;
  name: string;
  javaState: string | null;
  osState: string;
  cpu: number;
  elapsed: number;
  lines: string[];
}

const loading = ref(false);
const searched = ref(false);
const currentSearch = ref<SearchModel | null>(null);
const searchResult = ref<SearchHit[]>([]);

// --- chart ---

const stateCounts = computed(() => {
  const counts = new Map<string, number>();
  for (const hit of searchResult.value) {
    const state = hit.javaState ?? hit.osState;
    counts.set(state, (counts.get(state) ?? 0) + 1);
  }
  return counts;
});

// --- helpers ---

function getThreadState(hit: SearchHit): string {
  return hit.javaState ?? hit.osState;
}

/** Formats a millisecond duration for display (e.g. "1.25 s", "320 ms"). */
function prettyDuration(ms: number): string {
  if (ms >= 60_000) return (ms / 60_000).toFixed(2) + ' min';
  if (ms >= 1_000) return (ms / 1_000).toFixed(2) + ' s';
  return ms.toFixed(ms < 10 ? 2 : 0) + ' ms';
}

/** Sanitise and highlight a single content line. */
function renderContent(hit: SearchHit): string {
  const model = currentSearch.value;
  if (!model || !model.term) return '';

  const raw = typeof model.term === 'string' ? model.term.trim() : '';
  const terms = raw.split(/\s+/).filter(Boolean);
  if (!terms.length) return '';

  const patterns = terms.map((t) => {
    const escaped = model.regex ? t : t.replace(/[-[\]{}()*+?.,\\^$|]/g, '\\$&');
    const flags = 'g' + (model.matchCase ? '' : 'i');
    return new RegExp(`(${escaped})`, flags);
  });

  let content = '';
  hit.lines.forEach((line) => {
    let modified = line.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') + '\n';
    patterns.forEach((p) => {
      modified = modified.replace(p, '<span class="search-hit">$1</span>');
    });
    content += modified;
  });
  return content;
}

// --- search ---

async function doSearch(model: SearchModel) {
  searched.value = true;
  loading.value = true;
  currentSearch.value = model;
  searchResult.value = [];

  const terms =
    typeof model.term === 'string'
      ? model.term
          .trim()
          .split(/\s+/)
          .filter((s) => s.length > 0)
      : [];

  try {
    const result = await request('searchThreads', {
      term: terms,
      searchName: model.searchName,
      searchState: model.searchState,
      searchStack: model.searchStack,
      regex: model.regex,
      matchCase: model.matchCase,
      allowedJavaStates: model.allowedJavaStates?.length ? model.allowedJavaStates : undefined
    });
    searchResult.value = result ?? [];
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <div style="margin-bottom: 16px">
      <ThreadDumpSearchForm @submit="doSearch" />
    </div>

    <div v-if="searched" v-loading="loading" style="min-height: 40px">
      <template v-if="searched && !loading">
        <!-- summary bar -->
        <div class="result-summary">
          <span class="result-count">
            {{ searchResult.length }} {{ tdt('threadDumpSearch.resultsCount') }}
          </span>
          <el-divider direction="vertical" v-if="searchResult.length > 0" />
          <el-tag
            v-if="searchResult.length > 0"
            v-for="[state, count] in stateCounts"
            :key="state"
            :style="stateTagStyle(state)"
            size="small"
            disable-transitions
          >
            {{ state }}&nbsp;{{ count }}
          </el-tag>
        </div>

        <!-- result table -->
        <el-table
          v-if="searchResult.length > 0"
          :data="searchResult"
          :header-cell-style="TABLE_HEADER_CELL_STYLE"
          stripe
          style="width: 100%; margin-top: 12px"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <pre class="thread-content" v-html="renderContent(row)" />
            </template>
          </el-table-column>

          <el-table-column
            prop="name"
            :label="tdt('threadDumpSearch.threadNameLabel')"
            show-overflow-tooltip
          />

          <el-table-column :label="tdt('threadDumpSearch.stateLabel')">
            <template #default="{ row }">
              <el-tag :style="stateTagStyle(getThreadState(row))" size="small" disable-transitions>
                {{ getThreadState(row) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            prop="cpu"
            :label="tdt('threadDumpSearch.cpuLabel')"
            align="right"
            sortable
          >
            <template #default="{ row }">
              <span v-if="row.cpu > 0">{{ prettyDuration(row.cpu) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>

          <el-table-column
            prop="elapsed"
            :label="tdt('threadDumpSearch.elapsedLabel')"
            align="right"
            sortable
          >
            <template #default="{ row }">
              <span v-if="row.elapsed > 0">{{ prettyDuration(row.elapsed) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<style scoped>
.result-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 16px;
}

.result-count {
  font-size: var(--el-font-size-small);
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

/* Same look as the stack content shown in Thread.vue / MonitorThread.vue */
.thread-content {
  margin: 5px 12px;
  border-radius: 8px;
  padding: 7px;
  background-color: rgba(var(--el-color-primary-rgb), 0.1);
  white-space: pre;
  font-size: 14px;
  line-height: 1.5;
  overflow: auto;
  font-family: var(--el-font-family);
}

:deep(.search-hit) {
  color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
  font-weight: bold;
}
</style>
