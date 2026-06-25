/********************************************************************************
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.jifa.tda;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jifa.analysis.annotation.ApiParameterMeta;
import org.eclipse.jifa.analysis.cache.Cacheable;
import org.eclipse.jifa.analysis.cache.ProxyBuilder;
import org.eclipse.jifa.analysis.listener.ProgressListener;
import org.eclipse.jifa.common.domain.request.PagingRequest;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.common.util.PageViewBuilder;
import org.eclipse.jifa.tda.diagnoser.Diagnostic;
import org.eclipse.jifa.tda.diagnoser.ThreadDumpAnalysisConfig;
import org.eclipse.jifa.tda.diagnoser.ThreadDumpDiagnoser;
import org.eclipse.jifa.tda.enums.MonitorState;
import org.eclipse.jifa.tda.enums.ThreadType;
import org.eclipse.jifa.tda.model.CallSiteTree;
import org.eclipse.jifa.tda.model.Frame;
import org.eclipse.jifa.tda.model.IdentityPool;
import org.eclipse.jifa.tda.model.JavaThread;
import org.eclipse.jifa.tda.model.Monitor;
import org.eclipse.jifa.tda.model.RawMonitor;
import org.eclipse.jifa.tda.model.Snapshot;
import org.eclipse.jifa.tda.model.Thread;
import org.eclipse.jifa.tda.parser.ParserFactory;
import org.eclipse.jifa.tda.util.CollectionUtil;
import org.eclipse.jifa.tda.vo.Content;
import org.eclipse.jifa.tda.vo.Overview;
import org.eclipse.jifa.tda.vo.SearchHit;
import org.eclipse.jifa.tda.vo.VBlockingThread;
import org.eclipse.jifa.tda.vo.VFrame;
import org.eclipse.jifa.tda.vo.VMonitor;
import org.eclipse.jifa.tda.vo.VThread;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread dump analyzer
 */
public class ThreadDumpAnalyzer {

    private final Snapshot snapshot;

    ThreadDumpAnalyzer(Path path, ProgressListener listener) {
        snapshot = ParserFactory.buildParser(path).parse(path, listener);
    }

    /**
     * build a parser for a thread dump
     *
     * @param path     the path of thread dump
     * @param listener progress listener
     * @return analyzer
     */
    public static ThreadDumpAnalyzer build(Path path, ProgressListener listener) {
        return ProxyBuilder.build(ThreadDumpAnalyzer.class,
                                  new Class[]{Path.class, ProgressListener.class},
                                  new Object[]{path, listener});
    }

    private void computeThreadState(Overview o, Thread thread) {
        ThreadType type = thread.getType();
        switch (type) {
            case JAVA:
                JavaThread jt = ((JavaThread) thread);
                o.getJavaThreadStat().inc(jt.getJavaThreadState());
                o.getJavaThreadStat().inc(jt.getOsThreadState());
                if (jt.isDaemon()) {
                    o.getJavaThreadStat().incDaemon();
                }
                break;
            case JIT:
                o.getJitThreadStat().inc(thread.getOsThreadState());
                break;
            case GC:
                o.getGcThreadStat().inc(thread.getOsThreadState());
                break;
            case VM:
                o.getOtherThreadStat().inc(thread.getOsThreadState());
                break;
        }
        o.getThreadStat().inc(thread.getOsThreadState());
    }

    /**
     * @return the overview of the thread dump
     */
    @Cacheable
    public Overview overview() {
        Overview o = new Overview();
        CollectionUtil.forEach(t -> computeThreadState(o, t), snapshot.getJavaThreads(), snapshot.getNonJavaThreads());

        snapshot.getThreadGroup().forEach(
            (p, l) -> {
                for (Thread t : l) {
                    o.getThreadGroupStat().computeIfAbsent(p, i -> new Overview.ThreadStat()).inc(t.getOsThreadState());
                }
            }
        );
        o.setTimestamp(snapshot.getTimestamp());
        o.setVmInfo(snapshot.getVmInfo());
        o.setJniRefs(snapshot.getJniRefs());
        o.setJniWeakRefs(snapshot.getJniWeakRefs());

        if (snapshot.getDeadLockThreads() != null) {
            o.setDeadLockCount(snapshot.getDeadLockThreads().size());
        }

        o.setErrorCount(snapshot.getErrors().size());
        return o;
    }

