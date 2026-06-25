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

const rules = {
  term: [
    { required: true, message: () => tdt('threadDumpSearch.requiredMessage'), trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (err?: Error) => void) => {
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
  <el-card :header="tdt('threadDumpSearch.searchTitle')">
    <el-form
      ref="formRef"
      :inline="false"
      :model="search"
      :rules="rules"
      label-width="auto"
      size="small"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item :label="tdt('threadDumpSearch.searchInput')" prop="term">
            <el-input
              v-model="search.term"
              clearable
              size="large"
              :placeholder="tdt('threadDumpSearch.searchTitle')"
              @keydown.enter.prevent="submitSearchForm"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8" style="display: flex; align-items: center; gap: 8px; padding-top: 2px">
          <el-button type="primary" @click="submitSearchForm">
            {{ tdt('threadDumpSearch.searchTitle') }}
          </el-button>
          <el-checkbox v-model="search.advancedVisible" border>
            {{ tdt('threadDumpSearch.advancedToggle') }}
          </el-checkbox>
        </el-col>
      </el-row>

      <template v-if="search.advancedVisible">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-divider content-position="left">
              {{ tdt('threadDumpSearch.searchFields') }}
            </el-divider>
            <el-form-item :label="tdt('threadDumpSearch.searchFieldName')">
              <el-switch v-model="search.searchName" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.searchFieldState')">
              <el-switch v-model="search.searchState" />
            </el-form-item>
            <el-form-item :label="tdt('threadDumpSearch.searchFieldStack')">
              <el-switch v-model="search.searchStack" />
            </el-form-item>
          </el-col>

          <el-col :span="10">
            <el-divider content-position="left">
              {{ tdt('threadDumpSearch.searchOptions') }}
            </el-divider>
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
                :placeholder="tdt('threadDumpSearch.searchOptionThreadStatesPlaceholder')"
                style="width: 280px"
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
      </template>
    </el-form>
  </el-card>
</template>
