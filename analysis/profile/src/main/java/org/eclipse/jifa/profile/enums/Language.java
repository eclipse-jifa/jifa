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
package org.eclipse.jifa.profile.enums;

public enum Language {
    JAVA("java"),
    GO("go");

    private final String lang;

    Language(String lang) {
        this.lang = lang;
    }

    public static boolean isJava(String lang) {
        return JAVA.lang.equalsIgnoreCase(lang);
    }

    public static boolean isGo(String lang) {
        return GO.lang.equalsIgnoreCase(lang);
    }
}