    /**
     * @return the call site tree by parent id
     */
    public PageView<VFrame> callSiteTree(int parentId, PagingRequest paging) {
        CallSiteTree tree = snapshot.getCallSiteTree();
        if (parentId < 0 || parentId >= tree.getId2Node().length) {
            throw new IllegalArgumentException("Illegal parent id: " + parentId);
        }
        CallSiteTree.Node node = tree.getId2Node()[parentId];
        List<CallSiteTree.Node> children = node.getChildren() != null ? node.getChildren() : Collections.emptyList();
        return PageViewBuilder.build(children, paging, n -> {
            VFrame vFrame = new VFrame();
            vFrame.setId(n.getId());
            vFrame.setWeight(n.getWeight());
            vFrame.setEnd(n.getChildren() == null);

            Frame frame = n.getFrame();
            vFrame.setClazz(frame.getClazz());
            vFrame.setMethod(frame.getMethod());
            vFrame.setModule(frame.getModule());
            vFrame.setSourceType(frame.getSourceType());
            vFrame.setSource(frame.getSource());

            vFrame.setLine(frame.getLine());

            if (frame.getMonitors() != null) {
                List<VMonitor> vMonitors = new ArrayList<>();
                for (Monitor monitor : frame.getMonitors()) {
                    String clazz = null;
                    RawMonitor rm = monitor.getRawMonitor();
                    clazz = rm.getClazz();
                    vMonitors.add(new VMonitor(rm.getId(), rm.getAddress(), rm.isClassInstance(),
                                               clazz,
                                               monitor.getState()));
                }
                vFrame.setMonitors(vMonitors);
            }
            return vFrame;
        });
    }

    private PageView<VThread> buildVThreadPageView(List<Thread> threads, PagingRequest paging) {
        return PageViewBuilder.build(threads, paging, thread -> {
            VThread vThread = new VThread();
            vThread.setId(thread.getId());
            vThread.setName(thread.getName());
            return vThread;
        });
    }

    /**
     * @param name        the thread name filter (substring match, optional)
     * @param type        the thread type filter (optional)
     * @param threadState the Java or OS thread state to filter by (optional)
     * @param ids         explicit list of thread ids to include (optional)
     * @param paging      paging request
     * @return the threads filtered by name, type, state and/or id
     */
    public PageView<VThread> threads(@ApiParameterMeta(required = false) String name,
                                     @ApiParameterMeta(required = false) ThreadType type,
                                     @ApiParameterMeta(required = false) String threadState,
                                     @ApiParameterMeta(required = false) List<Integer> ids,
                                     PagingRequest paging) {
        List<Thread> threads = new ArrayList<>();
        CollectionUtil.forEach(t -> {
            if (type != null && t.getType() != type) {
                return;
            }
            if (StringUtils.isNotBlank(name) && !t.getName().contains(name)) {
                return;
            }
            if (StringUtils.isNotBlank(threadState) && !getThreadState(t).equals(threadState)) {
                return;
            }
            if (ids != null && !ids.isEmpty() && !ids.contains(t.getId())) {
                return;
            }
            threads.add(t);
        }, snapshot.getJavaThreads(), snapshot.getNonJavaThreads());

        return buildVThreadPageView(threads, paging);
    }

    /** Returns the most specific state string for a thread (Java state if available, else OS state). */
    private String getThreadState(Thread t) {
        if (t instanceof JavaThread) {
            JavaThread jt = (JavaThread) t;
            if (jt.getJavaThreadState() != null) {
                return String.valueOf(jt.getJavaThreadState());
            }
        }
        return String.valueOf(t.getOsThreadState());
    }

    /**
     * @param groupName the thread group name
     * @param paging    paging request
     * @return the threads filtered by group name and type
     */
    public PageView<VThread> threadsOfGroup(String groupName, PagingRequest paging) {
        List<Thread> threads = snapshot.getThreadGroup().getOrDefault(groupName, Collections.emptyList());
        return buildVThreadPageView(threads, paging);
    }

