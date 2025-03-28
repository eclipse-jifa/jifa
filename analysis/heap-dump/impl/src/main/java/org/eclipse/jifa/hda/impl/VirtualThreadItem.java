/********************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.jifa.common.annotation.UseAccessor;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.IResultTree;

import static org.eclipse.jifa.hda.api.Model.Thread;

@UseAccessor
public class VirtualThreadItem extends Thread.Item {
    static final int COLUMN_OBJECT = 0;
    static final int COLUMN_NAME = 1;
    static final int COLUMN_SHALLOW = 2;
    static final int COLUMN_RETAINED = 3;

    // changes depending on MAT report results
    final int COLUMN_CONTEXT_CLASS_LOADER;

    final int COLUMN_DAEMON;

    transient final IResultTree result;
    transient final Object row;

    public VirtualThreadItem(final IResultTree result, final Object row) {
        this.row = row;
        this.result = result;
        this.objectId = result.getContext(row).getObjectId();

        // the report changed a little in MAT:
        // Bug 572596 Add maximum retained heap size to thread overview stack
        // a row was injected at column position 5, so the daemon column may have been
        // pushed out to column 6
        boolean includesMaxLocalRetained = (result.getColumns().length == 10);
        this.COLUMN_CONTEXT_CLASS_LOADER = includesMaxLocalRetained ? 5 : 4;
        this.COLUMN_DAEMON = includesMaxLocalRetained ? 6 : 5;
    }

    @Override
    public int getObjectId() {
        return objectId;
    }

    @Override
    public String getObject() {
        return (String) result.getColumnValue(row, COLUMN_OBJECT);
    }

    @Override
    public String getName() {
        return (String) result.getColumnValue(row, COLUMN_NAME);
    }

    @Override
    public long getShallowSize() {
        return ((Bytes) result.getColumnValue(row, COLUMN_SHALLOW)).getValue();
    }

    @Override
    public long getRetainedSize() {
        return ((Bytes) result.getColumnValue(row, COLUMN_RETAINED)).getValue();
    }

    @Override
    public String getContextClassLoader() {
        return (String) result.getColumnValue(row, COLUMN_CONTEXT_CLASS_LOADER);
    }

    @Override
    public boolean isHasStack() {
        return result.hasChildren(row);
    }

    @Override
    public boolean isDaemon() {
        Object value = result.getColumnValue(row, COLUMN_DAEMON);
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
