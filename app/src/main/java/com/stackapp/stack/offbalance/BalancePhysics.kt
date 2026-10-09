package com.stackapp.stack.offbalance

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.RevoluteJointDef
import kotlin.math.*

enum class Difficulty(val title: String, val label: String, val description: String, val base: Float) {
    STEADY("STEADY", "EASY", "Wide base. Mostly slabs.", 3.2f),
    WOBBLY("WOBBLY", "NORMAL", "Mixed shapes. Find balance.", 2.8f),
    CHAOS("CHAOS", "HARD", "Tight base. Faster pressure.", 2.35f)
}
enum class PieceKind { SLAB, DISC, WEDGE }
data class PiecePose(val x: Float, val y: Float, val angle: Float, val width: Float, val height: Float, val kind: PieceKind, val color: Int)
data class BalanceFrame(val pieces: List<PiecePose>, val beam: PiecePose, val incoming: PiecePose?, val score: Int, val lean: Float, val down: Boolean, val canDrop: Boolean, val recovered: Boolean, val holdSeconds: Float)

/** One planar balance axis; all pieces have independent mass, friction and contacts. */
class BalancePhysics(val difficulty: Difficulty, val tutorial: Boolean = false, val trial: Int = -1) {
    private val world = World(Vec2(0f, -10f))
    private val bodies = ArrayList<Pair<Body, PiecePose>>()
    private val beam: Body
    private var pending: Body? = null
    private var contactTicks = 0
    private var pendingTicks = 0
    private var time = 0f
    private var phase = 0f
    private var remainder = 0.0
    private var score = 0
    private var down = false
    private var warned = false
    private var recovered = false
    private var hold = 0f
    private var input = 0f
    private var nextKind = PieceKind.SLAB
    private var nextX = 0f
    private var active = false
    private val baseWidth = if (tutorial || trial >= 0) 3.4f else difficulty.base

    init {
        val anchor = world.createBody(BodyDef())
        val ground=world.createBody(BodyDef().apply {position.set(0f,-1.2f)})
        ground.createFixture(PolygonShape().apply {setAsBox(8f,.2f)},0f).friction=.8f
        beam = world.createBody(BodyDef().apply { type = BodyType.DYNAMIC; position.set(0f, 0f); angularDamping = 2.6f })
        beam.createFixture(PolygonShape().apply { setAsBox(baseWidth / 2, .14f) }, 5f).apply { friction = .72f }
        world.createJoint(RevoluteJointDef().apply {
            initialize(anchor, beam, Vec2(0f, 0f)); enableLimit = true; lowerAngle = -.65f; upperAngle = .65f
        })
        addPiece(0f, .40f, PieceKind.SLAB, 0)
        if (trial == 1) {
            addPiece(0f, .91f, PieceKind.SLAB, 1); addPiece(0f, 1.42f, PieceKind.SLAB, 2)
            nextKind = PieceKind.DISC
        }
    }

    fun setInput(value: Float) { input = if(value.isFinite())value.coerceIn(-1f, 1f) else 0f }
    fun drop(): Boolean {
        if (down || pending != null || (trial == 1 && score >= 1)) return false
        active = true
        val top = top()
        pending = addPiece(nextX, top + .9f, nextKind, bodies.size)
        pendingTicks = 0; contactTicks = 0
        return true
    }

    /** Bounded catch-up prevents a resumed app or a slow frame from spiralling. */
    fun advance(seconds: Double): BalanceFrame {
        remainder += if(seconds.isNaN())0.0 else seconds.coerceIn(0.0, .1)
        while (remainder >= STEP) { step(); remainder -= STEP }
        return snapshot()
    }

