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

package org.eclipse.jifa.gclog.parser;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.common.domain.exception.ShouldNotReachHereException;
import org.eclipse.jifa.gclog.event.GCEvent;
import org.eclipse.jifa.gclog.event.eventInfo.CpuTime;
import org.eclipse.jifa.gclog.event.eventInfo.GCMemoryItem;
import org.eclipse.jifa.gclog.event.eventInfo.MemoryArea;
import org.eclipse.jifa.gclog.event.eventInfo.ReferenceGC;
import org.eclipse.jifa.gclog.model.GCEventType;
import org.eclipse.jifa.gclog.model.GCModel;
import org.eclipse.jifa.gclog.model.modeInfo.GCCollectorType;
import org.eclipse.jifa.gclog.model.modeInfo.VmOptions;
import org.eclipse.jifa.gclog.parser.ParseRule.PrefixAndValueParseRule;
import org.eclipse.jifa.gclog.util.Constant;
import org.eclipse.jifa.gclog.util.GCLogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.eclipse.jifa.gclog.util.Constant.KB2MB;
import static org.eclipse.jifa.gclog.util.Constant.MS2S;
import static org.eclipse.jifa.gclog.util.Constant.UNKNOWN_DOUBLE;

/*
 * We mainly consider -XX:+PrintGCDetails. -XX:+PrintReferenceGC and -XX:+PrintApplicationStopTime  are also considered
 * because they are commonly used, and they will greatly affect parsing. We will continue support for cases in the future.
 */

@Slf4j
public abstract class AbstractPreUnifiedGCLogParser extends AbstractGCLogParser {

    private LinkedList<List<GCLogToken>> sentenceToParseQueue = new LinkedList<>();
    private LinkedList<List<GCLogToken>> sentenceToAssembleStack = new LinkedList<>();

    private final static String[] FULL_LINE_PREFIXES = Arrays.stream(new String[]{
            "CommandLine flags: ",
            "OpenJDK",
            "Memory:",
            "   [Parallel Time",
            "      [GC Worker Start (ms)",
            "      [Ext Root Scanning (ms)",
            "      [Update RS (ms)",
            "         [Processed Buffers",
            "      [Scan RS (ms)",
            "      [Code Root Scanning (ms)",
            "      [Object Copy (ms)",
            "      [Termination (ms)",
            "         [Termination Attempts",
            "      [GC Worker Other (ms)",
            "      [GC Worker Total (ms)",
            "      [GC Worker End (ms)",
            "   [Code Root Fixup",
            "   [Code Root Purge",
            "   [Clear CT",
            "   [Other",
            "      [Evacuation Failure",
            "      [Choose CSet",
            "      [Ref Proc",
            "      [Ref Enq",
            "      [Redirty Cards",
            "      [Humongous Register",
            "      [Humongous Reclaim",
            "      [Free CSet",
            "   [Eden",
    }).sorted(Comparator.reverseOrder()).toArray(String[]::new);

    private static final String[] TRACETIME_GC_START_TITLES = Arrays.stream(new String[]{
            "GC pause",
            "GC",
            "Full GC",
    }).sorted(Comparator.reverseOrder()).toArray(String[]::new);

    private static final String[] TRACETIME_GENERATION_TITLES = Arrays.stream(new String[]{
            "ParNew",
            "ASParNew",
            "DefNew",
            "PSYoungGen",
            "PSOldGen",
            "ParOldGen",
            "Tenured",
            "CMS",
            "ASCMS",
    }).sorted(Comparator.reverseOrder()).toArray(String[]::new);

