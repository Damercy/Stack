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
import com.stackapp.stack.offbalance.ReviewerAccess
import com.stackapp.stack.ui.StackAdaptiveFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Non-exported sample session; every launch requires the signed review code. */
class DemoActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.attributes=window.attributes.apply{preferredRefreshRate=60f}
        lifecycleScope.launch {
            val authorized=withContext(Dispatchers.Default){
                val code=runCatching{intent.getStringExtra(ReviewerAccess.EXTRA_CODE)}.getOrNull().orEmpty()
                ReviewerAccess.valid(this@DemoActivity,code)
            }
            if(!authorized){finish();return@launch}
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
