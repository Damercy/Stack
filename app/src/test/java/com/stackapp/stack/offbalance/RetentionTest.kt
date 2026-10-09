package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test

class RetentionTest {
    private val day=86_400_000L
    @Test fun remindersRespectConsentPermissionQuietHoursAndCooldown(){
        val eligible=ReminderContext(true,true,3*day,day,day,16)
        assertTrue(shouldRemind(eligible));assertFalse(shouldRemind(eligible.copy(enabled=false)))
        assertFalse(shouldRemind(eligible.copy(permitted=false)));assertFalse(shouldRemind(eligible.copy(hour=23)))
        assertFalse(shouldRemind(eligible.copy(lastPlayed=eligible.now-1000)));assertFalse(shouldRemind(eligible.copy(lastSent=eligible.now-1000)))
        assertFalse(shouldRemind(eligible.copy(now=10*day)))
    }
    @Test fun paymentsCannotInterruptPlayOrAppearWithUnavailableProducts(){
        assertFalse(shouldOfferPurchase(false,true,false,10,true,3*day,0))
        assertFalse(shouldOfferPurchase(true,false,false,10,true,3*day,0))
        assertFalse(shouldOfferPurchase(true,true,true,10,true,3*day,0))
        assertFalse(shouldOfferPurchase(true,true,false,1,true,3*day,0))
        assertFalse(shouldOfferPurchase(true,true,false,10,false,3*day,0))
        assertTrue(shouldOfferPurchase(true,true,false,10,true,3*day,0))
    }
    @Test fun usernamesAreCaseInsensitiveAndExcludeAmbiguousWhitespace(){
        assertEquals(usernameKey("Rival_One"),usernameKey("rival_one"));assertTrue(validUsername("rival_one"))
        listOf("ab","hello world","a/b","x".repeat(21)).forEach{assertFalse(validUsername(it))}
    }
}
