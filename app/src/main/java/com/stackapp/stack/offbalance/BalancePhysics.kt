package com.stackapp.stack.offbalance

import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.RevoluteJointDef
import kotlin.math.*

enum class Difficulty(val title: String, val label: String, val description: String, val base: Float) {
    STEADY("STEADY", "EASY", "Wide base. Gentle pace.", 3.2f),
    WOBBLY("WOBBLY", "NORMAL", "Mixed shapes. Find balance.", 2.8f),
    CHAOS("CHAOS", "HARD", "Tight base. Faster pressure.", 2.35f)
}
enum class PieceKind { SLAB, DISC, WEDGE }
data class PiecePose(val x: Float, val y: Float, val angle: Float, val width: Float, val height: Float, val kind: PieceKind, val color: Int)
data class BalanceFrame(val pieces: List<PiecePose>, val beam: PiecePose, val incoming: PiecePose?, val score: Int, val lean: Float, val down: Boolean, val canDrop: Boolean, val recovered: Boolean, val holdSeconds: Float, val passes: Int = 0)

/** One planar balance axis; all pieces have independent mass, friction and contacts. */
class BalancePhysics(val difficulty: Difficulty, val tutorial: Boolean = false, val trial: Int = -1,
    private val passChance: Int = 20, private val passRoll: () -> Int = { kotlin.random.Random.nextInt(100) }) {
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
    private var collapseTime=0f
    private var settledTicks=0
    private var passes=0
    val collapseFinished get()=down && (collapseTime>=8f || collapseTime>=1.2f && settledTicks>=30)
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
        if (down) {
            world.gravity.set(0f,-10f);world.step(STEP.toFloat(),8,4)
            collapseTime+=STEP.toFloat()
            settledTicks=if(bodies.all{(body,_)->body.linearVelocity.lengthSquared()<.015f && abs(body.angularVelocity)<.12f})settledTicks+1 else 0
            return
        }
        // Small restoring torque buys recovery time without welding the tower together.
        val angle = beam.angle
        val assist = when {
            trial==2 -> 100f
            tutorial || trial>=0 -> 180f
            difficulty==Difficulty.STEADY -> {val layers=min(score,60);300f+layers*25f+layers*layers*8f}
            difficulty==Difficulty.WOBBLY -> 100f
            else -> 80f
        }
        val damping=if(difficulty==Difficulty.STEADY && trial<0)18f+min(score,60)*3f else 8f
        beam.applyTorque(-angle * assist - beam.angularVelocity * damping - input * 19f)
        // Easy mode adds gentle angular recovery to supported slabs. Contacts,
        // lateral motion and detached pieces remain fully simulated.
        if(difficulty==Difficulty.STEADY && trial<0 && !tutorial)bodies.forEach{(body,pose)->
            if(body!==pending && pose.kind==PieceKind.SLAB)
                body.applyTorque(-(body.angle-angle)*4f-body.angularVelocity*.8f)
        }
        world.gravity.set(input * 2.8f, -10f)
        val tallSteady=difficulty==Difficulty.STEADY && trial<0 && bodies.size>10
        world.step(STEP.toFloat(), if(tallSteady)16 else 8, if(tallSteady)8 else 4)
        // A loose piece must end the run even when the beam itself stays upright.
        // Check before scoring: contact with the floor is never a successful landing.
        var pendingFailed=false
        var supportedFailed=false
        bodies.forEach { (body, pose) ->
            val relativeAngle=body.angle-beam.angle
            val halfHeight=pose.height/2*abs(cos(relativeAngle))+pose.width/2*abs(sin(relativeAngle))
            val distance=-body.position.x*sin(beam.angle)+body.position.y*cos(beam.angle)
            val fallen=distance-halfHeight < -.18f || abs(body.position.x) > 5.5f ||
                (body !== pending && pose.kind != PieceKind.DISC && abs(body.angle) > .9f)
            if(fallen){if(body===pending)pendingFailed=true else supportedFailed=true}
        }
        if (pendingFailed || supportedFailed || abs(beam.angle) > .60f) {
            if (!supportedFailed && abs(beam.angle) <= .60f && pendingFailed && forgiveMiss()) return
            down = true; return
        }
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
                    difficulty == Difficulty.STEADY -> PieceKind.SLAB
                    difficulty == Difficulty.WOBBLY && score>=6 && score%8==6 -> PieceKind.DISC
                    difficulty == Difficulty.CHAOS && score>=8 && score%7==1 -> PieceKind.WEDGE
                    difficulty == Difficulty.CHAOS && score>=4 && score%7==4 -> PieceKind.DISC
                    else -> PieceKind.SLAB
                }
            }
            if (pending===body && (pendingTicks > 240 || body.position.y < -.7f)) {
                if (forgiveMiss()) return
                down = true
            }
        }
        val range=when {
            trial==0 || tutorial && score<4 -> .45f
            difficulty==Difficulty.STEADY -> .32f+min(score,30)*.008f
            score<4 && difficulty==Difficulty.WOBBLY -> .55f
            difficulty==Difficulty.CHAOS -> 1.15f
            else -> .9f
        }
        phase += speedMultiplier() * (when(difficulty){Difficulty.CHAOS->1.9f;Difficulty.WOBBLY->1.35f;else->1.05f}) * STEP.toFloat()
        if (!down && pending == null) nextX = (if(difficulty==Difficulty.STEADY && trial<0 && !tutorial)bodies.last().first.position.x else 0f) + sin(phase) * range
        val lean = abs(beam.angle)
        if (lean > .15f) warned = true
        if (warned && lean < .06f) recovered = true
        if (trial == 1 && score >= 1 && lean < .15f) hold += STEP.toFloat() else if (trial == 1) hold = 0f
    }

    /** Only discard the missed incoming piece while the existing tower is safe.
     * Never rewind a tower, award a layer, or suppress a genuine collapse. */
    private fun forgiveMiss(): Boolean {
        val missed=pending ?: return false
        if (tutorial || trial>=0 || score<3 || passes>=1 || passChance<=0 || abs(beam.angle)>.15f) return false
        if (bodies.any { (body,_) -> body!==missed &&
            (body.linearVelocity.lengthSquared()>.9f || abs(body.angularVelocity)>1.2f) }) return false
        if (passRoll() !in 0 until passChance.coerceIn(0,25)) return false
        bodies.removeAll { it.first===missed };world.destroyBody(missed)
        pending=null;pendingTicks=0;contactTicks=0;passes++
        nextX=(if(difficulty==Difficulty.STEADY)bodies.last().first.position.x else 0f)
        return true
    }

    fun speedMultiplier(): Float = if (tutorial || trial >= 0) 1f else if(difficulty==Difficulty.STEADY)1f+min(score,60)*.008f else 1f + min(score, 40) * .02f

    private fun width(kind: PieceKind) = when (kind) {
        PieceKind.SLAB -> if (difficulty == Difficulty.CHAOS && !tutorial) 1.2f else if(difficulty==Difficulty.STEADY && !tutorial && trial<0)1.9f else 1.65f
        PieceKind.DISC -> 1f
        PieceKind.WEDGE -> 1.45f
    }

    private fun top(): Float = bodies.maxOf { (b, p) -> b.position.y + p.height / 2 * abs(cos(b.angle)) + p.width / 2 * abs(sin(b.angle)) }
    private fun addPiece(x: Float, y: Float, kind: PieceKind, color: Int): Body {
        val width = width(kind)
        val height = if (kind == PieceKind.DISC) .86f else .5f
        val body = world.createBody(BodyDef().apply { type = BodyType.DYNAMIC; position.set(x,y); angularDamping = .65f; bullet = true })
        val outline=pieceOutline(kind,width,height)
        val shape=PolygonShape().apply {set(outline.map{Vec2(it.x,it.y)}.toTypedArray(),outline.size)}
        body.createFixture(FixtureDef().apply { this.shape = shape; density = 1.5f; friction = .82f; restitution = .015f })
        bodies.add(body to PiecePose(x,y,0f,width,height,kind,listOf(0,2,1)[color%3]))
        return body
    }
    fun snapshot(): BalanceFrame = BalanceFrame(
        bodies.map { (b,p) -> p.copy(x=b.position.x,y=b.position.y,angle=b.angle) },
        PiecePose(beam.position.x,beam.position.y,beam.angle,baseWidth,.28f,PieceKind.SLAB,1),
        if (!down && pending == null && !(trial == 1 && score >= 1)) PiecePose(nextX,top()+.9f,0f,width(nextKind),if(nextKind==PieceKind.DISC).86f else .5f,nextKind,listOf(0,2,1)[bodies.size%3]) else null,
        score, (-beam.angle/.5f).coerceIn(-1f,1f), down, !down && pending == null && !(trial == 1 && score >= 1), recovered, hold, passes
    )
    companion object { const val STEP = 1.0/60.0 }
}
