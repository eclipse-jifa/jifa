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
import * as echarts from 'echarts';
import { isDark } from '@/composables/theme';
import Content from '@/components/threaddump/Content.vue';
import Thread from '@/components/threaddump/Thread.vue';
import Monitor from '@/components/threaddump/Monitor.vue';
import CallSiteTree from '@/components/threaddump/CallSiteTree.vue';
import Diagnose from '@/components/threaddump/Diagnose.vue';
import CpuConsumingThreads from '@/components/threaddump/CpuConsumingThreads.vue';
import BlockedThreads from '@/components/threaddump/BlockedThreads.vue';
import ThreadDumpSearch from '@/components/threaddump/ThreadDumpSearch.vue';
import { stateColor } from '@/components/threaddump/thread-state-colors';

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

// ---- State Distribution Chart ----
const COLOR_PALETTE = [
  '#003f5c', '#2f4b7c', '#665191', '#a05195', '#d45087',
  '#f95d6a', '#ff7c43', '#ffa600', '#488f31', '#8aa1b4'
];
const stateChartRef = ref<HTMLElement | null>(null);
let stateChart: echarts.ECharts | null = null;
const stateChartData = ref<{ name: string; value: number }[]>([]);

function renderStateChart() {
  if (!stateChartRef.value || stateChartData.value.length === 0) return;
  stateChart?.dispose();
  stateChart = echarts.init(stateChartRef.value, isDark.value ? 'dark' : null);
  stateChart.setOption({
    color: COLOR_PALETTE,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'middle',
      textStyle: { fontSize: 11 },
      formatter: (name: string) => name.length > 24 ? name.slice(0, 23) + '…' : name
    },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['30%', '50%'],
      data: stateChartData.value,
      label: { show: false },
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } }
    }]
  });
}

watch(isDark, () => renderStateChart());

const resizeStateChart = () => stateChart?.resize();

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
  window.addEventListener('resize', resizeStateChart);
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

    _threadStats.push(
      buildThreadStat('total', overview.states, overview.threadStat.counts, Histogram)
    );

    threadStats.value = _threadStats;
    threadGroupStats.value = _threadGroupStats;

    stateChartData.value = overview.javaStates
      .map((name: string, i: number) => ({ name, value: overview.javaThreadStat.javaCounts[i] }))
      .filter((d: { name: string; value: number }) => d.value > 0);
    nextTick().then(renderStateChart);

    loading.value = false;
  });
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeStateChart);
  stateChart?.dispose();
  stateChart = null;
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
      <div style="display: flex; justify-content: center; padding: 0 10px">
        <div style="width: 100%; max-width: 1200px">
          <el-collapse v-model="activeNames">
            <el-collapse-item name="basicInfo" :title="tdt('basicInfo')">
              <el-row :gutter="16" align="top">
                <el-col :span="12">
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
                </el-col>
                <el-col :span="12">
                  <div style="font-weight: 600; margin-bottom: 8px; color: var(--el-text-color-regular)">
                    {{ tdt('stateDistributionTitle') }}
                  </div>
                  <div ref="stateChartRef" style="height: 180px" />
                </el-col>
              </el-row>
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
                        :color="stateColor(row.states[idx])"
                        style="cursor: pointer; color: #fff; border: none"
                        disable-transitions
                        @click="showThreadsByState(row.threadType, row.states[idx])"
                      >
                        {{ row.states[idx] }}: {{ row.counts[idx] }}
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
                        :color="stateColor(row.states[idx])"
                        style="cursor: pointer; color: #fff; border: none"
                        disable-transitions
                        @click="showThreadsOfGroupByState(row.key, row.states[idx])"
                      >
                        {{ row.states[idx] }}: {{ row.counts[idx] }}
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
