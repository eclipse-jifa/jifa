<!--
    Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
<script setup lang="ts">
import { tdt } from '@/i18n/i18n';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { prettyTime } from '@/support/utils';
import {
  Clock,
  CoffeeCup,
  Connection,
  Delete,
  Histogram,
  Operation,
  Platform,
  Promotion
} from '@element-plus/icons-vue';
import Content from '@/components/threaddump/Content.vue';
import Thread from '@/components/threaddump/Thread.vue';
import Monitor from '@/components/threaddump/Monitor.vue';
import CallSiteTree from '@/components/threaddump/CallSiteTree.vue';
import Diagnose from '@/components/threaddump/Diagnose.vue';
import CpuConsumingThreads from '@/components/threaddump/CpuConsumingThreads.vue';
import BlockedThreads from '@/components/threaddump/BlockedThreads.vue';
import ThreadDumpSearch from '@/components/threaddump/ThreadDumpSearch.vue';
import { stateTagStyle } from '@/components/threaddump/thread-state-colors';

const { request } = useAnalysisApiRequester();

const activeNames = ref<string[]>([
  'basicInfo',
  'diagnosis',
  'threadSummary',
  'threadGroupSummary',
  'blockedThreads',
  'cpuConsumingThreads',
  'javaMonitors',
  'callSiteTree',
  'threadSearch',
]);

const deadLockCount = ref(0);
const errorCount = ref(0);
const basicInfo = ref();
const threadStats = ref();

const threadGroupStats = ref();
const threadGroupTotal = computed(() =>
  threadGroupStats.value ? threadGroupStats.value.length : 0
);
const threadGroupPage = ref(1);
const threadGroupPageSize = 8;
const threadGroupMoreThanOnePage = computed(() => threadGroupTotal.value > threadGroupPageSize);
const tableDataOfThreadGroupStats = computed(() => {
  if (!threadGroupStats.value) {
    return [];
  }
  const start = (threadGroupPage.value - 1) * threadGroupPageSize;
  const end = start + threadGroupPageSize;
  return threadGroupStats.value.slice(start, end);
});

const loading = ref(false);

const threadDialogVisible = ref(false);
const selectedThreadType = ref();
const selectedThreadGroup = ref();
const selectedThreadState = ref();

function sum(arr) {
  return arr.reduce((l, r) => l + r);
}

function sortIndices(counts) {
  let indices = [];
  for (let i = 0; i < counts.length; i++) {
    if (counts[i] > 0) {
      indices.push(i);
    }
  }
  return indices.sort((i, j) => counts[j] - counts[i]);
}

function showThreads(type) {
  selectedThreadType.value = type;
  selectedThreadGroup.value = null;
  selectedThreadState.value = null;
  threadDialogVisible.value = true;
}

function showThreadsOfGroup(group) {
  selectedThreadGroup.value = group;
  selectedThreadType.value = null;
  selectedThreadState.value = null;
  threadDialogVisible.value = true;
}

function showThreadsByState(threadType, state) {
  selectedThreadType.value = threadType;
  selectedThreadGroup.value = null;
  selectedThreadState.value = state;
  threadDialogVisible.value = true;
}

function showThreadsOfGroupByState(group, state) {
  selectedThreadGroup.value = group;
  selectedThreadType.value = null;
  selectedThreadState.value = state;
  threadDialogVisible.value = true;
}

