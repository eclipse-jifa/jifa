/********************************************************************************
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
import heapDump from './heapdump/zh'
import gclog from './gclog/zh'
import threadDump from './threaddump/zh'
import jfr from '@/i18n/jfr/zh'

export default {
  jifa: {
    common: {
      confirm: '确定',
      cancel: '取消',
      submit: '提交',
      back: '返回',
      operations: '操作',
      copy: '复制',
      search: '搜索',
      result: '结果',
      description: '描述',
      detail: '细节',
      noData: '没有数据',
      file: '文件',
      messageBeforeLeave: '确定离开吗？',
      clickToLoadMore: '点击加载更多数据',
      expandedColumnWidth: '增加列宽',
      comma: '，'
    },

    header: {
      login: '登录',
      logout: '登出',

      download: '下载原文件'
    },

    menu: {
      files: "文件",
      analysisView: "分析视图",
    },

    serviceUnavailable: {
      title: '服务不可用',
      subtitle: '请联系系统管理员'
    },

    file: {
      name: '文件名',
      type: '类型',
      size: '大小',
      uploadedTime: '上传时间',
      operations: '操作',

      all: '所有类型',
      heapDump: '堆内存快照',
      GCLog: 'GC 日志',
      threadDump: '线程快照',
      jfr: 'JFR 文件',

      new: '新文件',

      analyze: '分析'
    },

    form: {
      requiredMessage: '请输入{0}',
      selectionRequiredMessage: "请选择 '{0}'",
      invalidLengthMessage: '{0}的有效长度为 [{1}, {2}]'
    },

    loginForm: {
      loginTitle: '请登录您的账号',
      loginSubtitle: '没有可用的账号？',
      signup: '注册',
      signupTitle: '注册一个新账号',
      signupSubtitle: '使用已有的账号？',
      login: '登录',
      usernameLabel: '用户名',
      usernamePlaceholder: "输入您的用户名，如: grace{'@'}gmail.com",
      passwordLabel: '密码',
      passwordPlaceholder: '输入您的密码',
      fullNameLabel: '姓名',
      fullNamePlaceholder: '输入您的姓名',
      emailLabel: '邮箱',
      usedAsUsername: '（将作为您的用户名）',
      emailPlaceholder: "输入您的邮箱, 如 grace{'@'}gmail.com",
      or: '或者',
      continueWith: '使用 {0} 账号登录',
      invalidEmailMessage: '请输入有效的邮箱地址'
    },

    fileTransferForm: {
      transferMethod: '传输方式',
      type: '类型',

      upload: '上传',
      filename: '文件名',
      text: '文本',

      dragOrClickToUpload: '拖拽文件至此处或点击进行上传',
      fileSizeLimit: '文件大小限制：{size}',
      fileSizeExceeded: '文件过大！文件大小 {fileSize} 超过限制 {maxSize}',
      uploadFailed: '文件上传失败',
      fileCountLimit: '只能上传一个文件',

      host: '主机',
      user: '用户',
      authentication: '认证方式',
      password: '密码',
      publicKey: '公钥',
      path: '路径',

      publicKeyCopyPrompt: '请复制公钥到 ~/.ssh/authorized_keys'
    },

    analysis: {
      log: '日志',
      success: '分析成功',
      comparison: '分析对比',
      selectComparisonTargets: '选择对比目标',
      setting: '分析设置',

      waitingForResourceScheduling: '分析资源调度中',
    },



    heapDump,
    gclog,
    threadDump,
    jfr
  }
};
