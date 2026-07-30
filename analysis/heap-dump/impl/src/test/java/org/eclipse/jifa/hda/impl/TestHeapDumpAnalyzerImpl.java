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
package org.eclipse.jifa.hda.impl;

import com.sun.management.HotSpotDiagnosticMXBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jifa.analysis.listener.ProgressListener;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.hda.api.HeapDumpAnalyzer;
import org.eclipse.jifa.hda.api.Model;
import org.eclipse.jifa.hda.api.SearchType;
import org.eclipse.jifa.hdp.provider.HeapDumpAnalysisApiExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class TestHeapDumpAnalyzerImpl {

    /** Regex-safe marker searched for by the getStrings tests. */
    private static final String SENTINEL = "JIFA_GETSTRINGS_SENTINEL_c4f1";

    /**
     * Offset of {@link #SENTINEL} inside {@link #SENTINEL_HOLDER}: past the 256 characters that
     * survive in an object's display name, but within the 1024 that MAT resolves as its value.
     */
    private static final int SENTINEL_OFFSET = 300;

    /** Kept in a field so that the string stays reachable in the dump taken below. */
    @SuppressWarnings("unused")
    private static final String SENTINEL_HOLDER = "x".repeat(SENTINEL_OFFSET) + SENTINEL;

    private static Path DIRECTORY;

    private static HeapDumpAnalyzer ANALYZER;

    @BeforeAll
    public static void init() throws Exception {
        // init environment
        DIRECTORY = Files.createTempDirectory("test-heap-dir");
        Path heapFile = Files.createTempFile(DIRECTORY, "test-heap", ".hprof").toAbsolutePath();
        Files.delete(heapFile);
        HotSpotDiagnosticMXBean platformMXBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        platformMXBean.dumpHeap(heapFile.toString(), false);
        Method buildAnalyzer = HeapDumpAnalysisApiExecutor.class.getDeclaredMethod("buildAnalyzer", Path.class, Map.class, ProgressListener.class);
        buildAnalyzer.setAccessible(true);
        ANALYZER = (HeapDumpAnalyzer) buildAnalyzer.invoke(new HeapDumpAnalysisApiExecutor(),
                                                           heapFile,
                                                           Collections.emptyMap(),
                                                           ProgressListener.NoOpProgressListener);
    }

    @AfterAll
    public static void clean() {
        if (DIRECTORY != null) {
            FileUtils.deleteQuietly(DIRECTORY.toFile());
        }
    }

    @Test
    public void testGetDetails() {
        ANALYZER.getDetails();
    }

    @Test
    public void testGetSystemProperties() {
        ANALYZER.getSystemProperties();
    }

    @Test
    public void testGetBiggestObjects() {
        ANALYZER.getBiggestObjects();
    }

    @Test
    public void testGetObjectInfo() {
        ANALYZER.getObjectInfo(1);
    }

    @Test
    public void testGetInspectorView() {
        ANALYZER.getInspectorView(1);
    }

    @Test
    public void testGetFields() {
        ANALYZER.getFields(1, 1, 10);
    }

    @Test
    public void testGetStaticFields() {
        ANALYZER.getStaticFields(1, 1, 10);
    }

    @Test
    public void mapAddressToId() {
        ANALYZER.mapAddressToId(ANALYZER.getInspectorView(1).objectAddress);
    }

    @Test
    public void testGetObjectValue() {
        ANALYZER.getObjectValue(1);
    }

    @Test
    public void testGetSummaryOfClassLoaders() {
        ANALYZER.getSummaryOfClassLoaders();
    }

    @Test
    public void testGetClassLoaders() {
        ANALYZER.getClassLoaders(1, 10);
    }

    @Test
    public void testGetChildrenOfClassLoader() {
        Model.ClassLoader.Item item = ANALYZER.getClassLoaders(1, 1).getData().get(0);
        ANALYZER.getChildrenOfClassLoader(item.objectId, 1, 10);
    }

    @Test
    public void testGetSummaryOfUnreachableObjects() {
        ANALYZER.getSummaryOfUnreachableObjects();
    }

    @Test
    public void testGetUnreachableObjects() {
        ANALYZER.getUnreachableObjects(1, 10);
    }

    @Test
    public void testGetSummaryOfDirectByteBuffers() {
        ANALYZER.getSummaryOfDirectByteBuffers();
    }

    @Test
    public void testGetDirectByteBuffers() {
        ANALYZER.getDirectByteBuffers(1, 10);
    }

    @Test
    public void testGetOutboundOfObject() {
        ANALYZER.getOutboundOfObject(1, 1, 10);
    }

    @Test
    public void testGetInboundOfObject() {
        ANALYZER.getInboundOfObject(1, 1, 10);
    }

    @Test
    public void testGetGCRoots() {
        ANALYZER.getGCRoots();
    }

    @Test
    public void testGetStrings() {
        ANALYZER.getStrings("abc", 1, 10);
    }

    /**
     * find_strings must match the string value, not the display name. Matching the display name
     * would make every String match its own class name and address.
     */
    @Test
    public void testGetStringsDoesNotMatchDisplayNamePrefix() {
        int all = ANALYZER.getStrings("", 1, 10).getTotalSize();
        assertTrue(all > 0, "the dump is expected to contain strings");
        assertTrue(ANALYZER.getStrings("java\\.lang\\.String @ 0x", 1, 10).getTotalSize() < all,
                   "searching the display name prefix must not match every string");
    }

    /**
     * The pattern must be matched against the whole string value. MAT resolves the value up to
     * 1024 characters, whereas the display name exposed as the result's first column truncates it
     * at 256, so the string holding the sentinel past that point is reported with a label that does
     * not contain the sentinel at all. Such a hit can only be produced by matching the value, which
     * is what makes this a regression guard against filtering on the label instead.
     */
    @Test
    public void testGetStringsMatchesValueBeyondDisplayNameTruncation() {
        PageView<Model.TheString.Item> hits = ANALYZER.getStrings(SENTINEL, 1, 100);
        assertTrue(hits.getData().stream().anyMatch(item -> !item.getLabel().contains(SENTINEL)),
                   "expected a hit whose label does not contain the sentinel, i.e. the string " +
                   "holding it at offset " + SENTINEL_OFFSET + ", got: " + hits.getData());
    }

    /** Paging must be served from the same result set, i.e. sizes stay stable across pages. */
    @Test
    public void testGetStringsPagingIsStable() {
        PageView<Model.TheString.Item> first = ANALYZER.getStrings("java", 1, 5);
        PageView<Model.TheString.Item> second = ANALYZER.getStrings("java", 2, 5);
        assertEquals(first.getTotalSize(), second.getTotalSize());
    }

    @Test
    public void testGetClassesOfGCRoot() {
        ANALYZER.getClassesOfGCRoot(1, 1, 10);
    }

    @Test
    public void testGetObjectsOfGCRoot() {
        ANALYZER.getObjectsOfGCRoot(1, 1, 1, 10);
    }

    @Test
    public void testGetOutboundClassOfClassReference() {
        ANALYZER.getOutboundClassOfClassReference(1);
    }

    @Test
    public void testGetInboundClassOfClassReference() {
        ANALYZER.getInboundClassOfClassReference(1);
    }

    @Test
    public void testGetOutboundsOfClassReference() {
        ANALYZER.getOutboundsOfClassReference(new int[]{1}, 1, 10);
    }

    @Test
    public void testGetInboundsOfClassReference() {
        ANALYZER.getInboundsOfClassReference(new int[]{1}, 1, 10);
    }

    @Test
    public void testGetPathToGCRoots() {
        ANALYZER.getPathToGCRoots(1, 1, 10);
    }

    @Test
    public void testGetLeakReport() {
        ANALYZER.getLeakReport();
    }

    @Test
    public void testGetOQLResult() {
        ANALYZER.getOQLResult("select * from java.lang.String", "shallowHeap", true, 1, 10);
    }

    @Test
    public void testGetCalciteSQLResult() {
    }

    @Test
    public void testGetSummaryOfThreads() {
    }

    @Test
    public void testGetThreads() {
        ANALYZER.getThreads("id", true, "", SearchType.BY_NAME, 1, 10);
    }

    @Test
    public void testGetStackTrace() {
        int id = ANALYZER.getThreads("id", true, "", SearchType.BY_NAME, 1, 1).getData().get(0).getObjectId();
        ANALYZER.getStackTrace(id);
    }

    @Test
    public void testGetLocalVariables() {
        int id = ANALYZER.getThreads("id", true, "", SearchType.BY_NAME, 1, 1).getData().get(0).getObjectId();
        ANALYZER.getLocalVariables(id, 1, false);
    }

    @Test
    public void testGetDuplicatedClasses() {
        ANALYZER.getDuplicatedClasses("", SearchType.BY_NAME, 1, 10);
    }

    @Test
    public void testGetHistogram() {
        ANALYZER.getHistogram(Model.Histogram.Grouping.BY_CLASS,
                              null,
                              "shallowHeap",
                              true,
                              null,
                              null, 1, 10);
    }

    @Test
    public void testGetHistogramObjects() {
        int id = ANALYZER.getHistogram(Model.Histogram.Grouping.BY_CLASS,
                                       null,
                                       "shallowHeap",
                                       true,
                                       null,
                                       null, 1, 1).getData().get(0).getObjectId();
        ANALYZER.getHistogramObjects(id, 1, 10);
    }

    @Test
    public void testGetRootsOfDominatorTree() {
        ANALYZER.getRootsOfDominatorTree(Model.DominatorTree.Grouping.NONE, "shallowHeap", true, null, SearchType.BY_NAME, 1, 10);
    }
}
