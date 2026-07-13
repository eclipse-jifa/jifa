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
import Thread from '@/components/threaddump/Thread.vue';

const { request } = useAnalysisApiRequester();

const loading = ref(false);

interface CpuThread {
  id: number;
  name: string;
  cpu: number | null;
}

const javaThreads = ref<CpuThread[]>([]);
const nonJavaThreads = ref<CpuThread[]>([]);

const threadDialogVisible = ref(false);
const selectedThreadId = ref<number | null>(null);

const COLOR_PALETTE = [
  '#003f5c', '#2f4b7c', '#665191', '#a05195', '#d45087',
  '#f95d6a', '#ff7c43', '#ffa600', '#488f31', '#8aa1b4'
];

const javaChartRef = ref<HTMLElement | null>(null);
const nonJavaChartRef = ref<HTMLElement | null>(null);
let javaChartInstance: echarts.ECharts | null = null;
let nonJavaChartInstance: echarts.ECharts | null = null;

const javaChartHeight = computed(() => Math.max(180, javaThreads.value.length * 40));
const nonJavaChartHeight = computed(() => Math.max(180, nonJavaThreads.value.length * 40));

function truncate(str: string, n: number): string {
  return str.length > n ? str.slice(0, n - 1) + '…' : str;
}

function openThread(id: number) {
  selectedThreadId.value = id;
  threadDialogVisible.value = true;
}

function buildChartOption(threads: CpuThread[]) {
  const maxMs = threads.reduce((m, t) => Math.max(m, t.cpu ?? 0), 0);
  const unitKey = maxMs >= 3_600_000 ? 'hours' : maxMs >= 60_000 ? 'minutes' : maxMs >= 1_000 ? 'seconds' : 'milliseconds';
  const div = unitKey === 'hours' ? 3_600_000 : unitKey === 'minutes' ? 60_000 : unitKey === 'seconds' ? 1_000 : 1;
  const unitLabel = tdt('cpuConsumingThreads.' + unitKey);
  const names = threads.map(t => truncate(t.name, 35));
  const values = threads.map(t => parseFloat(((t.cpu ?? 0) / div).toFixed(3)));
  return {
    unitLabel,
    option: {
      color: COLOR_PALETTE,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => `${params[0].name}<br/>${params[0].value} ${unitLabel}`
      },
      grid: { left: 220, right: 20, top: 10, bottom: 40 },
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
    }
  };
}

function renderJavaChart() {
  if (!javaChartRef.value || javaThreads.value.length === 0) return;
  javaChartInstance?.dispose();
  javaChartInstance = echarts.init(javaChartRef.value, isDark.value ? 'dark' : null);
  const { option } = buildChartOption(javaThreads.value);
  javaChartInstance.setOption(option);
  javaChartInstance.on('click', (params: any) => {
    const thread = javaThreads.value[params.dataIndex];
    if (thread) openThread(thread.id);
  });
}

function renderNonJavaChart() {
  if (!nonJavaChartRef.value || nonJavaThreads.value.length === 0) return;
  nonJavaChartInstance?.dispose();
  nonJavaChartInstance = echarts.init(nonJavaChartRef.value, isDark.value ? 'dark' : null);
  const { option } = buildChartOption(nonJavaThreads.value);
  nonJavaChartInstance.setOption(option);
  nonJavaChartInstance.on('click', (params: any) => {
    const thread = nonJavaThreads.value[params.dataIndex];
    if (thread) openThread(thread.id);
  });
}

function renderAllCharts() {
  renderJavaChart();
  renderNonJavaChart();
}

function resize() {
  javaChartInstance?.resize();
  nonJavaChartInstance?.resize();
}

watch(isDark, () => renderAllCharts());

// Track which panels are open so we can render charts when a panel is opened
const activeNames = ref<string[]>(['java', 'non-java']);

function onCollapseChange(val: string | number | (string | number)[]) {
  const arr = (Array.isArray(val) ? val : [val]).map(String);
  activeNames.value = arr;
  nextTick(() => {
    if (arr.includes('java')) renderJavaChart();
    if (arr.includes('non-java')) renderNonJavaChart();
  });
}

onMounted(async () => {
  window.addEventListener('resize', resize);
  loading.value = true;
  try {
    const [java, all] = await Promise.all([
      request('cpuConsumingThreads', { max: 10, type: 'JAVA' }),
      request('cpuConsumingThreads', { max: 10 })
    ]);
    const javaList: CpuThread[] = java ?? [];
    const allList: CpuThread[] = all ?? [];
    const javaIds = new Set(javaList.map((t: CpuThread) => t.id));
    javaThreads.value = javaList;
    nonJavaThreads.value = allList.filter((t: CpuThread) => !javaIds.has(t.id));
    await nextTick();
    renderAllCharts();
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  window.removeEventListener('resize', resize);
  javaChartInstance?.dispose();
  nonJavaChartInstance?.dispose();
});
</script>

<template>
  <div v-loading="loading">
    <el-collapse :model-value="activeNames" @change="onCollapseChange">
      <!-- Java Threads -->
      <el-collapse-item :title="tdt('cpuConsumingThreads.javaThreads')" name="java">
        <div
          v-if="javaThreads.length > 0"
          ref="javaChartRef"
          :style="{ height: `${javaChartHeight}px`, cursor: 'pointer' }"
        />
        <el-empty v-else-if="!loading" :description="'-'" />
      </el-collapse-item>

      <!-- Non-Java Threads -->
      <el-collapse-item :title="tdt('cpuConsumingThreads.nonJavaThreads')" name="non-java">
        <div
          v-if="nonJavaThreads.length > 0"
          ref="nonJavaChartRef"
          :style="{ height: `${nonJavaChartHeight}px`, cursor: 'pointer' }"
        />
        <el-empty v-else-if="!loading" :description="'-'" />
      </el-collapse-item>
    </el-collapse>

    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedThreadId != null ? [selectedThreadId] : []" />
    </el-dialog>
  </div>
</template>
