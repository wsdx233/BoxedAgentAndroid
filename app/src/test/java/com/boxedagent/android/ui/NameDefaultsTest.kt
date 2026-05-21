package com.boxedagent.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameDefaultsTest {
    @Test
    fun incrementTrailingNumberNameIncrementsFinalNumber() {
        assertEquals("Session 2", incrementTrailingNumberName("Session 1"))
        assertEquals("box10", incrementTrailingNumberName("box9"))
        assertEquals("alpha-100", incrementTrailingNumberName("alpha-099"))
        assertEquals("Agent 8  ", incrementTrailingNumberName("Agent 7  "))
    }

    @Test
    fun incrementTrailingNumberNameIgnoresNonNumericSuffixes() {
        assertNull(incrementTrailingNumberName("Session"))
        assertNull(incrementTrailingNumberName(""))
    }

    @Test
    fun nextReplicatedNameFallsBackToSuffixWhenNoTrailingNumber() {
        assertEquals("Session-copy", nextReplicatedName("Session", "session", "-copy"))
    }
}
