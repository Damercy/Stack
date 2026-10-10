package com.stackapp.stack

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import com.stackapp.stack.offbalance.*
import com.stackapp.stack.tap.RoomTapStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real touches, navigation and OS interruptions on the connected device. */
@RunWith(AndroidJUnit4::class)
class OffBalanceJourneyTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var server:JourneyServer
    private lateinit var pack:JourneyStylePack
    private lateinit var original:Map<String,Map<String,*>>
    private var originalName=""
    private val events=java.util.concurrent.CopyOnWriteArrayList<ProductEvent>()
    private lateinit var account:JourneyAccount
    private lateinit var reviewCode:String
    private val keepAwake=ActivityLifecycleCallback{activity,stage->
        if(stage==Stage.RESUMED)activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    @Before fun launch(){
        android.util.Log.i("BalanceJourney","setup")
        device.wakeUp()
        device.setOrientationNatural()
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(keepAwake)
        Configurator.getInstance().waitForIdleTimeout=0
        original=listOf("off_balance","rivals","balance_reminders","balance_flags","style_pack","stack_launch").associateWith{context.getSharedPreferences(it,Context.MODE_PRIVATE).all}
        originalName=RoomTapStore(context).load().displayName.orEmpty()
        context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().clear().putBoolean("introduced",true).putBoolean("touch",true).putString("difficulty","STEADY").commit()
        context.getSharedPreferences("rivals",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("balance_reminders",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("balance_flags",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("style_pack",Context.MODE_PRIVATE).edit().clear().commit()
        server=JourneyServer();CompetitionFactory.testRepository=server
        pack=JourneyStylePack();StylePackFactory.testStore=pack
        account=JourneyAccount();BalanceAccountFactory.testAccount=account
        val reviewKeys=java.security.KeyPairGenerator.getInstance("EC").apply{initialize(java.security.spec.ECGenParameterSpec("secp256r1"))}.generateKeyPair()
        val publicKey=java.util.Base64.getEncoder().encodeToString(reviewKeys.public.encoded)
        reviewCode=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.Signature.getInstance("SHA256withECDSA").run{initSign(reviewKeys.private);update("Stack local review access v1".toByteArray());sign()})
        ReviewerAccess.testVerifier={code->verifyReviewerCode(code,publicKey)}
        BalanceAnalyticsTestSink.accept={events.add(it)}
        events.clear();scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        requireText("PLAY")
        android.util.Log.i("BalanceJourney","ready")
    }
    @After fun restore(){
        device.pressHome()
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(keepAwake)
        scenario.close();BalancePhysicsFactory.testCreate=null;CompetitionFactory.testRepository=null;StylePackFactory.testStore=null
        BalanceAccountFactory.testAccount=null;ReviewGatewayFactory.testGateway=null;BalanceAnalyticsTestSink.accept=null;ReviewerAccess.testVerifier=null
        original.forEach{(name,values)->val e=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            values.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*>->{@Suppress("UNCHECKED_CAST") e.putStringSet(k,v as Set<String>)}}};e.commit()}
        RoomTapStore(context).saveDisplayName(originalName)
        BalanceReminders(context).sync();device.setOrientationNatural();device.unfreezeRotation()
    }
    private fun missing(message:String):Nothing {
        device.dumpWindowHierarchy(java.io.File(context.cacheDir,"failure-journey.xml"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { screenshot ->
            java.io.File(context.cacheDir,"failure-journey.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            screenshot.recycle()
        }
        error(message)
    }
    private fun requireText(value:String):UiObject2 {
        device.waitForIdle(100)
        val deadline=android.os.SystemClock.uptimeMillis()+10_000
        do {
            // Compose can redraw a label before UiAutomator receives its invalidation event.
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            if(android.os.Build.VERSION.SDK_INT>=33)automation.clearCache()
            else automation.serviceInfo=automation.serviceInfo
            device.findObject(By.text(value))?.let{return it}
            clock(100)
        }while(android.os.SystemClock.uptimeMillis()<deadline)
        missing("Missing text: $value")
    }
    private fun described(value:String):UiObject2 {device.wait(Until.hasObject(By.desc(value)),10_000);clock(650);device.dumpWindowHierarchy(java.io.File(context.cacheDir,"control-refresh.xml"));return device.findObject(By.desc(value)) ?: missing("Missing control: $value")}
    private fun resource(value:String)=device.wait(Until.findObject(By.res(value)),10_000) ?: missing("Missing element: $value")
    private fun tap(value:String){
        android.util.Log.i("BalanceJourney","tap $value")
        if(!device.wait(Until.hasObject(By.text(value)),1_000)){
            repeat(4){if(!device.hasObject(By.text(value)))device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.5f)}
        }
        requireText(value);clock(650)
        device.dumpWindowHierarchy(java.io.File(context.cacheDir,"tap-refresh.xml"))
        var bounds=requireText(value).visibleBounds
        var stable=0
        val deadline=android.os.SystemClock.uptimeMillis()+3_000
        while(stable<3 && android.os.SystemClock.uptimeMillis()<deadline){
            clock(100)
            val next=requireText(value).visibleBounds
            stable=if(next==bounds)stable+1 else 0
            bounds=next
        }
        device.click(bounds.centerX(),bounds.centerY());android.util.Log.i("BalanceJourney","tapped $value")
    }
    private fun music(state:String){android.util.Log.i("BalanceJourney","expect music $state");val found=device.wait(Until.hasObject(By.descContains("Music $state")),10_000)
        if(!found)device.dumpWindowHierarchy(java.io.File(context.cacheDir,"audio-journey.xml"))
        assertTrue("Music should be $state",found)}
    private fun score()=Regex("Run: (\\d+) layers").find(resource("run_status").contentDescription)?.groupValues?.get(1)?.toInt() ?: error("Missing score")
    private fun waitScore(value:Int){
        if(!device.wait(Until.hasObject(By.descStartsWith("Run: $value layers")),10_000)){
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            bitmap?.let{image->java.io.File(context.cacheDir,"camera-failure.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
            missing("Expected $value layers; ${resource("run_status").contentDescription}")
        }
    }
    private fun play(){tap("PLAY");resource("game_scene")}
    private fun drop(){resource("game_scene").click()}
    private fun pause(){described("Pause").click();requireText("RESUME");music("paused")}
    private fun friends(){tap("TODAY");tap("FRIENDS");resource("username_search")}
    private fun settings(){described("Settings").click();requireText("SETTINGS")}
    private fun clock(ms:Long){android.os.SystemClock.sleep(ms)}
    private fun holdBalance(left:Boolean){
        val b=(device.wait(Until.findObject(By.descStartsWith("Balance ")),10_000)?:error("Missing balance control")).visibleBounds
        val start=android.os.SystemClock.uptimeMillis()
        fun pointer(action:Int,x:Int){val e=android.view.MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,x.toFloat(),b.centerY().toFloat(),0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(e,true);e.recycle()}
        val edge=if(left)b.left+2 else b.right-2
        pointer(android.view.MotionEvent.ACTION_DOWN,b.centerX());pointer(android.view.MotionEvent.ACTION_MOVE,edge)
        try{clock(600)}finally{pointer(android.view.MotionEvent.ACTION_UP,edge)}
    }
    private fun freshLesson(){scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putBoolean("introduced",false).putInt("onboarding_step",0).putInt("onboarding_balance",0).commit();scenario=ActivityScenario.launch(MainActivity::class.java);requireText("LAND IT")}
    private fun reviewAccess(onboarding:Boolean=false){
        assertFalse(device.hasObject(By.text("TRY DEMO")))
        clock(650)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        if(android.os.Build.VERSION.SDK_INT>=33)automation.clearCache() else automation.serviceInfo=automation.serviceInfo
        resource(if(onboarding)"onboarding_step" else "brand_mark").longClick()
        requireText("REVIEW");resource("review_access_code").text=reviewCode
        tap("CONTINUE");requireText("DEMO · SAMPLE DATA")
    }
    private fun captureScreen(name:String){
        clock(700)
        val image=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(context.getExternalFilesDir(null),name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        image.recycle()
    }


    @Test fun demoAccessShowsPremiumContentWithoutChangingPlayerState(){
        settings()
        val before=listOf("off_balance","rivals","balance_reminders","balance_flags","style_pack").associateWith{context.getSharedPreferences(it,Context.MODE_PRIVATE).all}
        val nameBefore=RoomTapStore(context).load().displayName
        reviewAccess();tap("SKIP");requireText("PLAY")
        events.clear()
        settings();tap("DEMO ACCOUNT");requireText("LOCAL DEMO ACCOUNT");requireText("Demo Player")
        device.pressBack();requireText("SETTINGS");tap("STYLE PACK");requireText("YOUR STYLE")
        described("Theme ${BalanceSkin.NIGHT.title}").click();requireText("SELECTED")
        tap("RESTORE PURCHASES");requireText("Demo access · no purchase made")
        device.pressBack();requireText("SETTINGS");tap("SOUNDTRACK ›");tap("AFTER HOURS")
        device.pressBack();requireText("SETTINGS");device.pressBack();requireText("PLAY")
        tap("TODAY");requireText("@orbit_ada");tap("FRIENDS")
        resource("username_search").text="orbit";requireText("@orbit_ada")
        if(device.hasObject(By.desc("Done"))){device.pressBack();requireText("FRIENDS");clock(600)}
        tap("+ ADD");requireText("SAVED")
        assertTrue("Demo gameplay must not log production events",events.isEmpty())
        before.forEach{(key,values)->assertEquals("Production preference changed: $key",values,context.getSharedPreferences(key,Context.MODE_PRIVATE).all)}
        assertEquals(nameBefore,RoomTapStore(context).load().displayName)
        assertFalse(pack.state.value.owned)
        described("Exit demo").click();requireText("SETTINGS")
        assertFalse(device.hasObject(By.text("DEMO · SAMPLE DATA")))
        assertFalse(pack.state.value.owned)
    }
    @Test fun privateReviewAccessWorksFromFreshOnboardingAndRestartsCleanly(){
        freshLesson();reviewAccess(onboarding=true);requireText("LAND IT")
        tap("SKIP");settings();tap("HOW TO PLAY");requireText("LAND IT")
        described("Exit demo").click();requireText("LAND IT")
        assertFalse(BalancePreferences(context).introduced)
        reviewAccess(onboarding=true);requireText("LAND IT");requireText("DEMO · SAMPLE DATA")
        described("Exit demo").click();requireText("LAND IT")
    }
    @Test fun publicAppRejectsMissingAndInvalidReviewAccess(){
        settings();assertFalse(device.hasObject(By.text("TRY DEMO")))
        scenario.onActivity{it.startActivity(Intent(it,DemoActivity::class.java))}
        clock(1200);requireText("SETTINGS");assertFalse(device.hasObject(By.text("DEMO · SAMPLE DATA")))
        scenario.onActivity{it.startActivity(Intent(it,DemoActivity::class.java).putExtra(ReviewerAccess.EXTRA_CODE,"invalid"))}
        clock(1200);requireText("SETTINGS");assertFalse(device.hasObject(By.text("DEMO · SAMPLE DATA")))
        resource("brand_mark").longClick();requireText("REVIEW");tap("CONTINUE");requireText("CODE NOT RECOGNIZED. TRY AGAIN.")
        resource("review_access_code").text="invalid";tap("CONTINUE");requireText("CODE NOT RECOGNIZED. TRY AGAIN.")
        assertFalse(device.hasObject(By.text("DEMO · SAMPLE DATA")))
        resource("review_access_code").text=reviewCode;tap("CONTINUE");requireText("DEMO · SAMPLE DATA")
        device.setOrientationLeft();clock(800);requireText("DEMO · SAMPLE DATA")
        device.setOrientationNatural();clock(800);described("Exit demo").click();requireText("SETTINGS")
        assertFalse(device.hasObject(By.text("TRY DEMO")));captureScreen("settings-public.png")
        device.pressBack();requireText("PLAY")
        server.dailyLeaders=listOf("orbit_ada" to 42,"neon_sam" to 35,"soft_landing" to 28).mapIndexed{i,(name,score)->Competitor("leader_$i",name,mapOf("STEADY" to score),competitionDay(),mapOf("STEADY" to score))}
        tap("TODAY");requireText("@orbit_ada");assertFalse(device.hasObject(By.text("DEMO · SAMPLE DATA")));captureScreen("today-public.png")
        freshLesson();assertFalse(device.hasObject(By.text("TRY DEMO")));captureScreen("onboarding-public.png")
    }
    @Test fun publicLaunchResetClearsPreviewRecordsOnlyOnceAndPreservesPurchases(){
        scenario.close()
        val prefs=BalancePreferences(context);prefs.record(Difficulty.STEADY,77);prefs.completedRuns=20
        RoomTapStore(context).saveDisplayName("PreviewPlayer")
        context.getSharedPreferences("stack_launch",Context.MODE_PRIVATE).edit().clear().commit()
        val ownership=context.getSharedPreferences("style_pack",Context.MODE_PRIVATE)
        ownership.edit().putBoolean("owned",true).commit()
        context.getSharedPreferences("rivals",Context.MODE_PRIVATE).edit().putStringSet("ids",setOf("preview_rival")).commit()
        assertTrue(PublicLaunchReset.prepare(context));assertEquals(0,prefs.best(Difficulty.STEADY))
        assertEquals(0,prefs.completedRuns);assertFalse(prefs.introduced)
        assertTrue(context.getSharedPreferences("rivals",Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(ownership.getBoolean("owned",false));assertTrue(RoomTapStore(context).load().displayName.isNullOrBlank())
        prefs.record(Difficulty.STEADY,8)
        assertFalse(PublicLaunchReset.prepare(context));assertEquals(8,prefs.best(Difficulty.STEADY))
        scenario=ActivityScenario.launch(MainActivity::class.java);requireText("LAND IT")
    }
    @Test fun stackBrandFollowsNavigationAndContainsNoOldCornerBrand(){
        assertEquals("STACK",resource("brand_mark").text)
        settings();assertEquals("STACK",resource("brand_mark").text)
        tap("SOUNDTRACK \u203a");assertEquals("STACK",resource("brand_mark").text)
        assertFalse(device.hasObject(By.text("OFF BALANCE")))
        device.pressBack();requireText("SETTINGS");device.pressBack();requireText("PLAY")
        tap("TODAY");assertEquals("STACK",resource("brand_mark").text)
        device.pressBack();requireText("PLAY");play();assertEquals("STACK",resource("brand_mark").text)
    }
    @Test fun passCueExpiresContinuesWithoutScoringAndCannotHideAnotherMiss(){
        var game:BalancePhysics?=null
        BalancePhysicsFactory.testCreate={mode,tutorial,trial,chance->BalancePhysics(mode,tutorial,trial,chance,passRoll={0}).also{game=it}}
        play()
        fun pieces():List<Pair<org.jbox2d.dynamics.Body,PiecePose>>{
            val field=BalancePhysics::class.java.getDeclaredField("bodies").apply{isAccessible=true}
            @Suppress("UNCHECKED_CAST") return field.get(game!!) as List<Pair<org.jbox2d.dynamics.Body,PiecePose>>
        }
        fun centeredDrop(){
            InstrumentationRegistry.getInstrumentation().runOnMainSync{
                BalancePhysics::class.java.getDeclaredField("nextX").apply{isAccessible=true}.setFloat(game,pieces().last().first.position.x)
            };drop()
        }
        repeat(3){centeredDrop();waitScore(it+1);clock(350)}
        centeredDrop()
        InstrumentationRegistry.getInstrumentation().runOnMainSync{pieces().last().first.setTransform(org.jbox2d.common.Vec2(6f,-.1f),0f)}
        resource("free_pass");assertEquals(3,score())
        assertTrue(resource("run_status").contentDescription.endsWith("playing"))
        assertEquals(1,events.count{it.name=="tower_free_pass"})
        assertTrue(device.wait(Until.gone(By.res("free_pass")),4_000))
        centeredDrop();waitScore(4);clock(350)
        centeredDrop()
        InstrumentationRegistry.getInstrumentation().runOnMainSync{pieces().last().first.setTransform(org.jbox2d.common.Vec2(6f,-.1f),0f)}
        requireText("PLAY AGAIN");assertEquals(4,score());assertFalse(device.hasObject(By.res("free_pass")))
        tap("PLAY AGAIN");resource("game_scene");assertEquals(0,score())
        assertEquals(0,game!!.snapshot().passes);assertFalse(device.hasObject(By.res("free_pass")))
    }
    @Test fun profileIsOnlyVisibleInFinalLessonAndSavedAccount(){
        flags(mapOf("google_sign_in_enabled" to true))
        account.state.value=account.state.value.copy(signedIn=true,name="Demo Player",email="player@example.org")
        freshLesson();assertFalse(device.hasObject(By.res("google_identity")))
        described("Drop practice block").click();tap("NEXT");music("paused")
        assertFalse(device.hasObject(By.res("google_identity")))
        holdBalance(true);holdBalance(false);tap("NEXT");resource("google_identity");requireText("Demo Player")
        device.pressBack();requireText("BALANCE");assertFalse(device.hasObject(By.res("google_identity")))
        tap("NEXT");resource("google_identity");scenario.recreate();resource("google_identity")
        described("Skip lesson").click();requireText("PLAY");assertFalse(device.hasObject(By.res("google_identity")))
        settings();tap("GOOGLE ACCOUNT");resource("google_identity");assertFalse(device.hasObject(By.text("DONE")))
        device.pressBack();requireText("SETTINGS");assertFalse(device.hasObject(By.res("google_identity")))
    }
    @Test fun firstOnboardingLandingIsSilent(){
        freshLesson();music("paused");described("Drop practice block").click();tap("NEXT");music("paused")
        assertFalse(context.getSystemService(AudioManager::class.java).isMusicActive)
    }
    @Test fun automaticSettingsAndMusicSaveUseBackWithoutDone(){
        settings();assertFalse(device.hasObject(By.text("DONE")))
        tap("SOUNDTRACK \u203a");assertFalse(device.hasObject(By.text("DONE")))
        tap("NIGHT RUN");music("playing");device.pressBack();requireText("SETTINGS");music("paused")
        assertEquals(2,BalancePreferences(context).track)
        device.pressBack();requireText("PLAY")
    }
    @Test fun dailyPodiumRanksThreeAndHandlesEmptyOfflineAndModeChange(){
        fun player(id:String,score:Int,day:String=competitionDay())=Competitor(id,id,emptyMap(),day,mapOf("STEADY" to score))
        server.dailyLeaders=listOf(player("third",8),player("first",25),player("second",14),player("fourth",2),player("old_day",100,"2000-01-01"))
        tap("TODAY");resource("daily_rank_1");requireText("@first");requireText("@second");requireText("@third")
        assertFalse(device.hasObject(By.text("@fourth")));assertFalse(device.hasObject(By.text("@old_day")))
        assertTrue(requireText("@first").visibleBounds.top<requireText("@second").visibleBounds.top)
        described("Leaderboard WOBBLY").click();requireText("First tower takes the lead.")
        server.offline=true;described("Leaderboard STEADY").click();requireText("Couldn\u2019t load today\u2019s leaders.")
        server.offline=false;tap("RETRY");requireText("@first")
    }
    @Test fun homeStyleNudgeCanDismissAndCannotRepeatAfterRestartOrOwnership(){
        BalancePreferences(context).completedRuns=3
        pack.state.value=StylePackState(ready=true,price="US$4.99")
        flags(mapOf("payments_enabled" to true,"payment_verification_ready" to true,"offer_home_enabled" to true))
        described("Explore Style Pack");described("Dismiss Style Pack offer").click()
        scenario.recreate();requireText("PLAY");assertFalse(device.hasObject(By.desc("Explore Style Pack")))
        BalancePreferences(context).offerShownAt=0
        scenario.recreate();requireText("PLAY");described("Explore Style Pack").click();requireText("YOUR STYLE")
        assertFalse(device.hasObject(By.text("DONE")))
        pack.state.value=pack.state.value.copy(owned=true)
        device.pressBack();requireText("PLAY");assertFalse(device.hasObject(By.desc("Explore Style Pack")))
        requireText("YOUR STYLE")
    }
    @Test fun paidMusicPreviewExpiresAndCannotBecomeTheGameplayTrack(){
        flags(mapOf("style_pack_discovery_enabled" to true))
        settings();tap("SOUNDTRACK \u203a");tap("AFTER HOURS");music("playing")
        requireText("15 SECOND PREVIEW · STYLE PACK");requireText("KEEP THIS GROOVE")
        clock(16_000);music("paused")
        assertEquals(0,BalancePreferences(context).track);assertFalse(pack.state.value.owned)
        tap("KEEP THIS GROOVE");requireText("YOUR STYLE");music("paused")
        device.pressBack();requireText("MUSIC");music("paused")
        device.pressBack();requireText("SETTINGS");device.pressBack();play();drop();waitScore(1);music("playing")
        assertEquals(0,BalancePreferences(context).track);assertFalse(pack.state.value.owned)
    }

    @Test fun doubleTapDoesNotDuplicateTheFirstLanding(){
        play();clock(300);val bounds=resource("game_scene").visibleBounds
        repeat(2){
            val down=android.os.SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event,true);event.recycle()
            }
            clock(20)
        }
        waitScore(1);pause();assertEquals(1,score())
    }
    @Test fun pauseFreezesScoreAndMusicThenResumeContinues(){play();drop();waitScore(1);pause();val before=score();clock(1800);assertEquals(before,score());tap("RESUME");music("playing");assertEquals(before,score())}
    @Test fun backgroundingPausesWithoutLongCatchUp(){play();drop();waitScore(1);device.pressHome();clock(1400);context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));requireText("RESUME");music("paused");assertEquals(1,score());tap("RESUME");music("playing")}
    @Test fun resetFromPauseStartsAFreshSilentRun(){play();drop();waitScore(1);pause();tap("RESET LEVEL");waitScore(0);music("paused");drop();waitScore(1);music("playing")}
    @Test fun backDuringPlayPausesAndBackAgainResumes(){play();drop();waitScore(1);device.pressBack();requireText("RESUME");music("paused");device.pressBack();music("playing");assertEquals(1,score())}
    @Test fun endingARunReturnsHomeWithoutPurchaseInterruptions(){play();drop();waitScore(1);pause();tap("END RUN");requireText("PLAY");assertFalse(device.hasObject(By.textContains("BUY")));assertFalse(device.hasObject(By.textContains("SUBSCRIBE")));music("paused")}
    @Test fun tiltCanToppleTheTowerAndGameOverCannotKeepScoring(){
        android.util.Log.i("BalanceJourney","changing difficulty")
        scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putString("difficulty","CHAOS").commit();scenario=ActivityScenario.launch(MainActivity::class.java)
        play();drop();waitScore(1)
        val gauge=device.wait(Until.findObject(By.descStartsWith("Balance ")),10_000)?:error("Missing gauge")
        android.util.Log.i("BalanceJourney","holding gauge")
        val b=gauge.visibleBounds;val started=android.os.SystemClock.uptimeMillis()
        fun pointer(action:Int,x:Int){val event=android.view.MotionEvent.obtain(started,android.os.SystemClock.uptimeMillis(),action,x.toFloat(),b.centerY().toFloat(),0);event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event,true);event.recycle()}
        pointer(android.view.MotionEvent.ACTION_DOWN,b.centerX());pointer(android.view.MotionEvent.ACTION_MOVE,b.right-2)
        try{clock(8000)}finally{pointer(android.view.MotionEvent.ACTION_UP,b.right-2)}
        android.util.Log.i("BalanceJourney","released gauge")
        requireText("PLAY AGAIN");val final=score();clock(1700);assertEquals(final,score());drop();clock(500);assertEquals(final,score());tap("PLAY AGAIN");waitScore(0)
    }
    @Test fun musicSurvivesAnEntireLoopAndAnotherLoop(){play();drop();waitScore(1);music("playing");clock(22_000);music("playing");assertTrue(context.getSystemService(AudioManager::class.java).isMusicActive);assertEquals(1,score())}
    @Test fun transientAudioFocusLossResumesAfterInterruption(){
        play();drop();waitScore(1);music("playing")
        val manager=context.getSystemService(AudioManager::class.java)
        val interruption=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setOnAudioFocusChangeListener{}.build()
        try{assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,manager.requestAudioFocus(interruption));music("paused")}finally{manager.abandonAudioFocusRequest(interruption)}
        music("playing");assertEquals(1,score())
    }
    @Test fun muteAndUnmuteWhilePausedDoNotResetTheTower(){
        play();drop();waitScore(1);pause();tap("SETTINGS")
        val margin=(6*context.resources.displayMetrics.density).toInt()
        val slider=described("MUSIC").visibleBounds;device.click(slider.left+margin,slider.centerY());requireText("0%");device.pressBack();tap("RESUME");assertTrue(device.wait(Until.hasObject(By.desc("Run: 1 layers, playing")),10_000));music("paused");assertEquals(1,score())
        pause();tap("SETTINGS");val s=described("MUSIC").visibleBounds;device.click(s.right-margin,s.centerY());requireText("100%");device.pressBack();tap("RESUME");music("playing");assertEquals(1,score())
    }
    @Test fun switchingPreviewTracksThenLeavingStopsThePreview(){settings();tap("SOUNDTRACK ›");tap("MIDNIGHT SIGNAL");music("playing");tap("NIGHT RUN");music("playing");tap("SIDE B");music("playing");device.pressBack();music("paused");device.pressBack();requireText("PLAY")}
    @Test fun rotationPreservesAnActiveTowerAndKeepsPauseReachable(){
        play();drop();waitScore(1);device.setOrientationLeft()
        val until=android.os.SystemClock.uptimeMillis()+5_000
        while(resource("run_status").visibleBounds.width()<=device.displayHeight && android.os.SystemClock.uptimeMillis()<until)clock(100)
        assertTrue("Game layout must complete its rotation",resource("run_status").visibleBounds.width()>device.displayHeight)
        music("playing");assertEquals(1,score());pause();tap("RESUME");device.setOrientationNatural();assertEquals(1,score())
    }
    @Test fun trialLocksCannotBeBypassed(){tap("TRIALS");tap("04 / MIX IT UP");tap("START TRIAL");requireText("TRIAL 01");drop();waitScore(1);assertFalse(device.hasObject(By.text("NEXT TRIAL")))}
    @Test fun usernameTakenDoesNotSaveOrLeaveTheScreen(){server.registered=false;friends();tap("SET USERNAME");resource("username_entry").text="rival_one";tap("SAVE");requireText("Username taken.");resource("username_entry").text="new_player";tap("SAVE");requireText("SHARE @new_player")}
    @Test fun friendSearchBookmarkComparisonRefreshAndUnfollow(){
        friends();resource("username_search").text="rival";requireText("@rival_one");described("Follow rival_one").click();requireText("SAVED")
        resource("username_search").text="";requireText("@rival_one");requireText("12")
        server.rival=server.rival.copy(best=mapOf("STEADY" to 15));tap("REFRESH");requireText("15")
        described("Unfollow rival_one").click();requireText("Search a username to add a friend.")
    }
    @Test fun savedFriendsSurviveActivityRecreationAndNetworkFailure(){
        friends();resource("username_search").text="rival";described("Follow rival_one").click();resource("username_search").text="";requireText("@rival_one")
        scenario.recreate();requireText("PLAY");friends();requireText("@rival_one");server.offline=true;tap("REFRESH");requireText("Couldn’t connect. Try again.");requireText("@rival_one");server.offline=false;tap("RETRY");requireText("@rival_one")
    }
    @Test fun rapidSearchChangesNeverShowOldResults(){friends();val field=resource("username_search");field.text="rival";field.text="absent";requireText("No players found.");assertFalse(device.hasObject(By.text("@rival_one")))}
    @Test fun missingServerDoesNotShowInventedScores(){server.offline=true;friends();requireText("Couldn’t connect. Try again.");assertFalse(device.hasObject(By.text("@rival_one")));assertFalse(device.hasObject(By.text("12")))}
    @Test fun freshInstallTeachesLandingAndBalanceThenStartsAnUngatedRun(){
        freshLesson();assertFalse(device.hasObject(By.text("CONTINUE WITH GOOGLE")));assertFalse(device.hasObject(By.textStartsWith("UNLOCK")))
        described("Drop practice block").click();tap("NEXT");requireText("BALANCE");holdBalance(true);requireText("✓ LEFT");holdBalance(false);requireText("RIGHT ✓");tap("NEXT");requireText("YOU'RE IN");tap("LET'S STACK")
        drop();waitScore(1);pause();tap("END RUN");requireText("PLAY")
        assertTrue(context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).getBoolean("introduced",false))
        assertEquals(1,events.count{it.name=="tutorial_complete"});assertEquals(2,events.count{it.name=="tutorial_step_complete"});assertEquals(1,events.count{it.name=="tower_run_start"});assertEquals(1,events.count{it.name=="tower_run_end"})
        assertTrue(events.all{event->event.fields.keys.none{it in setOf("username","email","token","uid")}})
    }
    @Test fun interruptedPracticeCanRestartAndResumePersistedBalanceProgress(){
        freshLesson();described("Drop practice block").click();scenario.recreate();requireText("LAND IT")
        if(!device.hasObject(By.text("NEXT")))described("Drop practice block").click()
        tap("NEXT");requireText("BALANCE");holdBalance(true);requireText("✓ LEFT");scenario.recreate();requireText("BALANCE");requireText("✓ LEFT");holdBalance(false);tap("NEXT");requireText("YOU'RE IN")
        device.pressBack();requireText("BALANCE");requireText("RIGHT ✓");tap("NEXT");tap("LET'S STACK");waitScore(0)
    }
    @Test fun backgroundingALessonStopsAudioAndSkipStaysDismissed(){
        freshLesson();described("Drop practice block").click();device.pressHome();clock(1200);assertFalse(context.getSystemService(AudioManager::class.java).isMusicActive)
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));requireText("LAND IT");described("Skip lesson").click();requireText("PLAY");scenario.recreate();requireText("PLAY");assertFalse(device.hasObject(By.text("LAND IT")));assertEquals(1,events.count{it.name=="tutorial_skip"})
    }
    private fun lessonToAccount(){
        flags(mapOf("google_sign_in_enabled" to true));freshLesson()
        described("Drop practice block").click();tap("NEXT");holdBalance(true);holdBalance(false);tap("NEXT");requireText("CONTINUE WITH GOOGLE")
    }
    @Test fun onboardingGoogleCancellationAndFailureHaveAGuestExit(){
        lessonToAccount();account.result="cancel";tap("CONTINUE WITH GOOGLE");requireText("PLAY AS GUEST")
        account.result="fail";tap("CONTINUE WITH GOOGLE");requireText("Couldn’t sign in. Try again.")
        tap("PLAY AS GUEST");waitScore(0);drop();waitScore(1)
        assertEquals(1,events.count{it.name=="tutorial_complete"})
    }
    @Test fun onboardingGoogleSuccessStaysVisibleAcrossRecreationThenPlays(){
        lessonToAccount();tap("CONTINUE WITH GOOGLE");requireText("SAVED WITH GOOGLE. READY WHEN YOU ARE.")
        scenario.recreate();requireText("LET'S STACK");assertTrue(account.state.value.signedIn)
        tap("LET'S STACK");waitScore(0);drop();waitScore(1)
    }
    @Test fun tappingTheHeadlineDropsAndPauseAndBalanceDragsDoNot(){
        play();val surface=resource("game_surface").visibleBounds
        device.click(surface.centerX(),surface.top+210);waitScore(1)
        holdBalance(true);assertEquals(1,score());pause();assertEquals(1,score())
        device.click(surface.right-20,surface.bottom-30);clock(400);assertEquals(1,score());requireText("RESUME")
    }
    @Test fun lowerScreenTapsAreReachableAndRepeatedPressesDoNotDuplicateLandings(){
        play();val b=resource("game_surface").visibleBounds
        assertFalse(device.hasObject(By.text("DROP")))
        repeat(3){device.click(b.right-20,b.bottom-30)};waitScore(1);pause();assertEquals(1,score())
    }
    @Test fun googleCancellationAndFailureKeepGuestPlayAndRecords(){
        flags(mapOf("google_sign_in_enabled" to true));settings();tap("SAVE WITH GOOGLE");account.result="cancel";tap("CONTINUE WITH GOOGLE");requireText("CONTINUE WITH GOOGLE");assertFalse(account.state.value.signedIn)
        account.result="fail";tap("CONTINUE WITH GOOGLE");requireText("Couldn’t sign in. Try again.");tap("KEEP PLAYING AS GUEST");device.pressBack();play();drop();waitScore(1)
    }
    @Test fun switchingToASavedGoogleProfileRequiresTheCustomChoiceAndCanCancel(){
        flags(mapOf("google_sign_in_enabled" to true));settings();tap("SAVE WITH GOOGLE");account.result="saved";tap("CONTINUE WITH GOOGLE");requireText("USE SAVED PROFILE");tap("CANCEL");assertFalse(account.state.value.signedIn)
        server.player=server.player.copy(best=mapOf("STEADY" to 20),daily=mapOf("STEADY" to 3))
        tap("CONTINUE WITH GOOGLE");tap("USE SAVED PROFILE");requireText("CONNECTED WITH GOOGLE");clock(800);assertEquals(20,BalancePreferences(context).best(Difficulty.STEADY));assertEquals(3,BalancePreferences(context).today(Difficulty.STEADY))
        tap("SIGN OUT");requireText("CONTINUE WITH GOOGLE");assertFalse(account.state.value.signedIn);assertEquals(20,BalancePreferences(context).best(Difficulty.STEADY))
    }
    @Test fun signInLinksWithoutAUsernameGateAndNavigationCanReturnToPlay(){
        flags(mapOf("google_sign_in_enabled" to true));settings();tap("SAVE WITH GOOGLE");tap("CONTINUE WITH GOOGLE");requireText("CONNECTED WITH GOOGLE");device.pressBack();device.pressBack();requireText("PLAY");play();drop();waitScore(1)
    }
    @Test fun nativeReviewIsAttemptedOnceAcrossReturnAndActivityRecreation(){
        val gateway=JourneyReview();ReviewGatewayFactory.testGateway=gateway
        scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putInt("completed_runs",8).putLong("first_played_at",System.currentTimeMillis()-3L*86_400_000).commit();scenario=ActivityScenario.launch(MainActivity::class.java);requireText("PLAY");clock(3000)
        assertEquals(1,gateway.launches);scenario.recreate();requireText("PLAY");clock(2500);assertEquals(1,gateway.launches);assertTrue(BalancePreferences(context).reviewAttempted)
    }
    @Test fun reviewRemoteDisableAndBackgroundRequestCannotPresentACard(){
        val gateway=JourneyReview();ReviewGatewayFactory.testGateway=gateway
        scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putInt("completed_runs",8).putLong("first_played_at",System.currentTimeMillis()-3L*86_400_000).commit();scenario=ActivityScenario.launch(MainActivity::class.java)
        flags(mapOf("reviews_enabled" to false));clock(2300);assertEquals(0,gateway.launches)
        gateway.requestDelay=3500;flags(mapOf("reviews_enabled" to true));clock(2000);assertEquals(1,gateway.requests);device.pressHome();clock(4000);assertEquals(0,gateway.launches);assertFalse(BalancePreferences(context).reviewAttempted)
    }
    @Test fun newPlayerCanSaveFriendsBeforeChoosingAUsername(){server.registered=false;friends();resource("username_search").text="rival";described("Follow rival_one").click();resource("username_search").text="";requireText("@rival_one");requireText("SET USERNAME")}
    @Test fun searchRetryAfterNetworkFailureActuallySearchesAgain(){friends();server.offline=true;resource("username_search").text="rival";requireText("Search unavailable. Retry.");server.offline=false;tap("RETRY");requireText("@rival_one")}
    @Test fun usernameValidationAndDeleteConfirmationKeepLocalRecords(){
        server.registered=false;friends();tap("SET USERNAME");resource("username_entry").text="ab";tap("SAVE");requireText("Use 3–20 letters, numbers or _.")
        resource("username_entry").text="new_player";tap("SAVE");requireText("SHARE @new_player");device.pressBack();tap("@new_player");tap("DELETE PROFILE");tap("CANCEL");requireText("DELETE PROFILE");tap("DELETE PROFILE");tap("DELETE");requireText("SET USERNAME")
    }
    @Test fun comparisonModesRemainIndependent(){friends();resource("username_search").text="rival";requireText("12");described("Compare CHAOS").click();requireText("CHAOS · BEST");assertTrue(device.wait(Until.gone(By.text("12")),5_000));described("Compare STEADY").click();requireText("12")}
    @Test fun discTrialCompletesAndStartsTheRecoveryTrial(){
        scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putInt("trials",1).commit();scenario=ActivityScenario.launch(MainActivity::class.java)
        tap("TRIALS");tap("START TRIAL");requireText("TRIAL 02");drop();requireText("NEXT TRIAL");drop();clock(500);assertEquals(1,score());music("paused");tap("NEXT TRIAL");requireText("TRIAL 03");waitScore(0);drop();waitScore(1);music("playing")
    }
    @Test fun rivalReminderIsOptInDeduplicatedAndOpensFriends(){
        val reminders=BalanceReminders(context)
        Assume.assumeTrue(reminders.permitted())
        try {
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            val prefs=context.getSharedPreferences("balance_reminders",Context.MODE_PRIVATE)
            RivalStore(context).follow("rival")
            prefs.edit().putLong("played",System.currentTimeMillis()-7*60*60*1000L).commit()
            assertFalse(runBlocking{reminders.check(server)})
            reminders.enabled=true
            assertTrue(runBlocking{reminders.check(server,hour=12)})
            val until=android.os.SystemClock.uptimeMillis()+5_000
            while(manager.activeNotifications.none{it.id==72} && android.os.SystemClock.uptimeMillis()<until)clock(100)
            val notification=manager.activeNotifications.single{it.id==72}.notification
            assertEquals("rival_one moved ahead",notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertFalse(runBlocking{reminders.check(server,hour=12)})
            notification.contentIntent.send();resource("username_search")
            reminders.enabled=false
            assertFalse(runBlocking{reminders.check(server,hour=12)})
        } finally {
            reminders.enabled=false
            context.getSystemService(android.app.NotificationManager::class.java).cancel(72)
        }
    }
    private fun flags(values:Map<String,Any>){
        scenario.close()
        context.getSharedPreferences("balance_flags",Context.MODE_PRIVATE).edit().putString("values",org.json.JSONObject(values).toString()).commit()
        scenario=ActivityScenario.launch(MainActivity::class.java);requireText("PLAY")
    }
    @Test fun previewMusicLoopsAndLeavingStopsIt(){
        settings();tap("SOUNDTRACK ›");tap("SIDE B");music("playing");clock(22_000);music("playing")
        assertTrue(context.getSystemService(AudioManager::class.java).isMusicActive);device.pressBack();music("paused")
    }
    @Test fun remoteDiscoveryDoesNotPermitPaymentWithoutServerReadiness(){
        pack.state.value=StylePackState(ready=true,price="₹99")
        flags(mapOf("style_pack_discovery_enabled" to true,"payments_enabled" to true,"payment_verification_ready" to false))
        settings();tap("STYLE PACK");requireText("PACK COMING SOON")
        assertFalse(device.hasObject(By.textStartsWith("UNLOCK")))
        described("Theme MINT").click();assertEquals(BalanceSkin.GOLD,BalancePreferences(context).skin)
    }
    @Test fun pendingPaymentNeverUnlocksAndRestoreCanUnlockTheBundledPack(){
        pack.state.value=StylePackState(ready=true,price="₹99")
        flags(mapOf("style_pack_discovery_enabled" to true,"payments_enabled" to true,"payment_verification_ready" to true))
        settings();tap("STYLE PACK");tap("UNLOCK ₹99");requireText("PAYMENT PENDING")
        described("Theme MINT").click();assertEquals(BalanceSkin.GOLD,BalancePreferences(context).skin)
        pack.receiptVerified=true;tap("RESTORE PURCHASES");requireText("PACK UNLOCKED")
        described("Theme MINT").click();assertEquals(BalanceSkin.MINT,BalancePreferences(context).skin)
        device.pressBack();tap("SOUNDTRACK ›");tap("AFTER HOURS");music("playing")
    }
    @Test fun disablingSalesPreservesOwnedThemesAndMusic(){
        pack.state.value=StylePackState(owned=true)
        flags(emptyMap());settings();tap("STYLE PACK");described("Theme NIGHT").click()
        assertEquals(BalanceSkin.NIGHT,BalancePreferences(context).skin);device.pressBack();tap("SOUNDTRACK ›");tap("LAST LIGHT");music("playing")
        assertFalse(device.hasObject(By.textStartsWith("UNLOCK")))
    }
    @Test fun remoteSwitchesCanHideTrialsAndFriendsWithoutBlockingPlay(){
        flags(mapOf("trials_enabled" to false,"friends_enabled" to false,"reminders_enabled" to false))
        assertFalse(device.hasObject(By.text("TRIALS")));tap("TODAY");assertFalse(device.hasObject(By.text("FRIENDS")))
        device.pressBack();play();drop();waitScore(1);music("playing")
    }
    @Test fun multiLayerTowerKeepsItsBaseVisibleAsItGrows(){
        tap("TRIALS");tap("01 / FIRST FIVE");tap("START TRIAL");drop();waitScore(1)
        val colors=setOf(0xfff4efe0.toInt(),0xff263d9c.toInt(),0xffe84b2c.toInt())
        fun band(pixels:IntArray,bounds:android.graphics.Rect,ink:Boolean):Pair<Int,Int>? {
            for(y in bounds.top until bounds.bottom step 3){
                var left=-1;var right=-1;var pixelCount=0
                for(x in bounds.left until bounds.right step 3){
                    val c=pixels[(y-bounds.top)*bounds.width()+x-bounds.left]
                    if(if(ink)c==0xff111115.toInt() else c in colors){if(left<0)left=x;right=x;pixelCount++}
                }
                if(pixelCount>=10)return (left+right)/2 to y
            }
            return null
        }
        for(target in 2..5){
            val end=android.os.SystemClock.uptimeMillis()+10_000;var aligned=false;var samples=0
            var previousX:Int?=null;var previousTime=0L
            while(android.os.SystemClock.uptimeMillis()<end && !aligned){
                val bounds=resource("game_scene").visibleBounds
                val captureStart=android.os.SystemClock.uptimeMillis()
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No scene screenshot")
                val captureTime=(captureStart+android.os.SystemClock.uptimeMillis())/2
                val pixels=IntArray(bounds.width()*bounds.height());bitmap.getPixels(pixels,0,bounds.width(),bounds.left,bounds.top,bounds.width(),bounds.height())
                val incoming=band(pixels,bounds,false);val base=band(pixels,bounds,true)
                aligned=incoming!=null && base!=null && kotlin.math.abs(incoming.first-base.first)<=16
                // Anticipate the visual crossing between screenshots, just as a
                // player does. Emulator screenshots are much slower than a tap.
                if(!aligned && incoming!=null && base!=null && previousX!=null){
                    val velocity=(incoming.first-previousX!!).toFloat()/(captureTime-previousTime).coerceAtLeast(1)
                    val distance=incoming.first-base.first
                    if(kotlin.math.abs(distance)<=50 && distance*velocity<0 && kotlin.math.abs(velocity)>.05f){
                        val delay=(-distance/velocity-(android.os.SystemClock.uptimeMillis()-captureTime)-25).toLong()
                        if(delay in 0..350){clock(delay);aligned=true}
                    }
                }
                previousX=incoming?.first;previousTime=captureTime
                if(samples++<3)android.util.Log.i("BalanceJourney","Scene $target $bounds: incoming=$incoming, pivot=$base")
                if(!aligned && android.os.SystemClock.uptimeMillis()>end-500)java.io.File(context.cacheDir,"camera-failure.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                if(aligned){
                    android.util.Log.i("BalanceJourney","Align layer $target: incoming=${incoming!!.first}, pivot=${base!!.first}")
                    val down=android.os.SystemClock.uptimeMillis()
                    for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                        val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                        event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                        InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event,true);event.recycle()
                    }
                } else clock(35)
            }
            assertTrue("The incoming block should cross the balance axis",aligned);waitScore(target);clock(1400)
        }
        assertEquals(5,score());requireText("NEXT TRIAL")
        val bounds=resource("game_scene").visibleBounds;val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No scene screenshot")
        try {
            val pixels=IntArray(bounds.width()*bounds.height());bitmap.getPixels(pixels,0,bounds.width(),bounds.left,bounds.top,bounds.width(),bounds.height())
            val base=band(pixels,bounds,true) ?: error("The fulcrum disappeared")
            var previous=0;var bands=0
            for(y in bounds.top until base.second){
                val c=bitmap.getPixel(base.first,y)
                if(c in colors){if(c!=previous)bands++;previous=c}else previous=0
            }
            assertTrue("All six landed blocks and the beam should remain visible",bands>=7)
        } finally {bitmap.recycle()}
    }
}

