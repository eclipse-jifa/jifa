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
import * as d3 from 'd3';
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { tdt } from '@/i18n/i18n';
import { useI18n } from 'vue-i18n';
import Thread from '@/components/threaddump/Thread.vue';

const { request } = useAnalysisApiRequester();
const { t } = useI18n();

const loading = ref(false);

interface VThread {
  id: number;
  name: string;
}

interface VBlockingThread {
  blockingThread: VThread;
  blockedThreads: VThread[];
  heldLock?: { class: string } | null;
}

interface TreeNode {
  name: string;
  value: number;
  level: string;
  id: number;
  children?: TreeNode[];
}

const blockingThreads = ref<VBlockingThread[]>([]);
const svgRefs = ref<SVGSVGElement[]>([]);
const threadDialogVisible = ref(false);
const selectedIds = ref<number[]>([]);

function blockedTitle(bt: VBlockingThread): string {
  return t('jifa.threadDump.blockedThreads.title', {
    blocker: bt.blockingThread.name,
    count: bt.blockedThreads.length
  });
}

function openThread(id: number) {
  selectedIds.value = [id];
  threadDialogVisible.value = true;
}

function renderTree(svgEl: SVGSVGElement, root: TreeNode) {
  const margin = { top: 0, right: 0, bottom: 0, left: 40 };
  const neededHeight = Math.max(60, root.children!.length * 25);
  const width = 660 - margin.left - margin.right;
  const height = neededHeight - margin.top - margin.bottom;

  const treemap = d3.tree<TreeNode>().size([height, 200]);
  let nodes = d3.hierarchy(root, (d) => d.children);
  nodes = treemap(nodes);

  const svg = d3
    .select(svgEl)
    .attr('width', width + margin.left + margin.right)
    .attr('height', height + margin.top + margin.bottom);

  // clear previous renders
  svg.selectAll('*').remove();

  const g = svg
    .append('g')
    .attr('transform', `translate(${margin.left},${margin.top})`);

  // links
  g.selectAll('.link')
    .data(nodes.descendants().slice(1))
    .enter()
    .append('path')
    .attr('class', 'link')
    .attr('d', (d: any) => {
      return (
        `M${d.y},${d.x}` +
        `C${(d.y + d.parent.y) / 2},${d.x}` +
        ` ${(d.y + d.parent.y) / 2},${d.parent.x}` +
        ` ${d.parent.y},${d.parent.x}`
      );
    });

  // nodes
  const node = g
    .selectAll('.node')
    .data(nodes.descendants())
    .enter()
    .append('g')
    .attr('class', (d) => 'node' + (d.children ? ' node--internal' : ' node--leaf'))
    .attr('transform', (d: any) => `translate(${d.y},${d.x})`);

  node
    .append('circle')
    .attr('r', (d) => (d.data as TreeNode).value)
    .style('fill', (d) => (d.data as TreeNode).level)
    .style('cursor', 'pointer')
    .on('click', (_e, d) => openThread((d.data as TreeNode).id));

  // Only label leaf nodes (blocked threads) — the root (blocking thread)
  // is already shown in the <p> title above; rendering it here too causes
  // a duplicate label that overflows (via overflow:visible) into the title.
  node
    .filter((d: any) => !d.children)
    .append('text')
    .attr('dy', '.35em')
    .attr('x', 15)
    .attr('y', 0)
    .style('text-anchor', 'start')
    .style('font', '13px sans-serif')
    .style('cursor', 'pointer')
    .text((d) => (d.data as TreeNode).name)
    .on('click', (_e, d) => openThread((d.data as TreeNode).id));
}

function buildTree(bt: VBlockingThread): TreeNode {
  return {
    name: bt.blockingThread.name,
    value: 15,
    level: '#F56C6C',
    id: bt.blockingThread.id,
    children: bt.blockedThreads.map((c) => ({
      name: c.name,
      value: 10,
      level: '#409EFF',
      id: c.id
    }))
  };
}

function drawTrees() {
  nextTick(() => {
    svgRefs.value.forEach((el, i) => {
      if (el && blockingThreads.value[i]) {
        renderTree(el, buildTree(blockingThreads.value[i]));
      }
    });
  });
}

onMounted(() => {
  loading.value = true;
  request('blockingThreads', {}).then((data: VBlockingThread[]) => {
    blockingThreads.value = data ?? [];
    loading.value = false;
    drawTrees();
  });
});

// Reset the ref array before each update so stale SVG refs from removed
// list items do not linger (Vue 3 recommended pattern for function refs in v-for).
onBeforeUpdate(() => {
  svgRefs.value = [];
});
</script>

<template>
  <div v-loading="loading">
    <div v-if="blockingThreads.length === 0 && !loading" style="color: #67c23a; padding: 8px 0">
      ✔ {{ tdt('diagnosis.type.NO_ISSUES') }}
    </div>

    <div
      v-for="(bt, idx) in blockingThreads"
      :key="idx"
      style="margin-bottom: 24px"
    >
      <p style="margin: 0 0 6px; font-weight: 500; color: #F56C6C">
        ⚠ {{ blockedTitle(bt) }}
        <span v-if="bt.heldLock" style="font-weight: normal; color: #909399; font-size: 12px">
          — locked {{ bt.heldLock.class }}
        </span>
      </p>
      <svg
        :ref="(el) => { if (el) svgRefs[idx] = el as SVGSVGElement }"
        :key="'svg-' + idx"
        style="overflow: visible"
      />
    </div>

    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedIds" />
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.link) {
  fill: none;
  stroke: #ccc;
  stroke-width: 1px;
}
:deep(.link:hover) {
  stroke: #333;
  stroke-width: 2px;
}
:deep(.node circle) {
  stroke: steelblue;
  stroke-width: 1px;
}
:deep(.node:hover circle) {
  stroke: #007bff;
  stroke-width: 2px;
}
</style>
