package com.mobileforge.engine

import com.mobileforge.GameScene
import com.mobileforge.SceneObject
import kotlin.math.abs

class Actor(src: SceneObject) {
    var name: String = src.name
    var type: String = src.type
    var x: Float = src.x
    var y: Float = src.y
    var z: Float = src.z
    var rx: Float = src.rx
    var ry: Float = src.ry
    var rz: Float = src.rz
    var sx: Float = src.sx
    var sy: Float = src.sy
    var sz: Float = src.sz
    var color: String = src.color
    var script: String = src.script
    var solid: Boolean = src.solid
    var speed: Float = src.speed
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var alive: Boolean = true

    fun snapshot(): SceneObject = SceneObject(
        name, type, x, y, z, rx, ry, rz, sx, sy, sz, "", script, color, solid, speed,
    )
}

class InputState {
    var x: Float = 0f
    var y: Float = 0f
    var jump: Boolean = false
    var action: Boolean = false
}

class GameRuntime(
    source: GameScene,
    scripts: Map<String, String>,
    private val onLoadScene: ((String) -> Unit)? = null,
    private val onSound: ((String) -> Unit)? = null,
) {
    var dimension: String = source.dimension
    val actors = source.objects.map { Actor(it) }.toMutableList()
    val input = InputState()
    val log = ArrayDeque<String>()
    var score: Int = 0
    var elapsed: Float = 0f
    var playing: Boolean = false
        private set

    private val compiled = HashMap<String, ScriptInterpreter>()

    init {
        actors.forEach { actor ->
            val raw = scripts[actor.script] ?: return@forEach
            runCatching {
                compiled[actor.name] = ScriptInterpreter(CsTranspiler.scriptSource(actor.script, raw))
            }.onFailure { log("compile ${actor.name}: ${it.message}") }
        }
    }

    fun start() {
        playing = true
        elapsed = 0f
        fire("onSceneLoaded")
        fire("onStart")
        applyBuiltinsStart()
    }

    fun stop() {
        playing = false
    }

    fun click() = fire("onButtonClick")

    fun step(dt: Float) {
        if (!playing) return
        val clamped = dt.coerceIn(0.001f, 0.05f)
        elapsed += clamped
        actors.filter { it.alive }.forEach { actor ->
            call(actor, "onUpdate", clamped)
            applyBuiltinUpdate(actor, clamped)
            integrate(actor, clamped)
        }
        resolveCollisions()
        actors.removeAll { !it.alive }
    }

    fun sceneSnapshot(): GameScene = GameScene(
        name = "play",
        dimension = dimension,
        objects = actors.filter { it.alive }.map { it.snapshot() }.toMutableList(),
        file = java.io.File("play"),
    )

    private fun fire(event: String) {
        actors.filter { it.alive }.forEach { call(it, event, 0f) }
    }

    private fun call(actor: Actor, event: String, dt: Float, other: Actor? = null) {
        val script = compiled[actor.name] ?: return
        if (!script.has(event)) return
        val env = mutableMapOf(
            "api" to hostApi(actor, dt),
            "dt" to ScriptInterpreter.Val.Num(dt.toDouble()),
            "other" to if (other != null) actorVal(other) else ScriptInterpreter.Val.Null,
        )
        runCatching { script.call(event, env) }.onFailure { log("$event ${actor.name}: ${it.message}") }
    }

    private fun integrate(actor: Actor, dt: Float) {
        if (actor.type != "Player" && actor.type != "Enemy") return
        val g = if (dimension.equals("2D", true)) 18f else 16f
        actor.vy -= g * dt
        if (!actor.x.isFinite()) actor.x = 0f
        if (!actor.y.isFinite()) actor.y = 1f
        if (!actor.z.isFinite()) actor.z = 0f
        actor.y += actor.vy * dt
        val gy = groundY(actor)
        if (actor.y < gy) {
            actor.y = gy
            actor.vy = 0f
        }
    }

    private fun groundY(actor: Actor): Float {
        if (!dimension.equals("2D", true)) return 1f
        var y = -8f
        actors.filter { it.alive && (it.type == "Ground" || it.type == "Sprite") }.forEach { g ->
            if (abs(actor.x - g.x) < (abs(g.sx) + abs(actor.sx)) * 0.6f) {
                y = maxOf(y, g.y + abs(g.sy) * 0.5f + abs(actor.sy) * 0.5f)
            }
        }
        return y
    }

    private fun aabb(a: Actor, b: Actor): Boolean =
        abs(a.x - b.x) < (abs(a.sx) + abs(b.sx)) * 0.55f &&
            abs(a.y - b.y) < (abs(a.sy) + abs(b.sy)) * 0.55f &&
            abs(a.z - b.z) < (abs(a.sz) + abs(b.sz)) * 0.55f

    private fun resolveCollisions() {
        val live = actors.filter { it.alive }
        for (i in live.indices) {
            for (j in i + 1 until live.size) {
                val a = live[i]
                val b = live[j]
                if (!aabb(a, b)) continue
                call(a, "onCollisionEnter", 0f, b)
                call(b, "onCollisionEnter", 0f, a)
                applyBuiltinCollision(a, b)
                applyBuiltinCollision(b, a)
            }
        }
    }

    private fun applyBuiltinsStart() {
        actors.forEach { actor ->
            if (actor.type == "Coin") actor.solid = false
        }
    }

    private fun applyBuiltinUpdate(actor: Actor, dt: Float) {
        if (compiled.containsKey(actor.name)) return
        when (actor.type) {
            "Player" -> {
                val speed = if (actor.speed == 0f) 6f else actor.speed
                actor.x += input.x * speed * dt
                if (dimension.equals("2D", true)) {
                    actor.y += input.y * speed * dt
                } else {
                    actor.z += -input.y * speed * dt
                }
                if (input.jump && actor.y <= groundY(actor) + 0.05f) actor.vy = 7f
            }
            "Coin" -> actor.ry += dt * 120f
            "Enemy" -> actor.x = kotlin.math.sin(elapsed * (if (actor.speed == 0f) 2f else actor.speed)) * 5f
        }
    }

    private fun applyBuiltinCollision(self: Actor, other: Actor) {
        if (self.type != "Player") return
        when (other.type) {
            "Coin" -> {
                score += 5
                other.alive = false
                log("монета +5")
            }
            "Enemy" -> {
                score -= 2
                self.x = 0f
                self.y = 1f
                self.z = 10f
                log("урон -2")
            }
        }
    }

    private fun hostApi(actor: Actor, dt: Float): ScriptInterpreter.Val.Obj {
        val inputObj = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "x" -> ScriptInterpreter.Val.Num(input.x.toDouble())
                    "y" -> ScriptInterpreter.Val.Num(input.y.toDouble())
                    "jump" -> ScriptInterpreter.Val.Bool(input.jump)
                    "action" -> ScriptInterpreter.Val.Bool(input.action)
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
        )
        val timeObj = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "dt" -> ScriptInterpreter.Val.Num(dt.toDouble())
                    "elapsed" -> ScriptInterpreter.Val.Num(elapsed.toDouble())
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
        )
        val self = actorVal(actor)
        return ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "object" -> self
                    "input" -> inputObj
                    "time" -> timeObj
                    "score" -> ScriptInterpreter.Val.Num(score.toDouble())
                    "move" -> ScriptInterpreter.Val.Host { args ->
                        actor.x += args.getOrNull(0)?.num()?.toFloat() ?: 0f
                        actor.y += args.getOrNull(1)?.num()?.toFloat() ?: 0f
                        actor.z += args.getOrNull(2)?.num()?.toFloat() ?: 0f
                        ScriptInterpreter.Val.Null
                    }
                    "jump" -> ScriptInterpreter.Val.Host { args ->
                        if (actor.y <= groundY(actor) + 0.05f) {
                            actor.vy = args.getOrNull(0)?.num()?.toFloat() ?: 6f
                        }
                        ScriptInterpreter.Val.Null
                    }
                    "setPosition" -> ScriptInterpreter.Val.Host { args ->
                        actor.x = args.getOrNull(0)?.num()?.toFloat() ?: actor.x
                        actor.y = args.getOrNull(1)?.num()?.toFloat() ?: actor.y
                        actor.z = args.getOrNull(2)?.num()?.toFloat() ?: actor.z
                        ScriptInterpreter.Val.Null
                    }
                    "addScore" -> ScriptInterpreter.Val.Host { args ->
                        score += args.getOrNull(0)?.num()?.toInt() ?: 0
                        ScriptInterpreter.Val.Null
                    }
                    "destroy" -> ScriptInterpreter.Val.Host { args ->
                        val n = args.getOrNull(0)?.str()
                        actors.find { it.name == n }?.alive = false
                        ScriptInterpreter.Val.Null
                    }
                    "find" -> ScriptInterpreter.Val.Host { args ->
                        val n = args.getOrNull(0)?.str()
                        actors.find { it.alive && it.name == n }?.let { actorVal(it) } ?: ScriptInterpreter.Val.Null
                    }
                    "log" -> ScriptInterpreter.Val.Host { args ->
                        log(args.getOrNull(0)?.str().orEmpty())
                        ScriptInterpreter.Val.Null
                    }
                    "playSound", "sound" -> ScriptInterpreter.Val.Host { args ->
                        onSound?.invoke(args.getOrNull(0)?.str().orEmpty())
                        ScriptInterpreter.Val.Null
                    }
                    "loadScene" -> ScriptInterpreter.Val.Host { args ->
                        onLoadScene?.invoke(args.getOrNull(0)?.str().orEmpty())
                        ScriptInterpreter.Val.Null
                    }
                    "spawn" -> ScriptInterpreter.Val.Host { ScriptInterpreter.Val.Null }
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
            call = { name, args ->
                val obj = hostApi(actor, dt)
                val fn = obj.get(name)
                if (fn is ScriptInterpreter.Val.Host) fn.impl(args) else ScriptInterpreter.Val.Null
            },
        )
    }

    private fun actorVal(actor: Actor): ScriptInterpreter.Val.Obj = ScriptInterpreter.Val.Obj(
        get = { key ->
            when (key) {
                "name" -> ScriptInterpreter.Val.Str(actor.name)
                "type" -> ScriptInterpreter.Val.Str(actor.type)
                "x" -> ScriptInterpreter.Val.Num(actor.x.toDouble())
                "y" -> ScriptInterpreter.Val.Num(actor.y.toDouble())
                "z" -> ScriptInterpreter.Val.Num(actor.z.toDouble())
                "rx" -> ScriptInterpreter.Val.Num(actor.rx.toDouble())
                "ry" -> ScriptInterpreter.Val.Num(actor.ry.toDouble())
                "rz" -> ScriptInterpreter.Val.Num(actor.rz.toDouble())
                "sx" -> ScriptInterpreter.Val.Num(actor.sx.toDouble())
                "sy" -> ScriptInterpreter.Val.Num(actor.sy.toDouble())
                "sz" -> ScriptInterpreter.Val.Num(actor.sz.toDouble())
                "speed" -> ScriptInterpreter.Val.Num(actor.speed.toDouble())
                "color" -> ScriptInterpreter.Val.Str(actor.color)
                "solid" -> ScriptInterpreter.Val.Bool(actor.solid)
                "script" -> ScriptInterpreter.Val.Str(actor.script)
                else -> ScriptInterpreter.Val.Null
            }
        },
        set = { key, value ->
            when (key) {
                "x" -> actor.x = value.num().toFloat()
                "y" -> actor.y = value.num().toFloat()
                "z" -> actor.z = value.num().toFloat()
                "rx" -> actor.rx = value.num().toFloat()
                "ry" -> actor.ry = value.num().toFloat()
                "rz" -> actor.rz = value.num().toFloat()
                "sx" -> actor.sx = value.num().toFloat()
                "sy" -> actor.sy = value.num().toFloat()
                "sz" -> actor.sz = value.num().toFloat()
                "speed" -> actor.speed = value.num().toFloat()
                "color" -> actor.color = value.str()
                "solid" -> actor.solid = value.truthy()
                "name" -> actor.name = value.str()
                "type" -> actor.type = value.str()
            }
        },
    )

    private fun log(msg: String) {
        if (msg.isBlank()) return
        log.addFirst(msg)
        while (log.size > 40) log.removeLast()
    }
}
