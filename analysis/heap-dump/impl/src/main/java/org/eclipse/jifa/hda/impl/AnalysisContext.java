/********************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.jifa.hda.api.Model;
import org.eclipse.jifa.hda.api.Model.LeakReport;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.snapshot.ISnapshot;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AnalysisContext {

    final ISnapshot snapshot;

    volatile SoftReference<ClassLoaderExplorerData> classLoaderExplorerData = new SoftReference<>(null);

    volatile SoftReference<DirectByteBufferData> directByteBufferData = new SoftReference<>(null);

    volatile SoftReference<LeakReportData> leakReportData= new SoftReference<>(null);

    /**
     * Cache for find_strings results, keyed by the MAT pattern actually used. Keying by pattern
     * rather than caching every String in the heap keeps the retained set proportional to what was
     * asked for, and still serves the common case of paging through one search result.
     */
    final ConcurrentHashMap<String, SoftReference<StringData>> stringsCache = new ConcurrentHashMap<>();

    /** Cache for merge_shortest_paths IResultTree, keyed by the requested objectIds */
    final ConcurrentHashMap<MergePathTreeCacheKey, SoftReference<IResultTree>> mergePathTreeCache = new ConcurrentHashMap<>();

    /**
     * Monitor for populating {@link #mergePathTreeCache}. Not the context monitor: this class is
     * public and shared, so running a minutes-long BFS while holding its monitor would block
     * unrelated code that locks on the context (e.g. the classLoaderExplorerData path).
     * <p>
     * This is a single lock rather than a per-key one, so the first BFS for one set of objectIds
     * serializes the first BFS for any other set. That is an accepted trade-off for the paged UI
     * callers, which rarely request different object sets concurrently; cache hits never take the
     * lock. If concurrent distinct BFS runs become a requirement, switch to per-key memoization
     * (e.g. a FutureTask per key) rather than widening this lock.
     */
    final Object mergePathTreeLock = new Object();

    /** Cache for the final LeakReport Java object */
    volatile SoftReference<LeakReport> leakReportCache = new SoftReference<>(null);

    /**
     * Monitor for building the leak report. leakhunter runs a full-heap BFS that can take minutes,
     * so it must not be done while holding the context monitor, which is also used by the
     * classLoaderExplorerData path and is lockable by any code holding this public context.
     */
    final Object leakReportLock = new Object();

    AnalysisContext(ISnapshot snapshot) {
        this.snapshot = snapshot;
    }

    static class ClassLoaderExplorerData {

        IResultTree result;

        // classloader object Id -> record
        Map<Integer, Object> classLoaderIdMap;

        List<?> items;

        int definedClasses;

        int numberOfInstances;
    }

    static class DirectByteBufferData {
        static final String OQL =
            "SELECT s.@displayName as label, s.position as position, s.limit as limit, s.capacity as " +
            "capacity FROM java.nio.DirectByteBuffer s where s.cleaner != null";

        static final Map<String, Object> ARGS = new HashMap<>(1);
        static {
            ARGS.put("queryString", OQL);
        }

        RefinedTable resultContext;

        Model.DirectByteBuffer.Summary summary;

        public String label(Object row) {
            return (String) resultContext.getColumnValue(row, 0);
        }

        public int position(Object row) {
            return (Integer) resultContext.getColumnValue(row, 1);
        }

        public int limit(Object row) {
            return (Integer) resultContext.getColumnValue(row, 2);
        }

        public int capacity(Object row) {
            return (Integer) resultContext.getColumnValue(row, 3);
        }

    }

    static class LeakReportData {
        IResult result;
    }

    static class StringData {
        IResultTree tree;
        List<?> nodes;
    }

    static class MergePathTreeCacheKey {
        final int[] objectIds;
        final int hash;

        MergePathTreeCacheKey(int[] objectIds) {
            // Copy so the key is immune to later mutation of the caller's array, and sort so that
            // the same object set requested in a different order still hits the cache. The
            // merge_shortest_paths result does not depend on the input order, which only affects
            // sibling enumeration order in the resulting tree, and no caller relies on that.
            this.objectIds = objectIds.clone();
            Arrays.sort(this.objectIds);
            this.hash = Arrays.hashCode(this.objectIds);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MergePathTreeCacheKey)) return false;
            return Arrays.equals(objectIds, ((MergePathTreeCacheKey) o).objectIds);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AnalysisContext that = (AnalysisContext) o;
        return Objects.equals(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshot);
    }
}
