package com.stackapp.stack.offbalance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.stackapp.stack.R
import kotlin.math.*
import androidx.navigation3.ui.LocalNavAnimatedContentScope

val Sun = Color(0xFFF3E36B)
val Cobalt = Color(0xFF263D9C)
val Vermilion = Color(0xFFE84B2C)
val Ink = Color(0xFF111115)
val Cream = Color(0xFFF4EFE0)
val PosterFont = FontFamily(Font(R.font.anton))
val UtilityFont = FontFamily(Font(R.font.roboto_mono))
val LocalBalanceShared=staticCompositionLocalOf<SharedTransitionScope?>{null}
@Composable private fun sharedLabel(text:String,modifier:Modifier):Modifier {
    val scope=LocalBalanceShared.current ?: return modifier
    if(text !in setOf("TODAY","FRIENDS","TRIALS","SETTINGS"))return modifier
    val visibility=LocalNavAnimatedContentScope.current
    return with(scope){modifier.sharedBounds(rememberSharedContentState("label-$text"),visibility,boundsTransform={_,_->spring(.85f,650f)})}
}

@Composable fun Utility(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Int = 12, align: TextAlign = TextAlign.Start) {
    BasicText(text, sharedLabel(text,modifier), style = TextStyle(fontFamily=UtilityFont, fontSize=size.sp, color=if(color==Color.Unspecified)LocalBalancePalette.current.ink else color, letterSpacing=1.sp, textAlign=align, lineHeight=(size*1.5).sp))
}
@Composable fun Poster(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Int = 90, align: TextAlign = TextAlign.Start) {
    // Anton's OpenType cap height is 1760 / 2048 em. Align from the measured
    // baseline so ink and touch bounds agree even under tight parent constraints.
    val capLayout=sharedLabel(text,modifier).clipToBounds().layout { measurable,constraints ->
        val fontPixels=size.sp.toPx()
        val cap=fontPixels*1760f/2048f
        val overshoot=fontPixels*.025f
        val line=measurable.measure(constraints.copy(minHeight=0,maxHeight=Constraints.Infinity))
        val baseline=line[FirstBaseline]
        layout(line.width,constraints.constrainHeight((cap+2*overshoot).roundToInt())) {
            line.placeRelative(0,(cap+overshoot-baseline).roundToInt())
        }
    }
    BasicText(text, capLayout, maxLines=1, softWrap=false, style=TextStyle(fontFamily=PosterFont,fontSize=size.sp, color=if(color==Color.Unspecified)LocalBalancePalette.current.primary else color,lineHeight=size.sp,letterSpacing=(-1).sp,textAlign=align,
        platformStyle=PlatformTextStyle(includeFontPadding=false),lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Top,LineHeightStyle.Trim.Both)))
}
@Composable fun PosterFit(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,maxSize:Int=220) {
    val measurer=rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val reference=measurer.measure(text,TextStyle(fontFamily=PosterFont,fontSize=100.sp,letterSpacing=(-1).sp),maxLines=1).size.width
        val pixels=with(androidx.compose.ui.platform.LocalDensity.current){maxWidth.toPx()}
        Poster(text,Modifier.fillMaxWidth(),color,(96f*pixels/reference.coerceAtLeast(1)).toInt().coerceIn(20,maxSize.coerceAtLeast(20)))
    }
}
@Composable fun MovingPoster(text: String, modifier: Modifier = Modifier, size: Int = 110) {
    AnimatedContent(text,modifier,transitionSpec={
        (fadeIn(tween(130,30)) + slideInVertically(spring(dampingRatio=.76f,stiffness=520f)){it/3}) togetherWith
            (fadeOut(tween(90)) + slideOutVertically(tween(120)){-it/4})
    },label="Poster rhythm") { PosterFit(it,Modifier.fillMaxWidth(),maxSize=size) }
}
@Composable fun PressSurface(modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit, interactionSource:MutableInteractionSource?=null, enabled:Boolean=true, content: @Composable BoxScope.() -> Unit) {
    val defaultInteraction=remember { MutableInteractionSource() }
    val interaction = interactionSource ?: defaultInteraction
    val pressed by interaction.collectIsPressedAsState()
    val currentDescription by rememberUpdatedState(description)
    val pressAlpha by animateFloatAsState(if(pressed).72f else 1f,tween(80),label="Press feedback")
    Box(modifier.graphicsLayer { alpha=pressAlpha }
        .semantics { role=Role.Button; currentDescription?.let { contentDescription=it } }
        .clickable(enabled=enabled,interactionSource=interaction,indication=null,onClick=onClick),content=content)
}
@Composable fun Action(text: String, modifier: Modifier = Modifier, outline: Boolean = false, onClick: () -> Unit) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper

    PressSurface(modifier.fillMaxWidth().heightIn(min=if(outline)54.dp else 76.dp).then(if(outline) Modifier.border(1.5.dp,Ink) else Modifier.background(Cobalt)),onClick=onClick) {
        if(outline) Utility(text,Modifier.align(Alignment.Center).padding(14.dp),size=16)
        else BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal=14.dp,vertical=3.dp)) {
            val measure=rememberTextMeasurer()
            val reference=measure.measure(text,TextStyle(fontFamily=PosterFont,fontSize=64.sp,letterSpacing=(-1).sp),maxLines=1).size.width
            val pixels=with(androidx.compose.ui.platform.LocalDensity.current){maxWidth.toPx()}
            Poster(text,Modifier.fillMaxWidth(),Sun,(64*pixels/reference.coerceAtLeast(1)).toInt().coerceIn(12,64),TextAlign.Center)
        }
    }
}
@Composable fun Rule() {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
 Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha=.7f))) }
