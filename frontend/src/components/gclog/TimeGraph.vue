<!--
    Copyright (c) 2023 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
<script setup lang="ts">
import { useGCLogData } from '@/stores/gc-log-data';
import { currentLocale, gct } from '@/i18n/i18n';
import { COLORS, formatTimePeriod, getIthColor } from '@/components/gclog/utils';
import { prettyTime } from '@/support/utils';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import * as echarts from 'echarts';
import { isDark } from '@/composables/theme';
import { Aim, Close, FullScreen } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { cloneDeep } from 'lodash';

const props = defineProps({
  headerBarId: {
    type: String,
    required: true
  }
});

const { request } = useAnalysisApiRequester();
const router = useRouter();

const GCLogData = useGCLogData();
const metadata = GCLogData.metadata;

const useUptime = metadata.timestamp < 0;

const mounted = ref(false);
const loading = ref(false);
const fullScreen = ref(false);

let dataConfig;
const name2type: any = {};
const color: any = {};
const fixedColor = {
  'Young GC': '#5470c6',
  'Mixed GC': '#fac858',
  'Full GC': '#ee6666',
  'Garbage Collection': '#ee6666',
  'Concurrent Mark Cycle': '#91cc75',
  'Concurrent Undo Cycle': '#ea7ccc',
  CMS: '#91cc75'
};
const memoryUnit = 128 * 1024 * 1024; // 128MB

let timeGraphData;
const chartRef = ref();
const fullScreenChartRef = ref();
let chartInstance;
let dataLoaded = {};

function initialize() {
  const memory = [];
  if (metadata.generational) {
    memory.push('youngCapacity', 'oldUsed', 'oldCapacity');
  }
  if (metadata.collector === 'G1 GC' && metadata.logStyle === 'unified') {
    memory.push('humongousUsed');
  }
  memory.push('heapUsed', 'heapCapacity', 'metaspaceUsed');
  if (metadata.metaspaceCapacityReliable) {
    memory.push('metaspaceCapacity');
  }
  memory.push('reclamation');
  if (metadata.generational) {
    memory.push('promotion');
  }

  dataConfig = {
    memory: memory,
    time: metadata.importantEventTypes
  };

  allTypes().forEach((type) => (name2type[typeI18n(type)] = type));

  const types = [
    'youngCapacity',
    'oldUsed',
    'oldCapacity',
    'humongousUsed',
    'heapUsed',
    'heapCapacity',
    'metaspaceUsed',
    'metaspaceCapacity',
    'reclamation',
    'promotion',
    ...metadata.importantEventTypes
  ];

  let index = 0;
  types.forEach((type) => {
    if (fixedColor.hasOwnProperty(type)) {
      color[type] = fixedColor[type];
    } else {
      color[type] = getIthColor(index++);
    }
  });
}

initialize();

function revertTransformTime(time) {
  if (useUptime) {
    return time * 1000;
  } else {
    return time - metadata.timestamp;
  }
}

function applyTimeToConfig() {
  if (chartInstance) {
    const { dataZoom } = chartInstance.getOption();
    let zoom = dataZoom.find((e) => e.type === 'slider');

    let analysisConfig = cloneDeep(GCLogData.analysisConfig);
    analysisConfig.timeRange.start = revertTransformTime(zoom.startValue);
    analysisConfig.timeRange.end = revertTransformTime(zoom.endValue);
    GCLogData.setAnalysisConfig(analysisConfig);

    router.push({
      query: {
        start: analysisConfig.timeRange.start,
        end: analysisConfig.timeRange.end
      }
    });
  }
}

function transformTime(time) {
  if (useUptime) {
    return time / 1000;
  } else {
    return time + metadata.timestamp;
  }
}

function formatXAxis(time) {
  if (useUptime) {
    return Math.round(time);
  } else {
    return prettyTime(time, 'M-D h:m:s');
  }
}

function generateOptions() {
  const defaultTypes = chooseDefaultDataTypes();
  const selected = {};
  Object.keys(defaultTypes).forEach((type) => (selected[typeI18n(type)] = defaultTypes[type]));

  let option = {
    color: COLORS,
    legend: {
      orient: 'horizontal',
      left: 'center',
      selected: selected,
      selectorLabel: {}
    },
    grid: {
      top: 120
    },
    xAxis: {
      type: useUptime ? 'value' : 'time',
      min: transformTime(metadata.startTime),
      max: transformTime(metadata.endTime)
    },
    yAxis: [
      {
        type: 'value',
        id: 'memory',
        name: gct('timeGraph.memory'),
        position: 'left',
        z: 0,
        min: 0,
        splitNumber: 10,
        axisLabel: {
          formatter: (size) => Math.round((size * memoryUnit) / 1024 / 1024) + 'MB'
        },
        splitLine: {
          show: false
        }
      },
      {
        type: 'value',
        id: 'time',
        name: gct('timeGraph.time'),
        z: 1,
        min: 0,
        splitNumber: 10,
        axisLabel: {
          formatter: formatTimePeriod
        },
        splitLine: {
          show: false
        }
      }
    ],
    dataZoom: [
      {
        id: 'zoom',
        type: 'slider',
        startValue: transformTime(GCLogData.analysisConfig.timeRange.start),
        endValue: transformTime(GCLogData.analysisConfig.timeRange.end),
        filterMode: 'none',
        zoomLock: false,
        moveOnMouseMove: false,
        zoomOnMouseWheel: false,
        brushSelect: true,
        labelFormatter: formatXAxis,
        dataBackground: {
          lineStyle: {
            opacity: 0
          },
          areaStyle: {
            opacity: 0
          }
        }
      },
      {
        type: 'inside',
        zoomOnMouseWheel: true,
        moveOnMouseMove: true
      }
    ],
    series: []
  };

  allTypes().forEach((type) => {
    const category = getCategory(type);
    if (category === 'memory') {
      option.series.push({
        symbol: 'circle',
        symbolSize: 1,
        id: type,
        name: typeI18n(type),
        yAxisIndex: 0,
        type: 'line',
        sampling: 'lttb',
        color: color[type],
        data: []
      });
    } else if (category === 'time') {
      option.series.push({
        symbol: 'circle',
        symbolSize: 6,
        id: type,
        name: typeI18n(type),
        yAxisIndex: 1,
        type: 'scatter',
        sampling: 'lttb',
        color: color[type],
        data: []
      });
    }
  });
  chartInstance.setOption(option);
}