    private fun step() {
        time += STEP.toFloat()
        if (!active) return
        if (down) { world.gravity=Vec2(0f,-10f); world.step(STEP.toFloat(),8,4);return }
        // Small restoring torque buys recovery time without welding the tower together.
        val angle = beam.angle
        val assist = when {
            trial==2 -> 100f
            tutorial || trial>=0 -> 180f
            difficulty==Difficulty.STEADY -> 140f
            difficulty==Difficulty.WOBBLY -> 100f
            else -> 80f
        }
        beam.applyTorque(-angle * assist - beam.angularVelocity * 8f - input * 19f)
        world.gravity = Vec2(input * 2.8f, -10f)
        world.step(STEP.toFloat(), 8, 4)
        // A loose piece must end the run even when the beam itself stays upright.
        // Check before scoring: contact with the floor is never a successful landing.
        if (bodies.any { (body, pose) ->
            val relativeAngle=body.angle-beam.angle
            val halfHeight=if(pose.kind==PieceKind.DISC)pose.width/2 else pose.height/2*abs(cos(relativeAngle))+pose.width/2*abs(sin(relativeAngle))
            val distance=-body.position.x*sin(beam.angle)+body.position.y*cos(beam.angle)
            distance-halfHeight < -.18f || abs(body.position.x) > 5.5f ||
                (body !== pending && pose.kind != PieceKind.DISC && abs(body.angle) > .9f)
        } || abs(beam.angle) > .60f) { down = true; return }
        pending?.let { body ->
            pendingTicks++
            var edge = body.contactList
            var touching = false
            while (edge != null) { if (edge.contact.isTouching) touching = true; edge = edge.next }
            contactTicks = if (touching && body.linearVelocity.lengthSquared() < .9f) contactTicks + 1 else 0
            if (contactTicks >= 8) {
                score++; pending = null
                nextKind = when {
                    trial == 0 || tutorial && score < 5 -> PieceKind.SLAB
                    trial == 3 -> when(score%3){1->PieceKind.DISC;2->PieceKind.WEDGE;else->PieceKind.SLAB}
                    difficulty == Difficulty.STEADY -> if (score % 7 == 6) PieceKind.DISC else PieceKind.SLAB
                    score % 5 == 3 -> PieceKind.DISC
                    difficulty == Difficulty.CHAOS && score % 4 == 2 -> PieceKind.WEDGE
                    else -> PieceKind.SLAB
                }
            }
            if (pendingTicks > 240 || body.position.y < -.7f) down = true
        }
        val range=when {
            trial==0 || tutorial && score<4 -> .45f
            difficulty==Difficulty.STEADY -> .65f
            score<4 && difficulty==Difficulty.WOBBLY -> .55f
            difficulty==Difficulty.CHAOS -> 1.15f
            else -> .9f
        }
        phase += speedMultiplier() * (if (difficulty == Difficulty.CHAOS) 2.2f else 1.55f) * STEP.toFloat()
        if (!down && pending == null) nextX = sin(phase) * range
        val lean = abs(beam.angle)
        if (lean > .15f) warned = true
        if (warned && lean < .06f) recovered = true
        if (trial == 1 && score >= 1 && lean < .15f) hold += STEP.toFloat() else if (trial == 1) hold = 0f
    }

    fun speedMultiplier(): Float = if (tutorial || trial >= 0) 1f else 1f + min(score, 40) * .02f

    private fun width(kind: PieceKind) = when (kind) {
        PieceKind.SLAB -> if (difficulty == Difficulty.CHAOS && !tutorial) 1.2f else 1.65f
        PieceKind.DISC -> .86f
        PieceKind.WEDGE -> 1.35f
    }

    private fun top(): Float = bodies.maxOf { (b, p) -> b.position.y + if(p.kind==PieceKind.DISC)p.width/2 else p.height / 2 * abs(cos(b.angle)) + p.width / 2 * abs(sin(b.angle)) }
    private fun addPiece(x: Float, y: Float, kind: PieceKind, color: Int): Body {
        val width = width(kind)
        val height = if (kind == PieceKind.DISC) .86f else .5f
        val body = world.createBody(BodyDef().apply { type = BodyType.DYNAMIC; position.set(x,y); angularDamping = .25f; bullet = true })
        val shape = when (kind) {
            PieceKind.DISC -> CircleShape().apply { m_radius = width / 2 }
            PieceKind.WEDGE -> PolygonShape().apply { set(arrayOf(Vec2(-width/2,-height/2), Vec2(width/2,-height/2), Vec2(width/2,height/2)),3) }
            else -> PolygonShape().apply { setAsBox(width/2,height/2) }
        }
        body.createFixture(FixtureDef().apply { this.shape = shape; density = 1.5f; friction = .66f; restitution = .025f })
        bodies.add(body to PiecePose(x,y,0f,width,height,kind,listOf(0,2,1)[color%3]))
        return body
    }
    fun snapshot(): BalanceFrame = BalanceFrame(
        bodies.map { (b,p) -> p.copy(x=b.position.x,y=b.position.y,angle=b.angle) },
        PiecePose(beam.position.x,beam.position.y,beam.angle,baseWidth,.28f,PieceKind.SLAB,1),
        if (!down && pending == null && !(trial == 1 && score >= 1)) PiecePose(nextX,top()+.9f,0f,width(nextKind),if(nextKind==PieceKind.DISC).86f else .5f,nextKind,listOf(0,2,1)[bodies.size%3]) else null,
        score, (-beam.angle/.5f).coerceIn(-1f,1f), down, !down && pending == null && !(trial == 1 && score >= 1), recovered, hold
    )
    companion object { const val STEP = 1.0/60.0 }
}
