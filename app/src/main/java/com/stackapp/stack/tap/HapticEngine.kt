package com.stackapp.stack.tap

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

interface HapticEngine {
    fun clink(intensity: Float = 0.75f, accent: Int = 0)
    fun celebrate()
}

class AndroidHapticEngine(context: Context) : HapticEngine {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun clink(intensity: Float, accent: Int) {
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) return

        val strength = intensity.coerceIn(0.55f, 1f)
        val primary = (145 + (strength * 75) + (accent.coerceIn(0, 3) * 8))
            .toInt()
            .coerceAtMost(255)
        val echo = (90 + (strength * 50) + (accent.coerceIn(0, 3) * 6))
            .toInt()
            .coerceAtMost(210)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeVibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 14, 16, 18),
                    intArrayOf(0, primary, 0, echo),
                    -1,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            activeVibrator.vibrate(longArrayOf(0, 16, 18, 22), -1)
        }
    }

    override fun celebrate() {
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeVibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 18, 28, 22, 34, 28),
                    intArrayOf(0, 205, 0, 225, 0, 245),
                    -1,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            activeVibrator.vibrate(longArrayOf(0, 18, 28, 22, 34, 28), -1)
        }
    }
}
