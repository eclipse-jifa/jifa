/********************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.jifa.jfr.extractor;

import org.eclipse.jifa.jfr.util.StackTraceUtil;
import org.eclipse.jifa.jfr.model.TaskData;
import org.eclipse.jifa.jfr.model.jfr.RecordedEvent;
import org.eclipse.jifa.jfr.model.jfr.RecordedStackTrace;
import org.eclipse.jifa.jfr.model.jfr.RecordedThread;
import org.eclipse.jifa.jfr.model.TaskCount;
import org.eclipse.jifa.jfr.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class CountExtractor extends Extractor {
    CountExtractor(JFRAnalysisContext context, List<String> interested) {
        super(context, interested);
    }

    public static class TaskCountData extends TaskData {
        TaskCountData(RecordedThread thread) {
            super(thread);
        }

        long count;
    }

    protected final Map<Long, TaskCountData> data = new HashMap<>();

    TaskCountData getTaskCountData(RecordedThread thread) {
        return data.computeIfAbsent(thread.getJavaThreadId(), i -> new TaskCountData(thread));
    }

    protected void visitEvent(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }

        TaskCountData data = getTaskCountData(event.getThread());
        if (data.getSamples() == null) {
            data.setSamples(new HashMap<>());
        }

        data.getSamples().compute(stackTrace, (k, tmp) -> tmp == null ? 1 : tmp + 1);
        data.count += 1;
    }

    public List<TaskCount> buildTaskCounts() {
        List<TaskCount> counts = new ArrayList<>();
        for (TaskCountData data : this.data.values()) {
            if (data.count == 0) {
                continue;
            }

            TaskCount ts = new TaskCount();
            Task ta = new Task();
            ta.setId(data.getThread().getJavaThreadId());
            ta.setName(context.getThread(data.getThread()).getName());
            ts.setTask(ta);

            if (data.getSamples() != null) {
                ts.setCount(data.count);
                ts.setSamples(data.getSamples().entrySet().stream().collect(
                        Collectors.toMap(
                                e -> StackTraceUtil.build(e.getKey(), context.getSymbols()),
                                Map.Entry::getValue,
                                Long::sum)
                ));
            }

            counts.add(ts);
        }

        counts.sort((o1, o2) -> {
            long delta = o2.getCount() - o1.getCount();
            return delta > 0 ? 1 : (delta == 0 ? 0 : -1);
        });

        return counts;
    }
}
