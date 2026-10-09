package com.stackapp.stack.offbalance

import android.content.Context
import android.hardware.*
import android.view.Surface
import android.view.WindowManager
import kotlin.math.*

/** Gravity rejects tapping impulses; fallback accelerometer is low-pass filtered. */
class BalanceTilt(context: Context) : SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val windows=context.getSystemService(WindowManager::class.java)
    private val sensor=manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available get()=sensor!=null
    private var filtered=0f
    private var neutral=0f
    private var hasSample=false
    private var calibrateNext=true
    private var lastRotation=-1
    var value=0f; private set
    fun start() { calibrateNext=true; sensor?.let {manager?.registerListener(this,it,20_000)} }
    fun stop() { manager?.unregisterListener(this);value=0f }
    fun calibrate() { if(hasSample)neutral=filtered else calibrateNext=true;value=0f }
    override fun onSensorChanged(event: SensorEvent) {
        @Suppress("DEPRECATION") val rotation=windows?.defaultDisplay?.rotation?:Surface.ROTATION_0
        if(rotation!=lastRotation){lastRotation=rotation;hasSample=false;calibrateNext=true}
        val x=when(rotation){Surface.ROTATION_90 -> -event.values[1];Surface.ROTATION_180 -> -event.values[0];Surface.ROTATION_270 -> event.values[1];else ->event.values[0]}
        val angle=asin((x/9.81f).coerceIn(-1f,1f))
        filtered=if(hasSample)filtered*.88f+angle*.12f else angle;hasSample=true
        if(calibrateNext){neutral=filtered;calibrateNext=false}
        val delta=filtered-neutral
        value=if(abs(delta)<.025f)0f else (delta-sign(delta)*.025f)/.24f
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
}
