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
import * as echarts from 'echarts';
import { isDark } from '@/composables/theme';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { tdt } from '@/i18n/i18n';
import Diagnose from './Diagnose.vue';
import Thread from './Thread.vue';

const { request } = useAnalysisApiRequester();

const COLOR_PALETTE = [
  '#003f5c', '#2f4b7c', '#665191', '#a05195', '#d45087',
  '#f95d6a', '#ff7c43', '#ffa600', '#488f31', '#8aa1b4'
];

// ---- types ----
interface ThreadStat {
  counts: number[];
}

interface JavaThreadStat extends ThreadStat {
  javaCounts: number[];
  daemonCount?: number;
}

interface Overview {
  timestamp: string | null;
  vmInfo: string | null;
  jniRefs: number;
  jniWeakRefs: number;
  deadLockCount: number;
  errorCount: number;
  javaStates: string[];
  javaThreadStat: JavaThreadStat;
  threadGroupStat: Record<string, ThreadStat>;
}

interface CpuThread {
  id: number;
  name: string;
  cpu: number | null;
}

// ---- state ----
const overview = ref<Overview | null>(null);
const cpuThreads = ref<CpuThread[]>([]);
const loading = ref(false);

const threadDialogVisible = ref(false);
const selectedThreadId = ref<number | null>(null);

// ---- chart DOM refs ----
const stateChartRef = ref<HTMLElement | null>(null);
const cpuChartRef = ref<HTMLElement | null>(null);
const groupChartRef = ref<HTMLElement | null>(null);

let stateChart: echarts.ECharts | null = null;
let cpuChart: echarts.ECharts | null = null;
let groupChart: echarts.ECharts | null = null;

// ---- helpers ----
function truncate(str: string, n: number): string {
  return str.length > n ? str.slice(0, n - 1) + '…' : str;
}

function openThread(id: number) {
  selectedThreadId.value = id;
  threadDialogVisible.value = true;
}

// ---- derived data ----
const topGroups = computed<[string, number][]>(() => {
  if (!overview.value) return [];
  return Object.entries(overview.value.threadGroupStat)
    .map(([k, v]): [string, number] => [k, v.counts.reduce((a, b) => a + b, 0)])
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);
});

const cpuChartHeight = computed(() =>
  Math.max(180, cpuThreads.value.length * 40)
);

const groupChartHeight = computed(() =>
  Math.max(180, topGroups.value.length * 40)
);

// ---- render functions ----
function renderStateChart() {
  const ov = overview.value;
  if (!stateChartRef.value || !ov) return;
  stateChart?.dispose();
  stateChart = echarts.init(stateChartRef.value, isDark.value ? 'dark' : null);

  const data = ov.javaStates
    .map((name, i) => ({ name, value: ov.javaThreadStat.javaCounts[i] }))
    .filter(d => d.value > 0);

  stateChart.setOption({
    color: COLOR_PALETTE,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['38%', '50%'],
      data,
      label: { show: false },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' }
      }
    }]
  });
}

function renderCpuChart() {
  if (!cpuChartRef.value || cpuThreads.value.length === 0) return;
  cpuChart?.dispose();
  cpuChart = echarts.init(cpuChartRef.value, isDark.value ? 'dark' : null);

  const maxMs = cpuThreads.value.reduce((m, t) => Math.max(m, t.cpu ?? 0), 0);
  const unitKey = maxMs >= 3_600_000 ? 'hours' : maxMs >= 60_000 ? 'minutes' : maxMs >= 1_000 ? 'seconds' : 'milliseconds';
  const div = unitKey === 'hours' ? 3_600_000 : unitKey === 'minutes' ? 60_000 : unitKey === 'seconds' ? 1_000 : 1;
  const unitLabel = tdt('cpuConsumingThreads.' + unitKey);

  const names = cpuThreads.value.map(t => truncate(t.name, 35));
  const values = cpuThreads.value.map(t => parseFloat(((t.cpu ?? 0) / div).toFixed(3)));

  cpuChart.setOption({
    color: COLOR_PALETTE,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => `${params[0].name}<br/>${params[0].value} ${unitLabel}`
    },
    grid: { left: 220, right: 20, top: 10, bottom: 30 },
    xAxis: {
      type: 'value',
      name: `${tdt('cpuConsumingThreads.cpuConsumptionLabel')} (${unitLabel})`
    },
    yAxis: {
      type: 'category',
      data: names,
      inverse: true,
      axisLabel: { fontSize: 11, width: 200, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      cursor: 'pointer',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: COLOR_PALETTE[i % COLOR_PALETTE.length] }
      }))
    }]
  });

  cpuChart.on('click', (params: any) => {
    const thread = cpuThreads.value[params.dataIndex];
    if (thread) openThread(thread.id);
  });
}

