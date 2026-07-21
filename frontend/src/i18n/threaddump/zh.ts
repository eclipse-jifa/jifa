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
    none: "没有发现被阻塞的线程",
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
    javaThreads: "Java 线程",
    nonJavaThreads: "非 Java 线程",
    noCpuData: "该线程快照文件不包含 CPU 使用信息。",
  },

  // ----- CPU 增量对比 -----
  cpuConsumingThreadsCompareLabel: "CPU 增量对比（两个 Dump）",
  cpuConsumingThreadsCompare: {
    selectPrompt: "选择第二个线程 Dump 进行对比",
    noData: "在两个 Dump 中未找到具有匹配原生线程 ID 和 CPU 数据的线程。",
    cpuDeltaLabel: "CPU 增量",
    cpuFirstLabel: "CPU（第一个 Dump）",
    cpuSecondLabel: "CPU（第二个 Dump）",
    hours: "小时",
    minutes: "分钟",
    seconds: "秒",
    milliseconds: "毫秒",
  },

  // ----- 线程 Dump 对比页面 -----
  threadDumpCompare: {
    title: "线程 Dump 对比",
    selectFile1: "第一个 Dump",
    selectFile2: "第二个 Dump",
    selectPlaceholder: "选择线程 Dump 文件",
    compareButton: "对比",
    basicInfo: "基本信息",
    timeDiff: "时间差",
    vmInfoMatch: "VM 一致",
    vmInfoMismatch: "VM 不同",
    diagnosis: "诊断",
    threadSummary: "线程摘要",
    threadGroupSummary: "线程组摘要",
    cpuDelta: "CPU 耗时增量",
    stateDistribution: "状态分布",
    stateChanges: "线程状态变化",
    persistentBlockers: "持续阻塞线程（两个 Dump 均阻塞）",
    deltaPositive: "增加",
    deltaNegative: "减少",
    noFilesSelected: "请选择两个线程 Dump 文件进行对比",
    noFilesAvailable: "未找到线程 Dump 文件",
    noFilesAvailableHint: "请先上传线程 Dump 文件，然后再进行比较。",
    uploadNow: "立即上传",
    dump1Label: "Dump 1",
    dump2Label: "Dump 2",
    deltaLabel: "Δ",
    threadType: "类型",
    count: "数量",
    groupName: "线程组",
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
    label: "查找线程",
    searchTitle: "查找线程",
    searchPlaceholder: "输入关键字，可匹配线程名、状态和调用栈",
    searchInput: "搜索词",
    requiredMessage: "请输入搜索词",
    advancedToggle: "更多选项",
    matchFields: "匹配字段",
    matchFieldName: "线程名称",
    matchFieldState: "线程状态",
    matchFieldStack: "调用栈",
    otherOptions: "其他",
    searchOptionRegex: "正则表达式",
    searchOptionMatchCase: "区分大小写",
    searchOptionThreadStates: "状态过滤",
    searchOptionThreadStatesPlaceholder: "所有状态",
    threadStatesChartTitle: "线程状态",
    resultsCount: "个线程匹配",
    threadNameLabel: "线程",
    stateLabel: "状态",
    cpuLabel: "CPU 耗时",
    elapsedLabel: "运行时长",
  },

  // ----- 状态分布对比 -----
  stateDistributionCompare: {
    state: "状态",
    delta: "Δ",
    noData: "暂无线程状态数据。",
  },

  // ----- 线程状态变化 -----
  threadStateChanges: {
    changeType: "变化类型",
    dump1Impact: "Dump 1",
    dump2Impact: "Dump 2",
    delta: "Δ",
    transitionsTitle: "状态转换明细",
    count: "数量",
    summaryChanged: "状态已变化",
    summaryDisappeared: "已消失",
    summaryNew: "第二个 Dump 中的新线程",
    tabChanged: "状态变化",
    tabDisappeared: "已消失",
    tabNew: "新线程",
    thread: "线程",
    stateBefore: "状态（Dump 1）",
    stateAfter: "状态（Dump 2）",
    noChanges: "两个 Dump 之间没有线程改变状态。",
    noDisappeared: "两个 Dump 之间没有线程消失。",
    noNew: "第二个 Dump 中没有新线程出现。",
  },

  // ----- 持续阻塞线程 -----
  persistentBlockers: {
    warningTitle: "1 个线程在两个 Dump 中均被阻塞 | {count} 个线程在两个 Dump 中均被阻塞",
    summaryType: "类型",
    dump1: "Dump 1",
    dump2: "Dump 2",
    delta: "Δ",
    allBlocked: "阻塞线程（全部）",
    persistentOnly: "两个 Dump 均阻塞",
    resolved: "已解除阻塞",
    introduced: "Dump 2 中新增阻塞",
    detailsTitle: "两个 Dump 中均被阻塞的线程",
    thread: "线程",
    actions: "操作",
    inspect: "查看",
    noPersistentBlockers: "两个 Dump 中没有线程持续被阻塞——未检测到持续竞争。",
  },

  // ----- 诊断对比 -----
  diagnoseCompare: {
    issue: "问题",
    noIssue: "✓ 无问题",
    new: "新增",
    resolved: "已解决",
    issueType: {
      DEADLOCK:                  "死锁",
      HIGH_BLOCKED_THREAD_COUNT: "线程阻塞",
      HIGH_THREAD_COUNT:         "线程数过高",
      HIGH_STACK_SIZE:           "调用栈过深",
      HIGH_CPU_RATIO:            "CPU 占用率高",
      THREAD_THROWING_EXCEPTION: "抛出异常",
    },
  },

}