onMounted(() => {
  loading.value = true;
  request('overview').then((overview) => {
    deadLockCount.value = overview.deadLockCount;
    errorCount.value = overview.errorCount;
    basicInfo.value = [
      {
        key: 'time',
        value: overview.timestamp > 0 ? prettyTime(overview.timestamp, 'Y-M-D h:m:s') : '-',
        icon: shallowRef(Clock)
      },
      {
        key: 'vmInfo',
        value: overview.vmInfo,
        icon: shallowRef(Platform)
      },
      {
        key: 'jniRefs',
        value:
          overview.jniRefs >= 0 && overview.jniWeakRefs >= 0
            ? overview.jniRefs + ' (' + overview.jniWeakRefs + ' weak refs)'
            : overview.jniRefs >= 0
            ? overview.jniRefs
            : '-1',
        icon: shallowRef(Connection)
      }
    ];

function buildThreadStat(key, states, counts, icon, threadType?) {
  return {
    key,
    value: sum(counts),
    states,
    counts,
    icon: shallowRef(icon),
    threadType
  };
}

    let _threadStats = [
      buildThreadStat(
        'javaThread',
        overview.javaStates,
        overview.javaThreadStat.javaCounts,
        CoffeeCup,
        'JAVA'
      ),
      buildThreadStat(
        'jitThread',
        overview.states,
        overview.jitThreadStat.counts,
        Promotion,
        'JIT'
      ),
      buildThreadStat('gcThread', overview.states, overview.gcThreadStat.counts, Delete, 'GC'),
      buildThreadStat(
        'otherThread',
        overview.states,
        overview.otherThreadStat.counts,
        Operation,
        'VM'
      )
    ];

    let _threadGroupStats = [];

    for (let k in overview.threadGroupStat) {
      _threadGroupStats.push({
        key: k,
        value: sum(overview.threadGroupStat[k].counts),
        states: overview.states,
        counts: overview.threadGroupStat[k].counts
      });
    }

    _threadStats.sort((i, j) => j.value - i.value);
    _threadGroupStats.sort((i, j) => j.value - i.value);

    // The total row merges the per-type distributions so that each state tag
    // is consistent with the state used by the backend when filtering threads
    // (Java state for Java threads, OS state for the others).
    let mergedCounts = new Map();
    for (let stat of _threadStats) {
      stat.states.forEach((state, i) => {
        if (stat.counts[i] > 0) {
          mergedCounts.set(state, (mergedCounts.get(state) || 0) + stat.counts[i]);
        }
      });
    }
    _threadStats.push(
      buildThreadStat('total', [...mergedCounts.keys()], [...mergedCounts.values()], Histogram)
    );

    threadStats.value = _threadStats;
    threadGroupStats.value = _threadGroupStats;

    loading.value = false;
  });
});
</script>
<template>
  <div class="ej-common-view-div" v-loading="loading">
    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread
        :type="selectedThreadType"
        :group-name="selectedThreadGroup"
        :thread-state="selectedThreadState"
      />
    </el-dialog>

    <el-scrollbar>
      <div style="padding: 0 10px">
        <el-collapse v-model="activeNames">
          <el-collapse-item name="basicInfo" :title="tdt('basicInfo')">
            <el-table stripe :show-header="false" :data="basicInfo" v-loading="loading">
              <el-table-column>
                <template #default="{ row }">
                  <div style="display: flex; align-items: center">
                    <el-icon>
                      <component :is="row.icon" />
                    </el-icon>
                    <span style="margin-left: 10px">{{ tdt(row.key) }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="value"> </el-table-column>
            </el-table>
          </el-collapse-item>

          <el-collapse-item name="diagnosis" :title="tdt('diagnosis.title')">
            <Diagnose />
          </el-collapse-item>

          <el-collapse-item name="threadSummary" :title="tdt('threadSummary')">
            <el-table stripe :show-header="false" :data="threadStats" v-loading="loading">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div style="padding: 6px 12px; display: flex; flex-wrap: wrap; gap: 6px">
                    <el-tag
                      v-for="idx in sortIndices(row.counts)"
                      :key="idx"
                      :style="{ cursor: 'pointer', ...stateTagStyle(row.states[idx]) }"
                      disable-transitions
                      @click="showThreadsByState(row.threadType, row.states[idx])"
                    >
                      {{ row.states[idx] }}&nbsp;{{ row.counts[idx] }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <el-table-column>
                <template #default="{ row }">
                  <div style="display: flex; align-items: center">
                    <el-icon>
                      <component :is="row.icon" />
                    </el-icon>
                    <span
                      class="clickable"
                      style="margin-left: 10px"
                      @click="showThreads(row.threadType)"
                      >{{ tdt(row.key) }}</span
                    >
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="value"> </el-table-column>
            </el-table>
          </el-collapse-item>

          <el-collapse-item
            name="threadGroupSummary"
            :title="tdt('threadGroupSummary')"
            v-if="tableDataOfThreadGroupStats.length"
          >
            <el-table
              stripe
              :show-header="false"
              v-bind="threadGroupMoreThanOnePage ? { height: `${40 * threadGroupPageSize}px` } : {}"
              :data="tableDataOfThreadGroupStats"
            >
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div style="padding: 6px 12px; display: flex; flex-wrap: wrap; gap: 6px">
                    <el-tag
                      v-for="idx in sortIndices(row.counts)"
                      :key="idx"
                      :style="{ cursor: 'pointer', ...stateTagStyle(row.states[idx]) }"
                      disable-transitions
                      @click="showThreadsOfGroupByState(row.key, row.states[idx])"
                    >
                      {{ row.states[idx] }}&nbsp;{{ row.counts[idx] }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <el-table-column>
                <template #default="{ row }">
                  <span class="clickable" @click="showThreadsOfGroup(row.key)">{{ row.key }}</span>
                </template>
              </el-table-column>

              <el-table-column prop="value"> </el-table-column>
            </el-table>

            <div class="pagination" v-if="threadGroupMoreThanOnePage">
              <el-pagination
                layout="total, prev, pager, next"
                background
                :total="threadGroupTotal"
                :page-size="threadGroupPageSize"
                v-model:current-page="threadGroupPage"
              />
            </div>
          </el-collapse-item>

          <el-collapse-item name="javaMonitors" :title="tdt('monitors')">
            <Monitor />
          </el-collapse-item>

          <el-collapse-item name="blockedThreads" :title="tdt('blockedThreadsLabel')">
            <BlockedThreads />
          </el-collapse-item>

          <el-collapse-item name="cpuConsumingThreads" :title="tdt('cpuConsumingThreadsLabel')">
            <CpuConsumingThreads />
          </el-collapse-item>

          <el-collapse-item name="threadSearch" :title="tdt('threadDumpSearch.label')">
            <ThreadDumpSearch />
          </el-collapse-item>

          <el-collapse-item name="callSiteTree" :title="tdt('callSiteTree')">
            <CallSiteTree />
          </el-collapse-item>

          <el-collapse-item name="fileContent" :title="tdt('fileContent')">
            <Content />
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-scrollbar>
  </div>
</template>
<style scoped>
:deep(.el-collapse-item__content) {
  padding-bottom: 15px !important;
}

.pagination {
  margin-top: 15px;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  overflow: hidden;
}
</style>
