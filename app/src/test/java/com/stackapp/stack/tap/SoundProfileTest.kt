package com.stackapp.stack.tap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SoundProfileTest {
    @Test
    fun existingToneIdsRemainStable() {
        assertEquals("crystal", soundPalette("crystal").id)
        assertEquals("felt", soundPalette("felt").id)
    }

    @Test
    fun offToneIsAvailableWithoutSubscription() {
        val off = soundPalette("off")
        assertEquals("Off", off.label)
        assertFalse(off.premium)
    }
}
