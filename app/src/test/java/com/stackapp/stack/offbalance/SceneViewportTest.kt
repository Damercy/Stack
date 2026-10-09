package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SceneViewportTest {
    @Test fun incomingAndAllRotatedPiecesFitPhoneLandscapeAndUnfoldedScenes(){
        val shapes=PieceKind.entries.mapIndexed{i,k->PiecePose(if(i==0)-3f else 2f,8f+i*2,.9f,1.65f,.86f,k,i)}
        val beam=PiecePose(0f,0f,.5f,3.4f,.28f,PieceKind.SLAB,1)
        val incoming=PiecePose(1.15f,14f,0f,1.65f,.5f,PieceKind.SLAB,0)
        val frame=BalanceFrame(shapes,beam,incoming,3,0f,false,true,false,0f)
        listOf(360f to 550f,800f to 360f,1100f to 1400f).forEach{(w,h)->
            val v=SceneViewport.fit(frame,w,h)
            (shapes+beam+incoming).forEach{p->val dx=(if(p.kind==PieceKind.DISC)p.width/2 else (p.width*abs(cos(p.angle))+p.height*abs(sin(p.angle)))/2)+.28f
                val dy=(if(p.kind==PieceKind.DISC)p.width/2 else (p.height*abs(cos(p.angle))+p.width*abs(sin(p.angle)))/2)+.28f
                assertTrue(v.x+(p.x-dx)*v.scale>=0);assertTrue(v.x+(p.x+dx)*v.scale<=w)
                assertTrue(v.y-(p.y+dy)*v.scale>=0);assertTrue(v.y-(p.y-dy)*v.scale<=h)}
        }
    }
    @Test fun collapseAndTinyWindowsRemainFinite(){
        val game=BalancePhysics(Difficulty.CHAOS);game.drop();game.setInput(1f);repeat(600){game.advance(.01667)}
        val v=SceneViewport.fit(game.snapshot(),20f,30f)
        assertTrue(v.scale.isFinite());assertTrue(v.x.isFinite());assertTrue(v.y.isFinite())
    }
    @Test fun cameraDoesNotFollowTheIncomingPieceSidewaysOrZoomBackIn(){
        val game=BalancePhysics(Difficulty.STEADY)
        val camera=SceneCamera()
        val a=camera.advance(game.snapshot(),360f,600f,1f/60)
        val moved=game.snapshot().copy(incoming=game.snapshot().incoming!!.copy(x=1f))
        val b=camera.advance(moved,360f,600f,1f/60)
        assertEquals(a.x,b.x,0f);assertTrue(b.scale<=a.scale)
        val c=camera.advance(game.snapshot(),360f,600f,1f/60)
        assertTrue(c.scale<=b.scale)
    }
    @Test fun aNewLayerEasesOutInsteadOfSnappingAndKeepsTheTowerVisible(){
        val game=BalancePhysics(Difficulty.STEADY)
        val frame=game.snapshot().let{it.copy(incoming=it.incoming!!.copy(y=5.5f))}
        val camera=SceneCamera()
        val a=camera.advance(frame,360f,600f,1f/60)
        val taller=frame.copy(incoming=frame.incoming!!.copy(y=frame.incoming.y+.5f))
        val first=camera.advance(taller,360f,600f,1f/60)
        assertTrue(first.scale<a.scale);assertTrue(first.scale>a.scale*.97f)
        var settled=first;repeat(120){settled=camera.advance(taller,360f,600f,1f/60)}
        assertTrue(settled.scale<first.scale)
        assertTrue(settled.y-taller.incoming!!.y*settled.scale>0)
    }
    @Test fun cameraSupportsHundredsOfLayersAndResizesToAnUnfoldedWindow(){
        val game=BalancePhysics(Difficulty.STEADY);val camera=SceneCamera();var view=SceneViewport(1f,0f,0f)
        for(i in 1..500){
            val frame=game.snapshot().copy(incoming=game.snapshot().incoming!!.copy(y=i*.5f+3f))
            repeat(60){view=camera.advance(frame,360f,600f,1f/60)}
            assertTrue(view.scale.isFinite());assertTrue(view.y-(i*.5f+3f)*view.scale>=0)
        }
        val folded=view.scale
        view=camera.advance(game.snapshot(),1000f,900f,1f/60)
        assertEquals(500f,view.x,0f);assertTrue(view.scale>folded)
    }
}
