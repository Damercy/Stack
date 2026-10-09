package com.stackapp.stack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature

internal val LocalStackWindowSize = staticCompositionLocalOf { DpSize(360.dp, 800.dp) }

/** Use available window bounds; keep controls and the scene out of an occluding hinge. */
@Composable
fun StackAdaptiveFrame(content: @Composable () -> Unit) {
    val folds by collectFoldingFeaturesAsState()
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val fold = folds.firstOrNull { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
        var x = 0.dp
        var y = 0.dp
        var width = maxWidth
        var height = maxHeight
        if (fold != null) {
            with(density) {
                if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
                    val left = (fold.bounds.left - origin.x).toDp().coerceIn(0.dp, maxWidth)
                    val right = (fold.bounds.right - origin.x).toDp().coerceIn(left, maxWidth)
                    if (left >= maxWidth - right) width = left else { x = right; width = maxWidth - right }
                } else {
                    val top = (fold.bounds.top - origin.y).toDp().coerceIn(0.dp, maxHeight)
                    val bottom = (fold.bounds.bottom - origin.y).toDp().coerceIn(top, maxHeight)
                    if (top >= maxHeight - bottom) height = top else { y = bottom; height = maxHeight - bottom }
                }
            }
        }
        Box(Modifier.offset(x, y).width(width).height(height)) {
            CompositionLocalProvider(LocalStackWindowSize provides DpSize(width, height), content = content)
        }
    }
}
