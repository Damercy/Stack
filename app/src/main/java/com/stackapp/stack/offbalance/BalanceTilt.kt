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
        val angle=TiltMapping.angle(event.values[0],event.values[1],rotation)
        filtered=if(hasSample)filtered*.88f+angle*.12f else angle;hasSample=true
        if(calibrateNext){neutral=filtered;calibrateNext=false}
        value=TiltMapping.input(filtered,neutral)
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
}
