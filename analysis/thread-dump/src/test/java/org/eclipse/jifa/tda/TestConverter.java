/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 and CC0-1.0
 *
 * AI Disclosure: This file was largely AI-generated with GitHub Copilot.
 * The AI-generated portions are made available under CC0-1.0. The human
 * contributor has reviewed and verified the code.
 * Assisted-by: GitHub Copilot (Claude Sonnet 4.5)
 ********************************************************************************/

package org.eclipse.jifa.tda;

import org.eclipse.jifa.tda.util.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestConverter {

    // -----------------------------------------------------------------
    // parseSecureDouble – unit tests for the helper directly
    // -----------------------------------------------------------------

    @Test
    public void testParseSecureDouble_standardDot() {
        assertEquals(1.5,     Converter.parseSecureDouble("1.5"),     0.001);
        assertEquals(0.5,     Converter.parseSecureDouble("0.5"),     0.001);
        assertEquals(100.0,   Converter.parseSecureDouble("100"),     0.001);
        assertEquals(1234.56, Converter.parseSecureDouble("1234.56"), 0.001);
    }

    @Test
    public void testParseSecureDouble_germanDecimalComma() {
        // Single comma → decimal separator (German locale fallback)
        assertEquals(1.5,  Converter.parseSecureDouble("1,5"),  0.001);
        assertEquals(0.5,  Converter.parseSecureDouble("0,5"),  0.001);
        assertEquals(0.50, Converter.parseSecureDouble("0,50"), 0.001);
    }

    @Test
    public void testParseSecureDouble_ambiguousSingleComma() {
        // "1,234" – standard parse fails, German locale treats comma as decimal → 1.234
        // (correct for a JVM running in German locale; 1234 is not a valid time value here)
        assertEquals(1.234, Converter.parseSecureDouble("1,234"), 0.001);
    }

    @Test
    public void testParseSecureDouble_europeanThousandsAndDecimal() {
        // "1.234,56" – standard parse fails, German locale → 1234.56
        assertEquals(1234.56, Converter.parseSecureDouble("1.234,56"), 0.001);
        assertEquals(1000.0,  Converter.parseSecureDouble("1.000,0"),  0.001);
    }

    @Test
    public void testParseSecureDouble_whitespace() {
        assertEquals(1.5, Converter.parseSecureDouble("  1.5  "), 0.001);
        assertEquals(1.5, Converter.parseSecureDouble("  1,5  "), 0.001);
    }

    @Test
    public void testParseSecureDouble_invalid() {
        assertThrows(IllegalArgumentException.class, () -> Converter.parseSecureDouble("abc"));
        assertThrows(IllegalArgumentException.class, () -> Converter.parseSecureDouble(""));
    }

    // -----------------------------------------------------------------
    // str2TimeMillis – end-to-end tests
    // -----------------------------------------------------------------

    @Test
    public void testNull() {
        assertEquals(-1.0, Converter.str2TimeMillis(null));
    }

    @Test
    public void testMilliseconds_dot() {
        assertEquals(100.0, Converter.str2TimeMillis("100ms"),   0.001);
        assertEquals(100.5, Converter.str2TimeMillis("100.5ms"), 0.001);
    }

    @Test
    public void testMilliseconds_germanComma() {
        assertEquals(100.5, Converter.str2TimeMillis("100,5ms"), 0.001);
    }

    @Test
    public void testSeconds_dot() {
        assertEquals(500.0,  Converter.str2TimeMillis("0.5s"), 0.001);
        assertEquals(1000.0, Converter.str2TimeMillis("1s"),   0.001);
        assertEquals(1500.0, Converter.str2TimeMillis("1.5s"), 0.001);
    }

    @Test
    public void testSeconds_germanComma() {
        assertEquals(1500.0, Converter.str2TimeMillis("1,5s"), 0.001);
        assertEquals(500.0,  Converter.str2TimeMillis("0,5s"), 0.001);
    }

    @Test
    public void testSeconds_europeanThousandsAndDecimal() {
        // "1.234,56s" → German locale → 1234.56 s = 1_234_560 ms
        assertEquals(1234560.0, Converter.str2TimeMillis("1.234,56s"), 0.001);
    }

    @Test
    public void testUnknownFormat() {
        assertThrows(IllegalArgumentException.class, () -> Converter.str2TimeMillis("1.5x"));
        assertThrows(IllegalArgumentException.class, () -> Converter.str2TimeMillis("abc"));
    }
}
