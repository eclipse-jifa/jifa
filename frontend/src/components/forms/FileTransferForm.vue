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
import { useClipboard } from '@vueuse/core';
import {
  Connection,
  CopyDocument,
  Document,
  Link,
  Upload,
  UploadFilled
} from '@element-plus/icons-vue';
import { fileTypeMap } from '@/composables/file-types';
import axios from 'axios';
import { useEnv } from '@/stores/env';
import { t } from '@/i18n/i18n';

defineProps({
  visible: Boolean
});

const emit = defineEmits(['update:visible', 'transferCompletionCallback']);

const env = useEnv();

function _t(key: string, args?: any) {
  return t(`fileTransferForm.${key}`, args);
}

const paramTemplate = {
  method: 'UPLOAD',
  type: null,

  ossEndpoint: '',
  ossAccessKeyId: '',
  ossSecretAccessKey: '',
  ossBucketName: '',
  ossObjectKey: '',

  s3Region: '',
  s3AccessKey: '',
  s3SecretKey: '',
  s3BucketName: '',
  s3ObjectKey: '',

  scpHostname: '',
  scpUser: '',
  scpPassword: '',
  scpSourcePath: '',

  url: '',

  filename: '',
  text: ''
};

function buildSelectionRequiredRule(name) {
  return [
    {
      required: true,
      message: () => t('form.selectionRequiredMessage', [name]),
      trigger: 'blur'
    }
  ];
}

function buildRequiredRule(name) {
  return [
    {
      required: true,
      message: () => t('form.requiredMessage', [name]),
      trigger: 'blur'
    }
  ];
}

const rules = {
  method: buildSelectionRequiredRule(_t('transferMethod')),
  type: buildSelectionRequiredRule(_t('type')),
  ossEndpoint: buildRequiredRule('Endpoint'),
  ossAccessKeyId: buildRequiredRule('Access Key'),
  ossSecretAccessKey: buildRequiredRule('Secret Key'),
  ossBucketName: buildRequiredRule('Bucket Name'),
  ossObjectKey: buildRequiredRule('Object Key'),
  s3Region: buildRequiredRule('Region'),
  s3AccessKey: buildRequiredRule('Access Key'),
  s3SecretKey: buildRequiredRule('Secret Key'),
  s3BucketName: buildRequiredRule('Bucket Name'),
  s3ObjectKey: buildRequiredRule('Object Key'),
  scpHostname: buildRequiredRule(_t('host')),
  scpUser: buildRequiredRule(_t('user')),
  scpPassword: buildRequiredRule(_t('password')),
  scpSourcePath: buildRequiredRule(_t('path')),
  url: buildRequiredRule('URL'),
  filename: buildRequiredRule(_t('filename')),
  text: buildRequiredRule(_t('text'))
};

const form = ref();

function validate() {
  return new Promise((resolve, reject) => {
    form.value.validate((valid) => {
      if (valid) {
        resolve();
      } else {
        reject();
      }
    });
  });
}

const params = reactive({
  ...paramTemplate
});

const textSample = ref('');

watchEffect(() => {
  if (params.method === 'TEXT') {
    let type = params.type as string;
    switch (type) {
      case 'HEAP_DUMP': {
        params.method = 'UPLOAD';
        break;
      }
      case 'JFR_FILE': {
        params.method = 'UPLOAD';
        break;
      }
      case 'GC_LOG': {
        params.filename = 'gc.log';
        textSample.value = `[3.779s][info][gc,start      ] GC(10) Pause Young (Normal) (G1 Evacuation Pause)
[3.779s][info][gc,task       ] GC(10) Using 8 workers of 8 for evacuation
[3.789s][info][gc,phases     ] GC(10)   Pre Evacuate Collection Set: 0.0ms
[3.789s][info][gc,phases     ] GC(10)   Evacuate Collection Set: 8.5ms
[3.789s][info][gc,phases     ] GC(10)   Post Evacuate Collection Set: 1.6ms
[3.789s][info][gc,phases     ] GC(10)   Other: 0.2ms
[3.789s][info][gc,heap       ] GC(10) Eden regions: 90->0(113)
[3.789s][info][gc,heap       ] GC(10) Survivor regions: 2->12(12)
[3.789s][info][gc,heap       ] GC(10) Old regions: 52->56
[3.789s][info][gc,heap       ] GC(10) Humongous regions: 2->2
[3.789s][info][gc,metaspace  ] GC(10) Metaspace: 64964K->64964K(1110016K)
...`;
        break;
      }
      case 'THREAD_DUMP': {
        params.filename = 'stack.log';
        textSample.value = `2023-09-20 11:10:33
Full thread dump OpenJDK 64-Bit Server VM (17.0.2+8 mixed mode, emulated-client):

"Reference Handler" #2 daemon prio=10 os_prio=31 cpu=3.63ms elapsed=5702.04s tid=0x000000011f008200 nid=0x4a03 waiting on condition  [0x000000016c11a000]
   java.lang.Thread.State: RUNNABLE
        at java.lang.ref.Reference.waitForReferencePendingList(java.base@17.0.2/Native Method)
        at java.lang.ref.Reference.processPendingReferences(java.base@17.0.2/Reference.java:253)
        at java.lang.ref.Reference$ReferenceHandler.run(java.base@17.0.2/Reference.java:215)
...`;
        break;
      }
    }
  }
});

