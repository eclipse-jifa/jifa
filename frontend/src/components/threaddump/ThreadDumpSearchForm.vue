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
import { ArrowDown, Search } from '@element-plus/icons-vue';
import type { FormRules } from 'element-plus';

export interface SearchModel {
  term: string | null;
  advancedVisible: boolean;
  searchName: boolean;
  searchState: boolean;
  searchStack: boolean;
  regex: boolean;
  matchCase: boolean;
  allowedJavaStates: string[];
}

const emit = defineEmits<{
  (e: 'submit', search: SearchModel): void;
}>();

const THREAD_STATE_OPTIONS = [
  'RUNNABLE',
  'SLEEPING',
  'IN_OBJECT_WAIT',
  'IN_OBJECT_WAIT_TIMED',
  'PARKED',
  'PARKED_TIMED',
  'BLOCKED_ON_MONITOR_ENTER',
  'TERMINATED'
];

const search = reactive<SearchModel>({
  term: null,
  advancedVisible: false,
  searchName: true,
  searchState: true,
  searchStack: true,
  regex: false,
  matchCase: false,
  allowedJavaStates: []
});

const formRef = ref();

const rules: FormRules<SearchModel> = {
  term: [
    { required: true, message: tdt('threadDumpSearch.requiredMessage'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (search.regex && value) {
          try {
            new RegExp(value);
          } catch (e: any) {
            callback(new Error(String(e)));
            return;
          }
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

function submitSearchForm() {
  formRef.value?.validate((valid: boolean) => {
    if (valid) {
      emit('submit', { ...search });
    }
  });
}
</script>

<template>
  <el-form
    ref="formRef"
    :model="search"
    :rules="rules"
    label-width="auto"
    label-position="left"
    size="small"
    @submit.prevent="submitSearchForm"
  >
    <div class="search-bar">
      <el-form-item prop="term" class="search-input-item">
        <el-input
          v-model="search.term"
          clearable
          style="width: 420px"
          size="default"
          :placeholder="tdt('threadDumpSearch.searchPlaceholder')"
        >
          <template #append>
              <el-button :icon="Search" @click="submitSearchForm" />
          </template>
        </el-input>
      </el-form-item>
      <el-button
        size="default"
        @click="search.advancedVisible = !search.advancedVisible"
      >
        {{ tdt('threadDumpSearch.advancedToggle') }}
        <el-icon class="toggle-icon" :class="{ 'toggle-icon--expanded': search.advancedVisible }">
          <ArrowDown />
        </el-icon>
      </el-button>
    </div>

    <el-collapse-transition>
      <div v-if="search.advancedVisible" class="advanced-panel">
        <el-row :gutter="20">
          <el-col :span="10">
            <div class="section-title">
              {{ tdt('threadDumpSearch.matchFields') }}
            </div>
            <el-form-item :label="tdt('threadDumpSearch.matchFieldName')">
              <el-switch v-model="search.searchName" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.matchFieldState')">
              <el-switch v-model="search.searchState" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.matchFieldStack')">
              <el-switch v-model="search.searchStack" />
            </el-form-item>
          </el-col>

          <el-col :span="14">
            <div class="section-title">
              {{ tdt('threadDumpSearch.otherOptions') }}
            </div>
            <el-form-item :label="tdt('threadDumpSearch.searchOptionRegex')">
              <el-switch v-model="search.regex" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.searchOptionMatchCase')">
              <el-switch v-model="search.matchCase" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.searchOptionThreadStates')">
              <el-select
                v-model="search.allowedJavaStates"
                multiple
                collapse-tags
                collapse-tags-tooltip
                :placeholder="tdt('threadDumpSearch.searchOptionThreadStatesPlaceholder')"
                style="width: 220px"
              >
                <el-option
                  v-for="item in THREAD_STATE_OPTIONS"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-collapse-transition>
  </el-form>
</template>

<style scoped>
.toggle-icon {
  margin-left: 4px;
  transition: transform 0.3s;
}

.toggle-icon--expanded {
  transform: rotate(180deg);
}

.search-bar {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.search-input-item {
  margin-bottom: 0;
}

.advanced-panel {
  max-width: 760px;
  margin: 14px 0 0;
  padding: 16px 20px 2px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.section-title {
  font-weight: 600;
  color: var(--el-text-color-regular);
  padding-bottom: 10px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
