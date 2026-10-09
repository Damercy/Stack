package com.stackapp.stack.offbalance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.*

/** A short playable lesson. No account, payment or notification permission gate. */
@Composable fun OnboardingScreen(step:Int,foreground:Boolean,touch:Boolean,tilt:()->Float,
    useTouch:()->Unit,onStep:(Int)->Unit,complete:()->Unit,skip:()->Unit,back:()->Unit,
    landed:()->Unit,playing:(Boolean)->Unit,balanceBits:Int,saveBalance:(Int)->Unit) {
    val palette=LocalBalancePalette.current
    val game=remember { BalancePhysics(Difficulty.STEADY,true,trial=0) }
    val frame=remember { mutableStateOf(game.snapshot()) }
    var started by remember { mutableStateOf(false) }
    var landComplete by rememberSaveable { mutableStateOf(false) }
    var input by remember { mutableFloatStateOf(0f) }
    var heldLeft by rememberSaveable { mutableStateOf(balanceBits and 1!=0) }
    var heldRight by rememberSaveable { mutableStateOf(balanceBits and 2!=0) }
    val latestTilt by rememberUpdatedState(tilt)
    val latestTouch by rememberUpdatedState(touch)
    val latestLanded by rememberUpdatedState(landed)
    val latestSave by rememberUpdatedState(saveBalance)
    LaunchedEffect(step,foreground,started,landComplete) {
        playing(step==0 && foreground && started && !landComplete)
        if(!foreground || step==2 || step==0 && (!started || landComplete))return@LaunchedEffect
        var last=withFrameNanos{it}
        var leftTime=0f;var rightTime=0f
        while(isActive){
            val now=withFrameNanos{it};val dt=((now-last)/1e9f).coerceIn(0f,.1f);last=now
            if(step==0){
                val next=game.advance(dt.toDouble());frame.value=next
                if(next.score>=1){landComplete=true;latestLanded();playing(false);break}
            } else {
                if(!latestTouch)input=(latestTilt()*25).roundToInt()/25f
                leftTime=if(input<-.18f)leftTime+dt else 0f
                rightTime=if(input>.18f)rightTime+dt else 0f
                if(leftTime>=.2f && !heldLeft){heldLeft=true;latestSave((if(heldLeft)1 else 0)+(if(heldRight)2 else 0))}
                if(rightTime>=.2f && !heldRight){heldRight=true;latestSave((if(heldLeft)1 else 0)+(if(heldRight)2 else 0))}
            }
        }
    }
    DisposableEffect(Unit){onDispose{playing(false)}}
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=8.dp).testTag("onboarding")) {
        val wide=maxWidth>=650.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Symbol("Back",onClick=back);Utility("${step+1} / 3",size=11)
                PressSurface(Modifier.height(48.dp),"Skip lesson",skip){Utility("SKIP",Modifier.align(Alignment.Center))}
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                repeat(3){i->Box(Modifier.weight(1f).height(4.dp).background(if(i<=step)palette.accent else palette.ink.copy(alpha=.2f)))}
            }
            Spacer(Modifier.height(18.dp))
            val title=when(step){0->"LAND IT";1->"BALANCE";else->"YOU'RE IN"}
            val hint=when(step){0->if(landComplete)"ONE DOWN. THAT'S THE FEELING." else "TAP DROP. WATCH IT LAND."
                1->if(touch)"DRAG LEFT. THEN RIGHT." else "LOWER YOUR LEFT EDGE. THEN YOUR RIGHT."
                else->"START WITH FIVE. SEE HOW HIGH YOU GO."}
            if(wide)Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f).padding(end=24.dp)){MovingPoster(title,size=110);Spacer(Modifier.height(16.dp));Utility(hint,size=12)}
                LessonTower(step,frame,landComplete,input,Modifier.weight(1f).fillMaxHeight())
            } else {
                MovingPoster(title,size=110);Spacer(Modifier.height(16.dp));Utility(hint,Modifier.fillMaxWidth(),size=11,align=TextAlign.Center)
                LessonTower(step,frame,landComplete,input,Modifier.weight(1f).fillMaxWidth())
            }
            when(step){
                0 -> {
                    if(landComplete)Action("NEXT"){onStep(1)}
                    else PressSurface(Modifier.fillMaxWidth().height(88.dp).background(palette.primary),"Drop practice block",{
                        if(!started && game.drop()){started=true}
                    },enabled=!started,pressFeedback=false){PosterFit(if(started)"LANDING" else "DROP",Modifier.align(Alignment.Center).padding(14.dp),palette.background,maxSize=58)}
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Utility(if(heldLeft)"✓ LEFT" else "← LEFT",size=11);Utility(if(heldRight)"RIGHT ✓" else "RIGHT →",size=11)}
                    BalanceGauge(input,touch=touch,onBalance={input=it})
                    if(heldLeft && heldRight)Action("NEXT"){onStep(2)}
                    else Utility("TRY BOTH SIDES",Modifier.fillMaxWidth().padding(vertical=14.dp),size=11,align=TextAlign.Center)
                    if(!touch)PressSurface(Modifier.fillMaxWidth().height(48.dp),onClick=useTouch){Utility("USE TOUCH INSTEAD",Modifier.align(Alignment.Center),size=10)}
                }
                else -> {
                    Utility("FIRST TARGET / 5 LAYERS",Modifier.fillMaxWidth().padding(vertical=14.dp),align=TextAlign.Center,size=11)
                    Action("LET'S STACK",Modifier.testTag("onboarding_finish"),onClick=complete)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable private fun LessonTower(step:Int,frame:State<BalanceFrame>,complete:Boolean,input:Float,modifier:Modifier){
    if(step==0)TowerDrawing({if(complete)frame.value.copy(incoming=null) else frame.value},modifier)
    else {
        val angle=if(step==1)-input*.22f else -.04f
        fun pose(x:Float,y:Float,w:Float,h:Float,color:Int)=PiecePose(x*cos(angle)-y*sin(angle),x*sin(angle)+y*cos(angle),angle,w,h,PieceKind.SLAB,color)
        val lesson=BalanceFrame(listOf(pose(0f,.40f,1.65f,.5f,0),pose(0f,.91f,1.65f,.5f,2),pose(0f,1.42f,1.65f,.5f,1)),pose(0f,0f,3.4f,.28f,1),null,0,input,false,false,false,0f)
        TowerDrawing(lesson,modifier,decorative=true)
    }
}
