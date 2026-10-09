package com.stackapp.stack.offbalance

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stackapp.stack.MainActivity
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Check actual AudioTrack progress beyond every bundled WAV, not just UI state. */
@RunWith(AndroidJUnit4::class)
class BalanceAudioLoopTest {
    @Test fun everyGrooveContinuesBeyondItsBufferAndResumesAfterPause(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("off_balance",Context.MODE_PRIVATE)
        val introduced=prefs.getBoolean("introduced",false)
        prefs.edit().putBoolean("introduced",true).commit()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        var audio:BalanceAudio?=null
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        fun position():Int {var position=0;main{position=audio!!.playbackPosition};return position}
        try {
            main{audio=BalanceAudio(context)}
            for(track in 0..5){
                main{audio!!.update(track,.2f,true)}
                val deadline=android.os.SystemClock.uptimeMillis()+10_000
                while(position()==0 && android.os.SystemClock.uptimeMillis()<deadline)android.os.SystemClock.sleep(50)
                assertTrue("Groove $track must start",position()>0)
                android.os.SystemClock.sleep(21_000) // Beyond the longest 20-second buffer.
                val before=position();android.os.SystemClock.sleep(250)
                assertNotEquals("Groove $track ended at its first loop",before,position())
                main{audio!!.update(track,.2f,false)}
                android.os.SystemClock.sleep(150);val paused=position();android.os.SystemClock.sleep(200)
                assertEquals("Paused audio must not advance",paused,position())
                main{audio!!.update(track,.2f,true)}
                android.os.SystemClock.sleep(250)
                assertNotEquals("Groove $track must resume",paused,position())
            }
        }finally {
            main{audio?.close()};scenario.close();prefs.edit().putBoolean("introduced",introduced).commit()
        }
    }
}
