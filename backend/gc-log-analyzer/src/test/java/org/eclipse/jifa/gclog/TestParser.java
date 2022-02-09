/********************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.jifa.gclog;

import org.eclipse.jifa.common.listener.DefaultProgressListener;
import org.eclipse.jifa.gclog.model.*;
import org.eclipse.jifa.gclog.parser.*;
import org.eclipse.jifa.gclog.vo.GCCollectionResultItem;
import org.eclipse.jifa.gclog.vo.GCCollectorType;
import org.eclipse.jifa.gclog.vo.GCLogMetadata;
import org.eclipse.jifa.gclog.vo.GCLogStyle;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.eclipse.jifa.gclog.model.GCEvent.UNKNOWN_DOUBLE;
import static org.eclipse.jifa.gclog.model.GCEvent.UNKNOWN_INT;
import static org.eclipse.jifa.gclog.vo.GCSpecialSituation.TO_SPACE_EXHAUSTED;
import static org.eclipse.jifa.gclog.vo.HeapGeneration.*;
import static org.eclipse.jifa.gclog.TestUtil.stringToBufferedReader;

public class TestParser {

    public static final double DELTA = 1e-6;

    @Test
    public void testJDK11G1Parse() throws Exception {
        String log =
                "[0.015s][info][gc,heap] Heap region size: 1M\n" +
                        "[0.017s][info][gc     ] Using G1\n" +
                        "[0.017s][info][gc,heap,coops] Heap address: 0x00000007fc000000, size: 64 MB, Compressed Oops mode: Zero based, Oop shift amount: 3\n" +
                        "[0.050s][info   ][gc           ] Periodic GC enabled with interval 100 ms" +
                        "\n" +
                        "[1.000s][info][gc,start     ] GC(0) Pause Young (Normal) (Metadata GC Threshold)\n" +
                        "[1.000s][info][gc,task      ] GC(0) Using 8 workers of 8 for evacuation\n" +
                        "[1.010s][info][gc           ] GC(0) To-space exhausted\n" +
                        "[1.010s][info][gc,phases    ] GC(0)   Pre Evacuate Collection Set: 0.0ms\n" +
                        "[1.010s][info][gc,phases    ] GC(0)   Evacuate Collection Set: 9.5ms\n" +
                        "[1.010s][info][gc,phases    ] GC(0)   Post Evacuate Collection Set: 0.6ms\n" +
                        "[1.010s][info][gc,phases    ] GC(0)   Other: 0.5ms\n" +
                        "[1.010s][info][gc,heap      ] GC(0) Eden regions: 19->0(33)\n" +
                        "[1.010s][info][gc,heap      ] GC(0) Survivor regions: 0->3(3)\n" +
                        "[1.010s][info][gc,heap      ] GC(0) Old regions: 0->2\n" +
                        "[1.010s][info][gc,heap      ] GC(0) Humongous regions: 4->3\n" +
                        "[1.010s][info][gc,metaspace ] GC(0) Metaspace: 20679K->20679K(45056K)\n" +
                        "[1.010s][info][gc           ] GC(0) Pause Young (Concurrent Start) (Metadata GC Threshold) 19M->4M(64M) 10.709ms\n" +
                        "[1.010s][info][gc,cpu       ] GC(0) User=0.02s Sys=0.01s Real=0.01s\n" +
                        "\n" +
                        "[3.000s][info][gc           ] GC(1) Concurrent Cycle\n" +
                        "[3.000s][info][gc,marking   ] GC(1) Concurrent Clear Claimed Marks\n" +
                        "[3.000s][info][gc,marking   ] GC(1) Concurrent Clear Claimed Marks 0.057ms\n" +
                        "[3.000s][info][gc,marking   ] GC(1) Concurrent Scan Root Regions\n" +
                        "[3.002s][info][gc,marking   ] GC(1) Concurrent Scan Root Regions 2.709ms\n" +
                        "[3.002s][info][gc,marking   ] GC(1) Concurrent Mark (3.002s)\n" +
                        "[3.002s][info][gc,marking   ] GC(1) Concurrent Mark From Roots\n" +
                        "[3.002s][info][gc,task      ] GC(1) Using 2 workers of 2 for marking\n" +
                        "[3.005s][info][gc,marking   ] GC(1) Concurrent Mark From Roots 3.109ms\n" +
                        "[3.005s][info][gc,marking   ] GC(1) Concurrent Preclean\n" +
                        "[3.005s][info][gc,marking   ] GC(1) Concurrent Preclean 0.040ms\n" +
                        "[3.005s][info][gc,marking   ] GC(1) Concurrent Mark (2.391s, 2.394s) 3.251ms\n" +
                        "[3.005s][info][gc,start     ] GC(1) Pause Remark\n" +
                        "[3.005s][info][gc,stringtable] GC(1) Cleaned string and symbol table, strings: 9850 processed, 0 removed, symbols: 69396 processed, 29 removed\n" +
                        "[3.008s][info][gc            ] GC(1) Pause Remark 5M->5M(64M) 2.381ms\n" +
                        "[3.008s][info][gc,cpu        ] GC(1) User=0.01s Sys=0.00s Real=0.01s\n" +
                        "[3.008s][info][gc,marking    ] GC(1) Concurrent Rebuild Remembered Sets\n" +
                        "[3.010s][info][gc,marking    ] GC(1) Concurrent Rebuild Remembered Sets 2.151ms\n" +
                        "[3.010s][info][gc,start      ] GC(1) Pause Cleanup\n" +
                        "[3.010s][info][gc            ] GC(1) Pause Cleanup 6M->6M(64M) 0.094ms\n" +
                        "[3.010s][info][gc,cpu        ] GC(1) User=0.00s Sys=0.00s Real=0.00s\n" +
                        "[3.010s][info][gc,marking    ] GC(1) Concurrent Cleanup for Next Mark\n" +
                        "[3.012s][info][gc,marking    ] GC(1) Concurrent Cleanup for Next Mark 2.860ms\n" +
                        "[3.012s][info][gc            ] GC(1) Concurrent Cycle 14.256ms\n" +
                        "\n" +
                        "[7.055s][info   ][gc,task       ] GC(2) Using 8 workers of 8 for full compaction\n" +
                        "[7.055s][info   ][gc,start      ] GC(2) Pause Full (G1 Evacuation Pause)\n" +
                        "[7.056s][info   ][gc,phases,start] GC(2) Phase 1: Mark live objects\n" +
                        "[7.058s][info   ][gc,stringtable ] GC(2) Cleaned string and symbol table, strings: 1393 processed, 0 removed, symbols: 17391 processed, 0 removed\n" +
                        "[7.058s][info   ][gc,phases      ] GC(2) Phase 1: Mark live objects 2.650ms\n" +
                        "[7.058s][info   ][gc,phases,start] GC(2) Phase 2: Prepare for compaction\n" +
                        "[7.061s][info   ][gc,phases      ] GC(2) Phase 2: Prepare for compaction 2.890ms\n" +
                        "[7.061s][info   ][gc,phases,start] GC(2) Phase 3: Adjust pointers\n" +
                        "[7.065s][info   ][gc,phases      ] GC(2) Phase 3: Adjust pointers 3.890ms\n" +
                        "[7.065s][info   ][gc,phases,start] GC(2) Phase 4: Compact heap\n" +
                        "[7.123s][info   ][gc,phases      ] GC(2) Phase 4: Compact heap 57.656ms\n" +
                        "[7.123s][info   ][gc,heap        ] GC(2) Eden regions: 0->0(680)\n" +
                        "[7.123s][info   ][gc,heap        ] GC(2) Survivor regions: 0->0(85)\n" +
                        "[7.123s][info   ][gc,heap        ] GC(2) Old regions: 1700->1089\n" +
                        "[7.123s][info   ][gc,heap        ] GC(2) Humongous regions: 0->0\n" +
                        "[7.123s][info   ][gc,metaspace   ] GC(2) Metaspace: 3604K->3604K(262144K)\n" +
                        "[7.123s][info   ][gc             ] GC(2) Pause Full (G1 Evacuation Pause) 1700M->1078M(1700M) 67.806ms\n" +
                        "[7.123s][info   ][gc,cpu         ] GC(2) User=0.33s Sys=0.00s Real=0.07s";
        JDK11G1GCLogParser parser = (JDK11G1GCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));
        G1GCModel model = (G1GCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        // assert parsing success
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 3);

        // assert model info
        Assert.assertEquals(model.getStartTime(), 0.0 * 1000, DELTA);
        Assert.assertEquals(model.getEndTime(), 7.123 * 1000, DELTA);
        Assert.assertEquals(model.getCollectorType(), GCCollectorType.G1);

        Assert.assertEquals(model.getHeapRegionSize(), 1024);
        Assert.assertNull(model.getVmOptions());
        Assert.assertEquals(model.getParallelThread(), 8);
        Assert.assertEquals(model.getConcurrentThread(), 2);

        // assert events correct
        List<GCEvent> event = model.getGcEvents();
        GCEvent youngGC = event.get(0);
        Assert.assertEquals(youngGC.getGcid(), 0);
        Assert.assertEquals(youngGC.getSpecialSituations().get(0), TO_SPACE_EXHAUSTED);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getStartTime(), 1.0 * 1000, DELTA);
        Assert.assertEquals(youngGC.getPause(), 10.709, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 10.709, DELTA);
        Assert.assertEquals(youngGC.getCause(), "Metadata GC Threshold");
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 0.01 * 1000, DELTA);
        Assert.assertEquals(youngGC.getPhases().size(), 4);
        Assert.assertEquals(youngGC.getPhases().get(1).getEventType(), GCEventType.G1_COLLECT_EVACUATION);
        Assert.assertEquals(youngGC.getPhases().get(1).getDuration(), 9.5, DELTA);
        Assert.assertEquals(youngGC.getCollectionResult().getItems().get(1)
                , new GCCollectionResultItem(SURVIVOR, 0 * 1024, 3 * 1024, 3 * 1024));
        Assert.assertEquals(youngGC.getCollectionResult().getItems().get(4)
                , new GCCollectionResultItem(METASPACE, 20679, 20679, 45056));
        Assert.assertEquals(youngGC.getCollectionResult().getSummary()
                , new GCCollectionResultItem(TOTAL, 19 * 1024, 4 * 1024, 64 * 1024));

        GCEvent concurrentMark = event.get(1);
        Assert.assertEquals(concurrentMark.getGcid(), 1);
        Assert.assertEquals(concurrentMark.getEventType(), GCEventType.G1_CONCURRENT_CYCLE);
        Assert.assertEquals(concurrentMark.getDuration(), 14.256, DELTA);
        Assert.assertEquals(concurrentMark.getPause(), 2.381 + 0.094, DELTA);
        Assert.assertEquals(concurrentMark.getPhases().get(0).getEventType(), GCEventType.G1_CONCURRENT_CLEAR_CLAIMED_MARKS);
        Assert.assertEquals(concurrentMark.getPhases().get(0).getDuration(), 0.057, DELTA);

        GCEvent fullGC = event.get(2);
        Assert.assertEquals(fullGC.getGcid(), 2);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getStartTime(), 7.055 * 1000, DELTA);
        Assert.assertEquals(fullGC.getPause(), 67.806, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 67.806, DELTA);
        Assert.assertEquals(fullGC.getCause(), "G1 Evacuation Pause");
        Assert.assertEquals(fullGC.getPhases().size(), 4);
        Assert.assertEquals(fullGC.getPhases().get(3).getEventType(), GCEventType.G1_COMPACT_HEAP);
        Assert.assertEquals(fullGC.getPhases().get(3).getDuration(), 57.656, DELTA);
    }

    @Test
    public void testJDK11G1ParserDetectHeapRegionSize() throws Exception {
        String log = "[3.865s][info][gc,start      ] GC(14) Pause Young (Normal) (G1 Evacuation Pause)\n" +
                "[3.865s][info][gc,task       ] GC(14) Using 2 workers of 2 for evacuation\n" +
                "[3.982s][info][gc,phases     ] GC(14)   Pre Evacuate Collection Set: 0.0ms\n" +
                "[3.982s][info][gc,phases     ] GC(14)   Evacuate Collection Set: 116.2ms\n" +
                "[3.982s][info][gc,phases     ] GC(14)   Post Evacuate Collection Set: 0.3ms\n" +
                "[3.982s][info][gc,phases     ] GC(14)   Other: 0.2ms\n" +
                "[3.982s][info][gc,heap       ] GC(14) Eden regions: 5->0(5)\n" +
                "[3.982s][info][gc,heap       ] GC(14) Survivor regions: 1->1(1)\n" +
                "[3.982s][info][gc,heap       ] GC(14) Old regions: 32->37\n" +
                "[3.982s][info][gc,heap       ] GC(14) Humongous regions: 2->2\n" +
                "[3.982s][info][gc,metaspace  ] GC(14) Metaspace: 21709K->21707K(1069056K)\n" +
                "[3.982s][info][gc            ] GC(14) Pause Young (Normal) (G1 Evacuation Pause) 637M->630M(2048M) 116.771ms";
        JDK11G1GCLogParser parser = new JDK11G1GCLogParser();
        parser.setMetadata(new GCLogMetadata(GCCollectorType.G1, GCLogStyle.UNIFIED_STYLE));
        G1GCModel model = (G1GCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        // should infer region size 16m
        Assert.assertEquals(model.getHeapRegionSize(), 16 * 1024);
        Assert.assertEquals(model.getGcEvents().get(0).getCollectionResult().getItems().get(2)
                , new GCCollectionResultItem(OLD, 32 * 16 * 1024, 37 * 16 * 1024, UNKNOWN_INT));
        Assert.assertEquals(model.getGcEvents().get(0).getCollectionResult().getItems().get(4)
                , new GCCollectionResultItem(METASPACE, 21709, 21707, 1069056));
    }

    @Test
    public void testJDK11ParseDecoration() throws Exception {
        String log = "[2021-05-06T11:25:16.508+0800][info][gc           ] GC(0) Pause Young (Concurrent Start) (Metadata GC Threshold)\n" +
                "[2021-05-06T11:25:16.510+0800][info][gc           ] GC(1) Pause Young (Concurrent Start) (Metadata GC Threshold)\n";
        JDK11G1GCLogParser parser = new JDK11G1GCLogParser();
        parser.setMetadata(new GCLogMetadata(GCCollectorType.G1, GCLogStyle.UNIFIED_STYLE));
        GCModel model = parser.parse(stringToBufferedReader(log));
        Assert.assertEquals(model.getReferenceTimestamp(), 1620271516508d, DELTA);
        Assert.assertEquals(model.getGcEvents().get(1).getStartTime(), 2, DELTA);

        log = "[1000000000800ms][info][gc           ] GC(0) Pause Young (Concurrent Start) (Metadata GC Threshold)\n" +
                "[1000000000802ms][info][gc           ] GC(1) Pause Young (Concurrent Start) (Metadata GC Threshold)\n";
        parser = new JDK11G1GCLogParser();
        parser.setMetadata(new GCLogMetadata(GCCollectorType.G1, GCLogStyle.UNIFIED_STYLE));
        model = parser.parse(stringToBufferedReader(log));
        Assert.assertEquals(model.getReferenceTimestamp(), 1000000000800D, DELTA);
        Assert.assertEquals(model.getGcEvents().get(1).getStartTime(), 2, DELTA);
    }

    @Test
    public void testJDK11ZGCParser() throws Exception {
        String log =
                "[7.000s] GC(374) Garbage Collection (Proactive)\n" +
                        "[7.006s] GC(374) Pause Mark Start 4.459ms\n" +
                        "[7.312s] GC(374) Concurrent Mark 306.720ms\n" +
                        "[7.312s] GC(374) Pause Mark End 0.606ms\n" +
                        "[7.313s] GC(374) Concurrent Process Non-Strong References 1.290ms\n" +
                        "[7.314s] GC(374) Concurrent Reset Relocation Set 0.550ms\n" +
                        "[7.314s] GC(374) Concurrent Destroy Detached Pages 0.001ms\n" +
                        "[7.316s] GC(374) Concurrent Select Relocation Set 2.418ms\n" +
                        "[7.321s] GC(374) Concurrent Prepare Relocation Set 5.719ms\n" +
                        "[7.324s] GC(374) Pause Relocate Start 3.791ms\n" +
                        "[7.356s] GC(374) Concurrent Relocate 32.974ms\n" +
                        "[7.356s] GC(374) Load: 1.68/1.99/2.04\n" +
                        "[7.356s] GC(374) MMU: 2ms/0.0%, 5ms/0.0%, 10ms/0.0%, 20ms/0.0%, 50ms/0.0%, 100ms/0.0%\n" +
                        "[7.356s] GC(374) Mark: 8 stripe(s), 2 proactive flush(es), 1 terminate flush(es), 0 completion(s), 0 continuation(s)\n" +
                        "[7.356s] GC(374) Relocation: Successful, 359M relocated\n" +
                        "[7.356s] GC(374) NMethods: 21844 registered, 609 unregistered\n" +
                        "[7.356s] GC(374) Metaspace: 125M used, 128M capacity, 128M committed, 130M reserved\n" +
                        "[7.356s] GC(374) Soft: 18634 encountered, 0 discovered, 0 enqueued\n" +
                        "[7.356s] GC(374) Weak: 56186 encountered, 18454 discovered, 3112 enqueued\n" +
                        "[7.356s] GC(374) Final: 64 encountered, 16 discovered, 7 enqueued\n" +
                        "[7.356s] GC(374) Phantom: 1882 encountered, 1585 discovered, 183 enqueued\n" +
                        "[7.356s] GC(374)                Mark Start          Mark End        Relocate Start      Relocate End           High               Low\n" +
                        "[7.356s] GC(374)  Capacity:    40960M (100%)      40960M (100%)      40960M (100%)      40960M (100%)      40960M (100%)      40960M (100%)\n" +
                        "[7.356s] GC(374)   Reserve:       96M (0%)           96M (0%)           96M (0%)           96M (0%)           96M (0%)           96M (0%)\n" +
                        "[7.356s] GC(374)      Free:    35250M (86%)       35210M (86%)       35964M (88%)       39410M (96%)       39410M (96%)       35210M (86%)\n" +
                        "[7.356s] GC(374)      Used:     5614M (14%)        5654M (14%)        4900M (12%)        1454M (4%)         5654M (14%)        1454M (4%)\n" +
                        "[7.356s] GC(374)      Live:         -              1173M (3%)         1173M (3%)         1173M (3%)             -                  -\n" +
                        "[7.356s] GC(374) Allocated:         -                40M (0%)           40M (0%)          202M (0%)             -                  -\n" +
                        "[7.356s] GC(374)   Garbage:         -              4440M (11%)        3686M (9%)          240M (1%)             -                  -\n" +
                        "[7.356s] GC(374) Reclaimed:         -                  -               754M (2%)         4200M (10%)            -                  -\n" +
                        "[7.356s] GC(374) Garbage Collection (Proactive) 5614M(14%)->1454M(4%)\n" +
                        "[7.555s] === Garbage Collection Statistics =======================================================================================================================\n" +
                        "[7.555s]                                                              Last 10s              Last 10m              Last 10h                Total\n" +
                        "[7.555s]                                                              Avg / Max             Avg / Max             Avg / Max             Avg / Max\n" +
                        "[7.555s]   Collector: Garbage Collection Cycle                    362.677 / 362.677     365.056 / 529.211     315.229 / 868.961     315.229 / 868.961     ms\n" +
                        "[7.555s]  Contention: Mark Segment Reset Contention                     0 / 0                 1 / 106               0 / 238               0 / 238         ops/s\n" +
                        "[7.555s]  Contention: Mark SeqNum Reset Contention                      0 / 0                 0 / 1                 0 / 1                 0 / 1           ops/s\n" +
                        "[7.555s]  Contention: Relocation Contention                             1 / 10                0 / 52                0 / 87                0 / 87          ops/s\n" +
                        "[7.555s]    Critical: Allocation Stall                              0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Critical: Allocation Stall                                  0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s\n" +
                        "[7.555s]    Critical: GC Locker Stall                               0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Critical: GC Locker Stall                                   0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s\n" +
                        "[7.555s]      Memory: Allocation Rate                                  85 / 210             104 / 826              54 / 2628             54 / 2628        MB/s\n" +
                        "[7.555s]      Memory: Heap Used After Mark                           5654 / 5654           5727 / 6416           5588 / 14558          5588 / 14558       MB\n" +
                        "[7.555s]      Memory: Heap Used After Relocation                     1454 / 1454           1421 / 1814           1224 / 2202           1224 / 2202        MB\n" +
                        "[7.555s]      Memory: Heap Used Before Mark                          5614 / 5614           5608 / 6206           5503 / 14268          5503 / 14268       MB\n" +
                        "[7.555s]      Memory: Heap Used Before Relocation                    4900 / 4900           4755 / 5516           4665 / 11700          4665 / 11700       MB\n" +
                        "[7.555s]      Memory: Out Of Memory                                     0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s\n" +
                        "[7.555s]      Memory: Page Cache Flush                                  0 / 0                 0 / 0                 0 / 0                 0 / 0           MB/s\n" +
                        "[7.555s]      Memory: Page Cache Hit L1                                49 / 105              53 / 439              27 / 1353             27 / 1353        ops/s\n" +
                        "[7.555s]      Memory: Page Cache Hit L2                                 0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s\n" +
                        "[7.555s]      Memory: Page Cache Miss                                   0 / 0                 0 / 0                 0 / 551               0 / 551         ops/s\n" +
                        "[7.555s]      Memory: Undo Object Allocation Failed                     0 / 0                 0 / 0                 0 / 8                 0 / 8           ops/s\n" +
                        "[7.555s]      Memory: Undo Object Allocation Succeeded                  1 / 10                0 / 52                0 / 87                0 / 87          ops/s\n" +
                        "[7.555s]      Memory: Undo Page Allocation                              0 / 0                 0 / 1                 0 / 16                0 / 16          ops/s\n" +
                        "[7.555s]       Phase: Concurrent Destroy Detached Pages             0.001 / 0.001         0.001 / 0.001         0.001 / 0.012         0.001 / 0.012       ms\n" +
                        "[7.555s]       Phase: Concurrent Mark                             306.720 / 306.720     303.979 / 452.112     255.790 / 601.718     255.790 / 601.718     ms\n" +
                        "[7.555s]       Phase: Concurrent Mark Continue                      0.000 / 0.000         0.000 / 0.000       189.372 / 272.607     189.372 / 272.607     ms\n" +
                        "[7.555s]       Phase: Concurrent Prepare Relocation Set             5.719 / 5.719         6.314 / 14.492        6.150 / 36.507        6.150 / 36.507      ms\n" +
                        "[7.555s]       Phase: Concurrent Process Non-Strong References      1.290 / 1.290         1.212 / 1.657         1.179 / 2.334         1.179 / 2.334       ms\n" +
                        "[7.555s]       Phase: Concurrent Relocate                          32.974 / 32.974       35.964 / 86.278       31.599 / 101.253      31.599 / 101.253     ms\n" +
                        "[7.555s]       Phase: Concurrent Reset Relocation Set               0.550 / 0.550         0.615 / 0.937         0.641 / 5.411         0.641 / 5.411       ms\n" +
                        "[7.555s]       Phase: Concurrent Select Relocation Set              2.418 / 2.418         2.456 / 3.131         2.509 / 4.753         2.509 / 4.753       ms\n" +
                        "[7.555s]       Phase: Pause Mark End                                0.606 / 0.606         0.612 / 0.765         0.660 / 5.543         0.660 / 5.543       ms\n" +
                        "[7.555s]       Phase: Pause Mark Start                              4.459 / 4.459         4.636 / 6.500         6.160 / 547.572       6.160 / 547.572     ms\n" +
                        "[7.555s]       Phase: Pause Relocate Start                          3.791 / 3.791         3.970 / 5.443         4.047 / 8.993         4.047 / 8.993       ms\n" +
                        "[7.555s]    Subphase: Concurrent Mark                             306.253 / 306.593     303.509 / 452.030     254.759 / 601.564     254.759 / 601.564     ms\n" +
                        "[7.555s]    Subphase: Concurrent Mark Idle                          1.069 / 1.110         1.527 / 18.317        1.101 / 18.317        1.101 / 18.317      ms\n" +
                        "[7.555s]    Subphase: Concurrent Mark Try Flush                     0.554 / 0.685         0.872 / 18.247        0.507 / 18.247        0.507 / 18.247      ms\n" +
                        "[7.555s]    Subphase: Concurrent Mark Try Terminate                 0.978 / 1.112         1.386 / 18.318        0.998 / 18.318        0.998 / 18.318      ms\n" +
                        "[7.555s]    Subphase: Concurrent References Enqueue                 0.007 / 0.007         0.008 / 0.013         0.009 / 0.037         0.009 / 0.037       ms\n" +
                        "[7.555s]    Subphase: Concurrent References Process                 0.628 / 0.628         0.638 / 1.153         0.596 / 1.789         0.596 / 1.789       ms\n" +
                        "[7.555s]    Subphase: Concurrent Weak Roots                         0.497 / 0.618         0.492 / 0.670         0.502 / 1.001         0.502 / 1.001       ms\n" +
                        "[7.555s]    Subphase: Concurrent Weak Roots JNIWeakHandles          0.001 / 0.001         0.001 / 0.006         0.001 / 0.007         0.001 / 0.007       ms\n" +
                        "[7.555s]    Subphase: Concurrent Weak Roots StringTable             0.476 / 0.492         0.402 / 0.523         0.400 / 0.809         0.400 / 0.809       ms\n" +
                        "[7.555s]    Subphase: Concurrent Weak Roots VMWeakHandles           0.105 / 0.123         0.098 / 0.150         0.103 / 0.903         0.103 / 0.903       ms\n" +
                        "[7.555s]    Subphase: Pause Mark Try Complete                       0.000 / 0.000         0.001 / 0.004         0.156 / 1.063         0.156 / 1.063       ms\n" +
                        "[7.555s]    Subphase: Pause Remap TLABS                             0.040 / 0.040         0.046 / 0.073         0.050 / 0.140         0.050 / 0.140       ms\n" +
                        "[7.555s]    Subphase: Pause Retire TLABS                            0.722 / 0.722         0.835 / 1.689         0.754 / 1.919         0.754 / 1.919       ms\n" +
                        "[7.555s]    Subphase: Pause Roots                                   1.581 / 2.896         1.563 / 3.787         1.592 / 545.902       1.592 / 545.902     ms\n" +
                        "[7.555s]    Subphase: Pause Roots ClassLoaderDataGraph              1.461 / 2.857         1.549 / 3.782         1.554 / 6.380         1.554 / 6.380       ms\n" +
                        "[7.555s]    Subphase: Pause Roots CodeCache                         1.130 / 1.312         0.999 / 1.556         0.988 / 6.322         0.988 / 6.322       ms\n" +
                        "[7.555s]    Subphase: Pause Roots JNIHandles                        0.010 / 0.015         0.004 / 0.028         0.005 / 1.709         0.005 / 1.709       ms\n" +
                        "[7.555s]    Subphase: Pause Roots JNIWeakHandles                    0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Roots JRFWeak                           0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Roots JVMTIExport                       0.001 / 0.001         0.001 / 0.003         0.001 / 0.005         0.001 / 0.005       ms\n" +
                        "[7.555s]    Subphase: Pause Roots JVMTIWeakExport                   0.001 / 0.001         0.001 / 0.001         0.001 / 0.012         0.001 / 0.012       ms\n" +
                        "[7.555s]    Subphase: Pause Roots Management                        0.002 / 0.002         0.003 / 0.006         0.003 / 0.305         0.003 / 0.305       ms\n" +
                        "[7.555s]    Subphase: Pause Roots ObjectSynchronizer                0.000 / 0.000         0.000 / 0.001         0.000 / 0.006         0.000 / 0.006       ms\n" +
                        "[7.555s]    Subphase: Pause Roots Setup                             0.474 / 0.732         0.582 / 1.791         0.526 / 2.610         0.526 / 2.610       ms\n" +
                        "[7.555s]    Subphase: Pause Roots StringTable                       0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Roots SystemDictionary                  0.028 / 0.039         0.027 / 0.075         0.033 / 2.777         0.033 / 2.777       ms\n" +
                        "[7.555s]    Subphase: Pause Roots Teardown                          0.003 / 0.005         0.003 / 0.009         0.003 / 0.035         0.003 / 0.035       ms\n" +
                        "[7.555s]    Subphase: Pause Roots Threads                           0.262 / 1.237         0.309 / 1.791         0.358 / 544.610       0.358 / 544.610     ms\n" +
                        "[7.555s]    Subphase: Pause Roots Universe                          0.003 / 0.004         0.003 / 0.009         0.003 / 0.047         0.003 / 0.047       ms\n" +
                        "[7.555s]    Subphase: Pause Roots VMWeakHandles                     0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots                              0.000 / 0.003         0.000 / 0.007         0.000 / 0.020         0.000 / 0.020       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots JFRWeak                      0.001 / 0.001         0.001 / 0.002         0.001 / 0.012         0.001 / 0.012       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots JNIWeakHandles               0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots JVMTIWeakExport              0.001 / 0.001         0.001 / 0.001         0.001 / 0.008         0.001 / 0.008       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots Setup                        0.000 / 0.000         0.000 / 0.000         0.000 / 0.001         0.000 / 0.001       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots StringTable                  0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots SymbolTable                  0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots Teardown                     0.001 / 0.001         0.001 / 0.001         0.001 / 0.015         0.001 / 0.015       ms\n" +
                        "[7.555s]    Subphase: Pause Weak Roots VMWeakHandles                0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms\n" +
                        "[7.555s]      System: Java Threads                                    911 / 911             910 / 911             901 / 913             901 / 913         threads\n" +
                        "[7.555s] =========================================================================================================================================================\n" +
                        "[7.777s] Allocation Stall (ThreadPoolTaskScheduler-1) 0.204ms\n" +
                        "[7.888s] Allocation Stall (NioProcessor-2) 0.391ms";
        JDK11ZGCLogParser parser = (JDK11ZGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));
        ZGCModel model = (ZGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);

        Assert.assertEquals(model.getGcEvents().size(), 1);
        GCEvent gc = model.getGcEvents().get(0);
        Assert.assertEquals(gc.getGcid(), 374);
        Assert.assertEquals(gc.getStartTime(), 7000, DELTA);
        Assert.assertEquals(gc.getEndTime(), 7356, DELTA);
        Assert.assertEquals(gc.getDuration(), 356, DELTA);
        Assert.assertEquals(gc.getEventType(), GCEventType.ZGC_GARBAGE_COLLECTION);
        Assert.assertEquals(gc.getCause(), "Proactive");
        Assert.assertEquals(gc.getLastPhaseOfType(GCEventType.ZGC_PAUSE_MARK_START).getEndTime(), 7006, DELTA);
        Assert.assertEquals(gc.getLastPhaseOfType(GCEventType.ZGC_PAUSE_MARK_START).getDuration(), 4.459, DELTA);
        Assert.assertEquals(gc.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getTotal(), 128 * 1024);
        Assert.assertEquals(gc.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getPostUsed(), 125 * 1024);
        Assert.assertEquals(gc.getCollectionResult().getSummary().getTotal(), 40960 * 1024);
        Assert.assertEquals(gc.getCollectionResult().getSummary().getPreUsed(), 5614 * 1024);
        Assert.assertEquals(gc.getCollectionResult().getSummary().getPostUsed(), 1454 * 1024);
        Assert.assertEquals(gc.getAllocation(), 202 * 1024);
        Assert.assertEquals(gc.getReclamation(), 4200 * 1024);

        List<Map<String, ZGCModel.ZStatistics>> statistics = model.getStatistics();
        Assert.assertEquals(statistics.size(), 1);
        Assert.assertEquals(72, statistics.get(0).size());
        Assert.assertEquals(statistics.get(0).get("Collector: Garbage Collection Cycle ms").getUptime(), 7555, DELTA);
        Assert.assertEquals(statistics.get(0).get("System: Java Threads threads").getMax10s(), 911, DELTA);
        Assert.assertEquals(statistics.get(0).get("System: Java Threads threads").getMax10h(), 913, DELTA);
        List<GCEvent> allocationStalls = model.getAllocationStalls();
        Assert.assertEquals(allocationStalls.size(), 2);
        Assert.assertEquals(allocationStalls.get(1).getEndTime(), 7888, DELTA);
        Assert.assertEquals(allocationStalls.get(1).getDuration(), 0.391, DELTA);

    }

    @Test
    public void testJDK8CMSParser() throws Exception {
        String log =
                "OpenJDK 64-Bit Server VM (25.212-b469) for linux-amd64 JRE (1.8.0_212-b469), built on Jun 16 2019 15:54:49 by \"admin\" with gcc 4.8.2\n" +
                        "Memory: 4k page, physical 8388608k(5632076k free), swap 0k(0k free)\n" +
                        "610.956: [Full GC (Heap Dump Initiated GC) 610.956: [CMS[YG occupancy: 1212954 K (1843200 K)]611.637: [weak refs processing, 0.0018945 secs]611.639: [class unloading, 0.0454119 secs]611.684: [scrub symbol table, 0.0248340 secs]611.709: [scrub string table, 0.0033967 secs]: 324459K->175339K(3072000K), 1.0268069 secs] 1537414K->1388294K(4915200K), [Metaspace: 114217K->113775K(1153024K)], 1.0277002 secs] [Times: user=1.71 sys=0.05, real=1.03 secs]\n" +
                        "674.686: [GC (Allocation Failure) 674.687: [ParNew: 1922432K->174720K(1922432K), 0.1691241 secs] 3557775K->1858067K(4019584K), 0.1706065 secs] [Times: user=0.54 sys=0.04, real=0.17 secs]\n" +
                        "675.110: Total time for which application threads were stopped: 0.0001215 seconds, Stopping threads took: 0.0000271 seconds\n" +
                        "675.111: Application time: 0.0170944 seconds\n" +
                        "675.164: [GC (CMS Initial Mark) [1 CMS-initial-mark: 1683347K(2097152K)] 1880341K(4019584K), 0.0714398 secs] [Times: user=0.19 sys=0.05, real=0.07 secs]" +
                        "675.461: [CMS-concurrent-mark-start]\n" +
                        "705.287: [GC (Allocation Failure) 705.288: [ParNew: 1922432K->174720K(1922432K), 0.2481441 secs] 3680909K->2051729K(4019584K), 0.2502404 secs] [Times: user=0.93 sys=0.10, real=0.25 secs]\n" +
                        "709.876: [CMS-concurrent-mark: 17.528/34.415 secs] [Times: user=154.39 sys=4.20, real=34.42 secs]\n" +
                        "709.959: [CMS-concurrent-preclean-start]\n" +
                        "710.570: [CMS-concurrent-preclean: 0.576/0.611 secs] [Times: user=3.08 sys=0.05, real=0.69 secs]\n" +
                        "710.571: [CMS-concurrent-abortable-preclean-start]\n" +
                        "715.691: [GC (Allocation Failure) 715.692: [ParNew: 1922432K->174720K(1922432K), 0.1974709 secs] 3799441K->2119132K(4019584K), 0.1992381 secs] [Times: user=0.61 sys=0.04, real=0.20 secs]\n" +
                        "717.759: [CMS-concurrent-abortable-preclean: 5.948/7.094 secs] [Times: user=32.21 sys=0.66, real=7.19 secs]\n" +
                        "717.792: [GC (CMS Final Remark) [YG occupancy: 438765 K (1922432 K)]717.792: [Rescan (parallel) , 0.1330457 secs]717.925: [weak refs processing, 0.0007103 secs]717.926: [class unloading, 0.2074917 secs]718.134: [scrub symbol table, 0.0751664 secs]718.209: [scrub string table, 0.0137015 secs][1 CMS-remark: 1944412K(2097152K)] 2383178K(4019584K), 0.4315000 secs] [Times: user=0.77 sys=0.01, real=0.43 secs]\n" +
                        "718.226: [CMS-concurrent-sweep-start]\n" +
                        "724.991: [GC (Allocation Failure) 724.992: [ParNew: 1922432K->174720K(1922432K), 0.2272846 secs] 3377417K->1710595K(4019584K), 0.2289948 secs] [Times: user=0.70 sys=0.01, real=0.23 secs]\n" +
                        "728.865: [CMS-concurrent-sweep: 8.279/10.639 secs] [Times: user=48.12 sys=1.21, real=10.64 secs]\n" +
                        "731.570: [CMS-concurrent-reset-start]\n" +
                        "731.806: [CMS-concurrent-reset: 0.205/0.237 secs] [Times: user=1.43 sys=0.04, real=0.34 secs]\n" +
                        "778.294: [GC (Allocation Failure) 778.295: [ParNew: 1922432K->163342K(1922432K), 0.2104952 secs] 3570857K->1917247K(4019584K), 0.2120639 secs] [Times: user=0.63 sys=0.00, real=0.21 secs]\n" +
                        "778.534: [GC (CMS Initial Mark) [1 CMS-initial-mark: 1753905K(2097152K)] 1917298K(4019584K), 0.0645754 secs] [Times: user=0.20 sys=0.01, real=0.06 secs]\n" +
                        "778.601: [CMS-concurrent-mark-start]\n" +
                        "792.762: [CMS-concurrent-mark: 11.404/14.161 secs] [Times: user=61.30 sys=2.27, real=14.17 secs]\n" +
                        "792.763: [CMS-concurrent-preclean-start]\n" +
                        "795.862: [CMS-concurrent-preclean: 2.148/3.100 secs] [Times: user=12.43 sys=0.91, real=3.10 secs]\n" +
                        "795.864: [CMS-concurrent-abortable-preclean-start]\n" +
                        "795.864: [CMS-concurrent-abortable-preclean: 0.000/0.000 secs] [Times: user=0.03 sys=0.00, real=0.00 secs]\n" +
                        "795.886: [GC (CMS Final Remark) [YG occupancy: 1619303 K (1922432 K)]795.887: [Rescan (parallel) , 0.2995817 secs]796.186: [weak refs processing, 0.0001985 secs]796.187: [class unloading, 0.1856105 secs]796.372: [scrub symbol table, 0.0734544 secs]796.446: [scrub string table, 0.0079670 secs][1 CMS-remark: 2048429K(2097152K)] 3667732K(4019584K), 0.5676600 secs] [Times: user=1.34 sys=0.01, real=0.57 secs]\n" +
                        "796.456: [CMS-concurrent-sweep-start]\n" +
                        "796.991: [GC (Allocation Failure) 796.992: [ParNew: 1922432K->1922432K(1922432K), 0.0000267 secs]796.992: [CMS797.832: [CMS-concurrent-sweep: 1.180/1.376 secs] [Times: user=3.42 sys=0.14, real=1.38 secs]\n" +
                        " (concurrent mode failure): 2034154K->1051300K(2097152K), 4.6146919 secs] 3956586K->1051300K(4019584K), [Metaspace: 296232K->296083K(1325056K)], 4.6165192 secs] [Times: user=4.60 sys=0.05, real=4.62 secs]\n" +
                        "813.396: [GC (Allocation Failure) 813.396: [ParNew813.404: [SoftReference, 4 refs, 0.0000260 secs]813.405: [WeakReference, 59 refs, 0.0000110 secs]813.406: [FinalReference, 1407 refs, 0.0025979 secs]813.407: [PhantomReference, 11 refs, 10 refs, 0.0000131 secs]813.408: [JNI Weak Reference, 0.0000088 secs]: 69952K->8704K(78656K), 0.0104509 secs] 69952K->11354K(253440K), 0.0105137 secs] [Times: user=0.04 sys=0.01, real=0.01 secs]\n";
        JDK8GenerationalGCLogParser parser = (JDK8GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        CMSGCModel model = (CMSGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);

        Assert.assertEquals(model.getGcEvents().size(), 11);
        GCEvent fullgc = model.getGcEvents().get(0);
        Assert.assertEquals(fullgc.getStartTime(), 610956, DELTA);
        Assert.assertEquals(fullgc.getDuration(), 1027.7002, DELTA);
        Assert.assertEquals(fullgc.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullgc.getCause(), "Heap Dump Initiated GC");
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getPreUsed(), 114217);
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getPostUsed(), 113775);
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getTotal(), 1153024);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getPreUsed(), 1537414);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getPostUsed(), 1388294);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getTotal(), 4915200);
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(OLD).getPreUsed(), 324459);
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(OLD).getPostUsed(), 175339);
        Assert.assertEquals(fullgc.getCollectionResult().getFirstItemOfGeneRation(OLD).getTotal(), 3072000);
        Assert.assertEquals(fullgc.getPhases().size(), 4);
        Assert.assertEquals(fullgc.getLastPhaseOfType(GCEventType.WEAK_REFS_PROCESSING).getStartTime(), 611637, DELTA);
        Assert.assertEquals(fullgc.getLastPhaseOfType(GCEventType.WEAK_REFS_PROCESSING).getDuration(), 1.8945, DELTA);
        Assert.assertEquals(fullgc.getCpuTime().getUser(), 1710, DELTA);
        Assert.assertEquals(fullgc.getCpuTime().getSys(), 50, DELTA);
        Assert.assertEquals(fullgc.getCpuTime().getReal(), 1030, DELTA);

        fullgc = model.getGcEvents().get(9);
        Assert.assertEquals(fullgc.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getPreUsed(), 3956586);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getPostUsed(), 1051300);
        Assert.assertEquals(fullgc.getCollectionResult().getSummary().getTotal(), 4019584);

        GCEvent youngGC = model.getGcEvents().get(10);
        Assert.assertEquals(youngGC.getStartTime(), 813396, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 10.5137, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getPreUsed(), 69952);
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getPostUsed(), 11354);
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getTotal(), 253440);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG).getPreUsed(), 69952);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG).getPostUsed(), 8704);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG).getTotal(), 78656);
        Assert.assertNull(youngGC.getPhases());
        Assert.assertEquals(youngGC.getReferenceGC().getSoftReferenceStartTime(), 813404, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getSoftReferencePauseTime(), 0.0260, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getSoftReferenceCount(), 4);
        Assert.assertEquals(youngGC.getReferenceGC().getWeakReferenceStartTime(), 813405, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getWeakReferencePauseTime(), 0.0110, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getWeakReferenceCount(), 59);
        Assert.assertEquals(youngGC.getReferenceGC().getFinalReferenceStartTime(), 813406, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getFinalReferencePauseTime(), 2.5979, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getFinalReferenceCount(), 1407);
        Assert.assertEquals(youngGC.getReferenceGC().getPhantomReferenceStartTime(), 813407, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getPhantomReferencePauseTime(), 0.0131, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getPhantomReferenceCount(), 11);
        Assert.assertEquals(youngGC.getReferenceGC().getPhantomReferenceFreedCount(), 10);
        Assert.assertEquals(youngGC.getReferenceGC().getJniWeakReferenceStartTime(), 813408, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getJniWeakReferencePauseTime(), 0.0088, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getUser(), 40, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getSys(), 10, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 10, DELTA);

        GCEvent cms = model.getGcEvents().get(3);
        Assert.assertEquals(cms.getEventType(), GCEventType.CMS_CONCURRENT_MARK_SWEPT);
        Assert.assertEquals(cms.getStartTime(), 675164, DELTA);
        Assert.assertEquals(cms.getPhases().size(), 12, DELTA);
        for (GCEvent phase : cms.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.INITIAL_MARK).getStartTime(), 675164, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CONCURRENT_MARK).getDuration(), 34415, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CONCURRENT_MARK).getCpuTime().getUser(), 154390, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.REMARK).getCpuTime().getUser(), 770, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.REMARK).getDuration(), 431.5, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CMS_CONCURRENT_RESET).getDuration(), 237, DELTA);
    }

    @Test
    public void testJDK8G1GCParser() throws Exception {
        String log = "3.960: [GC pause (G1 Evacuation Pause) (young)4.000: [SoftReference, 0 refs, 0.0000435 secs]4.000: [WeakReference, 374 refs, 0.0002082 secs]4.001: [FinalReference, 5466 refs, 0.0141707 secs]4.015: [PhantomReference, 0 refs, 0 refs, 0.0000253 secs]4.015: [JNI Weak Reference, 0.0000057 secs], 0.0563085 secs]\n" +
                "   [Parallel Time: 39.7 ms, GC Workers: 4]\n" +
                "      [GC Worker Start (ms): Min: 3959.8, Avg: 3959.9, Max: 3960.1, Diff: 0.2]\n" +
                "      [Ext Root Scanning (ms): Min: 2.6, Avg: 10.1, Max: 17.9, Diff: 15.2, Sum: 40.4]\n" +
                "      [Update RS (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.0]\n" +
                "         [Processed Buffers: Min: 0, Avg: 0.0, Max: 0, Diff: 0, Sum: 0]\n" +
                "      [Scan RS (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.0]\n" +
                "      [Code Root Scanning (ms): Min: 0.0, Avg: 0.5, Max: 2.1, Diff: 2.1, Sum: 2.1]\n" +
                "      [Object Copy (ms): Min: 18.1, Avg: 26.2, Max: 33.7, Diff: 15.6, Sum: 104.9]\n" +
                "      [Termination (ms): Min: 0.0, Avg: 1.5, Max: 3.5, Diff: 3.5, Sum: 6.2]\n" +
                "         [Termination Attempts: Min: 1, Avg: 21.8, Max: 51, Diff: 50, Sum: 87]\n" +
                "      [GC Worker Other (ms): Min: 0.0, Avg: 0.1, Max: 0.1, Diff: 0.0, Sum: 0.2]\n" +
                "      [GC Worker Total (ms): Min: 38.0, Avg: 38.5, Max: 39.5, Diff: 1.5, Sum: 153.8]\n" +
                "      [GC Worker End (ms): Min: 3998.0, Avg: 3998.4, Max: 3999.4, Diff: 1.4]\n" +
                "   [Code Root Fixup: 0.2 ms]\n" +
                "   [Code Root Purge: 0.2 ms]\n" +
                "   [Clear CT: 0.2 ms]\n" +
                "   [Other: 16.0 ms]\n" +
                "      [Choose CSet: 0.0 ms]\n" +
                "      [Ref Proc: 15.1 ms]\n" +
                "      [Ref Enq: 0.2 ms]\n" +
                "      [Redirty Cards: 0.1 ms]\n" +
                "      [Humongous Register: 0.0 ms]\n" +
                "      [Humongous Reclaim: 0.0 ms]\n" +
                "      [Free CSet: 0.3 ms]\n" +
                "   [Eden: 184.0M(184.0M)->0.0B(160.0M) Survivors: 0.0B->24.0M Heap: 184.0M(3800.0M)->19.3M(3800.0M)]\n" +
                " [Times: user=0.07 sys=0.01, real=0.06 secs]\n" +
                "4.230: [GC concurrent-root-region-scan-start]\n" +
                "4.391: [GC concurrent-root-region-scan-end, 0.1608430 secs]\n" +
                "4.391: [GC concurrent-mark-start]\n" +
                "7.101: [GC concurrent-mark-reset-for-overflow]\n" +
                "19.072: [GC concurrent-mark-end, 14.6803750 secs]\n" +
                "19.078: [GC remark 19.078: [Finalize Marking, 0.1774665 secs] 19.255: [GC ref-proc, 0.1648116 secs] 19.420: [Unloading, 0.1221964 secs], 0.4785858 secs]\n" +
                " [Times: user=1.47 sys=0.31, real=0.48 secs]\n" +
                "19.563: [GC cleanup 11G->9863M(20G), 0.0659638 secs]\n" +
                " [Times: user=0.20 sys=0.01, real=0.07 secs]\n" +
                "19.630: [GC concurrent-cleanup-start]\n" +
                "19.631: [GC concurrent-cleanup-end, 0.0010377 secs]\n" +
                "23.346: [Full GC (Metadata GC Threshold)  7521M->7002M(46144M), 1.9242692 secs]\n" +
                "   [Eden: 0.0B(1760.0M)->0.0B(2304.0M) Survivors: 544.0M->0.0B Heap: 7521.7M(46144.0M)->7002.8M(46144.0M)], [Metaspace: 1792694K->291615K(698368K)]\n" +
                " [Times: user=2.09 sys=0.19, real=1.92 secs]\n" +
                "79.619: [GC pause (G1 Evacuation Pause) (mixed)79.636: [SoftReference, 1 refs, 0.0000415 secs]79.636: [WeakReference, 2 refs, 0.0000061 secs]79.636: [FinalReference, 3 refs, 0.0000049 secs]79.636: [PhantomReference, 4 refs, 5 refs, 0.0000052 secs]79.636: [JNI Weak Reference, 0.0000117 secs] (to-space exhausted), 0.0264971 secs]\n" +
                "   [Parallel Time: 20.5 ms, GC Workers: 4]\n" +
                "      [GC Worker Start (ms): Min: 1398294.3, Avg: 1398294.4, Max: 1398294.5, Diff: 0.2]\n" +
                "      [Ext Root Scanning (ms): Min: 1.8, Avg: 2.0, Max: 2.2, Diff: 0.4, Sum: 15.7]\n" +
                "      [Update RS (ms): Min: 1.2, Avg: 1.5, Max: 1.7, Diff: 0.5, Sum: 11.8]\n" +
                "         [Processed Buffers: Min: 21, Avg: 27.0, Max: 30, Diff: 9, Sum: 216]\n" +
                "      [Scan RS (ms): Min: 1.8, Avg: 1.9, Max: 2.2, Diff: 0.4, Sum: 15.5]\n" +
                "      [Code Root Scanning (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.0]\n" +
                "      [Object Copy (ms): Min: 14.5, Avg: 14.7, Max: 14.9, Diff: 0.4, Sum: 118.0]\n" +
                "      [Termination (ms): Min: 0.0, Avg: 0.1, Max: 0.1, Diff: 0.1, Sum: 0.5]\n" +
                "         [Termination Attempts: Min: 1, Avg: 148.2, Max: 181, Diff: 180, Sum: 1186]\n" +
                "      [GC Worker Other (ms): Min: 0.0, Avg: 0.0, Max: 0.1, Diff: 0.0, Sum: 0.3]\n" +
                "      [GC Worker Total (ms): Min: 20.1, Avg: 20.2, Max: 20.3, Diff: 0.2, Sum: 161.9]\n" +
                "      [GC Worker End (ms): Min: 1398314.7, Avg: 1398314.7, Max: 1398314.7, Diff: 0.0]\n" +
                "   [Code Root Fixup: 0.0 ms]\n" +
                "   [Code Root Purge: 0.0 ms]\n" +
                "   [Clear CT: 0.5 ms]\n" +
                "   [Other: 10.4 ms]\n" +
                "      [Choose CSet: 0.0 ms]\n" +
                "      [Ref Proc: 8.8 ms]\n" +
                "      [Ref Enq: 0.3 ms]\n" +
                "      [Redirty Cards: 0.2 ms]\n" +
                "      [Free CSet: 0.1 ms]\n" +
                "   [Eden: 2304.0M(2304.0M)->0.0B(2304.0M) Survivors: 192.0M->192.0M Heap: 15.0G(19.8G)->12.8G(19.8G)]\n" +
                " [Times: user=0.17 sys=0.00, real=0.03 secs]";
        JDK8G1GCLogParser parser = (JDK8G1GCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        G1GCModel model = (G1GCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 4);
        Assert.assertEquals(model.getParallelThread(), 4);

        GCEvent youngGC = model.getGcEvents().get(0);
        Assert.assertEquals(youngGC.getStartTime(), 3960, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 56.3085, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "G1 Evacuation Pause");
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getPreUsed(), 184 * 1024);
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getPostUsed(), (int) (19.3 * 1024));
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getTotal(), 3800 * 1024);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(EDEN).getPreUsed(), 184 * 1024);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(EDEN).getPostUsed(), 0);
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(EDEN).getTotal(), 160 * 1024);
        Assert.assertNotNull(youngGC.getPhases());
        for (GCEvent phase : youngGC.getPhases()) {
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(youngGC.getLastPhaseOfType(GCEventType.G1_GC_REFPROC).getDuration(), 15.1, DELTA);
        Assert.assertEquals(youngGC.getLastPhaseOfType(GCEventType.G1_CODE_ROOT_SCANNING).getDuration(), 0.5, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getSoftReferenceStartTime(), 4000, DELTA);
        Assert.assertEquals(youngGC.getReferenceGC().getJniWeakReferencePauseTime(), 0.0057, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getUser(), 70, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getSys(), 10, DELTA);
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 60, DELTA);

        GCEvent concurrentCycle = model.getGcEvents().get(1);
        Assert.assertEquals(concurrentCycle.getStartTime(), 4230, DELTA);
        Assert.assertEquals(concurrentCycle.getPhases().size(), 9);
        for (GCEvent phase : concurrentCycle.getPhases()) {
            if (phase.getEventType() != GCEventType.G1_CONCURRENT_MARK_RESET_FOR_OVERFLOW) {
                Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
            }
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.G1_CONCURRENT_SCAN_ROOT_REGIONS).getStartTime(), 4230, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.G1_CONCURRENT_SCAN_ROOT_REGIONS).getDuration(), 160.8430, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.REMARK).getStartTime(), 19078, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.REMARK).getDuration(), 478.5858, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.G1_PAUSE_CLEANUP).getCollectionResult().getSummary().getPostUsed(), 9863 * 1024, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.REMARK).getCpuTime().getUser(), 1470, DELTA);
        Assert.assertEquals(concurrentCycle.getLastPhaseOfType(GCEventType.G1_PAUSE_CLEANUP).getCpuTime().getSys(), 10, DELTA);

        GCEvent fullGC = model.getGcEvents().get(2);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getStartTime(), 23346, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 1924.2692, DELTA);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Metadata GC Threshold");
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getPreUsed(), 1792694);
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getPostUsed(), 291615);
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE).getTotal(), 698368);
        Assert.assertEquals(fullGC.getCollectionResult().getSummary().getPreUsed(), (int) (7521.7 * 1024));
        Assert.assertEquals(fullGC.getCollectionResult().getSummary().getPostUsed(), (int) (7002.8 * 1024));
        Assert.assertEquals(fullGC.getCollectionResult().getSummary().getTotal(), (int) (46144.0 * 1024));
        Assert.assertEquals(fullGC.getCpuTime().getUser(), 2090, DELTA);
        Assert.assertEquals(fullGC.getCpuTime().getSys(), 190, DELTA);
        Assert.assertEquals(fullGC.getCpuTime().getReal(), 1920, DELTA);

        GCEvent mixedGC = model.getGcEvents().get(3);
        Assert.assertEquals(mixedGC.getStartTime(), 79619, DELTA);
        Assert.assertEquals(mixedGC.getDuration(), 26.4971, DELTA);
        Assert.assertEquals(mixedGC.getEventType(), GCEventType.G1_YOUNG_MIXED_GC);
        Assert.assertEquals(mixedGC.getCause(), "G1 Evacuation Pause");
        Assert.assertTrue(mixedGC.hasSpecialSituation(TO_SPACE_EXHAUSTED));
        Assert.assertEquals(mixedGC.getCollectionResult().getSummary().getTotal(), (int) (19.8 * 1024 * 1024));
        Assert.assertEquals(mixedGC.getCollectionResult().getFirstItemOfGeneRation(EDEN).getPreUsed(), 2304 * 1024);
        Assert.assertNotNull(mixedGC.getPhases());
        for (GCEvent phase : mixedGC.getPhases()) {
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
    }

    @Test
    public void testJDK8G1GCParserAdaptiveSize() throws Exception {
        // although we don't read anything from options like -XX:+PrintAdaptiveSizePolicy, they should not
        // affect parsing
        String log = "2022-02-09T15:55:55.807+0800: 0.683: [GC pause (G1 Evacuation Pause) (young)\n" +
                "Desired survivor size 3670016 bytes, new threshold 15 (max 15)\n" +
                " 0.683: [G1Ergonomics (CSet Construction) start choosing CSet, _pending_cards: 0, predicted base time: 10.00 ms, remaining time: 240.00 ms, target pause time: 250.00 ms]\n" +
                " 0.683: [G1Ergonomics (CSet Construction) add young regions to CSet, eden: 51 regions, survivors: 0 regions, predicted young region time: 1298.76 ms]\n" +
                " 0.683: [G1Ergonomics (CSet Construction) finish choosing CSet, eden: 51 regions, survivors: 0 regions, old: 0 regions, predicted pause time: 1308.76 ms, target pause time: 250.00 ms]\n" +
                ", 0.0085898 secs]\n" +
                "   [Parallel Time: 5.5 ms, GC Workers: 4]\n" +
                "      [GC Worker Start (ms): Min: 682.6, Avg: 682.6, Max: 682.7, Diff: 0.0]\n" +
                "      [Ext Root Scanning (ms): Min: 0.8, Avg: 1.2, Max: 1.6, Diff: 0.8, Sum: 4.8]\n" +
                "      [Update RS (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.0]\n" +
                "         [Processed Buffers: Min: 0, Avg: 0.0, Max: 0, Diff: 0, Sum: 0]\n" +
                "      [Scan RS (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.0]\n" +
                "      [Code Root Scanning (ms): Min: 0.0, Avg: 0.2, Max: 0.9, Diff: 0.9, Sum: 0.9]\n" +
                "      [Object Copy (ms): Min: 3.5, Avg: 3.9, Max: 4.5, Diff: 1.0, Sum: 15.7]\n" +
                "      [Termination (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.1]\n" +
                "         [Termination Attempts: Min: 1, Avg: 6.8, Max: 9, Diff: 8, Sum: 27]\n" +
                "      [GC Worker Other (ms): Min: 0.0, Avg: 0.0, Max: 0.0, Diff: 0.0, Sum: 0.1]\n" +
                "      [GC Worker Total (ms): Min: 5.4, Avg: 5.4, Max: 5.4, Diff: 0.0, Sum: 21.6]\n" +
                "      [GC Worker End (ms): Min: 688.1, Avg: 688.1, Max: 688.1, Diff: 0.0]\n" +
                "   [Code Root Fixup: 0.0 ms]\n" +
                "   [Code Root Purge: 0.0 ms]\n" +
                "   [Clear CT: 0.1 ms]\n" +
                "   [Other: 3.0 ms]\n" +
                "      [Choose CSet: 0.0 ms]\n" +
                "      [Ref Proc: 2.6 ms]\n" +
                "      [Ref Enq: 0.0 ms]\n" +
                "      [Redirty Cards: 0.1 ms]\n" +
                "      [Humongous Register: 0.0 ms]\n" +
                "      [Humongous Reclaim: 0.0 ms]\n" +
                "      [Free CSet: 0.1 ms]\n" +
                "   [Eden: 52224.0K(52224.0K)->0.0B(45056.0K) Survivors: 0.0B->7168.0K Heap: 52224.0K(1024.0M)->8184.0K(1024.0M)]\n" +
                " [Times: user=0.02 sys=0.01, real=0.01 secs] ";
        JDK8G1GCLogParser parser = (JDK8G1GCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        G1GCModel model = (G1GCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 1);
        GCEvent youngGC = model.getGcEvents().get(0);
        Assert.assertEquals(youngGC.getStartTime(), 683, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 8.5898, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "G1 Evacuation Pause");
        Assert.assertEquals(youngGC.getCollectionResult().getSummary().getPreUsed(), 52224);
    }

    @Test
    public void testJDK11SerialGCParser() throws Exception {
        String log = "[0.486s][info][gc,start     ] GC(0) Pause Young (Allocation Failure)\n" +
                "[0.511s][info][gc,heap      ] GC(0) DefNew: 69952K->8704K(78656K)\n" +
                "[0.511s][info][gc,heap      ] GC(0) Tenured: 0K->24185K(174784K)\n" +
                "[0.511s][info][gc,metaspace ] GC(0) Metaspace: 6529K->6519K(1056768K)\n" +
                "[0.511s][info][gc           ] GC(0) Pause Young (Allocation Failure) 68M->32M(247M) 25.164ms\n" +
                "[0.511s][info][gc,cpu       ] GC(0) User=0.02s Sys=0.00s Real=0.02s\n" +
                "[5.614s][info][gc,start     ] GC(1) Pause Full (Allocation Failure)\n" +
                "[5.614s][info][gc,phases,start] GC(1) Phase 1: Mark live objects\n" +
                "[5.662s][info][gc,phases      ] GC(1) Phase 1: Mark live objects 47.589ms\n" +
                "[5.662s][info][gc,phases,start] GC(1) Phase 2: Compute new object addresses\n" +
                "[5.688s][info][gc,phases      ] GC(1) Phase 2: Compute new object addresses 26.097ms\n" +
                "[5.688s][info][gc,phases,start] GC(1) Phase 3: Adjust pointers\n" +
                "[5.743s][info][gc,phases      ] GC(1) Phase 3: Adjust pointers 55.459ms\n" +
                "[5.743s][info][gc,phases,start] GC(1) Phase 4: Move objects\n" +
                "[5.760s][info][gc,phases      ] GC(1) Phase 4: Move objects 17.259ms\n" +
                "[5.761s][info][gc             ] GC(1) Pause Full (Allocation Failure) 215M->132M(247M) 146.617ms";
        JDK11GenerationalGCLogParser parser = (JDK11GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        SerialGCModel model = (SerialGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 2);

        GCEvent youngGC = model.getGcEvents().get(0);
        Assert.assertEquals(youngGC.getGcid(), 0);
        Assert.assertEquals(youngGC.getStartTime(), 486, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 25.164, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG), new GCCollectionResultItem(YOUNG, 69952, 8704, 78656));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(OLD), new GCCollectionResultItem(OLD, 0, 24185, 174784));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE), new GCCollectionResultItem(METASPACE, 6529, 6519, 1056768));
        Assert.assertEquals(youngGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 68 * 1024, 32 * 1024, 247 * 1024));
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 20, DELTA);

        GCEvent fullGC = model.getGcEvents().get(1);
        Assert.assertEquals(fullGC.getGcid(), 1);
        Assert.assertEquals(fullGC.getStartTime(), 5614, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 146.617, DELTA);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Allocation Failure");
        Assert.assertEquals(fullGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 215 * 1024, 132 * 1024, 247 * 1024));
        Assert.assertEquals(fullGC.getPhases().size(), 4);
        for (GCEvent phase : fullGC.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_COMPUTE_NEW_OBJECT_ADDRESSES).getDuration(), 26.097, DELTA);
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_MOVE_OBJECTS).getDuration(), 17.259, DELTA);
    }

    @Test
    public void testJDK11ParallelGCParser() throws Exception {
        String log = "[0.455s][info][gc,start     ] GC(0) Pause Young (Allocation Failure)\n" +
                "[0.466s][info][gc,heap      ] GC(0) PSYoungGen: 65536K->10720K(76288K)\n" +
                "[0.466s][info][gc,heap      ] GC(0) ParOldGen: 0K->20800K(175104K)\n" +
                "[0.466s][info][gc,metaspace ] GC(0) Metaspace: 6531K->6531K(1056768K)\n" +
                "[0.466s][info][gc           ] GC(0) Pause Young (Allocation Failure) 64M->30M(245M) 11.081ms\n" +
                "[0.466s][info][gc,cpu       ] GC(0) User=0.03s Sys=0.02s Real=0.01s\n" +
                "[2.836s][info][gc,start     ] GC(1) Pause Full (Ergonomics)\n" +
                "[2.836s][info][gc,phases,start] GC(1) Marking Phase\n" +
                "[2.857s][info][gc,phases      ] GC(1) Marking Phase 21.145ms\n" +
                "[2.857s][info][gc,phases,start] GC(1) Summary Phase\n" +
                "[2.857s][info][gc,phases      ] GC(1) Summary Phase 0.006ms\n" +
                "[2.857s][info][gc,phases,start] GC(1) Adjust Roots\n" +
                "[2.859s][info][gc,phases      ] GC(1) Adjust Roots 1.757ms\n" +
                "[2.859s][info][gc,phases,start] GC(1) Compaction Phase\n" +
                "[2.881s][info][gc,phases      ] GC(1) Compaction Phase 22.465ms\n" +
                "[2.881s][info][gc,phases,start] GC(1) Post Compact\n" +
                "[2.882s][info][gc,phases      ] GC(1) Post Compact 1.054ms\n" +
                "[2.882s][info][gc,heap        ] GC(1) PSYoungGen: 10729K->0K(76288K)\n" +
                "[2.882s][info][gc,heap        ] GC(1) ParOldGen: 141664K->94858K(175104K)\n" +
                "[2.882s][info][gc,metaspace   ] GC(1) Metaspace: 7459K->7459K(1056768K)\n" +
                "[2.882s][info][gc             ] GC(1) Pause Full (Ergonomics) 148M->92M(245M) 46.539ms\n" +
                "[2.882s][info][gc,cpu         ] GC(1) User=0.17s Sys=0.00s Real=0.05s";
        JDK11GenerationalGCLogParser parser = (JDK11GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        ParallelGCModel model = (ParallelGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 2);

        GCEvent youngGC = model.getGcEvents().get(0);
        Assert.assertEquals(youngGC.getGcid(), 0);
        Assert.assertEquals(youngGC.getStartTime(), 455, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 11.081, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG), new GCCollectionResultItem(YOUNG, 65536, 10720, 76288));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(OLD), new GCCollectionResultItem(OLD, 0, 20800, 175104));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE), new GCCollectionResultItem(METASPACE, 6531, 6531, 1056768));
        Assert.assertEquals(youngGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 64 * 1024, 30 * 1024, 245 * 1024));
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 10, DELTA);

        GCEvent fullGC = model.getGcEvents().get(1);
        Assert.assertEquals(fullGC.getGcid(), 1);
        Assert.assertEquals(fullGC.getStartTime(), 2836, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 46.539, DELTA);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Ergonomics");
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG), new GCCollectionResultItem(YOUNG, 10729, 0, 76288));
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(OLD), new GCCollectionResultItem(OLD, 141664, 94858, 175104));
        Assert.assertEquals(fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE), new GCCollectionResultItem(METASPACE, 7459, 7459, 1056768));
        Assert.assertEquals(fullGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 148 * 1024, 92 * 1024, 245 * 1024));
        Assert.assertEquals(fullGC.getPhases().size(), 5);
        for (GCEvent phase : fullGC.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.PARALLEL_PHASE_SUMMARY).getDuration(), 0.006, DELTA);
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.PARALLEL_PHASE_COMPACTION).getDuration(), 22.465, DELTA);
        Assert.assertEquals(fullGC.getCpuTime().getReal(), 50, DELTA);
    }

    @Test
    public void testJDK11CMSGCParser() throws Exception {
        String log = "[0.479s][info][gc,start     ] GC(0) Pause Young (Allocation Failure)\n" +
                "[0.480s][info][gc,task      ] GC(0) Using 5 workers of 8 for evacuation\n" +
                "[0.510s][info][gc,heap      ] GC(0) ParNew: 69952K->8703K(78656K)\n" +
                "[0.510s][info][gc,heap      ] GC(0) CMS: 0K->24072K(174784K)\n" +
                "[0.510s][info][gc,metaspace ] GC(0) Metaspace: 6531K->6530K(1056768K)\n" +
                "[0.510s][info][gc           ] GC(0) Pause Young (Allocation Failure) 68M->32M(247M) 31.208ms\n" +
                "[0.510s][info][gc,cpu       ] GC(0) User=0.06s Sys=0.03s Real=0.03s\n" +
                "[3.231s][info][gc,start     ] GC(1) Pause Initial Mark\n" +
                "[3.235s][info][gc           ] GC(1) Pause Initial Mark 147M->147M(247M) 3.236ms\n" +
                "[3.235s][info][gc,cpu       ] GC(1) User=0.01s Sys=0.02s Real=0.03s\n" +
                "[3.235s][info][gc           ] GC(1) Concurrent Mark\n" +
                "[3.235s][info][gc,task      ] GC(1) Using 2 workers of 2 for marking\n" +
                "[3.257s][info][gc           ] GC(1) Concurrent Mark 22.229ms\n" +
                "[3.257s][info][gc,cpu       ] GC(1) User=0.07s Sys=0.00s Real=0.03s\n" +
                "[3.257s][info][gc           ] GC(1) Concurrent Preclean\n" +
                "[3.257s][info][gc           ] GC(1) Concurrent Preclean 0.264ms\n" +
                "[3.257s][info][gc,cpu       ] GC(1) User=0.00s Sys=0.00s Real=0.00s\n" +
                "[3.257s][info][gc,start     ] GC(1) Pause Remark\n" +
                "[3.259s][info][gc           ] GC(1) Pause Remark 149M->149M(247M) 1.991ms\n" +
                "[3.259s][info][gc,cpu       ] GC(1) User=0.02s Sys=0.03s Real=0.01s\n" +
                "[3.259s][info][gc           ] GC(1) Concurrent Sweep\n" +
                "[3.279s][info][gc           ] GC(1) Concurrent Sweep 19.826ms\n" +
                "[3.279s][info][gc,cpu       ] GC(1) User=0.03s Sys=0.00s Real=0.02s\n" +
                "[3.279s][info][gc           ] GC(1) Concurrent Reset\n" +
                "[3.280s][info][gc           ] GC(1) Concurrent Reset 0.386ms\n" +
                "[3.280s][info][gc,cpu       ] GC(1) User=0.00s Sys=0.00s Real=0.00s\n" +
                "[3.280s][info][gc,heap      ] GC(1) Old: 142662K->92308K(174784K)\n" +
                "[8.970s][info][gc,start     ] GC(2) Pause Full (Allocation Failure)\n" +
                "[8.970s][info][gc,phases,start] GC(2) Phase 1: Mark live objects\n" +
                "[9.026s][info][gc,phases      ] GC(2) Phase 1: Mark live objects 55.761ms\n" +
                "[9.026s][info][gc,phases,start] GC(2) Phase 2: Compute new object addresses\n" +
                "[9.051s][info][gc,phases      ] GC(2) Phase 2: Compute new object addresses 24.761ms\n" +
                "[9.051s][info][gc,phases,start] GC(2) Phase 3: Adjust pointers\n" +
                "[9.121s][info][gc,phases      ] GC(2) Phase 3: Adjust pointers 69.678ms\n" +
                "[9.121s][info][gc,phases,start] GC(2) Phase 4: Move objects\n" +
                "[9.149s][info][gc,phases      ] GC(2) Phase 4: Move objects 28.069ms\n" +
                "[9.149s][info][gc             ] GC(2) Pause Full (Allocation Failure) 174M->166M(247M) 178.617ms\n" +
                "[9.149s][info][gc,cpu         ] GC(2) User=0.17s Sys=0.00s Real=0.18s";
        JDK11GenerationalGCLogParser parser = (JDK11GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        CMSGCModel model = (CMSGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);
        Assert.assertEquals(model.getGcEvents().size(), 3);
        Assert.assertEquals(model.getParallelThread(), 8);
        Assert.assertEquals(model.getConcurrentThread(), 2);

        GCEvent youngGC = model.getGcEvents().get(0);
        Assert.assertEquals(youngGC.getGcid(), 0);
        Assert.assertEquals(youngGC.getStartTime(), 479, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 31.208, DELTA);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG), new GCCollectionResultItem(YOUNG, 69952, 8703, 78656));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(OLD), new GCCollectionResultItem(OLD, 0, 24072, 174784));
        Assert.assertEquals(youngGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE), new GCCollectionResultItem(METASPACE, 6531, 6530, 1056768));
        Assert.assertEquals(youngGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 68 * 1024, 32 * 1024, 247 * 1024));
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 30, DELTA);

        GCEvent cms = model.getGcEvents().get(1);
        Assert.assertEquals(cms.getGcid(), 1);
        Assert.assertEquals(cms.getEventType(), GCEventType.CMS_CONCURRENT_MARK_SWEPT);
        Assert.assertEquals(cms.getStartTime(), 3231, DELTA);
        Assert.assertEquals(cms.getPhases().size(), 6);
        for (GCEvent phase : cms.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
            Assert.assertNotNull(phase.getCpuTime());
            if (phase.getEventType() == GCEventType.INITIAL_MARK || phase.getEventType() == GCEventType.REMARK) {
                Assert.assertNotNull(phase.getCollectionResult().getSummary());
            }
        }
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.INITIAL_MARK).getStartTime(), 3231, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CONCURRENT_MARK).getDuration(), 22.229, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CONCURRENT_MARK).getCpuTime().getUser(), 70, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.REMARK).getCpuTime().getUser(), 20, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.REMARK).getDuration(), 1.991, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CMS_CONCURRENT_RESET).getDuration(), 0.386, DELTA);
        Assert.assertEquals(cms.getLastPhaseOfType(GCEventType.CMS_CONCURRENT_SWEEP).getCollectionResult().getFirstItemOfGeneRation(OLD), new GCCollectionResultItem(OLD, 142662, 92308, 174784));

        GCEvent fullGC = model.getGcEvents().get(2);
        Assert.assertEquals(fullGC.getGcid(), 2);
        Assert.assertEquals(fullGC.getStartTime(), 8970, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 178.617, DELTA);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Allocation Failure");
        Assert.assertEquals(fullGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 174 * 1024, 166 * 1024, 247 * 1024));
        Assert.assertEquals(fullGC.getCpuTime().getReal(), 180, DELTA);
        Assert.assertEquals(fullGC.getPhases().size(), 4);
        for (GCEvent phase : fullGC.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_COMPUTE_NEW_OBJECT_ADDRESSES).getDuration(), 24.761, DELTA);
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_MOVE_OBJECTS).getDuration(), 28.069, DELTA);
    }

    @Test
    public void testJDK8ParallelGCParser() throws Exception {
        String log =
                "0.141: [GC (Allocation Failure) [PSYoungGen: 25145K->4077K(29696K)] 25145K->16357K(98304K), 0.0225874 secs] [Times: user=0.10 sys=0.01, real=0.03 secs]\n" +
                        "0.269: [Full GC (Ergonomics) [PSYoungGen: 4096K->0K(55296K)] [ParOldGen: 93741K->67372K(174592K)] 97837K->67372K(229888K), [Metaspace: 3202K->3202K(1056768K)], 0.6862093 secs] [Times: user=2.60 sys=0.02, real=0.69 secs]\n" +
                        "0.962: [GC (Allocation Failure) [PSYoungGen: 51200K->4096K(77824K)] 118572K->117625K(252416K), 0.0462864 secs] [Times: user=0.29 sys=0.01, real=0.05 secs]\n" +
                        "1.872: [Full GC (Ergonomics) [PSYoungGen: 4096K->0K(103936K)] [ParOldGen: 169794K->149708K(341504K)] 173890K->149708K(445440K), [Metaspace: 3202K->3202K(1056768K)], 1.3724621 secs] [Times: user=8.33 sys=0.01, real=1.38 secs]\n" +
                        "3.268: [GC (Allocation Failure) [PSYoungGen: 99840K->56802K(113664K)] 249548K->302089K(455168K), 0.1043993 secs] [Times: user=0.75 sys=0.06, real=0.10 secs]\n" +
                        "14.608: [Full GC (Ergonomics) [PSYoungGen: 65530K->0K(113664K)] [ParOldGen: 341228K->720K(302592K)] 406759K->720K(416256K), [Metaspace: 3740K->3737K(1056768K)], 0.0046781 secs] [Times: user=0.02 sys=0.01, real=0.00 secs]\n";
        JDK8GenerationalGCLogParser parser = (JDK8GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        ParallelGCModel model = (ParallelGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);

        Assert.assertEquals(model.getGcEvents().size(), 6);

        GCEvent youngGC = model.getGcEvents().get(2);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getStartTime(), 962, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 46.2864, DELTA);
        GCCollectionResultItem youngGen = youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG);
        Assert.assertEquals(youngGen.getPreUsed(), 51200);
        Assert.assertEquals(youngGen.getPostUsed(), 4096);
        Assert.assertEquals(youngGen.getTotal(), 77824);
        GCCollectionResultItem total = youngGC.getCollectionResult().getSummary();
        Assert.assertEquals(total.getPreUsed(), 118572);
        Assert.assertEquals(total.getPostUsed(), 117625);
        Assert.assertEquals(total.getTotal(), 252416);
        Assert.assertEquals(youngGC.getCpuTime().getUser(), 290, DELTA);

        GCEvent fullGC = model.getGcEvents().get(5);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Ergonomics");
        Assert.assertEquals(fullGC.getStartTime(), 14608, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 4.6781, DELTA);
        youngGen = fullGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG);
        Assert.assertEquals(youngGen.getPreUsed(), 65530);
        Assert.assertEquals(youngGen.getPostUsed(), 0);
        Assert.assertEquals(youngGen.getTotal(), 113664);
        GCCollectionResultItem oldGen = fullGC.getCollectionResult().getFirstItemOfGeneRation(OLD);
        Assert.assertEquals(oldGen.getPreUsed(), 341228);
        Assert.assertEquals(oldGen.getPostUsed(), 720);
        Assert.assertEquals(oldGen.getTotal(), 302592);
        GCCollectionResultItem metaspace = fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE);
        Assert.assertEquals(metaspace.getPreUsed(), 3740);
        Assert.assertEquals(metaspace.getPostUsed(), 3737);
        Assert.assertEquals(metaspace.getTotal(), 1056768);
        total = fullGC.getCollectionResult().getSummary();
        Assert.assertEquals(total.getPreUsed(), 406759);
        Assert.assertEquals(total.getPostUsed(), 720);
        Assert.assertEquals(total.getTotal(), 416256);
        Assert.assertEquals(fullGC.getCpuTime().getUser(), 20, DELTA);
    }

    @Test
    public void testJDK8SerialGCParser() throws Exception {
        String log =
                "2021-12-07T11:18:11.688+0800: #0: [GC (Allocation Failure) 2021-12-07T11:18:11.688+0800: #0: [DefNew: 69952K->8704K(78656K), 0.0591895 secs] 69952K->56788K(253440K), 0.0592437 secs] [Times: user=0.05 sys=0.02, real=0.06 secs] \n" +
                        "2021-12-07T11:18:11.756+0800: #1: [GC (Allocation Failure) 2021-12-07T11:18:11.756+0800: #1: [DefNew: 78656K->8703K(78656K), 0.0700624 secs] 126740K->114869K(253440K), 0.0701086 secs] [Times: user=0.05 sys=0.01, real=0.07 secs] \n" +
                        "2021-12-07T11:18:11.833+0800: #2: [GC (Allocation Failure) 2021-12-07T11:18:11.833+0800: #2: [DefNew: 78655K->8703K(78656K), 0.0837783 secs]2021-12-07T11:18:11.917+0800: #3: [Tenured: 176115K->174136K(176128K), 0.1988447 secs] 184821K->174136K(254784K), [Metaspace: 3244K->3244K(1056768K)], 0.2828418 secs] [Times: user=0.27 sys=0.02, real=0.28 secs] \n" +
                        "2021-12-07T11:18:12.140+0800: #4: [GC (Allocation Failure) 2021-12-07T11:18:12.140+0800: #4: [DefNew: 116224K->14463K(130688K), 0.1247689 secs] 290360K->290358K(420916K), 0.1248360 secs] [Times: user=0.10 sys=0.03, real=0.12 secs] \n" +
                        "2021-12-07T11:18:12.273+0800: #5: [GC (Allocation Failure) 2021-12-07T11:18:12.273+0800: #5: [DefNew: 102309K->14463K(130688K), 0.1181527 secs]2021-12-07T11:18:12.391+0800: #6: [Tenured: 362501K->362611K(362612K), 0.3681604 secs] 378203K->376965K(493300K), [Metaspace: 3244K->3244K(1056768K)], 0.4867024 secs] [Times: user=0.46 sys=0.03, real=0.49 secs] \n" +
                        "2021-12-07T11:18:12.809+0800: #7: [GC (Allocation Failure) 2021-12-07T11:18:12.809+0800: #7: [DefNew: 227109K->30207K(272000K), 0.3180977 secs] 589721K->581277K(876356K), 0.3181286 secs] [Times: user=0.27 sys=0.05, real=0.32 secs] \n" +
                        "2021-12-07T11:18:13.160+0800: #8: [GC (Allocation Failure) 2021-12-07T11:18:13.160+0800: #8: [DefNew: 271999K->30207K(272000K), 0.2782985 secs]2021-12-07T11:18:13.438+0800: #9: [Tenured: 785946K->756062K(786120K), 0.8169720 secs] 823069K->756062K(1058120K), [Metaspace: 3782K->3782K(1056768K)], 1.0959870 secs] [Times: user=1.03 sys=0.07, real=1.09 secs] \n" +
                        "2021-12-07T11:18:14.386+0800: #10: [GC (Allocation Failure) 2021-12-07T11:18:14.386+0800: #10: [DefNew: 504128K->62975K(567104K), 0.5169362 secs] 1260190K->1260189K(1827212K), 0.5169650 secs] [Times: user=0.40 sys=0.12, real=0.52 secs] ";

        JDK8GenerationalGCLogParser parser = (JDK8GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        SerialGCModel model = (SerialGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);

        Assert.assertEquals(model.getGcEvents().size(), 8);
        Assert.assertEquals(model.getReferenceTimestamp(), 1638847091688.0, DELTA);

        GCEvent youngGC = model.getGcEvents().get(1);
        Assert.assertEquals(youngGC.getEventType(), GCEventType.YOUNG_GC);
        Assert.assertEquals(youngGC.getCause(), "Allocation Failure");
        Assert.assertEquals(youngGC.getStartTime(), 68, DELTA);
        Assert.assertEquals(youngGC.getDuration(), 70.1086, DELTA);
        GCCollectionResultItem youngGen = youngGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG);
        Assert.assertEquals(youngGen.getPreUsed(), 78656);
        Assert.assertEquals(youngGen.getPostUsed(), 8703);
        Assert.assertEquals(youngGen.getTotal(), 78656);
        GCCollectionResultItem total = youngGC.getCollectionResult().getSummary();
        Assert.assertEquals(total.getPreUsed(), 126740);
        Assert.assertEquals(total.getPostUsed(), 114869);
        Assert.assertEquals(total.getTotal(), 253440);
        Assert.assertEquals(youngGC.getCpuTime().getReal(), 70, DELTA);

        GCEvent fullGC = model.getGcEvents().get(6);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Allocation Failure");
        Assert.assertEquals(fullGC.getStartTime(), 1472, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 1095.987, DELTA);
        youngGen = fullGC.getCollectionResult().getFirstItemOfGeneRation(YOUNG);
        Assert.assertEquals(youngGen.getPreUsed(), 271999);
        Assert.assertEquals(youngGen.getPostUsed(), 30207);
        Assert.assertEquals(youngGen.getTotal(), 272000);
        GCCollectionResultItem oldGen = fullGC.getCollectionResult().getFirstItemOfGeneRation(OLD);
        Assert.assertEquals(oldGen.getPreUsed(), 785946);
        Assert.assertEquals(oldGen.getPostUsed(), 756062);
        Assert.assertEquals(oldGen.getTotal(), 786120);
        GCCollectionResultItem metaspace = fullGC.getCollectionResult().getFirstItemOfGeneRation(METASPACE);
        Assert.assertEquals(metaspace.getPreUsed(), 3782);
        Assert.assertEquals(metaspace.getPostUsed(), 3782);
        Assert.assertEquals(metaspace.getTotal(), 1056768);
        total = fullGC.getCollectionResult().getSummary();
        Assert.assertEquals(total.getPreUsed(), 823069);
        Assert.assertEquals(total.getPostUsed(), 756062);
        Assert.assertEquals(total.getTotal(), 1058120);
        Assert.assertEquals(fullGC.getCpuTime().getSys(), 70, DELTA);
    }

    @Test
    public void testJDK11GenerationalGCInterleave() throws Exception {
        String log =
                "[5.643s][info][gc,start     ] GC(3) Pause Young (Allocation Failure)\n" +
                        "[5.643s][info][gc,start     ] GC(4) Pause Full (Allocation Failure)\n" +
                        "[5.643s][info][gc,phases,start] GC(4) Phase 1: Mark live objects\n" +
                        "[5.691s][info][gc,phases      ] GC(4) Phase 1: Mark live objects 47.363ms\n" +
                        "[5.691s][info][gc,phases,start] GC(4) Phase 2: Compute new object addresses\n" +
                        "[5.715s][info][gc,phases      ] GC(4) Phase 2: Compute new object addresses 24.314ms\n" +
                        "[5.715s][info][gc,phases,start] GC(4) Phase 3: Adjust pointers\n" +
                        "[5.771s][info][gc,phases      ] GC(4) Phase 3: Adjust pointers 56.294ms\n" +
                        "[5.771s][info][gc,phases,start] GC(4) Phase 4: Move objects\n" +
                        "[5.789s][info][gc,phases      ] GC(4) Phase 4: Move objects 17.974ms\n" +
                        "[5.789s][info][gc             ] GC(4) Pause Full (Allocation Failure) 215M->132M(247M) 146.153ms\n" +
                        "[5.789s][info][gc,heap        ] GC(3) DefNew: 78655K->0K(78656K)\n" +
                        "[5.789s][info][gc,heap        ] GC(3) Tenured: 142112K->135957K(174784K)\n" +
                        "[5.789s][info][gc,metaspace   ] GC(3) Metaspace: 7462K->7462K(1056768K)\n" +
                        "[5.789s][info][gc             ] GC(3) Pause Young (Allocation Failure) 215M->132M(247M) 146.211ms\n" +
                        "[5.789s][info][gc,cpu         ] GC(3) User=0.15s Sys=0.00s Real=0.15s";

        JDK11GenerationalGCLogParser parser = (JDK11GenerationalGCLogParser)
                (new GCLogParserFactory().getParser(stringToBufferedReader(log)));

        SerialGCModel model = (SerialGCModel) parser.parse(stringToBufferedReader(log));
        model.calculateDerivedInfo(new DefaultProgressListener());
        Assert.assertNotNull(model);

        Assert.assertEquals(model.getGcEvents().size(), 1);
        GCEvent fullGC = model.getGcEvents().get(0);
        Assert.assertEquals(fullGC.getGcid(), 3);
        Assert.assertEquals(fullGC.getStartTime(), 5643, DELTA);
        Assert.assertEquals(fullGC.getDuration(), 146.211, DELTA);
        Assert.assertEquals(fullGC.getEventType(), GCEventType.FULL_GC);
        Assert.assertEquals(fullGC.getCause(), "Allocation Failure");
        Assert.assertEquals(fullGC.getCollectionResult().getSummary(), new GCCollectionResultItem(TOTAL, 215 * 1024, 132 * 1024, 247 * 1024));
        Assert.assertEquals(fullGC.getPhases().size(), 4);
        for (GCEvent phase : fullGC.getPhases()) {
            Assert.assertTrue(phase.getStartTime() != UNKNOWN_DOUBLE);
            Assert.assertTrue(phase.getDuration() != UNKNOWN_DOUBLE);
        }
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_COMPUTE_NEW_OBJECT_ADDRESSES).getDuration(), 24.314, DELTA);
        Assert.assertEquals(fullGC.getLastPhaseOfType(GCEventType.SERIAL_MOVE_OBJECTS).getDuration(), 17.974, DELTA);
    }

}
