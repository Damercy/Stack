package com.stackapp.stack

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.stackapp.stack.autominer.AutoMinerWorker
import com.stackapp.stack.offbalance.OffBalanceApp
import com.stackapp.stack.security.installFirebaseAppCheckIfAvailable
import com.stackapp.stack.tap.RoomTapStore
import com.stackapp.stack.ui.StackAdaptiveFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var boardRequest by mutableIntStateOf(0)
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_rivals",false))boardRequest++}
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash=installSplashScreen()
        super.onCreate(savedInstanceState)
        if(intent.getBooleanExtra("open_rivals",false))boardRequest++
        enableEdgeToEdge()
        window.attributes=window.attributes.apply{preferredRefreshRate=60f}
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.rgb(243, 227, 107)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (BuildConfig.USE_FIREBASE) installFirebaseAppCheckIfAvailable(this)
        var loading=true
        splash.setKeepOnScreenCondition { loading }
        lifecycleScope.launch {
        val (store,state)=withContext(Dispatchers.IO) {
            listOf("off_balance","rivals","balance_reminders","balance_flags","style_pack").forEach {
                getSharedPreferences(it,MODE_PRIVATE).all
            }
            val local=RoomTapStore(this@MainActivity)
            val saved=local.load()
            local.applyAutoMiner(System.currentTimeMillis())
            AutoMinerWorker.sync(this@MainActivity,saved.autoMinerActive)
            local to saved
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                StackAdaptiveFrame {
                    OffBalanceApp(
                        context = this@MainActivity,
                        initialName = state.displayName,
                        initialCountry = state.countryCode,
                        saveName = { name -> lifecycleScope.launch(Dispatchers.IO){store.saveDisplayName(name)}; Unit },
                        saveCountry = { code -> lifecycleScope.launch(Dispatchers.IO){store.saveCountryCode(code)}; Unit },
                        onLanding = { withContext(Dispatchers.IO) { store.recordTap(); Unit } },
                        openRivalsRequest = boardRequest,
                    )
                }
            }
        }
        loading=false
        }
    }
}
