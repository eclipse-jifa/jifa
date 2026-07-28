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

package org.eclipse.jifa.tda.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Converter {

    /**
     * Converts time string (e.g., "1.5s", "100ms") to milliseconds.
     * Supports both dot and comma decimal separators (US and European notation).
     * <pre>
     *   "0.50s"     →    500.0 ms
     *   "1,5s"      →   1500.0 ms
     *   "1.234,56s" → 1234560.0 ms
     * </pre>
     *
     * @param str time string with suffix "ms" or "s"
     * @return milliseconds, or {@code -1} if {@code str} is {@code null}
     */
    public static double str2TimeMillis(String str) {
        if (str == null) {
            return -1;
        }
        int length = str.length();
        if (str.endsWith("ms")) {
            return parseSecureDouble(str.substring(0, length - 2));
        } else if (str.endsWith("s")) {
            return parseSecureDouble(str.substring(0, length - 1)) * 1000;
        }
        throw new IllegalArgumentException(str);
    }

    /** Parses numeric strings with both dot and comma decimal separators. */
    public static double parseSecureDouble(String s) {
        String clean = s.trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ignore) {
            // Fallback: parse with explicit DecimalFormatSymbols to handle comma decimal
            try {
                DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
                symbols.setDecimalSeparator(',');  // Accept comma as decimal separator
                DecimalFormat df = new DecimalFormat("#.##", symbols);
                df.setParseBigDecimal(false);
                return df.parse(clean).doubleValue();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot parse '" + s + "' as a number", ex);
            }
        }
    }
}
