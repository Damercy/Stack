package com.stackapp.stack.tap

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.stackapp.stack.R

interface AudioEngine {
    fun playTone(intensity: Float = 0.75f, accent: Int = 0, count: Long = 1)
    fun previewTone(paletteId: String)
    fun selectTone(paletteId: String)
    fun clink(intensity: Float = 0.75f, accent: Int = 0, count: Long = 1)
    fun selectPalette(paletteId: String)
    fun previewPalette(paletteId: String)
    fun celebrate(count: Long)
    fun release()
}

class AndroidAudioEngine(context: Context) : AudioEngine {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private val soundIds = mapOf(
        "crystal" to soundPool.load(context, R.raw.stack_crystal, 1),
        "felt" to soundPool.load(context, R.raw.stack_felt, 1),
        "celeste" to soundPool.load(context, R.raw.stack_celeste, 1),
        "marimba" to soundPool.load(context, R.raw.stack_marimba, 1),
        "kalimba" to soundPool.load(context, R.raw.stack_kalimba, 1),
        "pizzicato" to soundPool.load(context, R.raw.stack_pizzicato, 1),
        "temple" to soundPool.load(context, R.raw.stack_temple, 1),
        "bloom" to soundPool.load(context, R.raw.stack_bloom, 1),
        "fifth" to soundPool.load(context, R.raw.stack_fifth, 1),
        "joy" to soundPool.load(context, R.raw.stack_joy, 1),
    )
    private val readySounds = mutableSetOf<Int>()
    private var selectedPaletteId = "crystal"

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readySounds += sampleId
        }
    }

    override fun playTone(intensity: Float, accent: Int, count: Long) {
        if (selectedPaletteId == "off") return
        val level = intensity.coerceIn(0.55f, 1f)
        val step = ((count - 1).coerceAtLeast(0) % 8).toInt()
        val phrase = ((count - 1).coerceAtLeast(0) / 8).toInt()
        val notes = notesFor(selectedPaletteId, phrase)
        playNote(
            paletteId = selectedPaletteId,
            rate = (notes[step] + accent.coerceIn(0, 3) * 0.01f).coerceIn(0.55f, 1.92f),
            volume = 0.55f + (level * 0.22f),
        )

        if (count % 8L == 0L) {
            handler.postDelayed({
                playNote(selectedPaletteId, notes[0] * 1.5f, 0.32f + level * 0.10f)
            }, 72L)
        }
        if (count % 32L == 0L) {
            handler.postDelayed({
                playNote(selectedPaletteId, notes[0] * 1.78f, 0.28f + level * 0.10f)
            }, 150L)
        }
    }

    override fun clink(intensity: Float, accent: Int, count: Long) =
        playTone(intensity, accent, count)

    override fun selectPalette(paletteId: String) {
        selectTone(paletteId)
    }

    override fun selectTone(paletteId: String) {
        selectedPaletteId = soundPalette(paletteId).id
    }

    override fun previewPalette(paletteId: String) {
        previewTone(paletteId)
    }

    override fun previewTone(paletteId: String) {
        val id = soundPalette(paletteId).id
        if (id == "off") return
        playNote(id, notesFor(id, 0)[0], 0.72f)
    }

    override fun celebrate(count: Long) {
        val root = notesFor(selectedPaletteId, (count / 8).toInt())[0]
        playNote(selectedPaletteId, root, 0.58f)
        handler.postDelayed({ playNote(selectedPaletteId, root * 1.26f, 0.48f) }, 85L)
        handler.postDelayed({ playNote(selectedPaletteId, root * 1.50f, 0.42f) }, 165L)
    }

    private fun playNote(paletteId: String, rate: Float, volume: Float) {
        val soundId = soundIds[paletteId] ?: return
        if (soundId !in readySounds) return
        soundPool.play(
            soundId,
            volume,
            volume,
            1,
            0,
            rate.coerceIn(0.5f, 2f),
        )
    }

    override fun release() {
        handler.removeCallbacksAndMessages(null)
        readySounds.clear()
        soundPool.release()
    }

    private companion object {
        val PATTERNS = listOf(
            floatArrayOf(1.00f, 1.12f, 1.26f, 1.50f, 1.26f, 1.68f, 1.50f, 1.34f),
            floatArrayOf(1.00f, 1.26f, 1.12f, 1.50f, 1.68f, 1.50f, 1.26f, 1.12f),
            floatArrayOf(1.00f, 1.12f, 1.34f, 1.50f, 1.34f, 1.12f, 1.26f, 1.50f),
            floatArrayOf(1.00f, 1.50f, 1.34f, 1.26f, 1.12f, 1.26f, 1.50f, 1.68f),
            floatArrayOf(1.00f, 1.26f, 1.50f, 1.68f, 1.50f, 1.34f, 1.12f, 1.26f),
            floatArrayOf(1.00f, 1.34f, 1.12f, 1.50f, 1.26f, 1.68f, 1.34f, 1.50f),
        )

        fun notesFor(paletteId: String, phrase: Int): FloatArray =
            when (paletteId) {
                "fifth" -> floatArrayOf(1.26f, 1.26f, 1.26f, 1.00f, 1.12f, 1.12f, 1.12f, 0.94f)
                "joy" -> floatArrayOf(1.00f, 1.00f, 1.12f, 1.26f, 1.26f, 1.12f, 1.00f, 0.89f)
                else -> PATTERNS[(phrase + soundPalettes.indexOfFirst { it.id == paletteId } * 2)
                    .mod(PATTERNS.size)]
            }
    }
}
