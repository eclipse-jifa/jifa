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

import org.eclipse.jifa.analysis.listener.DefaultProgressListener;
import org.eclipse.jifa.common.domain.request.PagingRequest;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.tda.enums.ThreadType;
import org.eclipse.jifa.tda.vo.Content;
import org.eclipse.jifa.tda.vo.Overview;
import org.eclipse.jifa.tda.vo.VFrame;
import org.eclipse.jifa.tda.vo.VMonitor;
import org.eclipse.jifa.tda.vo.VThread;
import org.eclipse.jifa.tda.vo.VThreadDelta;
import org.eclipse.jifa.tda.vo.VThreadStateChange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestAnalyzer extends TestBase {

    @Test
    public void test() throws Exception {
        ThreadDumpAnalyzer tda =
            new ThreadDumpAnalyzer(pathOfResource("jstack_8.log"), new DefaultProgressListener());
        Overview o1 = tda.overview();
        Overview o2 = tda.overview();
        Assertions.assertEquals(o1, o2);
        Assertions.assertEquals(o1.hashCode(), o2.hashCode());

        PageView<VThread> threads = tda.threads("main", ThreadType.JAVA, null, null, new PagingRequest(1, 1));
        Assertions.assertEquals(1, threads.getTotalSize());

        PageView<VFrame> frames = tda.callSiteTree(0, new PagingRequest(1, 16));
        Assertions.assertTrue(frames.getTotalSize() > 0);
        Assertions.assertNotEquals(frames.getData().get(0), frames.getData().get(1));

        PageView<VMonitor> monitors = tda.monitors(new PagingRequest(1, 8));
        Assertions.assertTrue(monitors.getTotalSize() > 0);

        Content line2 = tda.content(2, 1);
        Assertions.assertEquals("Full thread dump OpenJDK 64-Bit Server VM (18-internal+0-adhoc.denghuiddh.my-jdk mixed " +
                            "mode, sharing):", line2.getContent().get(0));
    }

    @Test
    public void testThreadStateChanges_sameDump() throws Exception {
        // Comparing a dump to itself: no state changes, no disappeared, no new threads
        ThreadDumpAnalyzer tda = new ThreadDumpAnalyzer(pathOfResource("jstack_17_with_blocked.log"),
                                                         new DefaultProgressListener());
        List<VThreadStateChange> changes = tda.threadStateChanges(
                pathOfResource("jstack_17_with_blocked.log"));

        Assertions.assertTrue(changes.isEmpty(),
                "Comparing a dump to itself should yield no state changes");
    }

    @Test
    public void testThreadStateChanges_differentDumps() throws Exception {
        // jstack_17_with_blocked has BLOCKED threads; jstack_8 does not → threads appear/disappear
        ThreadDumpAnalyzer tda = new ThreadDumpAnalyzer(pathOfResource("jstack_17_with_blocked.log"),
                                                         new DefaultProgressListener());
        List<VThreadStateChange> changes = tda.threadStateChanges(pathOfResource("jstack_8.log"));

        // Different JVMs: all threads disappeared from dump1, all threads in dump2 are new
        long disappeared = changes.stream().filter(c -> c.getStateBefore() != null && c.getStateAfter() == null).count();
        long newThreads  = changes.stream().filter(c -> c.getStateBefore() == null && c.getStateAfter() != null).count();
        Assertions.assertTrue(disappeared > 0, "Some threads should have disappeared");
        Assertions.assertTrue(newThreads  > 0, "Some threads should be new");
    }

    @Test
    public void testPersistentBlockers_sameDump() throws Exception {
        // Same dump: all blocked threads are "persistent" (blocked in both)
        ThreadDumpAnalyzer tda = new ThreadDumpAnalyzer(pathOfResource("jstack_17_with_blocked.log"),
                                                         new DefaultProgressListener());
        List<VThread> blockers = tda.persistentBlockers(pathOfResource("jstack_17_with_blocked.log"));

        // jstack_17_with_blocked has 4 BLOCKED threads (pool-1-thread-1,3,4,5)
        Assertions.assertEquals(4, blockers.size());
        // Results are sorted by name
        Assertions.assertEquals("pool-1-thread-1", blockers.get(0).getName());
    }

    @Test
    public void testPersistentBlockers_noOverlap() throws Exception {
        // jstack_8 has no BLOCKED threads → 0 persistent blockers
        ThreadDumpAnalyzer tda = new ThreadDumpAnalyzer(pathOfResource("jstack_17_with_blocked.log"),
                                                         new DefaultProgressListener());
        List<VThread> blockers = tda.persistentBlockers(pathOfResource("jstack_8.log"));

        Assertions.assertTrue(blockers.isEmpty(), "No persistent blockers expected across different JVMs");
    }

    @Test
    public void testCpuConsumingThreadsCompare_sameDump() throws Exception {
        // Same dump: delta is 0 for every matched thread, sorted descending (all 0)
        ThreadDumpAnalyzer tda = new ThreadDumpAnalyzer(pathOfResource("jstack_17_with_blocked.log"),
                                                         new DefaultProgressListener());
        List<VThreadDelta> deltas = tda.cpuConsumingThreadsCompare(
                pathOfResource("jstack_17_with_blocked.log"), null, -1);

        Assertions.assertFalse(deltas.isEmpty(), "Should find threads with CPU data");
        for (VThreadDelta d : deltas) {
            Assertions.assertEquals(0.0, d.getCpuDelta(), 0.001,
                    "Delta must be 0 when comparing a dump to itself");
            Assertions.assertEquals(d.getCpuFirst(), d.getCpuSecond(), 0.001);
        }
    }
}
