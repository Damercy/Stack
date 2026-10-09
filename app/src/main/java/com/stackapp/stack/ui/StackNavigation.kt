package com.stackapp.stack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StackNavigation(selected: Int, rail: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalStackPalette.current
    val tabs = listOf(0 to "Today", 1 to "Stack", 2 to "Collection")
    val haptics = LocalHapticFeedback.current
    val item: @Composable (Int, String, Modifier) -> Unit = { index, label, itemModifier ->
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .93f else 1f, spring(stiffness = 650f), label = "tab-press")
        val tint by animateColorAsState(if (selected == index) palette.accent else palette.secondary, label = "tab-color")
        val highlight by animateColorAsState(if (selected == index) palette.accent.copy(alpha = .10f) else Color.Transparent, label = "tab-selection")
        Column(
            itemModifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(22.dp)).background(highlight)
                .selectable(selected == index, interactionSource = interaction, indication = null, role = Role.Tab) {
                    if (selected != index) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(index) }
                }.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(when (index) { 0 -> Icons.Rounded.BarChart; 1 -> Icons.Rounded.Layers; else -> Icons.Rounded.Tune }, null, Modifier.size(24.dp), tint = tint)
            Text(label, color = tint, fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (rail) {
        Column(modifier.padding(start = 12.dp).width(80.dp).clip(RoundedCornerShape(30.dp)).background(palette.colorScheme.surface).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (index, label) -> item(index, label, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier.padding(horizontal = 20.dp, vertical = 10.dp).widthIn(max = 440.dp).fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)).background(palette.colorScheme.surface).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) { tabs.forEach { (index, label) -> item(index, label, Modifier.weight(1f)) } }
    }
}
