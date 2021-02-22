/********************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.jifa.worker.route.heapdump;

import io.vertx.core.Future;
import org.eclipse.jifa.common.request.PagingRequest;
import org.eclipse.jifa.common.vo.PageView;
import org.eclipse.jifa.common.vo.support.SearchType;
import org.eclipse.jifa.worker.route.ParamKey;
import org.eclipse.jifa.worker.route.RouteMeta;

import static org.eclipse.jifa.hda.api.Model.DuplicatedClass;
import static org.eclipse.jifa.worker.support.Analyzer.getOrOpenAnalysisContext;
import static org.eclipse.jifa.worker.support.hda.AnalysisEnv.HEAP_DUMP_ANALYZER;

class DuplicatedClassesRoute extends HeapBaseRoute {

    @RouteMeta(path = "/duplicatedClasses/classes")
    void classRecords(Future<PageView<DuplicatedClass.ClassItem>> future, @ParamKey("file") String file,
                      @ParamKey(value = "searchText", mandatory = false) String searchText,
                      @ParamKey(value = "searchType", mandatory = false) SearchType searchType,
                      PagingRequest pagingRequest) {
        future.complete(HEAP_DUMP_ANALYZER.getDuplicatedClasses(getOrOpenAnalysisContext(file)
            , searchText, searchType, pagingRequest.getPage(), pagingRequest.getPageSize()));
    }

    @RouteMeta(path = "/duplicatedClasses/classLoaders")
    void classLoaderRecords(Future<PageView<DuplicatedClass.ClassLoaderItem>> future, @ParamKey("file") String file,
                            @ParamKey("index") int index,
                            PagingRequest pagingRequest) {
        future.complete(HEAP_DUMP_ANALYZER.getClassloadersOfDuplicatedClass(getOrOpenAnalysisContext(file)
            , index, pagingRequest.getPage(), pagingRequest.getPageSize()));
    }
}
