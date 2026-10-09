package com.stackapp.stack.offbalance
import org.junit.Assert.*
import org.junit.Test

class BalanceReviewTest {
    private val now=100L*86_400_000
    private fun eligible(moment:ReviewMoment=ReviewMoment(newBest=true,layers=12),runs:Int=5,attempted:Boolean=false,last:Long=0,config:BalanceConfig=BalanceConfig())=reviewEligible(config,runs,now-3*86_400_000,now,attempted,last,moment)
    @Test fun distinctMilestonesCanBecomeTheOneReviewOpportunity(){assertTrue(eligible());assertTrue(eligible(ReviewMoment(trialsComplete=2)));assertTrue(eligible(ReviewMoment(returned=true),8))}
    @Test fun newUsersSmallScoresAndEarlyReturnsAreNotInterrupted(){assertFalse(eligible(runs=4));assertFalse(eligible(ReviewMoment(newBest=true,layers=3)));assertFalse(eligible(ReviewMoment(returned=true),7));assertFalse(reviewEligible(BalanceConfig(),20,now,now,false,0,ReviewMoment(trialsComplete=2)))}
    @Test fun successfulAttemptCannotRepeatEvenAtAnotherTrigger(){assertFalse(eligible(attempted=true));assertFalse(eligible(ReviewMoment(trialsComplete=4),100,attempted=true))}
    @Test fun failuresWaitAtLeastThirtyDaysAndRemoteSwitchCanDisable(){assertFalse(eligible(last=now-29L*86_400_000));assertTrue(eligible(last=now-31L*86_400_000));assertFalse(eligible(config=BalanceConfig(reviews=false)))}
}