const byUpload = computed(() => params.method === 'UPLOAD');
const byOSS = computed(() => params.method === 'OSS');
const byS3 = computed(() => params.method === 'S3');
const bySCP = computed(() => params.method === 'SCP');
const byURL = computed(() => params.method === 'URL');
const byText = computed(() => params.method === 'TEXT');

const scpAuthentication = ref('Password');
const usePassword = computed(() => scpAuthentication.value === 'Password');
const { copy, isSupported } = useClipboard({ source: env.publicKey });

const processing = ref(false);
const transferId = ref(null);
const percentage = ref(0);
const progressStatus = ref({});
const message = ref('');
const fileList = ref([]);

// Get file size limit from server configuration
const maxFileSize = computed(() => env.maxFileSize);
const maxFileSizeFormatted = computed(() => formatFileSize(maxFileSize.value));

function resetStates() {
  processing.value = false;
  transferId.value = null;
  percentage.value = 0;
  progressStatus.value = {};
  message.value = '';
  fileList.value = [];
}

watch(
  () => params.method,
  () => {
    resetStates();
  }
);

function error(msg: string) {
  progressStatus.value = {
    status: 'exception'
  };
  message.value = msg;
  processing.value = false;
}

function onUploadSuccess(fileId) {
  fileList.value = [];
  emit('transferCompletionCallback', fileId);
}

// File upload validation
function validateFileUpload(file) {
  // Check file size
  if (file.size > maxFileSize.value) {
    const fileSizeStr = formatFileSize(file.size);
    error(_t('fileSizeExceeded', { 
      fileSize: fileSizeStr, 
      maxSize: maxFileSizeFormatted.value 
    }));
    return false;
  }
  
  // Clear error message before upload
  message.value = '';
  return true;
}

// File upload error handling
function onUploadError(error, file, fileList) {
  console.error('Upload error:', error);
  
  // Try to extract error message from response
  let errorMessage = _t('uploadFailed');
  if (error && error.response) {
    let responseData = null;
    
    // Check if error.response is already an object or needs parsing
    if (typeof error.response === 'string') {
      try {
        responseData = JSON.parse(error.response);
      } catch (e) {
        console.warn('Failed to parse error response as JSON:', e);
      }
    } else if (typeof error.response === 'object') {
      // axios typically provides response as object with data property
      responseData = error.response.data || error.response;
    }
    
    // Extract message from response data
    if (responseData && responseData.message) {
      errorMessage = responseData.message;
    }
  }
  
  error(errorMessage);
}

// File count exceed handling
function onFileExceed(files, fileList) {
  error(_t('fileCountLimit'));
}

