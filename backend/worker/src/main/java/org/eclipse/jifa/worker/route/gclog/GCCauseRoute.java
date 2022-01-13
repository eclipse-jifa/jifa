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

package org.eclipse.jifa.worker.route.gclog;

import org.eclipse.jifa.gclog.model.GCModel;
import io.vertx.core.Promise;
import org.eclipse.jifa.worker.route.ParamKey;
import org.eclipse.jifa.worker.route.RouteMeta;
import org.eclipse.jifa.worker.support.Analyzer;

import java.util.List;

public class GCCauseRoute extends GCLogBaseRoute {
    @RouteMeta(path = "/gcCause")
    void basicInfo(Promise<List<GCModel.GCCauseInfo>> promise, @ParamKey("file") String file) {
        final GCModel model = Analyzer.getOrOpenGCLogModel(file);
        promise.complete(model.getGCCauseInfo());
    }
}