private class JourneyServer:CompetitionRepository {
    override val available=true
    @Volatile var offline=false
    @Volatile var registered=true
    @Volatile var rival=Competitor("rival","rival_one",mapOf("STEADY" to 12),competitionDay(),mapOf("STEADY" to 8))
    var dailyLeaders=listOf(rival)
    var player=Competitor("me","player_one",emptyMap(),competitionDay(),emptyMap())
    private fun check(){if(offline)throw java.io.IOException("Offline fixture")}
    override suspend fun own():Competitor?{check();return if(registered)player else null}
    override suspend fun claim(name:String):Competitor{check();if(usernameKey(name)=="rival_one")throw NameUnavailable();registered=true;player=player.copy(username=name);return player}
    override suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor?{check();if(!registered)return null;player=player.copy(best=player.best+(mode.name to best),daily=player.daily+(mode.name to today));return player}
    override suspend fun search(prefix:String):List<Competitor>{delay(80);check();return listOf(rival).filter{usernameKey(it.username).startsWith(usernameKey(prefix))}}
    override suspend fun load(ids:Set<String>):List<Competitor>{check();return listOf(rival).filter{it.id in ids}}
    override suspend fun leaders(mode:Difficulty):List<Competitor>{check();return dailyLeaders.filter{it.day==competitionDay() && it.score(mode,true)>0}.sortedWith(compareByDescending<Competitor>{it.score(mode,true)}.thenBy{usernameKey(it.username)}).take(3)}
    override suspend fun remove(){check();registered=false}
}

