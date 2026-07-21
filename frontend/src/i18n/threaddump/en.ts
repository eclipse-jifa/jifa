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
  title: "Thread Dump Analysis",
  addFile: "Add Thread Dump File",
  overview: 'Overview',
  lockView: 'Lock View',
  basicInfo: 'Basic Information',
  time: 'Time',
  vmInfo: 'VM Information',
  jniRefs: 'JNI References',
  jniWeakRefs: 'JNI Weak References',
  errorPrompt: 'Number of errors found during parsing: ',
  deadLockCount: 'Dead Lock Count',
  threadSummary: "Thread Summary",
  threadGroupSummary: "Thread Group Summary",
  javaThread: "Java Thread",
  jitThread: "JIT Thread",
  gcThread: "GC Thread",
  otherThread: "Other Thread",
  total: "Total",
  monitors: "Java Monitors",
  callSiteTree: "Call Site Tree",
  fileContent: "File Content",
  loadFileContent: "Load",
  loadMoreFileContent: "Load More",
  threadNameLabel: "Thread Name",
  compareWithAnother: "Compare with another dump",

  // ----- Blocked Threads -----
  blockedThreadsLabel: "Blocked Threads",
  blockedThreads: {
    // {blocker} = name of the blocking thread, {count} = number of blocked threads
    title: "{blocker} is blocking 1 thread | {blocker} is blocking {count} threads",
    none: "No blocked threads found",
  },

  // ----- CPU Consuming Threads -----
  cpuConsumingThreadsLabel: "CPU Consuming Threads",
  cpuConsumingThreads: {
    title: "Top CPU consuming threads",
    cpuConsumptionLabel: "CPU consumption",
    hours: "hours",
    minutes: "minutes",
    seconds: "seconds",
    milliseconds: "ms",
    javaThreads: "Java Threads",
    nonJavaThreads: "Non-Java Threads",
    noCpuData: "This thread dump does not contain CPU usage information.",
  },

  // ----- CPU Delta (Compare) -----
  cpuConsumingThreadsCompareLabel: "CPU Delta (Compare Two Dumps)",
  cpuConsumingThreadsCompare: {
    selectPrompt: "Select second thread dump to compare",
    noData: "No threads with matching native IDs and CPU data found in both dumps.",
    cpuDeltaLabel: "CPU delta",
    cpuFirstLabel: "CPU (first dump)",
    cpuSecondLabel: "CPU (second dump)",
    hours: "hours",
    minutes: "minutes",
    seconds: "seconds",
    milliseconds: "ms",
  },

  // ----- Thread Dump Compare Page -----
  threadDumpCompare: {
    title: "Thread Dump Compare",
    selectFile1: "First Dump",
    selectFile2: "Second Dump",
    selectPlaceholder: "Select thread dump file",
    compareButton: "Compare",
    basicInfo: "Basic Information",
    timeDiff: "Time difference",
    vmInfoMatch: "VM matches",
    vmInfoMismatch: "VM differs",
    diagnosis: "Diagnosis",
    threadSummary: "Thread Summary",
    threadGroupSummary: "Thread Group Summary",
    cpuDelta: "CPU Time Delta",
    stateDistribution: "State Distribution",
    stateChanges: "Thread State Changes",
    persistentBlockers: "Persistent Blockers (blocked in both dumps)",
    deltaPositive: "increased",
    deltaNegative: "decreased",
    noFilesSelected: "Select two thread dump files to compare",
    noFilesAvailable: "No thread dump files found",
    noFilesAvailableHint: "Upload a thread dump file first to start comparing.",
    uploadNow: "Upload now",
    dump1Label: "Dump 1",
    dump2Label: "Dump 2",
    deltaLabel: "Δ",
    threadType: "Type",
    count: "Count",
    groupName: "Group",
  },

  // ----- Diagnosis Compare -----
  diagnoseCompare: {
    issue: "Issue",
    noIssue: "✓ No issue",
    new: "New",
    resolved: "Resolved",
    // Human-readable short labels for each issue type (used as row label)
    issueType: {
      DEADLOCK:                  "Deadlock",
      HIGH_BLOCKED_THREAD_COUNT: "Blocked threads",
      HIGH_THREAD_COUNT:         "High thread count",
      HIGH_STACK_SIZE:           "Large stack",
      HIGH_CPU_RATIO:            "High CPU ratio",
      THREAD_THROWING_EXCEPTION: "Exception throwing",
    },
  },

  // ----- Diagnosis -----
  diagnosis: {
    title: "Diagnosis",
    examine: "Examine",
    messageColumn: "Message",
    fileColumn: "File",
    suggestionColumn: "Suggestion",
    type: {
      // {count} = number of affected threads, {name} = thread name (singular only)
      // {threshold} = configured threshold value
      NO_ISSUES: "No issues found",
      NO_ISSUES_SUGGESTION: "",

      DEADLOCK: "{count} threads are in a deadlock",
      DEADLOCK_SUGGESTION:
        "Deadlocks happen when two or more threads are waiting for each other indefinitely. " +
        "They are caused by incorrect ordering of resource locking.",

      HIGH_BLOCKED_THREAD_COUNT: "1 thread is blocked | {count} threads are blocked",
      HIGH_BLOCKED_THREAD_COUNT_SUGGESTION:
        "A large number of blocked threads often indicates a bottleneck. " +
        "Examine the stack traces and review locking and synchronisation.",

      HIGH_THREAD_COUNT: "{count} is a high thread count",
      HIGH_THREAD_COUNT_SUGGESTION:
        "Such high thread counts can lead to memory exhaustion and thread starvation. " +
        "Look for thread leaks or consider using thread pools to reduce thread creation.",

      HIGH_STACK_SIZE: "{name} has a very large stack (> {threshold} frames) | {count} threads have a very large stack (> {threshold} frames)",
      HIGH_STACK_SIZE_SUGGESTION:
        "Large stack sizes can lead to StackOverflowError and decreased performance. " +
        "Check for excessive recursion.",

      HIGH_CPU_RATIO: "{name} has a high CPU ratio | {count} threads have a high CPU ratio",
      HIGH_CPU_RATIO_SUGGESTION:
        "A high CPU ratio means a thread is consuming large amounts of CPU over its lifetime. " +
        "This is not necessarily bad, but marks very active threads worth investigating.",

      THREAD_THROWING_EXCEPTION: "{name} is throwing an exception | {count} threads are throwing exceptions",
      THREAD_THROWING_EXCEPTION_SUGGESTION:
        "A thread throwing an exception may indicate an issue in the application. " +
        "Keep in mind that creating stack traces is expensive – frequent exceptions can impact performance.",
    },
  },

  // ----- Thread Search -----
  threadDumpSearch: {
    label: "Find Threads",
    searchTitle: "Find Threads",
    searchPlaceholder: "Enter keywords to match thread names, states and stack traces",
    searchInput: "Search term",
    requiredMessage: "Please enter a search term",
    advancedToggle: "More Options",
    matchFields: "Match Fields",
    matchFieldName: "Thread Name",
    matchFieldState: "Thread State",
    matchFieldStack: "Stack Trace",
    otherOptions: "Other",
    searchOptionRegex: "Regular Expression",
    searchOptionMatchCase: "Match Case",
    searchOptionThreadStates: "Filter States",
    searchOptionThreadStatesPlaceholder: "All states",
    threadStatesChartTitle: "Thread States",
    resultsCount: "threads found",
    threadNameLabel: "Thread",
    stateLabel: "State",
    cpuLabel: "CPU Time",
    elapsedLabel: "Elapsed",
  },

  // ----- State Distribution Compare -----
  stateDistributionCompare: {
    state: "State",
    delta: "Δ",
    noData: "No thread state data available.",
  },

  // ----- Thread State Changes -----
  threadStateChanges: {
    changeType: "Change type",
    dump1Impact: "Dump 1",
    dump2Impact: "Dump 2",
    delta: "Δ",
    transitionsTitle: "State transition breakdown",
    count: "Count",
    summaryChanged: "State changed",
    summaryDisappeared: "Disappeared",
    summaryNew: "New in second dump",
    tabChanged: "State Changed",
    tabDisappeared: "Disappeared",
    tabNew: "New",
    thread: "Thread",
    stateBefore: "State (Dump 1)",
    stateAfter: "State (Dump 2)",
    noChanges: "No threads changed state between the two dumps.",
    noDisappeared: "No threads disappeared between the two dumps.",
    noNew: "No new threads appeared in the second dump.",
  },

  // ----- Persistent Blockers -----
  persistentBlockers: {
    warningTitle: "1 thread was blocked in both dumps | {count} threads were blocked in both dumps",
    summaryType: "Type",
    dump1: "Dump 1",
    dump2: "Dump 2",
    delta: "Δ",
    allBlocked: "Blocked threads (all)",
    persistentOnly: "Blocked in both dumps",
    resolved: "Resolved blockers",
    introduced: "New blockers in dump 2",
    detailsTitle: "Threads blocked in both dumps",
    thread: "Thread",
    actions: "Actions",
    inspect: "Inspect",
    noPersistentBlockers: "No threads were blocked in both dumps – no persistent contention detected.",
  },
}