function renderGroupChart() {
  if (!groupChartRef.value || topGroups.value.length === 0) return;
  groupChart?.dispose();
  groupChart = echarts.init(groupChartRef.value, isDark.value ? 'dark' : null);

  const names = topGroups.value.map(([k]) => truncate(k, 35));
  const values = topGroups.value.map(([, v]) => v);

  groupChart.setOption({
    color: COLOR_PALETTE,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 220, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'value', name: 'Threads' },
    yAxis: {
      type: 'category',
      data: names,
      inverse: true,
      axisLabel: { fontSize: 11, width: 200, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: COLOR_PALETTE[i % COLOR_PALETTE.length] }
      }))
    }]
  });
}

function renderAllCharts() {
  renderStateChart();
  renderCpuChart();
  renderGroupChart();
}

function resizeCharts() {
  stateChart?.resize();
  cpuChart?.resize();
  groupChart?.resize();
}

watch(isDark, () => renderAllCharts());

// ---- load ----
onMounted(async () => {
  window.addEventListener('resize', resizeCharts);
  loading.value = true;
  try {
    const [ov, cpu] = await Promise.all([
      request('overview'),
      request('cpuConsumingThreads', { max: 10, type: 'JAVA' })
    ]);
    overview.value = ov;
    cpuThreads.value = cpu ?? [];
    await nextTick();
    renderAllCharts();
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts);
  stateChart?.dispose();
  cpuChart?.dispose();
  groupChart?.dispose();
});
</script>

<template>
  <div v-loading="loading">
    <template v-if="overview">
      <!-- Diagnosis -->
      <el-card :header="tdt('threadDumpOverview.diagnosisTitle')" style="margin-bottom: 16px">
        <Diagnose />
      </el-card>

      <!-- Charts row: state distribution + CPU -->
      <el-row :gutter="16" style="margin-bottom: 16px" align="stretch">
        <!-- Java Thread State Distribution -->
        <el-col :span="8">
          <el-card :header="tdt('threadDumpOverview.stateDistributionTitle')" style="height: 100%">
            <div ref="stateChartRef" style="height: 240px" />
          </el-card>
        </el-col>

        <!-- Top CPU Consuming Threads -->
        <el-col :span="16">
          <el-card :header="tdt('threadDumpOverview.cpuConsumingTitle')" style="height: 100%">
            <div
              v-if="cpuThreads.length > 0"
              ref="cpuChartRef"
              :style="{ height: `${cpuChartHeight}px`, cursor: 'pointer' }"
            />
            <el-empty v-else :description="'-'" />
          </el-card>
        </el-col>
      </el-row>

      <!-- Thread Group Summary -->
      <el-card
        v-if="topGroups.length > 0"
        :header="tdt('threadDumpOverview.threadGroupTitle')"
        style="margin-bottom: 16px"
      >
        <div ref="groupChartRef" :style="{ height: `${groupChartHeight}px` }" />
      </el-card>
    </template>

    <!-- Thread detail dialog -->
    <el-dialog v-model="threadDialogVisible" width="70%" top="5vh" destroy-on-close>
      <Thread v-if="selectedThreadId !== null" :ids="[selectedThreadId]" />
    </el-dialog>
  </div>
</template>
