package com.stackapp.stack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.lifecycleScope
import com.stackapp.stack.offbalance.DemoSession
import com.stackapp.stack.offbalance.OffBalanceApp
import com.stackapp.stack.ui.StackAdaptiveFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit, non-exported entry point for trying all content without an account. */
class DemoActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.attributes=window.attributes.apply{preferredRefreshRate=60f}
        lifecycleScope.launch {
            val session=withContext(Dispatchers.IO){
                if(savedInstanceState==null)DemoSession.reset(this@DemoActivity)
                DemoSession(this@DemoActivity)
            }
            setContent { MaterialTheme(colorScheme=lightColorScheme()) { StackAdaptiveFrame {
                OffBalanceApp(this@DemoActivity,"demo_player","US",{}, {}, {},demoSession=session)
            } } }
        }
    }
}
