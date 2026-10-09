package com.stackapp.stack.offbalance

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*

data class BalancePalette(val background:Color,val primary:Color,val accent:Color,val ink:Color,val paper:Color)
enum class BalanceSkin(val title:String,val palette:BalancePalette) {
    GOLD("GOLD",BalancePalette(Sun,Cobalt,Vermilion,Ink,Cream)),
    PAPER("PAPER",BalancePalette(Color(0xffeee8da),Color(0xff353e69),Color(0xffcc6043),Color(0xff292825),Color(0xfffaf7ee))),
    MINT("MINT",BalancePalette(Color(0xffbae8c7),Color(0xff184d44),Color(0xfff06e42),Color(0xff162e28),Color(0xfff5f5dd))),
    NIGHT("NIGHT",BalancePalette(Color(0xff171a29),Color(0xffcdd5f3),Color(0xfff37554),Color(0xffece9d8),Color(0xffb8bdc4)))
}
val LocalBalancePalette=staticCompositionLocalOf{BalanceSkin.GOLD.palette}
data class BalanceReward(val id:Int,val label:String)

/** A finite draw-only burst: no pointer interception and no idle particle loop. */
@Composable fun RewardBurst(reward:BalanceReward,duration:Int,finished:()->Unit) {
    val progress=remember(reward.id){Animatable(0f)}
    val done by rememberUpdatedState(finished)
    val palette=LocalBalancePalette.current
    LaunchedEffect(reward.id){progress.animateTo(1f,tween(duration,easing=LinearEasing));done()}
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val p=progress.value;val alpha=(1-p).coerceIn(0f,1f)
            drawRect(palette.accent.copy(alpha=.035f*alpha))
            val origin=Offset(size.width/2,size.height*.42f)
            repeat(24){i->
                val angle=(i*137%360)*PI.toFloat()/180
                val travel=min(size.width,size.height)*(.22f+(i%5)*.035f)*p
                val center=origin+Offset(cos(angle)*travel,sin(angle)*travel+size.height*.22f*p*p)
                val color=listOf(palette.primary,palette.accent,palette.ink)[i%3].copy(alpha=alpha)
                rotate(i*29f+p*180f,center){drawRoundRect(color,center-Offset(3.dp.toPx(),6.dp.toPx()),androidx.compose.ui.geometry.Size(6.dp.toPx(),12.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))}
            }
        }
        PosterFit(reward.label,Modifier.align(Alignment.Center).offset(y=(-80).dp).padding(horizontal=24.dp).graphicsLayer {
            alpha=sin(progress.value*PI.toFloat()).coerceAtLeast(0f);translationY=-24.dp.toPx()*progress.value
        },color=palette.ink)
    }
}