@Composable fun Symbol(kind: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
    val resolvedColor=if(color==Color.Unspecified)Ink else color

    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if(pressed)if(kind=="Settings")12f else -4f else 0f,spring(.6f,800f),label="Icon flick")
    PressSurface(modifier.size(48.dp),kind,onClick,interaction) {
        Canvas(Modifier.size(26.dp).align(Alignment.Center).graphicsLayer{rotationZ=turn}) {
            val s=size.width; val line=Stroke(2.6.dp.toPx(),cap=StrokeCap.Square)
            when(kind) {
                "Pause" -> { drawRect(resolvedColor,Offset(s*.2f,0f),androidx.compose.ui.geometry.Size(s*.2f,s));drawRect(resolvedColor,Offset(s*.6f,0f),androidx.compose.ui.geometry.Size(s*.2f,s)) }
                "Back" -> { val p=Path().apply { moveTo(s*.7f,s*.1f);lineTo(s*.25f,s*.5f);lineTo(s*.7f,s*.9f) };drawPath(p,resolvedColor,style=line) }
                "Settings" -> { for(i in 0..7) rotate(i*45f) { drawRect(resolvedColor,Offset(s*.43f,0f),androidx.compose.ui.geometry.Size(s*.14f,s*.28f)) };drawCircle(resolvedColor,s*.35f);drawCircle(Sun,s*.13f) }
                "Sound" -> { val p=Path().apply { moveTo(0f,s*.35f);lineTo(s*.25f,s*.35f);lineTo(s*.5f,s*.1f);lineTo(s*.5f,s*.9f);lineTo(s*.25f,s*.65f);lineTo(0f,s*.65f);close() };drawPath(p,resolvedColor);drawArc(resolvedColor,-60f,120f,false,Offset(s*.27f,s*.1f),androidx.compose.ui.geometry.Size(s*.65f,s*.8f),style=line) }
                else -> { drawLine(resolvedColor,Offset(s*.15f,s*.15f),Offset(s*.85f,s*.85f),line.width);drawLine(resolvedColor,Offset(s*.85f,s*.15f),Offset(s*.15f,s*.85f),line.width) }
            }
        }
    }
}
@Composable fun Switch(on: Boolean, label: String, changed: (Boolean)->Unit) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper

    val travel by animateFloatAsState(if(on)1f else 0f,spring(.6f,800f),label="Toggle bounce")
    PressSurface(Modifier.size(64.dp,48.dp),label,{changed(!on)}) {
        Canvas(Modifier.size(60.dp,32.dp).align(Alignment.Center).semantics { stateDescription=if(on)"On" else "Off" }) {
            drawRoundRect(if(on)Vermilion else Ink.copy(alpha=.3f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))
            drawCircle(Cream,size.height*.37f,Offset(size.height/2+travel*(size.width-size.height),size.height/2))
        }
    }
}
@Composable fun BalanceSlider(value: Float, label: String, changed:(Float)->Unit) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper

    val wave=rememberInfiniteTransition(label="Slider wave")
    val phase=wave.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(2600,easing=LinearEasing)),label="Wave phase")
    var width by remember { mutableFloatStateOf(1f) }
    val inset=with(androidx.compose.ui.platform.LocalDensity.current){12.dp.toPx()}
    val currentChanged by rememberUpdatedState(changed)
    fun valueAt(x:Float)=((x-inset)/(width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription=label;progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
        setProgress { currentChanged(it.coerceIn(0f,1f));true }
    }.pointerInput(inset){detectTapGestures { currentChanged(valueAt(it.x)) }}
        .pointerInput(inset){detectDragGestures { change,_ -> change.consume();currentChanged(valueAt(change.position.x)) }}) {
        width=size.width
        val inset=12.dp.toPx();val thumb=inset+value*(size.width-2*inset)
        drawLine(Ink.copy(alpha=.3f),Offset(thumb,center.y),Offset(size.width-inset,center.y),2.dp.toPx(),StrokeCap.Round)
        val path=Path();val span=(thumb-inset).coerceAtLeast(0f);val steps=(span/3.dp.toPx()).toInt().coerceAtLeast(1)
        for(i in 0..steps){val x=inset+span*i/steps;val envelope=minOf((x-inset)/12.dp.toPx(),(thumb-x)/12.dp.toPx(),1f).coerceAtLeast(0f)
            val y=center.y+sin((x-inset)/24.dp.toPx()*2*PI.toFloat()-phase.value)*3.dp.toPx()*envelope
            if(i==0)path.moveTo(x,y) else path.lineTo(x,y)}
        drawPath(path,Ink,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        drawRoundRect(Vermilion,Offset(thumb-4.dp.toPx(),center.y-13.dp.toPx()),androidx.compose.ui.geometry.Size(8.dp.toPx(),26.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
    }
}

@Composable fun BalanceGauge(lean: Float, modifier: Modifier = Modifier, touch: Boolean=false, onBalance:(Float)->Unit = {}) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper

    Canvas(modifier.fillMaxWidth().height(98.dp).semantics { contentDescription="Balance ${if(abs(lean)<.25f)"centered" else if(lean>0)"right" else "left"}" }
        .pointerInput(touch) { if(touch) detectDragGestures(onDragEnd={onBalance(0f)},onDragCancel={onBalance(0f)}) { c,_ -> c.consume();onBalance(((c.position.x/size.width)-.5f)*2f) } }) {
        val r=min(size.width*.43f,size.height*.94f);val o=Offset(center.x,size.height)
        for(i in -10..10) { val a=(i*6-90)*PI/180; val inner=r-(if(i%5==0)12 else 6).dp.toPx(); drawLine(Ink,Offset(o.x+cos(a).toFloat()*inner,o.y+sin(a).toFloat()*inner),Offset(o.x+cos(a).toFloat()*r,o.y+sin(a).toFloat()*r),1.3.dp.toPx()) }
        rotate(lean*58f,o) { val p=Path().apply {moveTo(o.x-13.dp.toPx(),o.y-r+12.dp.toPx());lineTo(o.x+13.dp.toPx(),o.y-r+12.dp.toPx());lineTo(o.x,o.y);close()};drawPath(p,Cream);drawLine(Vermilion,o,Offset(o.x,o.y-r+9.dp.toPx()),4.dp.toPx()) }
    }
}
@Composable fun GrooveWave(modifier:Modifier=Modifier) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper

    val pulse=rememberInfiniteTransition(label="Groove preview")
    val phase by pulse.animateFloat(0f,6.283f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Beat bars")
    Canvas(modifier.width(54.dp).height(28.dp)) {
        repeat(10){i -> val h=(.25f+abs(sin(phase+i*.8f))*.65f)*size.height
            drawLine(Vermilion,Offset(i*size.width/10,center.y-h/2),Offset(i*size.width/10,center.y+h/2),2.dp.toPx()) }
    }
}

/** Deliberately simple extrusion; contact positions come exclusively from the solver. */
@Composable fun TowerDrawing(frame: BalanceFrame, modifier: Modifier = Modifier, alpha: Float=1f, decorative:Boolean=false) {
    TowerDrawing({frame},modifier,alpha,decorative)
}
@Composable fun TowerDrawing(frameProvider: () -> BalanceFrame, modifier: Modifier = Modifier, alpha: Float=1f, decorative:Boolean=false) {
    val palette=LocalBalancePalette.current
    val Ink=palette.ink;val Cream=palette.paper;val Cobalt=palette.primary;val Vermilion=palette.accent

    val camera=remember { SceneCamera() }
    val lastDraw=remember { longArrayOf(0L) }
    Canvas(modifier.graphicsLayer { this.alpha=alpha }) {
        val frame=frameProvider()
        val now=System.nanoTime()
        val elapsed=if(lastDraw[0]==0L)0f else (now-lastDraw[0])/1e9f
        lastDraw[0]=now
        val viewport=if(decorative)SceneViewport.fit(frame,size.width,size.height,true) else camera.advance(frame,size.width,size.height,elapsed)
        val scale=viewport.scale
        val base=Offset(viewport.x,viewport.y)
        fun polygon(points:List<Offset>,color:Color) { val p=Path();points.forEachIndexed { i,v -> if(i==0)p.moveTo(v.x,v.y) else p.lineTo(v.x,v.y) };p.close();drawPath(p,color) }
        drawOval(Ink.copy(alpha=.09f),Offset(base.x-scale*1.8f,base.y+scale*.5f),androidx.compose.ui.geometry.Size(scale*3.8f,scale*.22f))
        polygon(listOf(Offset(base.x,base.y+scale*.07f),Offset(base.x-scale*.56f,base.y+scale*.65f),Offset(base.x+scale*.56f,base.y+scale*.65f)),Ink)
        fun piece(p:PiecePose) {
            val c=when(p.color){0->Cream;1->Cobalt;else->Vermilion};val w=p.width*scale;val h=p.height*scale
            val x=base.x+p.x*scale;val y=base.y-p.y*scale; val depth=Offset(scale*.21f,-scale*.17f)
            rotate(-p.angle*180f/PI.toFloat(),Offset(x,y)) {
                if(p.kind==PieceKind.DISC) {
                    drawCircle(c.copy(red=c.red*.75f,green=c.green*.75f,blue=c.blue*.75f),w/2,Offset(x+depth.x,y+depth.y))
                    drawCircle(c,w/2,Offset(x,y));drawCircle(Ink.copy(alpha=.10f),w*.39f,Offset(x,y),style=Stroke(1.dp.toPx()))
                    drawLine(Ink.copy(alpha=.09f),Offset(x-w*.3f,y-w*.18f),Offset(x+w*.3f,y+w*.18f),1.dp.toPx())
                } else {
                    val a=Offset(x-w/2,y-h/2);val b=Offset(x+w/2,y-h/2);val d=Offset(x+w/2,y+h/2);val e=Offset(x-w/2,y+h/2)
                    val top=if(p.kind==PieceKind.WEDGE)listOf(e,e+depth,b+depth,b) else listOf(a,a+depth,b+depth,b)
                    polygon(top,Color(c.red*.9f+.1f,c.green*.9f+.1f,c.blue*.9f+.1f))
                    polygon(listOf(b,b+depth,d+depth,d),Color(c.red*.70f,c.green*.70f,c.blue*.70f))
                    polygon(if(p.kind==PieceKind.WEDGE)listOf(e,d,b) else listOf(a,b,d,e),c)
                }
            }
        }
        piece(frame.beam);frame.pieces.forEach(::piece);frame.incoming?.let(::piece)
    }
}