    private static final String[] TRACETIME_OTHER_TITLES = Arrays.stream(new String[]{
            "CMS-concurrent-mark-start",
            "CMS-concurrent-mark",
            "CMS-concurrent-preclean-start",
            "CMS-concurrent-preclean",
            "CMS-concurrent-abortable-preclean-start",
            "CMS-concurrent-abortable-preclean",
            "CMS-concurrent-sweep-start",
            "CMS-concurrent-sweep",
            "CMS-concurrent-reset-start",
            "CMS-concurrent-reset",
            "Rescan (parallel) ",
            "Rescan (non-parallel) ",
            "CMS:MSC ",
            "grey object rescan",
            "root rescan",
            "visit unhandled CLDs",
            "dirty klass scan",
            "weak refs processing",
            "class unloading",
            "scrub symbol table",
            "scrub string table",
            "Verify After",
            "Verify Before",
            "GC ref-proc",
            "phase 1",
            "phase 2",
            "phase 3",
            "phase 4",
            "adjust roots",
            "compaction phase",
            "par compact",
            "deferred updates",
            "dense prefix task setup",
            "drain task setup",
            "steal task setup",
            "marking phase",
            "par mark",
            "reference processing",
            "post compact",
            "pre compact",
            "summary phase",
            "Scavenge",
            "References",
            "StringTable",
            "Heap Dump (after full gc): ",
            "Class Histogram (after full gc): ",
            "Heap Dump (before full gc): ",
            "Class Histogram (before full gc): ",
            "per-gen-adjust",
            "marking",
            "ref processing",
            "adjust-strong-roots",
            "adjust-weak-roots",
            "adjust-preserved-marks",
            "adjust-heap",
            "Preclean SoftReferences",
            "Preclean WeakReferences",
            "Preclean FinalReferences",
            "Preclean PhantomReferences",
            "SoftReference",
            "WeakReference",
            "FinalReference",
            "PhantomReference",
            "JNI Weak Reference",
            "par-adjust-pointers",
            "GC concurrent-root-region-scan-start",
            "GC concurrent-root-region-scan-end",
            "GC concurrent-mark-start",
            "GC concurrent-mark-end",
            "GC concurrent-mark-reset-for-overflow",
            "GC concurrent-mark-abort",
            "GC remark ",
            "Finalize Marking",
            "GC ref-proc",
            "Unloading",
            "GC cleanup",
            "GC concurrent-cleanup-start",
            "GC concurrent-cleanup-end"
    }).sorted(Comparator.reverseOrder()).toArray(String[]::new);

    private static final String[][] EMBEDDED_SENTENCE_BEGIN_END = {
            {"[1 CMS-", ")]"},
            {"[YG occupancy: ", "]"},
            {" CMS: abort preclean due to time ", null},
            {" CMS: abort preclean due to loop ", null},
            {" (promotion failed) ", null},
            {" (promotion failed)", null},
            {" (concurrent mode failure)", null},
            {" (to-space exhausted)", null},
            {"--", null},
            {" [Times", "]"}
    };

    private static final String[] EMBEDDED_SENTENCE_WITH_BRACKET = Arrays.stream(EMBEDDED_SENTENCE_BEGIN_END)
            .filter(beginEnd -> {
                if (beginEnd[1] == null) {
                    return beginEnd[0].startsWith(" (") && beginEnd[0].endsWith(")");
                } else {
                    return beginEnd[0].startsWith(" (") && beginEnd[1].endsWith(")");
                }
            })
            .map(begin_end -> begin_end[0])
            .toArray(String[]::new);

    /*
     * In preunified gclogs a sentence may insert into another sentence and this makes parsing difficult. Overall,
     * there are two types of sentences:
     * (1) Each sentence is consist of two parts like: "datestamp/timestamp: [action" + "heap change, duration]"
     * (2) The sentence is printed simultaneously, and there is no common datestamp or timestamp
     * We will first reassemble original gclog text into lines like this, and then do actual parsing work.
     * for example
     * 2021-11-24T23:23:44.225-0800: 796.991: [GC (Allocation Failure) 2021-11-24T23:23:44.225-0800: 796.992: [ParNew: 1922432K->1922432K(1922432K), 0.0000267 secs]2021-11-24T23:23:44.226-0800: 796.992: [CMS2021-11-24T23:23:45.066-0800: 797.832: [CMS-concurrent-sweep: 1.180/1.376 secs] [Times: user=3.42 sys=0.14, real=1.38 secs]
     * (concurrent mode failure): 2034154K->1051300K(2097152K), 4.6146919 secs] 3956586K->1051300K(4019584K), [Metaspace: 296232K->296083K(1325056K)], 4.6165192 secs] [Times: user=4.60 sys=0.05, real=4.62 secs]
     * will be transformed into
     * 2021-11-24T23:23:44.225-0800: 796.991: [GC (Allocation Failure) 3956586K->1051300K(4019584K), [Metaspace: 296232K->296083K(1325056K)], 4.6165192 secs] [Times: user=4.60 sys=0.05, real=4.62 secs]
     * 2021-11-24T23:23:44.225-0800: 796.992: [ParNew: 1922432K->1922432K(1922432K), 0.0000267 secs]
     * 2021-11-24T23:23:44.226-0800: 796.992: [CMS: 2034154K->1051300K(2097152K), 4.6146919 secs]
     * 2021-11-24T23:23:45.066-0800: 797.832: [CMS-concurrent-sweep: 1.180/1.376 secs] [Times: user=3.42 sys=0.14, real=1.38 secs]
     * (concurrent mode failure)
     *
     */
    @Override
    protected final void doParseLine(String line) {
        new LineAssembler(this, line).doAssemble();
        if (sentenceToAssembleStack.isEmpty()) {
            while (!sentenceToParseQueue.isEmpty()) {
                doParseSentence(pollSentenceToParse());
            }
        }
    }

