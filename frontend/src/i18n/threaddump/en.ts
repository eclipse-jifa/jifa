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

  // ----- Blocked Threads -----
  blockedThreadsLabel: "Blocked Threads",
  blockedThreads: {
    // {blocker} = name of the blocking thread, {count} = number of blocked threads
    title: "{blocker} is blocking 1 thread | {blocker} is blocking {count} threads",
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
    label: "Search Threads",
    searchTitle: "Search Threads",
    searchInput: "Search term",
    requiredMessage: "Please enter a search term",
    advancedToggle: "Advanced",
    searchFields: "Search Fields",
    searchFieldName: "Thread Name",
    searchFieldState: "Thread State",
    searchFieldStack: "Stack Trace",
    searchOptions: "Options",
    searchOptionRegex: "Regular Expression",
    searchOptionMatchCase: "Match Case",
    searchOptionThreadStates: "Filter States",
    searchOptionThreadStatesPlaceholder: "All states",
    threadStatesChartTitle: "Thread States",
    resultsCount: "threads found",
    noResults: "No matching threads found.",
    threadNameLabel: "Thread",
  },

  // ----- Thread Dump Overview / Compare -----
  threadDumpOverview: {
    label: "Dump Overview",
    diagnosisTitle: "Diagnosis",
    stateDistributionTitle: "Java Thread State Distribution",
    cpuConsumingTitle: "Top CPU Consuming Threads",
    cpuConsumingDatasetLabel: "CPU ({unit})",
    threadGroupTitle: "Thread Group Summary",
  },
}