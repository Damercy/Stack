package com.stackapp.stack.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class StackTheme(val id: String) {
    Light("light"),
    Dark("dark"),
}

fun stackTheme(id: String): StackTheme = StackTheme.entries.firstOrNull { it.id == id } ?: StackTheme.Light

internal data class StackPalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val accent: Color,
    val brass: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val sceneScrim: Color,
    val colorScheme: ColorScheme,
)

internal val LocalStackPalette = staticCompositionLocalOf { lightStackPalette }

internal val lightStackPalette = StackPalette(
    background = Color(0xFFF2F2F7),
    foreground = Color(0xFF1C1C1E),
    secondary = Color(0xFF636366),
    accent = Color(0xFF007AFF),
    brass = Color(0xFF8B7446),
    glassTint = Color(0xF2FFFFFF),
    glassBorder = Color(0x183C3C43),
    glassHighlight = Color(0xD9FFFFFF),
    sceneScrim = Color(0x1AEEF2EF),
    colorScheme = lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color.White,
        surface = Color.White,
        onSurface = Color(0xFF1C1C1E),
    ),
)

internal val darkStackPalette = StackPalette(
    background = Color(0xFF000000),
    foreground = Color(0xFFF2F2F7),
    secondary = Color(0xFFAEAEB2),
    accent = Color(0xFF0A84FF),
    brass = Color(0xFFBDA36B),
    glassTint = Color(0xF21C1C1E),
    glassBorder = Color(0x28545458),
    glassHighlight = Color(0x70FFFFFF),
    sceneScrim = Color(0x30090A09),
    colorScheme = darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        surface = Color(0xFF1C1C1E),
        onSurface = Color(0xFFF2F2F7),
    ),
)

internal fun StackTheme.palette() = if (this == StackTheme.Light) lightStackPalette else darkStackPalette
