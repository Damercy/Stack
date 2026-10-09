package com.stackapp.stack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in checks against live services; no profile mutations or purchases. */
@RunWith(AndroidJUnit4::class)
class PhysicalServiceTest {
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun liveOnly(){
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveServices")=="true")
        assumeTrue(BuildConfig.USE_FIREBASE)
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    @After fun close(){scenario?.close()}
    @Test fun googleSessionSurvivesRestartAndRefreshesItsToken()=runBlocking {
        val user=FirebaseAuth.getInstance().currentUser
        assertNotNull("Complete Google sign-in before the live checks",user)
        assertFalse(user!!.isAnonymous)
        assertTrue(user.providerData.any{it.providerId=="google.com"})
        val result=withTimeout(30_000){user.getIdToken(true).await()}
        assertFalse(result.token.isNullOrBlank())
    }
    @Test fun attestedStatusIsClosedWhileSalesAreDisabled()=runBlocking {
        assertFalse(withTimeout(30_000){FirebaseAppCheck.getInstance().getAppCheckToken(true).await()}.token.isBlank())
        val data=withTimeout(30_000){FirebaseFunctions.getInstance().getHttpsCallable("stylePackStatus").call().await()}.data as Map<*,*>
        assertEquals("off_balance_style_pack",data["productId"])
        assertEquals(false,data["ready"])
    }
    @Test fun invalidReceiptNeverUnlocksThePack()=runBlocking {
        try {
            withTimeout(30_000){FirebaseFunctions.getInstance().getHttpsCallable("verifyStylePack").call(mapOf("productId" to "wrong_product","token" to "invalid")).await()}
            fail("Invalid receipt was accepted")
        }catch(error:FirebaseFunctionsException){assertEquals(FirebaseFunctionsException.Code.INVALID_ARGUMENT,error.code)}
    }
    @Test fun authenticatedProfileAndUsernameSearchReachTheServer()=runBlocking {
        val user=FirebaseAuth.getInstance().currentUser
        assertNotNull(user)
        val db=FirebaseFirestore.getInstance()
        withTimeout(30_000){db.collection("players").document(user!!.uid).get(Source.SERVER).await()}
        val matches=withTimeout(30_000){db.collection("players").orderBy("key").startAt("stack").endAt("stack\uf8ff").limit(8).get(Source.SERVER).await()}
        assertTrue(matches.size()<=8)
    }
    @Test fun remoteLoginIsEnabledAndSalesAreClosed()=runBlocking {
        val remote=FirebaseRemoteConfig.getInstance()
        withTimeout(30_000){remote.fetch(0).await();remote.activate().await()}
        assertTrue(remote.getBoolean("google_sign_in_enabled"))
        assertFalse(remote.getBoolean("payments_enabled"))
        assertFalse(remote.getBoolean("payment_verification_ready"))
    }
}
