package com.stackapp.stack.offbalance

/** Flat contact faces make unusual pieces learnable without fake support forces. */
data class ShapePoint(val x:Float,val y:Float)
fun pieceOutline(kind:PieceKind,width:Float,height:Float):List<ShapePoint> = when(kind){
    PieceKind.SLAB -> listOf(ShapePoint(-width/2,-height/2),ShapePoint(width/2,-height/2),ShapePoint(width/2,height/2),ShapePoint(-width/2,height/2))
    PieceKind.WEDGE -> listOf(ShapePoint(-width/2,-height/2),ShapePoint(width/2,-height/2),ShapePoint(width*.30f,height/2),ShapePoint(-width*.30f,height/2))
    PieceKind.DISC -> listOf(ShapePoint(-width*.34f,-height/2),ShapePoint(width*.34f,-height/2),ShapePoint(width/2,-height*.28f),ShapePoint(width/2,height*.28f),ShapePoint(width*.34f,height/2),ShapePoint(-width*.34f,height/2),ShapePoint(-width/2,height*.28f),ShapePoint(-width/2,-height*.28f))
}
