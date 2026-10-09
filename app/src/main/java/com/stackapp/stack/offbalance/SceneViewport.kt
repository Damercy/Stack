package com.stackapp.stack.offbalance

import kotlin.math.*

/** Fits rotated front faces, extrusion, incoming piece and fulcrum together. */
data class SceneViewport(val scale: Float, val x: Float, val y: Float) {
    companion object {
        fun fit(frame: BalanceFrame, width: Float, height: Float, decorative: Boolean = false): SceneViewport {
            var left = -1.9f; var right = 2.1f; var bottom = -.95f; var top = if(decorative)1.65f else 3.2f
            val poses=frame.pieces + frame.beam + listOfNotNull(frame.incoming)
            poses.forEach { p ->
                val c=abs(cos(p.angle)); val s=abs(sin(p.angle))
                val dx=(if(p.kind==PieceKind.DISC)p.width/2 else (p.width*c+p.height*s)/2)+.28f
                val dy=(if(p.kind==PieceKind.DISC)p.width/2 else (p.height*c+p.width*s)/2)+.28f
                left=min(left,p.x-dx);right=max(right,p.x+dx);bottom=min(bottom,p.y-dy);top=max(top,p.y+dy)
            }
            val padding=if(decorative).025f else .06f
            val scale=min(width*(1-2*padding)/(right-left),height*(1-2*padding)/(top-bottom)).coerceAtLeast(.01f)
            return SceneViewport(scale,width/2-(left+right)*scale/2,height/2+(top+bottom)*scale/2)
        }
    }
}

/** One camera per run. A fixed pivot avoids following the incoming block sideways. */
class SceneCamera {
    private var scale=0f
    private var extent=0f
    private var top=3.2f
    private var bottom=-.95f
    private var width=0f
    private var height=0f

    fun advance(frame:BalanceFrame,w:Float,h:Float,seconds:Float):SceneViewport {
        var radius=2.1f
        var highest=3.2f
        var lowest=-.95f
        fun include(p:PiecePose){
            val c=abs(cos(p.angle));val s=abs(sin(p.angle))
            val dx=(if(p.kind==PieceKind.DISC)p.width/2 else (p.width*c+p.height*s)/2)+.28f
            val dy=(if(p.kind==PieceKind.DISC)p.width/2 else (p.height*c+p.width*s)/2)+.28f
            radius=max(radius,abs(p.x)+dx);highest=max(highest,p.y+dy);lowest=min(lowest,p.y-dy)
        }
        include(frame.beam);frame.pieces.forEach(::include);frame.incoming?.let(::include)
        // Reserve a full layer before it arrives. Bounds grow monotonically so
        // settling and changing shapes never make the camera pump in and out.
        extent=max(extent,radius);top=max(top,highest);bottom=min(bottom,lowest)
        val target=min(w*.88f/(2*extent),h*.88f/(top-bottom+.85f)).coerceAtLeast(.000001f)
        val resized=w!=width || h!=height
        if(scale==0f || resized)scale=target else {
            val blend=1-exp(-seconds.coerceIn(0f,.1f)/.32f)
            scale+= (min(scale,target)-scale)*blend
            // Emergency containment for a sudden fall or a very slow frame.
            scale=min(scale,min(w*.94f/(2*radius),h*.94f/(highest-lowest)))
        }
        width=w;height=h
        return SceneViewport(scale,w/2,h*.94f+bottom*scale)
    }
}
