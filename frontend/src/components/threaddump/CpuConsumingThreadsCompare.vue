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
import * as echarts from 'echarts';
import { isDark } from '@/composables/theme';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { THREAD_DUMP } from '@/composables/file-types';
import { tdt } from '@/i18n/i18n';
import Thread from '@/components/threaddump/Thread.vue';

const props = defineProps<{
  file1: string;
  file2: string;
}>();

const { requestWithTarget } = useAnalysisApiRequester();

interface ThreadDelta {
  id: number;
  name: string;
  cpuFirst: number;
  cpuSecond: number;
  cpuDelta: number;
}

const loading = ref(false);
const deltas = ref<ThreadDelta[]>([]);
const threadDialogVisible = ref(false);
const selectedThreadId = ref<number | null>(null);

const chartRef = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;

const chartHeight = computed(() => Math.max(180, deltas.value.length * 40));

const COLOR_POS = '#f95d6a';
const COLOR_NEG = '#488f31';

function truncate(str: string, n: number): string {
  return str.length > n ? str.slice(0, n - 1) + '…' : str;
}

type TimeUnit = 'hours' | 'minutes' | 'seconds' | 'milliseconds';
const DIVISORS: Record<TimeUnit, number> = { hours: 3_600_000, minutes: 60_000, seconds: 1_000, milliseconds: 1 };

function unitLabel(u: TimeUnit): string {
  return tdt(`cpuConsumingThreadsCompare.${u}`) ?? u;
}

function formatMs(ms: number): { value: number; unit: TimeUnit } {
  if (ms >= 3_600_000) return { value: parseFloat((ms / 3_600_000).toFixed(3)), unit: 'hours' };
  if (ms >= 60_000)    return { value: parseFloat((ms / 60_000).toFixed(3)),    unit: 'minutes' };
  if (ms >= 1_000)     return { value: parseFloat((ms / 1_000).toFixed(3)),     unit: 'seconds' };
  return                      { value: parseFloat(ms.toFixed(1)),               unit: 'milliseconds' };
}

function renderChart() {
  if (!chartRef.value || deltas.value.length === 0) return;
  chartInstance?.dispose();
  chartInstance = echarts.init(chartRef.value, isDark.value ? 'dark' : null);

  const maxAbs = deltas.value.reduce((m, t) => Math.max(m, Math.abs(t.cpuDelta)), 0);
  const { unit } = formatMs(maxAbs);
  const div = DIVISORS[unit];
  const label = unitLabel(unit);

  const names  = deltas.value.map(t => truncate(t.name, 35));
  const values = deltas.value.map(t => parseFloat((t.cpuDelta / div).toFixed(3)));

  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const d = deltas.value[params[0].dataIndex];
        const first  = formatMs(d.cpuFirst);
        const second = formatMs(d.cpuSecond);
        const delta  = formatMs(Math.abs(d.cpuDelta));
        return [
          `<b>${d.name}</b>`,
          `${tdt('cpuConsumingThreadsCompare.cpuFirstLabel')}: ${first.value} ${unitLabel(first.unit)}`,
          `${tdt('cpuConsumingThreadsCompare.cpuSecondLabel')}: ${second.value} ${unitLabel(second.unit)}`,
          `${tdt('cpuConsumingThreadsCompare.cpuDeltaLabel')}: ${d.cpuDelta >= 0 ? '+' : '-'}${delta.value} ${unitLabel(delta.unit)}`,
        ].join('<br/>');
      }
    },
    grid: { left: 220, right: 40, top: 10, bottom: 40 },
    xAxis: {
      type: 'value',
      name: `${tdt('cpuConsumingThreadsCompare.cpuDeltaLabel')} (${label})`,
      nameLocation: 'middle',
      nameGap: 28,
    },
    yAxis: {
      type: 'category',
      data: names,
      inverse: true,
      axisLabel: { fontSize: 11, width: 200, overflow: 'truncate' },
    },
    series: [{
      type: 'bar',
      cursor: 'pointer',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: deltas.value[i].cpuDelta >= 0 ? COLOR_POS : COLOR_NEG },
      })),
    }],
  });

  chartInstance.on('click', (params: any) => {
    const d = deltas.value[params.dataIndex];
    if (d) {
      selectedThreadId.value = d.id;
      threadDialogVisible.value = true;
    }
  });
}

function resize() { chartInstance?.resize(); }

async function load() {
  if (!props.file1 || !props.file2) return;
  deltas.value = [];
  chartInstance?.dispose();
  chartInstance = null;
  loading.value = true;
  try {
    const result: ThreadDelta[] = await requestWithTarget(
      'cpuConsumingThreadsCompare',
      THREAD_DUMP,
      props.file1,
      { other: props.file2, max: 20 }
    );
    deltas.value = result ?? [];
    await nextTick();
    renderChart();
  } finally {
    loading.value = false;
  }
}

watch([() => props.file1, () => props.file2], load);
watch(isDark, renderChart);

onMounted(() => {
  window.addEventListener('resize', resize);
  load();
});

onUnmounted(() => {
  window.removeEventListener('resize', resize);
  chartInstance?.dispose();
});
</script>

<template>
  <div v-loading="loading" :style="{ minHeight: '80px' }">
    <div
      v-if="deltas.length > 0"
      ref="chartRef"
      :style="{ height: `${chartHeight}px`, cursor: 'pointer' }"
    />
    <el-empty
      v-else-if="!loading"
      :description="tdt('cpuConsumingThreadsCompare.noData')"
    />
    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedThreadId != null ? [selectedThreadId] : []" />
    </el-dialog>
  </div>
</template>

