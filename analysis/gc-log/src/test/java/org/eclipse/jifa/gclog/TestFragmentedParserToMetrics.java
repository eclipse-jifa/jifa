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
package org.eclipse.jifa.gclog;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.gclog.fragment.GCLogAnalyzer;
import org.eclipse.jifa.gclog.fragment.Metric;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class TestFragmentedParserToMetrics {
    public static final double DELTA = 1e-6;
    private static final String INSTANCEIDKEY = "instanceId", INSTANCEIDVALUE = "test-instanceId";

    private List<Metric> parse(List<String> context, long startTime, long endTime) {
        GCLogAnalyzer gcLogAnalyzer = new GCLogAnalyzer();
        List<Metric> result = null;
        Map<String, String> instanceId = new ImmutableMap.Builder<String, String>().put(INSTANCEIDKEY, INSTANCEIDVALUE).build();
        try {
            result = gcLogAnalyzer.parseToMetrics(context, instanceId, startTime, endTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // This is barely a template, fill the file set to evaluate analyse speed.
    public void testAnalyseSpeed() {
        Set<String> files = new ImmutableSet.Builder<String>().build();
        GCLogAnalyzer gcLogAnalyzer = new GCLogAnalyzer();
        files.forEach(file -> {
            List<String> gclog = TestUtil.generateShuffledGCLog(file);
            Map<String, String> instanceId = new ImmutableMap.Builder<String, String>().put(INSTANCEIDKEY, file).build();
            long beginTime = System.currentTimeMillis();
            List<Metric> result = null;
            try {
                result = gcLogAnalyzer.parseToMetrics(gclog, instanceId, 0, Long.MAX_VALUE);
            } catch (Exception e) {
                e.printStackTrace();
            }
            double spendTime = (System.currentTimeMillis() - beginTime) / 1000.;
            System.out.printf("file %s analysed, %d lines in total, %d metrics produced, duration %fs, %f lines processed per minute, producing %f metrics\n",
                    file, gclog.size(), result.size(), spendTime, gclog.size()/spendTime*60, result.size()/spendTime*60);
        });
    }

    @Test
    public void testMetricLength() {
        Map<String, Integer> metricSizeForEachGCLog = new ImmutableMap.Builder<String, Integer>()
                .put("11G1Parser.log", 50)
                .put("8CMSParser.log", 130)
                .put("8CMSPrintGC.log", 40)
                .put("8G1PrintGC.log", 47)
                .put("8ParallelGCParser.log", 75)
                .put("8G1GCParser.log", 77)
                .put("8G1GCParserAdaptiveSize.log", 28)
                .put("8ConcurrentPrintDateTimeStamp.log", 65)
                .put("8CMSCPUTime.log", 26)
                .put("8CMSPromotionFailed.log", 13)
                .put("8CMSScavengeBeforeRemark.log", 38)
                .put("8GenerationalGCInterleave.log", 13)
                .build();
        metricSizeForEachGCLog.forEach((gclog, size) ->
                Assertions.assertEquals(Integer.valueOf(parse(TestUtil.generateShuffledGCLog(gclog), 0, Long.MAX_VALUE).size()), size));
    }

    @Test
    public void testMetricContent() {
        List<Metric> metrics = parse(TestUtil.generateShuffledGCLog("8CMSCPUTime.log"), 1669618629000L, 1669618630000L);

        Map<String, Map<String, Double>> valueForEachMetric = new ImmutableMap.Builder<String, Map<String, Double>>()
                .put("GC_CPU_USED", new ImmutableMap.Builder<String, Double>()
                        .put("USER", 180.0).put("SYS", 30.0).put("REAL", 50.0).build())
                .put("GC_PAUSE_TIME", new ImmutableMap.Builder<String, Double>()
                        .put("Young GC", 50.0).build())
                .put("BEFORE_GC_REGION_SIZE", new ImmutableMap.Builder<String, Double>()
                        .put("Young", 1761607680.).put("Old", 0.).put("Heap", 1761607680.).build())
                .put("AFTER_GC_REGION_SIZE", new ImmutableMap.Builder<String, Double>()
                        .put("Young", 36896768.).put("Old", 0.).put("Heap", 36896768.).build())
                .build();

        Assertions.assertEquals(metrics.size(), 12);
        valueForEachMetric.forEach((metricName, labelMap) ->
                labelMap.forEach((label, value) -> {
                    List<Metric> actualMetrics = metrics.stream()
                            .filter(metric -> metric.getName().equals(metricName))
                            .filter(metric -> metric.getLabel().get("type").equals(label))
                            .filter(metric -> metric.getTimestamp() == 1669618629974L)
                            .filter(metric -> metric.getLabel().get("gc_type").equals("CMS GC"))
                            .filter(metric -> metric.getLabel().get(INSTANCEIDKEY).equals(INSTANCEIDVALUE))
                            .collect(Collectors.toList());
                    Assertions.assertEquals(actualMetrics.size(), 1);
                    Assertions.assertEquals(actualMetrics.get(0).getValue(), value, DELTA);
                })
        );
    }

    @Test
    public void testMetricContentSubphase() {
        List<Metric> metrics = parse(TestUtil.generateShuffledGCLog("11CMSUpTime.log"), 0, Long.MAX_VALUE);
        Map<String, Double> subphaseMap = new ImmutableMap.Builder<String, Double>()
                .put("Initial Mark", 3.41)
                .put("Concurrent Mark", 136.57)
                .put("Concurrent Preclean", 4.724)
                .put("Concurrent Abortable preclean", 197.909)
                .put("Final Remark", 75.082)
                .put("Concurrent Sweep", 166.162)
                .put("Concurrent Reset", 609.769)
                .build();

        subphaseMap.forEach((subphase, value) -> {
            List<Metric> actualMetrics = metrics.stream()
                    .filter(metric -> metric.getTimestamp() == 1693292160355L)
                    .filter(metric -> metric.getName().equals("GC_SUBPHASE_TIME"))
                    .filter(metric -> metric.getLabel().get("gc_type").equals("CMS GC"))
                    .filter(metric -> metric.getLabel().get(INSTANCEIDKEY).equals(INSTANCEIDVALUE))
                    .filter(metric -> metric.getLabel().get("type").equals("CMS"))
                    .filter(metric -> metric.getLabel().get("subphase").equals(subphase))
                    .collect(Collectors.toList());
            Assertions.assertEquals(actualMetrics.size(), 1);
            Assertions.assertEquals(actualMetrics.get(0).getValue(), value, DELTA);
        });
    }
}
