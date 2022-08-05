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

package org.eclipse.jifa.gclog.diagnoser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.jifa.common.JifaException;
import org.eclipse.jifa.gclog.model.GCModel;
import org.eclipse.jifa.gclog.util.I18nStringView;
import org.eclipse.jifa.gclog.util.Key2ValueListMap;
import org.eclipse.jifa.gclog.vo.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.jifa.gclog.diagnoser.AbnormalSeverity.*;
import static org.eclipse.jifa.gclog.diagnoser.AbnormalType.*;
import static org.eclipse.jifa.gclog.model.GCEventType.*;

/**
 * To diagnose abnormal in gclog, we mainly try to analyze 3 things:
 * 1. what's going wrong
 * 2. why it is going wrong
 * 3. how to deal with it
 * Currently, we have just implemented finding global serious and definite problem
 * and give general suggestions based on phenomenon without analyzing cause specific cause.
 * In the future, we will
 * 1. do local diagnose on each event, find abnormal of event info, explain its cause
 * and give appropriate suggestion if necessary.
 * 2. Try to find accurate cause and give "the best" suggestion for those serious based on local diagnose.
 */
public class GlobalDiagnoser {
    private GCModel model;
    private AnalysisConfig config;

    private Key2ValueListMap<String, Double> allProblems = new Key2ValueListMap<>();
    private List<AbnormalPoint> mostSeriousProblemList = new ArrayList<>();
    private List<AbnormalPoint> mergedMostSeriousProblemList = new ArrayList<>();
    private AbnormalPoint mostSerious = new AbnormalPoint(LAST_TYPE, null, NONE);

    public GlobalDiagnoser(GCModel model, AnalysisConfig config) {
        this.model = model;
        this.config = config;
    }

    public GlobalAbnormalInfo diagnose() {
        findAllAbnormalPoints();
        mergeTimeRanges();
        return generateVo();
    }

    private void findAllAbnormalPoints() {
        for (Method rule : globalDiagnoseRules) {
            try {
                rule.invoke(this);
            } catch (Exception e) {
                e.printStackTrace();
                //todo: remove this line
                System.exit(1);
            }
        }
    }

    // We consider a problem is happening continuously if two adjacent abnormal points occurs
    // in one minute.
    private final static long MERGE_ABNORMAL_POINTS_THRESHOLD = 60000;

    private void mergeTimeRanges() {
        mostSeriousProblemList.sort(Comparator.comparingDouble(ab -> ab.getSite().getStartTime()));
        AbnormalPoint mergedEvent = null;
        double mergePoint = 0;
        for (AbnormalPoint ab : mostSeriousProblemList) {
            if (mergedEvent == null) {
                mergedEvent = ab.cloneExceptSite();
                // We assume that all events must have a start time, but end time may be unknown
                mergePoint = Math.max(ab.getSite().getStartTime(), ab.getSite().getEndTime())
                        + MERGE_ABNORMAL_POINTS_THRESHOLD;
            } else {
                double end = Math.max(ab.getSite().getStartTime(), ab.getSite().getEndTime());
                if (end <= mergePoint) {
                    double newMergePoint = Math.max(mergePoint, end + MERGE_ABNORMAL_POINTS_THRESHOLD);
                    double duration = newMergePoint - mergedEvent.getSite().getStartTime();
                    mergedEvent.getSite().setDuration(duration);
                } else {
                    mergedMostSeriousProblemList.add(mergedEvent);
                    mergedEvent = ab.cloneExceptSite();
                    mergePoint = Math.max(ab.getSite().getStartTime(), ab.getSite().getEndTime())
                            + MERGE_ABNORMAL_POINTS_THRESHOLD;
                }
            }
        }
        if (mergedEvent != null) {
            mostSeriousProblemList.add(mergedEvent);
        }
    }

    private GlobalAbnormalInfo generateVo() {
        MostSeriousProblemSummary summary = null;
        if (mergedMostSeriousProblemList.size() > 0) {
            mergedMostSeriousProblemList.sort(
                    (ab1, ab2) -> Double.compare(ab2.getSite().getDuration(), ab1.getSite().getDuration()));
            AbnormalPoint first = mergedMostSeriousProblemList.get(0);
            summary = new MostSeriousProblemSummary(
                    mergedMostSeriousProblemList.stream()
                            .limit(3)
                            .map(ab -> ab.getSite().toTimeRange())
                            .collect(Collectors.toList()),
                    new I18nStringView(first.getType().getName()),
                    first.generateDefaultSuggestions(model)
            );
        }
        return new GlobalAbnormalInfo(summary, allProblems.getInnerMap());
    }

    private static List<Method> globalDiagnoseRules = new ArrayList<>();

    static {
        initializeRules();
    }

    private static void initializeRules() {
        Method[] methods = GlobalDiagnoser.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getAnnotation(GlobalDiagnoseRule.class) != null) {
                method.setAccessible(true);
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod) || Modifier.isFinal(mod) ||
                        !(Modifier.isPublic(mod) || Modifier.isProtected(mod))) {
                    throw new JifaException("Illegal method modifier: " + method);
                }
                globalDiagnoseRules.add(method);
            }
        }
    }

    @GlobalDiagnoseRule
    protected void fullGC() {
        model.iterateEventsWithinTimeRange(model.getGcEvents(), config.getTimRange(), event -> {
            if (event.getEventType() != FULL_GC) {
                return;
            }
            GCCause cause = event.getCause();
            if (cause.isMetaspaceFullGCCause()) {
                addAbnormalPoint(new AbnormalPoint(METASPACE_FULL_GC, event, ULTRA));
            } else if (cause.isHeapMemoryTriggeredFullGCCause()) {
                addAbnormalPoint(new AbnormalPoint(HEAP_MEMORY_FULL_GC, event, ULTRA));
            } else if (cause == GCCause.SYSTEM_GC) {
                addAbnormalPoint(new AbnormalPoint(AbnormalType.SYSTEM_GC, event, HIGH));
            }
        });
    }

    private void addAbnormalPoint(AbnormalPoint point) {
        allProblems.put(point.getType().getName(), point.getSite().getStartTime());
        int compare = AbnormalPoint.compareByImportance.compare(point, mostSerious);
        if (compare > 0) {
            mostSeriousProblemList.clear();
        }
        if (compare >= 0) {
            mostSeriousProblemList.add(point);
        }
    }

    public GlobalDiagnoser(GCModel model) {
        this.model = model;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface GlobalDiagnoseRule {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalAbnormalInfo {
        private MostSeriousProblemSummary mostSeriousProblem;
        private Map<String, List<Double>> seriousProblem;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MostSeriousProblemSummary {
        private List<TimeRange> sites;
        private I18nStringView problem;
        private List<I18nStringView> suggestions;
    }
}
