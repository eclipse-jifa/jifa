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
import { Lock, WarningFilled } from '@element-plus/icons-vue';
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
  const margin = { top: 10, right: 160, bottom: 10, left: 200 };
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
    .attr('class', (d) => 'node' + (d.children ? ' node--blocker' : ' node--blocked'))
    .attr('transform', (d: any) => `translate(${d.y},${d.x})`);

  node
    .append('circle')
    .attr('r', (d) => (d.data as TreeNode).value)
    .on('click', (_e, d) => openThread((d.data as TreeNode).id));

  // Label all nodes: root (blocking thread) left of its circle, leaf nodes (blocked) right of theirs.
  // Colors come from Element Plus CSS variables (see <style>), so both themes are supported.
  node
    .append('text')
    .attr('class', 'node-label')
    .attr('dy', '.35em')
    .attr('x', (d: any) => (d.children ? -20 : 15))
    .attr('y', 0)
    .style('text-anchor', (d: any) => (d.children ? 'end' : 'start'))
    .text((d) => (d.data as TreeNode).name)
    .on('click', (_e, d) => openThread((d.data as TreeNode).id));
}

function buildTree(bt: VBlockingThread): TreeNode {
  return {
    name: bt.blockingThread.name,
    value: 15,
    id: bt.blockingThread.id,
    children: bt.blockedThreads.map((c) => ({
      name: c.name,
      value: 10,
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
    <el-alert
      v-if="blockingThreads.length === 0 && !loading"
      type="success"
      :title="tdt('blockedThreads.none')"
      :closable="false"
      show-icon
    />

    <div v-for="(bt, idx) in blockingThreads" :key="idx" class="blocked-group">
      <div class="blocked-group__header">
        <el-icon class="blocked-group__icon"><WarningFilled /></el-icon>
        <span class="blocked-group__title">{{ blockedTitle(bt) }}</span>
        <el-tag v-if="bt.heldLock" type="info" size="small" class="blocked-group__lock">
          <el-icon style="vertical-align: -2px; margin-right: 4px"><Lock /></el-icon>
          {{ bt.heldLock.class }}
        </el-tag>
      </div>
      <svg
        :ref="(el) => { if (el) svgRefs[idx] = el as SVGSVGElement }"
        :key="'svg-' + idx"
        style="overflow: visible; display: block; margin: 0 auto"
      />
    </div>

    <el-dialog v-model="threadDialogVisible" width="80%" destroy-on-close>
      <Thread :ids="selectedIds" />
    </el-dialog>
  </div>
</template>

<style scoped>
.blocked-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background-color: var(--el-bg-color);
  padding: 12px 16px 16px;
  margin-bottom: 16px;
  overflow-x: auto;
}
.blocked-group__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.blocked-group__icon {
  color: var(--el-color-danger);
}
.blocked-group__title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.blocked-group__lock {
  max-width: 100%;
}
:deep(.link) {
  fill: none;
  stroke: var(--el-border-color-darker);
  stroke-width: 1px;
}
:deep(.link:hover) {
  stroke: var(--el-text-color-secondary);
  stroke-width: 2px;
}
:deep(.node circle) {
  stroke-width: 1px;
  cursor: pointer;
}
:deep(.node--blocker > circle) {
  fill: var(--el-color-danger);
  stroke: var(--el-color-danger-dark-2);
}
:deep(.node--blocked > circle) {
  fill: var(--el-color-primary);
  stroke: var(--el-color-primary-dark-2);
}
:deep(.node:hover circle) {
  stroke-width: 2px;
}
:deep(.node-label) {
  fill: var(--el-text-color-primary);
  font-size: 13px;
  font-family: var(--el-font-family);
  cursor: pointer;
}
:deep(.node--blocker > .node-label) {
  fill: var(--el-color-danger);
  font-weight: 600;
}
</style>
