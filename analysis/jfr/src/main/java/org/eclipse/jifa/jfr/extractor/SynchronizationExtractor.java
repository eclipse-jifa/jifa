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

import org.eclipse.jifa.jfr.common.EventConstant;
import org.eclipse.jifa.jfr.model.jfr.RecordedEvent;
import org.eclipse.jifa.jfr.model.DimensionResult;
import org.eclipse.jifa.jfr.model.AnalysisResult;
import org.eclipse.jifa.jfr.model.TaskSum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizationExtractor extends SumExtractor {
    protected static final List<String> INTERESTED = Collections.unmodifiableList(new ArrayList<>() {
        {
            add(EventConstant.JAVA_MONITOR_ENTER);
        }
    });

    public SynchronizationExtractor(JFRAnalysisContext context) {
        super(context, INTERESTED);
    }

    @Override
    void visitMonitorEnter(RecordedEvent event) {
        visitEvent(event, event.getDurationNano());
    }

    @Override
    void visitThreadPark(RecordedEvent event) {
        visitEvent(event, event.getDurationNano());
    }

    @Override
    public void fillResult(AnalysisResult result) {
        DimensionResult<TaskSum> tsResult = new DimensionResult<>();
        tsResult.setList(buildTaskSums());
        result.setSynchronization(tsResult);
    }
}