private class JourneyStylePack:StylePackStore {
    override val state=kotlinx.coroutines.flow.MutableStateFlow(StylePackState())
    var receiptVerified=false
    override suspend fun sync(config:BalanceConfig)=Unit
    override fun buy(activity:android.app.Activity,config:BalanceConfig){state.value=state.value.copy(pending=true,message="PAYMENT PENDING")}
    override fun restore(){if(receiptVerified)state.value=state.value.copy(owned=true,pending=false,message="PACK UNLOCKED")}
    override fun close()=Unit
}

private class JourneyReview:ReviewGateway {
    override val available=true
    @Volatile var requests=0;@Volatile var launches=0;@Volatile var requestDelay=0L
    override suspend fun request(){requests++;delay(requestDelay)}
    override suspend fun launch(activity:android.app.Activity){launches++}
}
private class JourneyAccount:BalanceAccount {
    override val state=kotlinx.coroutines.flow.MutableStateFlow(AccountState())
    var result="success"
    override fun configure(enabled:Boolean){state.value=state.value.copy(configured=enabled)}
    override suspend fun signIn(activity:android.app.Activity){state.value=state.value.copy(busy=true,message="");delay(150);state.value=when(result){"cancel"->state.value.copy(busy=false);"fail"->state.value.copy(busy=false,message="Couldn’t sign in. Try again.");"saved"->state.value.copy(busy=false,savedProfile=true);else->state.value.copy(busy=false,signedIn=true)}}
    override suspend fun useSavedProfile(){state.value=state.value.copy(signedIn=true,savedProfile=false,revision=state.value.revision+1)}
    override fun cancelSwitch(){state.value=state.value.copy(savedProfile=false,message="")}
    override suspend fun signOut(){state.value=state.value.copy(signedIn=false,revision=state.value.revision+1)}
    override fun close()=Unit
}
