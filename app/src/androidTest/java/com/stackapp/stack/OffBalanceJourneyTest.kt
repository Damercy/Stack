package com.stackapp.stack

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    @Before fun launch(){
        android.util.Log.i("BalanceJourney","setup")
        Configurator.getInstance().waitForIdleTimeout=0
        original=listOf("off_balance","rivals","balance_reminders","balance_flags","style_pack").associateWith{context.getSharedPreferences(it,Context.MODE_PRIVATE).all}
        originalName=RoomTapStore(context).load().displayName.orEmpty()
        context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().clear().putBoolean("introduced",true).putBoolean("touch",true).putString("difficulty","STEADY").commit()
        context.getSharedPreferences("rivals",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("balance_reminders",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("balance_flags",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("style_pack",Context.MODE_PRIVATE).edit().clear().commit()
        server=JourneyServer();CompetitionFactory.testRepository=server
        pack=JourneyStylePack();StylePackFactory.testStore=pack
        scenario=ActivityScenario.launch(MainActivity::class.java)
        requireText("PLAY")
        android.util.Log.i("BalanceJourney","ready")
    }
    @After fun restore(){
        device.pressHome()
        scenario.close();CompetitionFactory.testRepository=null;StylePackFactory.testStore=null
        original.forEach{(name,values)->val e=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            values.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*>->{@Suppress("UNCHECKED_CAST") e.putStringSet(k,v as Set<String>)}}};e.commit()}
        RoomTapStore(context).saveDisplayName(originalName)
        BalanceReminders(context).sync();device.unfreezeRotation()
    }
    private fun missing(message:String):Nothing {device.dumpWindowHierarchy(java.io.File(context.cacheDir,"failure-journey.xml"));error(message)}
    private fun requireText(value:String)=device.wait(Until.findObject(By.text(value)),10_000) ?: missing("Missing text: $value")
    private fun described(value:String):UiObject2 {device.wait(Until.hasObject(By.desc(value)),10_000);clock(300);return device.findObject(By.desc(value)) ?: missing("Missing control: $value")}
    private fun resource(value:String)=device.wait(Until.findObject(By.res(value)),10_000) ?: missing("Missing element: $value")
    private fun tap(value:String){
        android.util.Log.i("BalanceJourney","tap $value")
        if(!device.wait(Until.hasObject(By.text(value)),1_000)){
            repeat(4){if(!device.hasObject(By.text(value)))device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.5f)}
        }
        requireText(value);clock(300);requireText(value).click();android.util.Log.i("BalanceJourney","tapped $value")
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
        val slider=described("MUSIC").visibleBounds;device.click(slider.left+margin,slider.centerY());requireText("0%");tap("DONE");tap("RESUME");music("paused");assertEquals(1,score())
        pause();tap("SETTINGS");val s=described("MUSIC").visibleBounds;device.click(s.right-margin,s.centerY());requireText("100%");tap("DONE");tap("RESUME");music("playing");assertEquals(1,score())
    }
    @Test fun switchingPreviewTracksThenLeavingStopsThePreview(){settings();tap("SOUNDTRACK ›");tap("SIDE A");music("playing");tap("NIGHT RUN");music("playing");tap("SIDE B");music("playing");tap("DONE");music("paused");tap("DONE");requireText("PLAY")}
    @Test fun rotationPreservesAnActiveTowerAndKeepsPauseReachable(){play();drop();waitScore(1);device.setOrientationLeft();music("playing");assertEquals(1,score());pause();tap("RESUME");device.setOrientationNatural();assertEquals(1,score())}
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
    @Test fun freshInstallStartsWithPlayAndNoNameOrPaymentGate(){
        scenario.close();context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).edit().putBoolean("introduced",false).commit();scenario=ActivityScenario.launch(MainActivity::class.java)
        resource("game_scene");assertFalse(device.hasObject(By.text("SAVE")));drop();waitScore(1);pause();tap("END RUN");requireText("PLAY")
        assertTrue(context.getSharedPreferences("off_balance",Context.MODE_PRIVATE).getBoolean("introduced",false))
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
        Assume.assumeTrue(java.time.LocalTime.now().hour in 10..20)
        val reminders=BalanceReminders(context)
        Assume.assumeTrue(reminders.permitted())
        try {
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            val prefs=context.getSharedPreferences("balance_reminders",Context.MODE_PRIVATE)
            RivalStore(context).follow("rival")
            prefs.edit().putLong("played",System.currentTimeMillis()-7*60*60*1000L).commit()
            assertFalse(runBlocking{reminders.check(server)})
            reminders.enabled=true
            assertTrue(runBlocking{reminders.check(server)})
            val notification=manager.activeNotifications.single{it.id==72}.notification
            assertEquals("rival_one moved ahead",notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertFalse(runBlocking{reminders.check(server)})
            notification.contentIntent.send();resource("username_search")
            reminders.enabled=false
            assertFalse(runBlocking{reminders.check(server)})
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
        assertTrue(context.getSystemService(AudioManager::class.java).isMusicActive);tap("DONE");music("paused")
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
        tap("DONE");tap("SOUNDTRACK ›");tap("AFTER HOURS");music("playing")
    }
    @Test fun disablingSalesPreservesOwnedThemesAndMusic(){
        pack.state.value=StylePackState(owned=true)
        flags(emptyMap());settings();tap("STYLE PACK");described("Theme NIGHT").click()
        assertEquals(BalanceSkin.NIGHT,BalancePreferences(context).skin);tap("DONE");tap("SOUNDTRACK ›");tap("LAST LIGHT");music("playing")
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
    private var player=Competitor("me","player_one",emptyMap(),competitionDay(),emptyMap())
    private fun check(){if(offline)throw java.io.IOException("Offline fixture")}
    override suspend fun own():Competitor?{check();return if(registered)player else null}
    override suspend fun claim(name:String):Competitor{check();if(usernameKey(name)=="rival_one")throw NameUnavailable();registered=true;player=player.copy(username=name);return player}
    override suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor?{check();if(!registered)return null;player=player.copy(best=player.best+(mode.name to best),daily=player.daily+(mode.name to today));return player}
    override suspend fun search(prefix:String):List<Competitor>{delay(80);check();return listOf(rival).filter{usernameKey(it.username).startsWith(usernameKey(prefix))}}
    override suspend fun load(ids:Set<String>):List<Competitor>{check();return listOf(rival).filter{it.id in ids}}
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