// Format file size
function formatFileSize(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function doTransfer() {
  resetStates();
  form.value.validate((valid) => {
    if (valid) {
      processing.value = true;
      axios
        .post('/jifa-api/files/transfer', params)
        .then((resp) => {
          transferId.value = resp.data;
          queryProgress();
        })
        .catch((e) => {
          error(e.response.data.message);
        });
    }
  });
}

function queryProgress() {
  axios
    .get(`/jifa-api/files/transfer/${transferId.value}`)
    .then((resp) => {
      let progress = resp.data;
      let state = progress.state;
      if (state === 'IN_PROGRESS') {
        if (progress.totalSize > 0) {
          percentage.value = Math.floor((progress.transferredSize / progress.totalSize) * 100);
        }
        setTimeout(queryProgress, 1000);
      } else if (state === 'SUCCESS') {
        percentage.value = 100;
        progressStatus.value = {
          status: 'success'
        };
        nextTick(() => emit('transferCompletionCallback', progress.fileId));
      } else {
        error(progress.failureMessage);
      }
    })
    .catch((e) => {
      error(e.response.data.message);
    });
}

function close(done: any) {
  if (processing.value) {
    return;
  }
  done();
}

function isEnabled(method: string) {
  for (let m of env.disabledFileTransferMethods) {
    if (m === method) {
      return false;
    }
  }
  return true;
}

const dialogWidth = computed(() => {
  return env.disabledFileTransferMethods.length > 0 ? '640px' : '710px';
});
</script>
<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="(newValue: boolean) => $emit('update:visible', newValue)"
    :before-close="close"
    :title="t('file.new')"
    :width="dialogWidth"
  >
    <el-form
      label-position="right"
      label-width="110px"
      size="large"
      :disabled="processing"
      hide-required-asterisk
      :model="params"
      :rules="rules"
      ref="form"
    >
      <el-form-item :label="_t('transferMethod')" prop="method">
        <el-radio-group v-model="params.method">
          <el-radio-button label="UPLOAD" v-if="isEnabled('UPLOAD')">
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <Upload />
              </el-icon>
              {{ _t('upload') }}
            </div>
          </el-radio-button>
          <el-radio-button label="OSS" v-if="isEnabled('OSS')">
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <UploadFilled />
              </el-icon>
              OSS
            </div>
          </el-radio-button>
          <el-radio-button label="S3" v-if="isEnabled('S3')">
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <UploadFilled />
              </el-icon>
              S3
            </div>
          </el-radio-button>
          <el-radio-button label="SCP" v-if="isEnabled('SCP')">
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <Connection />
              </el-icon>
              SCP
            </div>
          </el-radio-button>
          <el-radio-button label="URL" v-if="isEnabled('URL')">
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <Link />
              </el-icon>
              URL
            </div>
          </el-radio-button>

          <el-radio-button
            label="TEXT"
            :disabled="
              (params.type as String) === 'HEAP_DUMP' || (params.type as String) === 'JFR_FILE'
            "
            v-if="isEnabled('TEXT')"
          >
            <div class="ej-file-transfer-method-button">
              <el-icon style="margin-right: 8px" size="14">
                <Document />
              </el-icon>
              {{ _t('text') }}
            </div>
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="_t('type')" prop="type">
        <el-radio-group v-model="params.type">
          <el-radio-button v-for="[key, type] in fileTypeMap" :label="key"
            >{{ t(`file.${type.labelKey}`) }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <!-- Upload items -->
      <el-form-item :label="t('common.file')" v-if="byUpload">
        <el-upload
          style="width: 420px"
          drag
          :before-upload="validateFileUpload"
          method="post"
          action="/jifa-api/files/upload"
          :headers="env.uploadHeader"
          :data="{ type: params.type }"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          :file-list="fileList"
          :limit="1"
          :on-exceed="onFileExceed"
        >
          <el-icon class="el-icon--upload">
            <UploadFilled />
          </el-icon>
          <div class="el-upload__text">{{ _t('dragOrClickToUpload') }}</div>
          <template #tip>
            <div class="el-upload__tip">
              {{ _t('fileSizeLimit', { size: maxFileSizeFormatted }) }}
            </div>
          </template>
        </el-upload>
        
        <!-- Upload error message - follows the upload component -->
        <div
          style="
            margin-top: 8px;
            width: 420px;
            padding: 8px 16px;
            border-radius: var(--el-border-radius-small);
            font-size: 12px;
            word-break: break-all;
            background-color: var(--el-color-error-light-9);
            color: var(--el-color-error);
          "
          v-if="message && byUpload"
        >
          {{ message }}
        </div>
      </el-form-item>
      <!-- Upload items end-->

      <!-- OSS items -->
      <el-form-item label="Endpoint" prop="ossEndpoint" v-if="byOSS">
        <el-input
          v-model="params.ossEndpoint"
          placeholder="Endpoint"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Access Key" prop="ossAccessKeyId" v-if="byOSS">
        <el-input
          v-model="params.ossAccessKeyId"
          placeholder="Access Key"
          clearable
          type="password"
          show-password
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Secret Key" prop="ossSecretAccessKey" v-if="byOSS">
        <el-input
          v-model="params.ossSecretAccessKey"
          placeholder="Secret Access Key"
          clearable
          type="password"
          show-password
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Bucket Name" prop="ossBucketName" v-if="byOSS">
        <el-input
          v-model="params.ossBucketName"
          placeholder="Bucket Name"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Object Key" prop="ossObjectKey" v-if="byOSS">
        <el-input
          v-model="params.ossObjectKey"
          placeholder="Object Key"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>
      <!-- OSS items end -->

      <!-- S3 items -->
      <el-form-item label="Region" prop="s3Region" v-if="byS3">
        <el-input
          v-model="params.s3Region"
          placeholder="Region"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Access Key" prop="s3AccessKey" v-if="byS3">
        <el-input
          v-model="params.s3AccessKey"
          placeholder="Access Key"
          clearable
          type="password"
          show-password
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Secret Key" prop="s3SecretKey" v-if="byS3">
        <el-input
          v-model="params.s3SecretKey"
          placeholder="Secret Access Key"
          clearable
          type="password"
          show-password
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Bucket Name" prop="s3BucketName" v-if="byS3">
        <el-input
          v-model="params.s3BucketName"
          placeholder="Bucket Name"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item label="Object Key" prop="s3ObjectKey" v-if="byS3">
        <el-input
          v-model="params.s3ObjectKey"
          placeholder="Object Key"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>
      <!-- S3 items end -->

      <!-- SCP items -->
      <el-form-item :label="_t('host')" prop="scpHostname" v-if="bySCP">
        <el-input
          v-model="params.scpHostname"
          :placeholder="_t('host')"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item :label="_t('user')" prop="scpUser" v-if="bySCP">
        <el-input
          v-model="params.scpUser"
          :placeholder="_t('user')"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item :label="_t('authentication')" v-if="bySCP">
        <el-radio-group v-model="scpAuthentication">
          <el-radio-button label="Password">{{ _t('password') }}</el-radio-button>
          <el-radio-button label="Public Key">{{ _t('publicKey') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="_t('password')" prop="scpPassword" v-if="bySCP && usePassword">
        <el-input
          v-model="params.scpPassword"
          :placeholder="_t('password')"
          clearable
          type="password"
          show-password
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>

      <el-form-item :label="_t('publicKey')" v-if="bySCP && !usePassword && isSupported">
        <el-button
          :icon="CopyDocument as any"
          @click="copy(env.publicKey?.ssh2)"
          :disabled="!isSupported"
          >{{ t('common.copy') }}
        </el-button>

        <el-alert
          style="margin-left: 7px; width: fit-content; height: 40px"
          :title="_t('publicKeyCopyPrompt')"
          type="info"
          :closable="false"
        />
      </el-form-item>

      <el-form-item :label="_t('path')" prop="scpSourcePath" v-if="bySCP">
        <el-input
          v-model="params.scpSourcePath"
          :placeholder="_t('path')"
          clearable
          class="ej-file-transfer-form-input"
        >
        </el-input>
      </el-form-item>
      <!-- SCP items end -->

      <!-- URL items -->
      <el-form-item label="URL" prop="url" v-if="byURL">
        <el-input
          v-model="params.url"
          placeholder="URL"
          clearable
          class="ej-file-transfer-form-input"
          @keydown.enter="(e) => e.preventDefault()"
        >
        </el-input>
      </el-form-item>
      <!-- URL items end -->

      <!-- TEXT items -->
      <el-form-item :label="_t('filename')" prop="filename" v-if="byText">
        <el-input
          v-model="params.filename"
          :placeholder="_t('filename')"
          clearable
          class="ej-file-transfer-form-input"
          @keydown.enter="(e) => e.preventDefault()"
        >
        </el-input>
      </el-form-item>

      <el-form-item :label="_t('text')" prop="text" v-if="byText">
        <el-input
          v-model="params.text"
          type="textarea"
          :placeholder="textSample"
          :rows="12"
          clearable
          resize="none"
          class="ej-file-transfer-form-input"
          :maxlength="16777216"
          show-word-limit
        />
      </el-form-item>
      <!-- TEXT items end -->

      <el-form-item v-if="!byUpload">
        <el-button type="primary" @click="doTransfer">{{ t('common.submit') }}</el-button>
      </el-form-item>
    </el-form>

    <el-progress
      style="margin-left: 110px; width: 420px"
      :stroke-width="15"
      text-inside
      striped
      striped-flow
      :duration="64"
      :percentage="percentage"
      v-bind="progressStatus"
      v-if="!byUpload && (processing || progressStatus.status)"
    />

    <div
      style="
        margin-left: 110px;
        margin-top: 7px;
        width: 420px;
        padding: 8px 16px;
        border-radius: var(--el-border-radius-small);
        font-size: 12px;
        word-break: break-all;
        background-color: var(--el-color-error-light-9);
        color: var(--el-color-error);
      "
      v-if="message && !byUpload"
    >
      {{ message }}
    </div>
  </el-dialog>
</template>

<style scoped>
.ej-file-transfer-method-button {
  display: flex;
  align-items: center;
}

.ej-file-transfer-form-input {
  width: 420px;
}
</style>
