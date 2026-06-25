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

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class Converter {

    /**
     * Converts a time string as produced by JVM thread dumps into milliseconds.
     * <p>
     * Accepted unit suffixes are {@code ms} (milliseconds) and {@code s} (seconds).
     * The numeric part is parsed via {@link #parseSecureDouble(String)}, which
     * handles both dot ({@code '.'}) and comma ({@code ','}) decimal separators.
     * <pre>
     *   "0.50s"     →    500.0 ms  (standard/US)
     *   "1,5s"      →   1500.0 ms  (German locale decimal comma)
     *   "1.234,56s" → 1234560.0 ms  (European thousands + decimal comma)
     *   "100ms"     →    100.0 ms
     *   "100,5ms"   →    100.5 ms  (German locale)
     * </pre>
     *
     * @param str the time string from the thread dump
     * @return the time in milliseconds, or {@code -1} if {@code str} is {@code null}
     * @throws IllegalArgumentException if the string has an unrecognised unit suffix
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

    /**
     * Parses a numeric string that may use either {@code '.'} or {@code ','} as
     * the decimal separator, using a safe two-step strategy:
     * <ol>
     *   <li>Attempt {@link Double#parseDouble(String)} (US / standard notation,
     *       e.g. {@code "1.5"}, {@code "100"}).</li>
     *   <li>On {@link NumberFormatException}, fall back to
     *       {@link NumberFormat#getInstance(Locale) NumberFormat.getInstance(Locale.GERMANY)},
     *       which correctly handles the German/European decimal comma and
     *       dot-as-thousands-separator
     *       (e.g. {@code "1,5"} → {@code 1.5},
     *            {@code "1.234,56"} → {@code 1234.56}).</li>
     * </ol>
     * <p>
     * This two-step approach avoids the ambiguity of position-based heuristics.
     * A string such as {@code "1,234"} (single comma only) is unambiguously
     * handled: standard parsing fails, German-locale parsing interprets the comma
     * as a decimal separator, yielding {@code 1.234} – the semantically correct
     * value for a time produced by a JVM running in a German locale.
     *
     * @param s the numeric string to parse; leading/trailing whitespace is trimmed
     * @return the parsed {@code double} value
     * @throws IllegalArgumentException if {@code s} cannot be parsed by either strategy
     */
    public static double parseSecureDouble(String s) {
        String clean = s.trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ignore) {
            // fall through
        }
        try {
            java.text.ParsePosition pos = new java.text.ParsePosition(0);
            Number n = NumberFormat.getInstance(Locale.US).parse(clean, pos);
            if (n != null && pos.getIndex() == clean.length()) {
                return n.doubleValue();
            }
            throw new ParseException("Unparseable number: \"" + clean + "\"", pos.getErrorIndex());
        } catch (ParseException usEx) {
            try {
                java.text.ParsePosition pos = new java.text.ParsePosition(0);
                Number n = NumberFormat.getInstance(Locale.GERMANY).parse(clean, pos);
                if (n != null && pos.getIndex() == clean.length()) {
                    return n.doubleValue();
                }
                throw new ParseException("Unparseable number: \"" + clean + "\"", pos.getErrorIndex());
            } catch (ParseException deEx) {
                throw new IllegalArgumentException("Cannot parse '" + s + "' as a number", deEx);
            }
        }
    }
}
