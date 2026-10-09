package com.stackapp.stack.tap

data class SoundPalette(
    val id: String,
    val label: String,
    val premium: Boolean,
)

val soundPalettes = listOf(
    SoundPalette("crystal", "Crystal", false),
    SoundPalette("felt", "Felt", false),
    SoundPalette("celeste", "Celeste", true),
    SoundPalette("marimba", "Marimba", true),
    SoundPalette("kalimba", "Kalimba", true),
    SoundPalette("pizzicato", "Pizzicato", true),
    SoundPalette("temple", "Temple", true),
    SoundPalette("bloom", "Bloom", true),
    SoundPalette("fifth", "Fifth", true),
    SoundPalette("joy", "Joy", true),
    SoundPalette("off", "Off", false),
)

fun soundPalette(id: String): SoundPalette =
    soundPalettes.firstOrNull { it.id == id } ?: soundPalettes.first()