function getMissingTypes(expectedTypes) {
  return allTypes()
    .filter((type) => expectedTypes[type] === true)
    .filter((type) => dataLoaded[type] !== true);
}

function loadData(expectedTypes) {
  const missingTypes = getMissingTypes(expectedTypes);
  if (missingTypes.length === 0) {
    return;
  }

  loading.value = true;

  request('timeGraphData', { dataTypes: missingTypes }).then((data) => {
    timeGraphData = data;
    missingTypes.forEach((type) => (dataLoaded[type] = true));
    renderChart(missingTypes);
    loading.value = false;
  });
}

function renderChart(types) {
  const option = {
    series: types.map((type) => {
      const yUnit = getCategory(type) === 'memory' ? memoryUnit : 1;
      return {
        id: type,
        data: timeGraphData[type].map((d) => [transformTime(d[0]), d[1] / yUnit])
      };
    })
  };
  chartInstance.setOption(option);
}

function allTypes() {
  return [...dataConfig.memory, ...dataConfig.time];
}

function typeI18n(type) {
  if (getCategory(type) === 'memory') {
    return gct('timeGraph.' + type);
  } else {
    return gct('timeGraph.durationOf', { type: type });
  }
}

function getCategory(type): string | undefined {
  for (let t in dataConfig) {
    if (dataConfig[t].indexOf(type) >= 0) {
      return t;
    }
  }
}

function chooseDefaultDataTypes() {
  const result = {};
  allTypes().forEach((type) => {
    result[type] = false;
  });
  ['oldUsed', 'humongousUsed', 'heapUsed', ...metadata.mainPauseEventTypes]
    .filter((type) => getCategory(type) !== undefined)
    .forEach((type) => (result[type] = true));
  return result;
}

function resizeChart() {
  if (chartInstance) {
    chartInstance.resize();
  }
}

function initChart(ref) {
  chartInstance = echarts.init(ref.value, isDark.value ? 'dark' : null);
  chartInstance.on('legendselectchanged', (params) => {
    const expectedTypes = {};
    Object.keys(params.selected).forEach((name) => (expectedTypes[name2type[name]] = true));
    loadData(expectedTypes);
  });
}

function switchChartTo(ref) {
  let oldChartInstance = chartInstance;
  initChart(ref);
  chartInstance.setOption(oldChartInstance.getOption());
  oldChartInstance.dispose();
}

function openFullScreen() {
  fullScreen.value = true;
  nextTick(() => {
    switchChartTo(fullScreenChartRef);
  });
}

function closeFullScreen() {
  fullScreen.value = false;
  nextTick(() => {
    switchChartTo(chartRef);
  });
}

function rerenderChart() {
  if (chartInstance) {
    const { series } = chartInstance.getOption();
    chartInstance.dispose();
    initChart(fullScreen.value ? fullScreenChartRef : chartRef);
    generateOptions();
    loadData(chooseDefaultDataTypes());
  }
}

watch([currentLocale, isDark], () => {
  initialize();
  dataLoaded = {};
  rerenderChart();
});

onMounted(() => {
  window.addEventListener('resize', resizeChart);

  initChart(chartRef);
  generateOptions();
  loadData(chooseDefaultDataTypes());

  mounted.value = true;
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart);
});
</script>
<template>
  <teleport :to="`#${headerBarId}`" v-if="mounted && !loading">
    <div style="display: flex; align-items: center">
      <el-tooltip placement="top" :content="gct('applyTimeToConfig')" :show-arrow="false">
        <el-icon class="icon" size="18" @click="applyTimeToConfig">
          <Aim />
        </el-icon>
      </el-tooltip>
      <el-tooltip placement="top" :content="gct('timeGraph.fullScreen')" :show-arrow="false">
        <el-icon class="icon" style="margin-left: 8px" size="18" @click="openFullScreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>
    </div>
  </teleport>

  <div style="height: 600px" ref="chartRef" v-loading="loading"></div>

  <teleport to="body" v-if="fullScreen">
    <div class="fullscreen-container">
      <div class="fullscreen-header">
        <el-icon class="icon" size="18" @click="closeFullScreen">
          <Close />
        </el-icon>
      </div>
      <div class="fullscreen-body" ref="fullScreenChartRef" />
    </div>
  </teleport>
</template>
<style scoped>
.icon {
  color: var(--el-color-info);
  cursor: pointer;
}

.icon:hover {
  color: var(--el-color-primary);
}

.fullscreen-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

.fullscreen-header {
  padding: 20px;
  display: flex;
  flex-direction: row-reverse;
}

.fullscreen-body {
  padding: 0 20px 20px 20px;
  flex-grow: 1;
}
</style>