    public List<String> rawContentOfThread(int id) throws IOException {
        Thread thread = snapshot.getThreadMap().get(id);
        if (thread == null) {
            throw new IllegalArgumentException("Thread id is illegal: " + id);
        }
        String path = snapshot.getPath();

        int start = thread.getLineStart();
        int end = thread.getLineEnd();
        List<String> content = new ArrayList<>();

        try (LineNumberReader lnr = new LineNumberReader(new FileReader(path))) {
            for (int i = 1; i < start; i++) {
                lnr.readLine();
            }

            for (int i = start; i <= end; i++) {
                content.add(lnr.readLine());
            }
        }

        return content;
    }

    /**
     * @param lineNo    start line number
     * @param lineLimit line count
     * @return the raw content
     * @throws IOException
     */
    public Content content(int lineNo, int lineLimit) throws IOException {
        String path = snapshot.getPath();

        int end = lineNo + lineLimit - 1;
        List<String> content = new ArrayList<>();
        boolean reachEnd;

        try (LineNumberReader lnr = new LineNumberReader(new FileReader(path))) {
            for (int i = 1; i < lineNo; i++) {
                String line = lnr.readLine();
                if (line == null) {
                    break;
                }
            }

            for (int i = lineNo; i <= end; i++) {
                String line = lnr.readLine();
                if (line == null) {
                    break;
                }
                content.add(line);
            }

            String line = lnr.readLine();
            reachEnd = line == null;
        }
        return new Content(content, reachEnd);
    }

    /**
     * @param paging paging request
     * @return the monitors
     */
    public PageView<VMonitor> monitors(PagingRequest paging) {
        IdentityPool<RawMonitor> monitors = snapshot.getRawMonitors();
        return PageViewBuilder.build(monitors.objects(), paging,
                                     m -> new VMonitor(m.getId(), m.getAddress(), m.isClassInstance(), m.getClazz()));
    }

    /**
     * @param id     monitor id
     * @param state  monitor state
     * @param paging paging request
     * @return the threads by monitor id and state
     */
    public PageView<VThread> threadsByMonitor(int id, MonitorState state, PagingRequest paging) {
        Map<MonitorState, List<Thread>> map = snapshot.getMonitorThreads().get(id);
        if (map == null) {
            throw new IllegalArgumentException("Illegal monitor id: " + id);
        }
        return buildVThreadPageView(map.getOrDefault(state, Collections.emptyList()), paging);
    }

    /**
     * @param id monitor id
     * @return the <state, count> map by monitor id
     */
    public Map<MonitorState, Integer> threadCountsByMonitor(int id) {
        Map<MonitorState, List<Thread>> map = snapshot.getMonitorThreads().get(id);
        if (map == null) {
            throw new IllegalArgumentException("Illegal monitor id: " + id);
        }

        Map<MonitorState, Integer> counts = new HashMap<>();
        map.forEach((s, l) -> counts.put(s, l.size()));
        return counts;
    }

    /**
     * Returns all threads that are blocking at least one other thread via monitor
     * ownership, sorted descending by number of blocked threads, then by blocker
     * thread name.
     *
     * @return list of blocking threads with their blocked threads and held monitor
     */
    public List<VBlockingThread> blockingThreads() {
        List<VBlockingThread> result = new ArrayList<>();

        Map<Integer, Map<MonitorState, List<Thread>>> allMonitors = snapshot.getMonitorThreads();
        for (Entry<Integer, Map<MonitorState, List<Thread>>> monitorEntry : allMonitors.entrySet()) {
            Map<MonitorState, List<Thread>> monitorMap = monitorEntry.getValue();
            if (!monitorMap.containsKey(MonitorState.LOCKED)) {
                continue;
            }
            Thread blockingThread = monitorMap.get(MonitorState.LOCKED).stream().findFirst().orElse(null);
            List<Thread> blockedThreads = new ArrayList<>();
            if (monitorMap.containsKey(MonitorState.WAITING_TO_LOCK)) {
                blockedThreads.addAll(monitorMap.get(MonitorState.WAITING_TO_LOCK));
            }
            if (monitorMap.containsKey(MonitorState.WAITING_TO_RE_LOCK)) {
                blockedThreads.addAll(monitorMap.get(MonitorState.WAITING_TO_RE_LOCK));
            }
            if (!blockedThreads.isEmpty() && blockingThread != null) {
                VBlockingThread r = new VBlockingThread();
                r.setBlockedThreads(blockedThreads.stream()
                        .map(this::convertToVThread)
                        .collect(Collectors.toList()));
                r.setBlockingThread(convertToVThread(blockingThread));
                Monitor mon = findBlockingMonitor(blockedThreads.get(0));
                if (mon != null) {
                    r.setHeldLock(new VMonitor(
                            mon.getRawMonitor().getId(),
                            mon.getRawMonitor().getAddress(),
                            mon.getRawMonitor().isClassInstance(),
                            mon.getRawMonitor().getClazz(),
                            mon.getState()));
                }
                result.add(r);
            }
        }

        result.sort(Comparator
                .<VBlockingThread>comparingInt(m -> m.getBlockedThreads().size())
                .reversed()
                .thenComparing(m -> m.getBlockingThread().getName()));
        return result;
    }

