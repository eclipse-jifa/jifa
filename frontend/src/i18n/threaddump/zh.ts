/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
export default {
  title: "线程分析",
  addFile: "添加 Thread Dump",
  overview: '概览',
  lockView: '锁视图',
  basicInfo: '基础信息',
  threadSummary: "线程概要",
  time: '时间',
  vmInfo: '虚拟机',
  jniRefs: 'JNI References',
  jniWeakRefs: 'JNI Weak References',
  errorPrompt: '解析过程中产生的错误数：',
  deadLockCount: '死锁数量',
  threadGroupSummary: "线程池概要",
  javaThread: "Java Thread",
  jitThread: "JIT Thread",
  gcThread: "GC Thread",
  otherThread: "Other Thread",
  total: "Total",
  monitors: "Java 同步器",
  callSiteTree: "调用栈",
  fileContent: "文件内容",
  loadFileContent: "加载",
  loadMoreFileContent: "加载更多",
  threadNameLabel: "线程名",

  // ----- 阻塞线程 -----
  blockedThreadsLabel: "阻塞线程",
  blockedThreads: {
    title: "{blocker} 阻塞了 1 个线程 | {blocker} 阻塞了 {count} 个线程",
  },

  // ----- CPU 耗时线程 -----
  cpuConsumingThreadsLabel: "CPU 耗时线程",
  cpuConsumingThreads: {
    title: "展示最高 CPU 耗时线程",
    cpuConsumptionLabel: "CPU 耗时",
    hours: "小时",
    minutes: "分钟",
    seconds: "秒",
    milliseconds: "毫秒",
  },

  // ----- 诊断 -----
  diagnosis: {
    title: "诊断",
    examine: "检查",
    messageColumn: "消息",
    fileColumn: "文件",
    suggestionColumn: "建议",
    type: {
      NO_ISSUES: "没有发现问题",
      NO_ISSUES_SUGGESTION: "",

      DEADLOCK: "{count} 个线程发生死锁",
      DEADLOCK_SUGGESTION:
        "死锁发生在两个或更多线程无限期地等待对方时，通常由资源加锁顺序错误引起。",

      HIGH_BLOCKED_THREAD_COUNT: "1 个线程被阻塞 | {count} 个线程被阻塞",
      HIGH_BLOCKED_THREAD_COUNT_SUGGESTION:
        "大量线程阻塞通常意味着系统存在瓶颈，请检查调用栈和同步加锁代码。",

      HIGH_THREAD_COUNT: "线程数 {count} 过高",
      HIGH_THREAD_COUNT_SUGGESTION:
        "如此高的线程数可能导致内存耗尽和线程饥饿，请查找线程泄漏或考虑使用线程池减少线程创建。",

      HIGH_STACK_SIZE: "{name} 的调用栈非常深（> {threshold} 帧） | {count} 个线程调用栈非常深（> {threshold} 帧）",
      HIGH_STACK_SIZE_SUGGESTION:
        "调用栈过深可能导致 StackOverflowError 并降低性能，请检查是否存在过深的递归。",

      HIGH_CPU_RATIO: "{name} 的 CPU 占用率较高 | {count} 个线程的 CPU 占用率较高",
      HIGH_CPU_RATIO_SUGGESTION:
        "CPU 占用率高意味着该线程在其生命周期中消耗了大量 CPU，不一定是问题，但值得关注。",

      THREAD_THROWING_EXCEPTION: "{name} 正在抛出异常 | {count} 个线程正在抛出异常",
      THREAD_THROWING_EXCEPTION_SUGGESTION:
        "正在抛出异常的线程可能表明应用程序存在问题。请注意，创建堆栈跟踪开销很大，频繁抛出异常会影响性能。",
    },
  },

  // ----- Thread Search -----
  threadDumpSearch: {
    label: "搜索线程",
    searchTitle: "搜索线程",
    searchInput: "搜索词",
    requiredMessage: "请输入搜索词",
    advancedToggle: "高级",
    searchFields: "搜索字段",
    searchFieldName: "线程名称",
    searchFieldState: "线程状态",
    searchFieldStack: "调用栈",
    searchOptions: "选项",
    searchOptionRegex: "正则表达式",
    searchOptionMatchCase: "区分大小写",
    searchOptionThreadStates: "状态过滤",
    searchOptionThreadStatesPlaceholder: "所有状态",
    threadStatesChartTitle: "线程状态",
    resultsCount: "个线程匹配",
    noResults: "未找到匹配的线程。",
    threadNameLabel: "线程",
  },

  // ----- Thread Dump Overview / Compare -----
  threadDumpOverview: {
    label: "转储概览",
    diagnosisTitle: "诊断",
    stateDistributionTitle: "Java 线程状态分布",
    cpuConsumingTitle: "CPU 占用最高的线程",
    cpuConsumingDatasetLabel: "CPU（{unit}）",
    threadGroupTitle: "线程组汇总",
  },
}