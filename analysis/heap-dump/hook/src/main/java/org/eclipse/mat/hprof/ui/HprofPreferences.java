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

package org.eclipse.mat.hprof.ui;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.hprof.HprofPlugin;

public class HprofPreferences {

    public static final String STRICTNESS_PREF = "hprofStrictness"; //$NON-NLS-1$

    public static final HprofStrictness DEFAULT_STRICTNESS = HprofStrictness.STRICTNESS_STOP;

    public static final String ADDITIONAL_CLASS_REFERENCES = "hprofAddClassRefs"; //$NON-NLS-1$

    /** Whether to treat stack frames as pseudo-objects and methods as pseudo-classes */
    public static final String P_METHODS = "methodsAsClasses"; //$NON-NLS-1$
    public static final String NO_METHODS_AS_CLASSES = "none"; //$NON-NLS-1$
    public static final String RUNNING_METHODS_AS_CLASSES = "running"; //$NON-NLS-1$
    public static final String FRAMES_ONLY = "frames"; //$NON-NLS-1$

    public static ThreadLocal<HprofStrictness> TL = new ThreadLocal<>();

    public static void setStrictness(HprofStrictness strictness) {
        TL.set(strictness);
    }

    public static HprofStrictness getCurrentStrictness() {
        HprofStrictness strictness = TL.get();
        return strictness != null ? strictness : DEFAULT_STRICTNESS;
    }

    public static boolean useAdditionalClassReferences() {
        return Platform.getPreferencesService().getBoolean(HprofPlugin.getDefault().getBundle().getSymbolicName(),
                                                           HprofPreferences.ADDITIONAL_CLASS_REFERENCES, false, null);
    }

    public enum HprofStrictness {
        STRICTNESS_STOP("hprofStrictnessStop"), //$NON-NLS-1$

        STRICTNESS_WARNING("hprofStrictnessWarning"), //$NON-NLS-1$

        STRICTNESS_PERMISSIVE("hprofStrictnessPermissive"); //$NON-NLS-1$

        private final String name;

        HprofStrictness(String name) {
            this.name = name;
        }

        public static HprofStrictness parse(String value) {
            if (value != null && value.length() > 0) {
                for (HprofStrictness strictness : values()) {
                    if (strictness.toString().equals(value)) {
                        return strictness;
                    }
                }
            }
            return DEFAULT_STRICTNESS;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Preference for whether pseudo-objects should be created
     * for stack frames and methods.
     */
    public static String methodsAsClasses()
    {
        String pref = Platform.getPreferencesService().getString(HprofPlugin.getDefault().getBundle().getSymbolicName(),
                        HprofPreferences.P_METHODS, HprofPreferences.NO_METHODS_AS_CLASSES, null);
        return pref;
    }
}