    /**
     * Returns the threads with the highest CPU usage, in descending order.
     *
     * @param type limit to threads of this type; {@code null} means all types
     * @param max  maximum number of results; {@code -1} means unlimited
     * @return list of threads sorted from most to least CPU-intensive
     */
    public List<VThread> cpuConsumingThreads(@ApiParameterMeta(required = false) ThreadType type,
                                              int max) {
        Stream<Thread> stream = snapshot.getThreadMap().values().stream()
                .filter(t -> type == null || t.getType() == type);
        stream = stream.sorted(Comparator.comparingDouble(Thread::getCpu).reversed())
                .limit(max < 0 ? Integer.MAX_VALUE : max);
        return stream.map(this::convertToVThread).collect(Collectors.toList());
    }

    /**
     * Computes which threads consumed the most CPU <em>between</em> two thread
     * dumps by matching threads via their native thread id ({@code tid}).
     *
     * @param other the second (later) thread dump analyzer
     * @param max   maximum number of results; {@code -1} means unlimited
     * @param type  limit to threads of this type; {@code null} means all types
     * @return threads sorted by delta-CPU descending
     */
    public List<VThread> cpuConsumingThreadsCompare(ThreadDumpAnalyzer other, int max,
                                                     @ApiParameterMeta(required = false) ThreadType type) {
        int limit = max < 0 ? Integer.MAX_VALUE : max;
        Map<Long, Thread> otherByTid = other.snapshot.getThreadMap().values().stream()
            .collect(Collectors.toMap(Thread::getTid, t -> t, (a, b) -> a));
 
        Map<Thread, Double> cpuDelta = new HashMap<>();
        for (Thread first : snapshot.getThreadMap().values()) {
            if (type != null && first.getType() != type) {
                continue;
            }
            Thread second = otherByTid.get(first.getTid());
            if (second != null && second.getCpu() > 0) {
                cpuDelta.put(first, second.getCpu() - first.getCpu());
            }
        }
        List<VThread> result = new ArrayList<>();
        cpuDelta.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(limit)
                .forEach(e -> {
                    VThread vt = convertToVThread(e.getKey());
                    vt.setCpu(e.getValue());
                    result.add(vt);
                });
        return result;
    }

    /**
     * Diagnoses the thread dump for potential issues based on the given
     * configuration and returns any issues found.
     *
     * @param config the configuration to use; a default config is used if {@code null}
     * @return potentially empty list of diagnostic issues
     */
    public List<Diagnostic> diagnose(
            @ApiParameterMeta(required = false) ThreadDumpAnalysisConfig config) {
        return new ThreadDumpDiagnoser().analyze(
                snapshot,
                config != null ? config : new ThreadDumpAnalysisConfig());
    }

