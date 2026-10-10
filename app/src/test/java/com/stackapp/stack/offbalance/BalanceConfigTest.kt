package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test

class BalanceConfigTest {
    @Test fun launchDefaultsCannotSellOrShowOffers(){
        val c=BalanceConfig.decode(emptyMap())
        assertFalse(c.payments);assertFalse(c.verificationReady);assertFalse(c.styleDiscovery)
        assertFalse(c.offerEligible(100,true,true,false,false,Long.MAX_VALUE,0))
    }
    @Test fun offersRequireReadinessProgressSuccessAndCooldown(){
        val c=BalanceConfig(payments=true,verificationReady=true,offerResults=true,offerTrials=true)
        val now=50*3_600_000L
        assertTrue(c.offerEligible(3,true,false,false,false,now,0))
        assertTrue(c.offerEligible(3,false,true,false,false,now,0))
        assertFalse(c.offerEligible(2,true,false,false,false,now,0))
        assertFalse(c.offerEligible(3,true,false,true,false,now,0))
        assertFalse(c.offerEligible(3,true,false,false,true,now,0))
        assertFalse(c.offerEligible(3,false,false,false,false,now,0))
        assertFalse(c.offerEligible(3,true,false,false,false,now,now-1000))
        assertFalse(c.copy(verificationReady=false).offerEligible(3,true,false,false,false,now,0))
        assertFalse(c.copy(payments=false).offerEligible(3,true,false,false,false,now,0))
    }
    @Test fun invalidRemoteValuesFallBackAndAggressiveValuesAreBounded(){
        val c=BalanceConfig.decode(mapOf("payments_enabled" to "nonsense","style_pack_product_id" to "../../bad","offer_min_runs" to -10,"offer_cooldown_hours" to 0,"celebration_duration_ms" to 999999,"milestone_every" to 1,"reminder_away_hours" to 0))
        assertFalse(c.payments);assertEquals("off_balance_style_pack",c.product)
        assertEquals(3,c.minimumRuns);assertEquals(24,c.offerCooldownHours)
        assertEquals(1600,c.celebrationMillis);assertEquals(5,c.milestoneEvery);assertEquals(6,c.reminderAwayHours)
    }
    @Test fun remoteReminderControlsDoNotOverrideConsentAndHaveAQuietWindow(){
        val now=10*24*3_600_000L
        val context=ReminderContext(true,true,now,now-12*3_600_000L,0,14)
        assertTrue(shouldRemind(context))
        assertFalse(shouldRemind(context,BalanceConfig(reminders=false)))
        assertFalse(shouldRemind(context.copy(enabled=false)))
        assertFalse(shouldRemind(context.copy(permitted=false)))
        assertFalse(shouldRemind(context.copy(hour=2)))
        assertFalse(shouldRemind(context,BalanceConfig(reminderAwayHours=24)))
    }

    @Test fun freePassRemoteControlsHaveSafeBounds(){
        assertTrue(BalanceConfig.decode(emptyMap()).freePasses)
        assertEquals(20,BalanceConfig.decode(emptyMap()).freePassChance)
        assertFalse(BalanceConfig.decode(mapOf("free_passes_enabled" to false)).freePasses)
        assertEquals(25,BalanceConfig.decode(mapOf("free_pass_chance_percent" to 100)).freePassChance)
        assertEquals(0,BalanceConfig.decode(mapOf("free_pass_chance_percent" to -1)).freePassChance)
    }
}
