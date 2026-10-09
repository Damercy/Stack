package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test

class StyleDiscoveryTest {
    private val enabled=BalanceConfig(payments=true,verificationReady=true,offerHome=true)
    private val now=100*3_600_000L
    @Test fun homeOfferRequiresProgressVerifiedAvailabilityAndNonOwnership(){
        assertTrue(enabled.homeOfferEligible(3,false,true,now,0))
        assertFalse(enabled.homeOfferEligible(2,false,true,now,0))
        assertFalse(enabled.homeOfferEligible(100,true,true,now,0))
        assertFalse(enabled.homeOfferEligible(100,false,false,now,0))
        assertFalse(enabled.copy(payments=false).homeOfferEligible(100,false,true,now,0))
        assertFalse(enabled.copy(verificationReady=false).homeOfferEligible(100,false,true,now,0))
        assertFalse(enabled.copy(offerHome=false).homeOfferEligible(100,false,true,now,0))
    }
    @Test fun dismissalQuietPeriodIsSharedWithResultOffersAndSurvivesClockRollback(){
        assertFalse(enabled.homeOfferEligible(100,false,true,now,now-23*3_600_000L))
        assertTrue(enabled.homeOfferEligible(100,false,true,now,now-24*3_600_000L))
        assertFalse(enabled.homeOfferEligible(100,false,true,now,now+1))
        assertFalse(enabled.copy(offerResults=true).offerEligible(100,true,false,false,false,now,now-1000))
    }
}