    /**
     * Searches through all threads and returns those whose name, state or
     * stack trace match all of the given terms.
     *
     * @param term              search terms; each term must match at least one of the
     *                          enabled search fields (AND semantics across terms)
     * @param searchName        include the thread name in the search (default: true)
     * @param searchState       include the thread state in the search (default: true)
     * @param searchStack       include the stack trace in the search (default: true)
     * @param regex             treat terms as regular expressions (default: false)
     * @param matchCase         perform a case-sensitive search (default: false)
     * @param allowedJavaStates if non-empty, only include threads whose Java state is
     *                          one of these values
     * @return matching threads together with their raw content lines
     */
    public List<SearchHit> searchThreads(
            @ApiParameterMeta(required = false) List<String> term,
            @ApiParameterMeta(required = false) Boolean searchName,
            @ApiParameterMeta(required = false) Boolean searchState,
            @ApiParameterMeta(required = false) Boolean searchStack,
            @ApiParameterMeta(required = false) Boolean regex,
            @ApiParameterMeta(required = false) Boolean matchCase,
            @ApiParameterMeta(required = false) List<String> allowedJavaStates) {
        if (term == null || term.isEmpty()) {
            return Collections.emptyList();
        }

        boolean doSearchName  = !Boolean.FALSE.equals(searchName);
        boolean doSearchState = !Boolean.FALSE.equals(searchState);
        boolean doSearchStack = !Boolean.FALSE.equals(searchStack);
        boolean doRegex       = Boolean.TRUE.equals(regex);
        int     flags         = Boolean.TRUE.equals(matchCase) ? 0 : Pattern.CASE_INSENSITIVE;

        List<Pattern> patterns = new ArrayList<>();
        for (String t : term) {
            try {
                patterns.add(Pattern.compile(doRegex ? t : Pattern.quote(t), flags));
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex term: " + t, e);
            }
        }

        List<SearchHit> results = new ArrayList<>();

        CollectionUtil.forEach(t -> {
            // Optional state pre-filter
            if (allowedJavaStates != null && !allowedJavaStates.isEmpty()) {
                if (!(t instanceof JavaThread)) return;
                JavaThread jt = (JavaThread) t;
                String state = jt.getJavaThreadState() != null
                        ? String.valueOf(jt.getJavaThreadState()) : "";
                if (!allowedJavaStates.contains(state)) return;
            }

            List<String> rawLines;
            try {
                rawLines = rawContentOfThread(t.getId());
            } catch (IOException e) {
                rawLines = Collections.emptyList();
            }

            String nameStr  = t.getName() != null ? t.getName() : "";
            String stateStr = getThreadState(t);
            String stackStr = rawLines.size() > 1
                    ? String.join("\n", rawLines.subList(1, rawLines.size()))
                    : "";

            boolean matches = patterns.stream().allMatch(p ->
                    (doSearchName  && p.matcher(nameStr).find())
                 || (doSearchState && p.matcher(stateStr).find())
                 || (doSearchStack && p.matcher(stackStr).find())
            );

            if (matches) {
                SearchHit hit = new SearchHit();
                hit.setId(t.getId());
                hit.setName(t.getName());
                hit.setOsState(String.valueOf(t.getOsThreadState()));
                if (t instanceof JavaThread) {
                    JavaThread jt = (JavaThread) t;
                    if (jt.getJavaThreadState() != null) {
                        hit.setJavaState(String.valueOf(jt.getJavaThreadState()));
                    }
                }
                if (t.getCpu() > 0)     hit.setCpu(t.getCpu());
                if (t.getElapsed() > 0) hit.setElapsed(t.getElapsed());
                hit.setLines(rawLines);
                results.add(hit);
            }
        }, snapshot.getJavaThreads(), snapshot.getNonJavaThreads());

        return results;
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    /**
     * Converts a model {@link Thread} to a lightweight {@link VThread} VO,
     * copying cpu and elapsed times when available ({@code > 0}).
     */
    private VThread convertToVThread(Thread thread) {
        VThread vt = new VThread();
        vt.setId(thread.getId());
        vt.setName(thread.getName());
        if (thread.getCpu() > 0) {
            vt.setCpu(thread.getCpu());
        }
        if (thread.getElapsed() > 0) {
            vt.setElapsed(thread.getElapsed());
        }
        return vt;
    }

    /**
     * Finds the monitor that a blocked thread is waiting to acquire by inspecting
     * the thread-level monitor list and the first frame that carries monitor
     * information.
     */
    private Monitor findBlockingMonitor(Thread thread) {
        List<Monitor> candidates = new ArrayList<>();
        if (thread instanceof JavaThread) {
            JavaThread blockedThread = (JavaThread) thread;
            if (blockedThread.getTrace() != null && blockedThread.getTrace().getFrames() != null) {
                for (Frame frame : blockedThread.getTrace().getFrames()) {
                    if (frame.getMonitors() != null) {
                        Arrays.stream(frame.getMonitors()).forEach(candidates::add);
                        // only the first frame with monitors is relevant
                        break;
                    }
                }
            }
        }
        return candidates.stream()
                .filter(m -> m.getState() == MonitorState.WAITING_TO_LOCK
                          || m.getState() == MonitorState.WAITING_TO_RE_LOCK)
                .findFirst()
                .orElse(null);
    }
}
