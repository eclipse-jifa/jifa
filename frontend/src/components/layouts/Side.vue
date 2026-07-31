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
import {t} from '@/i18n/i18n';
import {useRoute, useRouter} from 'vue-router';
import {DataAnalysis, Files, ScaleToOriginal} from '@element-plus/icons-vue';
import {useAnalysisStore} from '@/stores/analysis';
import type {FileType} from '@/composables/file-types';

const router = useRouter();
const route = useRoute();

const defaultActive = computed(() => (route.meta && route.meta.fileType ? 'Analysis' : route.name));

const analysis = useAnalysisStore();

function onSelect(index) {
  if (index === 'Analysis') {
    router.push({
      path: `/${(<FileType>analysis.fileType).routePath}/${analysis.target}`
    });
  } else {
    analysis.leaveGuard = false;
    router.push({ name: index });
  }
}
</script>
<template>
  <div>
    <el-menu class="ej-side-menu" :default-active="defaultActive" @select="onSelect" collapse>
      <el-tooltip placement="right" :show-arrow="false" :offset="6" :content="t('menu.files')">
        <el-menu-item index="Files">
          <el-icon>
            <Files/>
          </el-icon>
        </el-menu-item>
      </el-tooltip>
      <el-tooltip
          placement="right"
          :show-arrow="false"
          :offset="6"
          :content="t('menu.analysisView')"
          :disabled="!analysis.target"
      >
        <el-menu-item index="Analysis" :disabled="!analysis.target">
          <el-icon>
            <DataAnalysis/>
          </el-icon>
        </el-menu-item>
      </el-tooltip>
      <el-tooltip placement="right" :show-arrow="false" :offset="6" :content="t('menu.threadDumpCompare')">
        <el-menu-item index="ThreadDumpCompare">
          <el-icon>
            <ScaleToOriginal/>
          </el-icon>
        </el-menu-item>
      </el-tooltip>
    </el-menu>
  </div>
</template>
<style scoped>
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item:first-child) {
  border-top-left-radius: var(--el-border-radius-base);
  border-top-right-radius: var(--el-border-radius-base);
}

.ej-side-menu {
  height: 100%;
  background-color: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
}
</style>