    private void doParseSentence(List<GCLogToken> line) {
        try {
            GCEvent event = new GCEvent();
            String title = null;
            String referenceGC = null;
            String datestamp = null;
            for (GCLogToken token : line) {
                if (token.getType() == TOKEN_LINE_FULL_SENTENCE || token.getType() == TOKEN_EMBEDDED_SENTENCE) {
                    doParseFullSentence(token.getValue());
                    return;
                } else if (token.getType() == TOKEN_DATESTAMP) {
                    datestamp = token.getValue();
                } else if (token.getType() == TOKEN_UPTIME) {
                    event.setStartTime(MS2S * Double.parseDouble(token.getValue()));
                } else if (token.getType() == TOKEN_GCID) {
                    event.setGcid(Integer.parseInt(token.getValue()));
                } else if (token.getType() == TOKEN_GC_TRACETIME_TITLE) {
                    // title is complex, it may include event name, gc cause, generation or something else.
                    // let subclass parse it
                    title = token.getValue();
                } else if (token.getType() == TOKEN_SAFEPOINT) {
                    if (doBeforeParsingGCTraceTime(event, datestamp)) {
                        doParseSafePoint(event, token.getValue());
                    }
                    return;
                } else if (token.getType() == TOKEN_MEMORY_CHANGE) {
                    long[] memories = GCLogUtil.parseMemorySizeFromTo(token.getValue(), (int) KB2MB);
                    GCMemoryItem item = new GCMemoryItem(MemoryArea.HEAP, memories);
                    event.setMemoryItem(item);
                } else if (token.getType() == TOKEN_REFERENCE_GC) {
                    referenceGC = token.getValue();
                } else if (token.getType() == TOKEN_DURATION) {
                    event.setDuration(MS2S * Double.parseDouble(token.getValue()));
                } else if (token.getType() == TOKEN_METASPACE) {
                    long[] memories = GCLogUtil.parseMemorySizeFromTo(token.getValue(), (int) KB2MB);
                    GCMemoryItem item = new GCMemoryItem(MemoryArea.METASPACE, memories);
                    event.setMemoryItem(item);
                } else if (token.getType() == TOKEN_RIGHT_BRACKET) {
                    // do nothing
                } else {
                    throw new ShouldNotReachHereException();
                }
            }
            // jni weak does not print reference count
            if (referenceGC != null || "JNI Weak Reference".equals(title)) {
                if (doBeforeParsingGCTraceTime(event, datestamp)) {
                    doParseReferenceGC(event, title, referenceGC);
                }
            } else if (title != null) {
                if (doBeforeParsingGCTraceTime(event, datestamp)) {
                    doParseGCTraceTime(event, title);
                }
            }
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }

    protected abstract void doParseFullSentence(String sentence);

    protected abstract void doParseGCTraceTime(GCEvent event, String title);

    // subclass should tell which event this reference gc belongs to
    protected abstract GCEvent getReferenceGCEvent();

    // subclass should tell which event this cputime belongs to
    protected abstract List<GCEventType> getCPUTimeGCEvent();

    // 0.231: Total time for which application threads were stopped: 0.0001215 seconds, Stopping threads took: 0.0000271 seconds
    private void doParseSafePoint(GCEvent event, String s) {
        if (!s.startsWith("Total time for which application")) {
            return;
        }
        parseSafepointStop(event.getStartTime(), s);
    }

    // "123 refs"
    // "123 refs, 234 refs"
    private void doParseReferenceGC(GCEvent event, String title, String referenceGCString) {
        GCEvent referenceGCEvent = getReferenceGCEvent();
        if (referenceGCEvent == null) {
            return;
        }
        ReferenceGC referenceGC = referenceGCEvent.getReferenceGC();
        if (referenceGC == null) {
            referenceGC = new ReferenceGC();
            referenceGCEvent.setReferenceGC(referenceGC);
        }
        List<Integer> counts = null;
        if (referenceGCString != null) {
            counts = Arrays.stream(referenceGCString.split(", "))
                    .map(s -> Integer.parseInt(s.substring(0, s.length() - " refs".length())))
                    .collect(Collectors.toList());
        }
        switch (title) {
            case "SoftReference":
                referenceGC.setSoftReferencePauseTime(event.getDuration());
                referenceGC.setSoftReferenceStartTime(event.getStartTime());
                referenceGC.setSoftReferenceCount(counts.get(0));
                break;
            case "WeakReference":
                referenceGC.setWeakReferencePauseTime(event.getDuration());
                referenceGC.setWeakReferenceStartTime(event.getStartTime());
                referenceGC.setWeakReferenceCount(counts.get(0));
                break;
            case "FinalReference":
                referenceGC.setFinalReferencePauseTime(event.getDuration());
                referenceGC.setFinalReferenceStartTime(event.getStartTime());
                referenceGC.setFinalReferenceCount(counts.get(0));
                break;
            case "PhantomReference":
                referenceGC.setPhantomReferencePauseTime(event.getDuration());
                referenceGC.setPhantomReferenceStartTime(event.getStartTime());
                referenceGC.setPhantomReferenceCount(counts.get(0));
                if (counts.size() > 1) {
                    referenceGC.setPhantomReferenceFreedCount(counts.get(1));
                }
                break;
            case "JNI Weak Reference":
                referenceGC.setJniWeakReferencePauseTime(event.getDuration());
                referenceGC.setJniWeakReferenceStartTime(event.getStartTime());
                break;
            default:
                throw new ShouldNotReachHereException();
        }
    }

    private double lastUptime = UNKNOWN_DOUBLE;

    private boolean doBeforeParsingGCTraceTime(GCEvent event, String datestampString) {
        double timestamp = UNKNOWN_DOUBLE;
        double uptime = event.getStartTime();
        GCModel model = getModel();
        // set model reference timestamp
        if (model.getReferenceTimestamp() == UNKNOWN_DOUBLE && datestampString != null) {
            // parsing timestamp is expensive, do it lazily
            timestamp = GCLogUtil.parseDateStamp(datestampString);
            double startTimestamp = uptime == UNKNOWN_DOUBLE ? timestamp : timestamp - uptime;
            model.setReferenceTimestamp(startTimestamp);
        }
        // set event start time
        if (event.getStartTime() == UNKNOWN_DOUBLE) {
            if (datestampString != null && model.getReferenceTimestamp() != UNKNOWN_DOUBLE) {
                if (timestamp == UNKNOWN_DOUBLE) {
                    timestamp = GCLogUtil.parseDateStamp(datestampString);
                }
                uptime = timestamp - model.getReferenceTimestamp();
            } else {
                // HACK: There may be rare concurrency issue in printing uptime and datestamp when two threads
                // are printing simultaneously and this may lead to problem in parsing. Copy the uptime from
                // the last known uptime.
                uptime = lastUptime;
            }
            event.setStartTime(uptime);
        }
        if (event.getStartTime() == UNKNOWN_DOUBLE) {
            // we have no way to know uptime
            return false;
        } else {
            lastUptime = event.getStartTime();
        }
        // set model start and end time
        if (model.getStartTime() == UNKNOWN_DOUBLE) {
            model.setStartTime(uptime);
        }
        model.setEndTime(Math.max(uptime, model.getEndTime()));
        return true;
    }

    private void pushSentenceToAssemble(List<GCLogToken> sentence) {
        sentenceToAssembleStack.offerLast(sentence);
    }

    private List<GCLogToken> pollSentenceToAssemble() {
        return sentenceToAssembleStack.pollLast();
    }

    private void pushSentenceToParse(List<GCLogToken> sentence) {
        sentenceToParseQueue.offerLast(sentence);
    }

    private List<GCLogToken> pollSentenceToParse() {
        return sentenceToParseQueue.pollFirst();
    }

    private interface GCLogTokenType {
        // for efficiency, sometimes we do not check strictly
        GCLogToken parseNextToken(String line, int index, AbstractPreUnifiedGCLogParser parser);
    }

    @Override
    protected void endParsing() {
        // maybe we have met some mistake, try to flush any sentence that may be valid
        while (!sentenceToParseQueue.isEmpty()) {
            List<GCLogToken> line = pollSentenceToParse();
            if (sentenceIsValid(line)) {
                doParseSentence(line);
            }
        }
    }

    private boolean sentenceIsValid(List<GCLogToken> sentence) {
        if (sentence.isEmpty()) {
            return false;
        } else if (sentence.size() == 1) {
            return sentence.get(0).getType() == TOKEN_LINE_FULL_SENTENCE
                    || sentence.get(0).getType() == TOKEN_EMBEDDED_SENTENCE;
        } else {
            return Arrays.asList(GC_TRACETIME_END_TOKEN_TYPES).contains(sentence.get(sentence.size() - 1).getType());
        }
    }

    @Data
    private static class GCLogToken {
        private GCLogTokenType type;
        private String value; // its corresponding string in original text. Some signs like ',' or ')' may have been removed
        private int end;  // index of next character after this token in original text

        public GCLogToken(String value, int end) {
            this.value = value;
            this.end = end;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    // "2021-11-24T23:23:44.225-0800: "
    protected final static GCLogTokenType TOKEN_DATESTAMP = (line, index, parser) -> {
        if (line.charAt(index) == ' ') {
            index++;
        }
        if (GCLogUtil.isDatestamp(line, index)) {
            String s = line.substring(index, index + GCLogUtil.DATESTAMP_LENGTH);
            return new GCLogToken(s, newEndAfterPossiblyMeetColunSpace(line,   index + GCLogUtil.DATESTAMP_LENGTH));
        } else {
            return null;
        }
    };

    private static int newEndAfterPossiblyMeetColunSpace(String line, int index) {
        if (GCLogUtil.stringSubEquals(line, index, ": ")) {
            return index + 2;
        } else {
            return index;
        }
    }

    // ": "
    protected final static GCLogTokenType TOKEN_COLON_SPACE = (line, index, parser) -> {
        int end = newEndAfterPossiblyMeetColunSpace(line, index);
        if (index != end) {
            String s = line.substring(index, end);
            return new GCLogToken(s, end);
        } else {
            return null;
        }
    };

    // "12.979: "
    protected final static GCLogTokenType TOKEN_UPTIME = (line, index, parser) -> {
        if (line.charAt(index) == ' ') {
            index++;
        }
        int end = GCLogUtil.isDecimal(line, index, 3);
        if (end >= 0) {
            String s = line.substring(index, end);
            return new GCLogToken(s, newEndAfterPossiblyMeetColunSpace(line, end));
        } else {
            return null;
        }
    };

    // "#12: "
    protected final static GCLogTokenType TOKEN_GCID = (line, index, parser) -> {
        if (line.charAt(index) != '#') {
            return null;
        }
        for (int i = index + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c)) {
                continue;
            }
            if (index != i + 1) {
                String s = line.substring(index + 1, i);
                return new GCLogToken(s, newEndAfterPossiblyMeetColunSpace(line, i));
            } else {
                return null;
            }
        }
        return null;
    };

    // 2021-11-24T23:23:55.013-0800: 807.779: [GC (Allocation Failure) 2021-11-24T23:23:55.013-0800: 807.780:
    // 2021-05-16T19:49:24.719+0800: 170551.726: [GC pause (GCLocker Initiated GC) (young), 0.0218447 secs]
    // 2021-05-16T19:49:31.213+0800: 170558.220: [GC pause (G1 Evacuation Pause) (young), 0.0210546 secs]
    // 2021-10-03T22:27:00.414+0800: 528676.801: [Full GC (Allocation Failure)  19G->4441M(20G), 12.4414569 secs]
    // 2021-07-02T10:22:48.500+0800: 61076.005: [Full GC 61076.006: [CMS: 368928K->248751K(628736K), 1.3006011 secs] 474459K->248751K(1205120K), [Metaspace: 272682K->272682K(1298432K)], 1.3027769 secs] [Times: user=1.22 sys=0.00, real=1.30 secs]
    // 2021-08-25T11:28:31.969+0800: 114402.958: [GC pause (Metadata GC Threshold) (young) (initial-mark), 0.3875850 secs]
    // 0.269: [Full GC (Ergonomics) [PSYoungGen: 4096K->0K(55296K)] [ParOldGen: 93741K->67372K(174592K)] 97837K->67372K(229888K), [Metaspace: 3202K->3202K(1056768K)], 0.6862093 secs] [Times: user=2.60 sys=0.02, real=0.69 secs]
    // " [1 CMS-initial-mark"
    // 2023-02-22T01:35:44.598+0800: 8.897: [GC pause (G1 Evacuation Pause) (young) 1120M->67183K(22432M), 0.0629057 secs]
    protected final static GCLogTokenType TOKEN_GC_TRACETIME_TITLE = (line, index, parser) -> {
        if (line.charAt(index) == ' ') {
            index++;// ps full gc has an extra space
        }
        if (index >= line.length() || line.charAt(index) != '[') {
            return null;
        }
        index++;
        if (GCLogUtil.stringSubEquals(line, index, "1 ")) {
            index += 2;
        }
        String title;
        if ((title = GCLogUtil.stringSubEqualsAny(line, index, TRACETIME_OTHER_TITLES)) != null) {
            return new GCLogToken(title, index + title.length());
        } else if ((title = GCLogUtil.stringSubEqualsAny(line, index, TRACETIME_GC_START_TITLES)) != null) {
            // gc cause is a part of title
            int end = index + title.length();
            boolean endWithEmbeddedSentence = false;
            while (true) {
                if (!GCLogUtil.stringSubEquals(line, end, " (")) {
                    break;
                }
                // maybe the () is an embedded sentence
                if (GCLogUtil.stringSubEqualsAny(line, end, EMBEDDED_SENTENCE_WITH_BRACKET) != null) {
                    endWithEmbeddedSentence = true;
                    break;
                }
                int rightBracket = GCLogUtil.nextBalancedRightBracket(line, end + 2);
                if (rightBracket < 0) {
                    break;
                }
                end = rightBracket + 1;
            }
            // HACK: G1 young gc doesn't have extra space in the end
            boolean isG1YoungGC = parser.getMetadata().getCollector() == GCCollectorType.G1
                    && GCLogUtil.stringSubEquals(line, index, "GC pause");
            if (!endWithEmbeddedSentence && !isG1YoungGC && end < line.length() && line.charAt(end) == ' ') {
                end++;
            }
            return new GCLogToken(line.substring(index, end), end);
        } else if ((title = GCLogUtil.stringSubEqualsAny(line, index, TRACETIME_GENERATION_TITLES)) != null) {
            return new GCLogToken(title, index + title.length());
        }
        return null;
    };

    private final static Pattern MEMORY_CHANGE_PATTERN = Pattern.compile("^(:?\\d+[kmgt]?b?(\\(:?\\d+[kmgt]?b?\\))?->)?\\d+[kmgt]?b?(\\(:?\\d+[kmgt]?b?\\))?$");
    // " 1922432K->174720K(1922432K)"
    // " 1922432->174720K(1922432)"
    // " 1922432K(1922432K)->174720K(1922432K)"
    // " 1880341K(4019584K)"
    protected final static GCLogTokenType TOKEN_MEMORY_CHANGE = (line, index, parser) -> {
        if (line.charAt(index) == ':') {
            index++;
        }
        if (index >= line.length() || line.charAt(index) != ' ') {
            return null;
        }
        int end;
        for (end = index + 1; end < line.length(); end++) {
            char c = line.charAt(end);
            if (c == ' ' || c == ',' || c == ']') {// find end position
                String memoryChangeString = line.substring(index + 1, end).toLowerCase();
                if (MEMORY_CHANGE_PATTERN.matcher(memoryChangeString).matches()) {
                    return new GCLogToken(memoryChangeString, c == ']' ? end + 1 : end);
                } else {
                    return null;
                }
            }
        }
        return null;
    };

    // ", 0.2240876 secs]"
    // ": 0.576/0.611 secs]"
    protected final static GCLogTokenType TOKEN_DURATION = (line, index, parser) -> {
        if (GCLogUtil.stringSubEquals(line, index, ", ")) {
            int end = GCLogUtil.isDecimal(line, index + 2, 7);
            if (end >= 0 && GCLogUtil.stringSubEquals(line, end, " secs]")) {
                String s = line.substring(index + 2, end);
                return new GCLogToken(s, end + " secs]".length());
            }
        } else if (GCLogUtil.stringSubEquals(line, index, ": ")) {
            int slash = GCLogUtil.isDecimal(line, index + 2, 3);
            if (slash < 0 || line.charAt(slash) != '/') {
                return null;
            }
            int end = GCLogUtil.isDecimal(line, slash + 1, 3);
            if (end >= 0 && GCLogUtil.stringSubEquals(line, end, " secs]")) {
                String s = line.substring(slash + 1, end);
                return new GCLogToken(s, end + " secs]".length());
            }
        }
        return null;
    };

    // ", 123 refs"
    // return end index if matching, else -1
    private static int isReferenceGCToken(String line, int index) {
        if (!GCLogUtil.stringSubEquals(line, index, ", ")) {
            return -1;
        }
        int i = index + 2;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        if (i < line.length() && GCLogUtil.stringSubEquals(line, i, " refs")) {
            return i + " refs".length();
        } else {
            return -1;
        }
    }

    // ", 123 refs"
    // ", 123 refs, 234 refs"  // may appear twice
    protected final static GCLogTokenType TOKEN_REFERENCE_GC = (line, index, parser) -> {
        int end = isReferenceGCToken(line, index);
        if (end < 0) {
            return null;
        }
        int nextEnd = isReferenceGCToken(line, end);
        if (nextEnd >= 0) {
            end = nextEnd;
        }
        return new GCLogToken(line.substring(index + 2, end), end);
    };

    // ", [Metaspace: 246621K->246621K(1273856K)]"
    protected final static GCLogTokenType TOKEN_METASPACE = (line, index, parser) -> {
        if (GCLogUtil.stringSubEquals(line, index, ", [Metaspace: ")) {
            int indexEnd = line.indexOf(']', index);
            String s = line.substring(index + ", [Metaspace: ".length(), indexEnd);
            return new GCLogToken(s, indexEnd + 1);
        } else {
            return null;
        }
    };

    // "]"
    protected final static GCLogTokenType TOKEN_RIGHT_BRACKET = (line, index, parser) ->
            line.charAt(index) == ']' ? new GCLogToken("]", index + 1) : null;

    protected final static GCLogTokenType TOKEN_LINE_FULL_SENTENCE = (line, index, parser) -> {
        for (String prefix : FULL_LINE_PREFIXES) {
            if (line.startsWith(prefix)) {
                return new GCLogToken(line, line.length());
            }
        }
        return null;
    };

    protected final static GCLogTokenType TOKEN_EMBEDDED_SENTENCE = (line, index, parser) -> {
        for (String[] beginEnd : EMBEDDED_SENTENCE_BEGIN_END) {
            if (GCLogUtil.stringSubEquals(line, index, beginEnd[0])) {
                if (beginEnd[1] == null) {
                    return new GCLogToken(beginEnd[0], index + beginEnd[0].length());
                } else {
                    int end = line.indexOf(beginEnd[1], index + beginEnd[0].length());
                    if (end >= 0) {
                        end += beginEnd[1].length();
                        return new GCLogToken(line.substring(index, end), end);
                    }
                }
            }
        }
        return null;
    };

    // 0.231: Total time for which application threads were stopped: 0.0001215 seconds, Stopping threads took: 0.0000271 seconds
    // 0.248: Application time: 0.0170944 seconds
    // safepoint info prints datestamp or timestamp, but it is not printed in [] style
    protected final static GCLogTokenType TOKEN_SAFEPOINT = (line, index, parser) -> {
        if (GCLogUtil.stringSubEquals(line, index, "Total time for which") ||
                GCLogUtil.stringSubEquals(line, index, "Application time:")) {
            return new GCLogToken(line.substring(index), line.length());
        } else {
            return null;
        }
    };

    // order of tokens matters
    private static final GCLogTokenType[] GC_TRACETIME_BEGIN_TOKEN_TYPES = {
            TOKEN_DATESTAMP,
            TOKEN_UPTIME,
            TOKEN_GCID,
            TOKEN_GC_TRACETIME_TITLE,
    };

    private static final GCLogTokenType[] GC_TRACETIME_END_TOKEN_TYPES = {
            TOKEN_SAFEPOINT,
            TOKEN_MEMORY_CHANGE,
            TOKEN_REFERENCE_GC,
            TOKEN_METASPACE,
            TOKEN_DURATION,
            TOKEN_RIGHT_BRACKET,
    };

    private static class LineAssembler {
        AbstractPreUnifiedGCLogParser parser;
        private final String line;
        private int cursor = 0;
        private GCLogToken lastToken;

        public LineAssembler(AbstractPreUnifiedGCLogParser parser, String line) {
            this.parser = parser;
            this.line = line;
        }

        private void doAssemble() {
            if (checkNextToken(TOKEN_LINE_FULL_SENTENCE)) {
                parser.pushSentenceToParse(List.of(lastToken));
            }
            while (!endOfLine()) {
                if (checkNextToken(TOKEN_EMBEDDED_SENTENCE)) {
                    parser.pushSentenceToParse(List.of(lastToken));
                    continue;
                }

                List<GCLogToken> sentence = null;
                for (GCLogTokenType tokenType : GC_TRACETIME_BEGIN_TOKEN_TYPES) {
                    if (sentence == null && tokenType == TOKEN_GCID) {
                        // at least one of -XX:+PrintGCDateStamps and -XX:+PrintGCTimeStamps is needed, gcid can not
                        // occur at beginning
                        continue;
                    }
                    if (checkNextToken(tokenType)) {
                        if (sentence == null) {
                            sentence = new ArrayList<>();
                        }
                        sentence.add(lastToken);
                    }
                }
                if (sentence != null) {
                    parser.pushSentenceToAssemble(sentence);
                    parser.pushSentenceToParse(sentence);
                    continue;
                }

                for (GCLogTokenType tokenType : GC_TRACETIME_END_TOKEN_TYPES) {
                    if (!checkNextToken(tokenType)) {
                        continue;
                    }
                    if (sentence == null) {
                        if (tokenType == TOKEN_SAFEPOINT || tokenType == TOKEN_REFERENCE_GC) {
                            // They are always printed together with timestamp
                            sentence = parser.pollSentenceToAssemble();
                        } else {
                            // Filter out some invalid sentence. This may be useful when
                            // the log is using unsupported options
                            do {
                                sentence = parser.pollSentenceToAssemble();
                            } while (sentence != null &&
                                    sentence.get(sentence.size() - 1).getType() != TOKEN_GC_TRACETIME_TITLE);
                        }
                        if (sentence == null) {
                            break; // log is incomplete?
                        }
                    }
                    sentence.add(lastToken);
                }
                if (sentence != null) {
                    continue;
                }
                if (checkNextToken(TOKEN_COLON_SPACE)) {
                    // HACK: when we find ": " and this can not match any other token type, it is likely
                    // that we meet a concurrent problem of gclog parsing. Just swallow this token.
                    continue;
                }
                // some logs like -XX:+PrintTenuringDistribution, -XX:+PrintHeapAtGC are ignored
                break;
            }
        }

        private boolean endOfLine() {
            return cursor >= line.length();
        }

        private boolean checkNextToken(GCLogTokenType tokenType) {
            if (endOfLine()) {
                return false;
            }
            GCLogToken token = tokenType.parseNextToken(line, cursor, parser);
            if (token != null) {
                token.setType(tokenType);
                cursor = token.end;
                lastToken = token;
            }
            return token != null;
        }
    }

    protected static void copyPhaseDataToStart(GCEvent phaseStart, GCEvent phase) {
        if (phaseStart.getDuration() == Constant.UNKNOWN_DOUBLE) {
            if (phase.getDuration() != Constant.UNKNOWN_DOUBLE) {
                phaseStart.setDuration(phase.getDuration());
            } else if (phase.getStartTime() != Constant.UNKNOWN_DOUBLE && phaseStart.getStartTime() != Constant.UNKNOWN_DOUBLE) {
                phaseStart.setDuration(phase.getStartTime() - phaseStart.getStartTime());
            }
        }
        if (phase.getMemoryItems() != null && phaseStart.getMemoryItems() == null) {
            phaseStart.setMemoryItems(phase.getMemoryItems());
        }
        if (phase.getCpuTime() != null && phaseStart.getCpuTime() == null) {
            phaseStart.setCpuTime(phase.getCpuTime());
        }
    }

    protected static final ParseRule commandLineRule = new PrefixAndValueParseRule("CommandLine flags:",
            ((parser, context, prefix, flags) -> parser.getModel().setVmOptions(new VmOptions(flags))));

    private LinkedList<GCEvent> eventsWaitingForCpuTime = new LinkedList<>();

    protected void pushIfWaitingForCpuTime(GCEvent event) {
        if (getCPUTimeGCEvent().contains(event.getEventType())) {
            eventsWaitingForCpuTime.offerLast(event);
        }
    }
    protected static final ParseRule cpuTimeRule = new PrefixAndValueParseRule(" [Times",
            ((parser, context, prefix, value) -> {
                LinkedList<GCEvent> queue = ((AbstractPreUnifiedGCLogParser)parser).eventsWaitingForCpuTime;
                while (!queue.isEmpty()) {
                    GCEvent event = queue.pollLast();
                    if (event.getCpuTime() == null) {
                        CpuTime cpuTime = GCLogUtil.parseCPUTime(value.substring(0, value.length() - 1));
                        event.setCpuTime(cpuTime);
                        return;
                    }
                }
            }));
}
