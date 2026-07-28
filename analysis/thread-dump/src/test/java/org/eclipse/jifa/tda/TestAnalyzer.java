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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
