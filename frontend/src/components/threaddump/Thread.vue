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
import { useAnalysisApiRequester } from '@/composables/analysis-api-requester';
import { tdt } from '@/i18n/i18n';
import { Search } from '@element-plus/icons-vue';

const props = defineProps({
  groupName: {
    type: String,
    required: false
  },
  type: {
    type: String,
    required: false
  },
  threadState: {
    type: String,
    required: false
  },
  ids: {
    type: Array as () => number[],
    required: false
  }
});

const { request } = useAnalysisApiRequester();

const loading = ref(false);

const name = ref();
const nameInput = ref();

const page = ref(1);
const pageSize = 10;
const totalSize = ref(0);
const moreThanOnePage = computed(() => totalSize.value > pageSize);
const tableData = ref();

function toggleContent(row) {
  if (row.contentLoaded) {
    row.contentVisible = !row.contentVisible;
    return;
  }

  loading.value = true;
  let id = row.id;
  request('rawContentOfThread', {
    id
  }).then((content) => {
    row.content = content.join('\n');
    row.contentLoaded = true;
    row.contentVisible = true;
    loading.value = false;
  });
}

function loadThreads() {
  loading.value = true;
  let groupName = props.groupName;
  let type = props.type;
  let ids = props.ids;
  let paging = {
    page: page.value,
    pageSize
  };
  request(
    groupName ? 'threadsOfGroup' : 'threads',
    groupName
      ? {
          groupName,
          ...paging
        }
      : {
          type,
          name: name.value,
          threadState: props.threadState,
          ids: ids && ids.length > 0 ? ids : undefined,
          ...paging
        }
  ).then((pageView) => {
    totalSize.value = pageView.totalSize;
    tableData.value = pageView.data.map((thread) => ({
      ...thread,
      content: '',
      contentLoaded: false,
      contentVisible: false
    }));
    loading.value = false;
  });
}

function updateNameAndLoad() {
  if (name.value === nameInput.value) {
    return;
  }
  name.value = nameInput.value;
  page.value = 1;
  loadThreads();
}

function reset() {
  nameInput.value = null;
  name.value = null;
  page.value = 1;
}

watch([props], () => {
  reset();
  loadThreads();
});

onMounted(() => {
  loadThreads();
});
</script>
<template>
  <div style="margin-bottom: 15px" v-if="!groupName">
    <el-input style="width: 360px" clearable v-model="nameInput" @change="updateNameAndLoad">
      <template #prepend>
        {{ tdt('threadNameLabel') }}
      </template>
      <template #append>
        <el-button :icon="Search as any" size="small" @click="updateNameAndLoad" />
      </template>
    </el-input>
  </div>
  <el-table
    stripe
    :show-header="false"
    :max-height="`${40 * pageSize}px`"
    v-bind="moreThanOnePage ? { height: `${40 * pageSize}px` } : {}"
    :data="tableData"
    v-loading="loading"
  >
    <el-table-column>
      <template #default="{ row }">
        <span class="clickable" @click="toggleContent(row)">{{ row.name }}</span>
        <div class="content" v-if="row.contentVisible && row.contentLoaded">
          <el-scrollbar>
            {{ row.content }}
          </el-scrollbar>
        </div>
      </template>
    </el-table-column>
  </el-table>
  <div class="pagination" v-if="moreThanOnePage">
    <el-pagination
      layout="total, prev, pager, next"
      background
      :total="totalSize"
      :page-size="pageSize"
      v-model:current-page="page"
      @current-change="loadThreads"
      :disabled="loading"
    />
  </div>
</template>
<style scoped>
.pagination {
  margin-top: 15px;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  overflow: hidden;
}

.content {
  margin-top: 5px;
  border-radius: 8px;
  padding: 7px;
  background-color: rgba(var(--el-color-primary-rgb), 0.1);
  white-space: pre;
  font-size: 14px;
  line-height: 1.5;
  overflow-y: auto;
}
</style>
