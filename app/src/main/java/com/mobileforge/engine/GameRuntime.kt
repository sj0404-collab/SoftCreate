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
    var mesh: String = src.mesh
    var extra: org.json.JSONObject = org.json.JSONObject(src.extra.toString())
    var parent: String = src.parent
    var tag: String = src.tag
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var alive: Boolean = src.enabled
    val particles = mutableListOf<Particle>()

    fun snapshot(): SceneObject = SceneObject(
        name, type, x, y, z, rx, ry, rz, sx, sy, sz, "", script, color, solid, speed, extra,
        "Cube", "", "Directional", 1f, 60f, 0.3f, 200f, tag, "Default", alive, 1f, "", "", parent,
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
    private val prefabs: Map<String, String> = emptyMap(),
) {
    var dimension: String = source.dimension
    val actors = source.objects.map { Actor(it) }.toMutableList()
    val input = InputState()
    val log = ArrayDeque<String>()
    val signals = SignalBus()
    var score: Int = 0
    var elapsed: Float = 0f
    var timeScale: Float = source.timeScale.coerceIn(0f, 8f)
    var gravity: Float = source.gravity
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
        val clamped = (dt * timeScale).coerceIn(0.0001f, 0.05f)
        elapsed += clamped
        actors.filter { it.alive }.forEach { actor ->
            call(actor, "onUpdate", clamped)
            applyComponents(actor, clamped)
            applyBuiltinUpdate(actor, clamped)
            integrate(actor, clamped)
        }
        resolveCollisions()
        actors.removeAll { !it.alive }
    }

    fun sceneSnapshot(): GameScene {
        val objs = actors.filter { it.alive }.map { it.snapshot() }.toMutableList()
        actors.filter { it.alive }.forEach { a ->
            a.particles.forEachIndexed { i, p ->
                objs += SceneObject(
                    name = "${a.name}_fx$i", type = "Particle",
                    x = p.x, y = p.y, z = p.z, sx = 0.18f, sy = 0.18f, sz = 0.18f,
                    color = p.color, solid = false, mesh = "Sphere",
                )
            }
        }
        return GameScene("play", dimension, objs, java.io.File("play"))
    }

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

    private fun eachComp(actor: Actor, type: String, fn: (org.json.JSONObject) -> Unit) {
        val arr = actor.extra.optJSONArray("components") ?: return
        val want = EngineKit.alias(type)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("enabled", true) && EngineKit.alias(o.optString("type")) == want) fn(o)
        }
    }

    private fun applyComponents(actor: Actor, dt: Float) {
        eachComp(actor, "Rigidbody") { c ->
            if (c.optBoolean("useGravity", true)) actor.vy -= gravity * dt
            val drag = c.optDouble("drag", 0.05).toFloat()
            actor.vx *= (1f - drag)
            actor.vz *= (1f - drag)
        }
        eachComp(actor, "Animator") { c ->
            if (!c.optBoolean("playing", true)) return@eachComp
            val spd = c.optDouble("speed", 1.0).toFloat()
            when (c.optString("clip", "spin")) {
                "spin" -> actor.ry += 90f * spd * dt
                "bob" -> actor.y += (kotlin.math.sin(elapsed * 4.0 * spd) * 0.01).toFloat()
                else -> actor.ry += 45f * spd * dt
            }
        }
        eachComp(actor, "CharacterController") { c ->
            val speed = c.optDouble("speed", if (actor.speed == 0f) 6.0 else actor.speed.toDouble()).toFloat()
            actor.x += input.x * speed * dt
            if (dimension.equals("2D", true)) actor.y += input.y * speed * dt
            else actor.z += -input.y * speed * dt
            if (input.jump && actor.y <= groundY(actor) + 0.08f) actor.vy = c.optDouble("jump", 7.0).toFloat()
        }
        eachComp(actor, "NavMeshAgent") { c ->
            val target = c.optString("target")
            val dest = actors.find { it.alive && (it.name == target || it.tag == target) } ?: return@eachComp
            val spd = c.optDouble("speed", 4.0).toFloat()
            val dx = dest.x - actor.x
            val dz = dest.z - actor.z
            val len = kotlin.math.sqrt(dx * dx + dz * dz)
            if (len > 0.15f) {
                actor.x += dx / len * spd * dt
                actor.z += dz / len * spd * dt
            }
        }
        eachComp(actor, "Projectile") { c ->
            val spd = c.optDouble("speed", 12.0).toFloat()
            val yaw = Math.toRadians(actor.ry.toDouble())
            actor.x += (kotlin.math.sin(yaw) * spd * dt).toFloat()
            actor.z += (kotlin.math.cos(yaw) * spd * dt).toFloat()
            val life = c.optDouble("life", 3.0) - dt
            c.put("life", life)
            if (life <= 0) actor.alive = false
        }
        eachComp(actor, "ParticleSystem") { c ->
            if (actor.particles.size < c.optInt("count", 8)) {
                actor.particles += Particle(actor.x, actor.y + 0.4f, actor.z, 0f, 1.2f, 0f, 0.5f, c.optString("color", "#ffcc66"))
            }
            actor.particles.forEach { p -> p.y += p.vy * dt; p.life -= dt }
            actor.particles.removeAll { it.life <= 0f }
        }
        eachComp(actor, "AudioSource") { c ->
            if (c.optBoolean("playOnAwake") && !c.optBoolean("_played")) {
                onSound?.invoke(c.optString("clip")); c.put("_played", true)
            }
        }
    }

    private fun integrate(actor: Actor, dt: Float) {
        val body = hasComponent(actor.extra, "Rigidbody") ||
            actor.type in listOf("Player", "Enemy", "Pawn", "Character", "Npc")
        if (!body) return
        val g = gravity
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
        var y = if (dimension.equals("2D", true)) -8f else 0f
        var hit = false
        actors.filter {
            it.alive && it.name != actor.name &&
                (it.type == "Ground" || it.type == "Terrain" || it.mesh.equals("Plane", true))
        }.forEach { g ->
            val on = if (dimension.equals("2D", true)) {
                abs(actor.x - g.x) < (abs(g.sx) + abs(actor.sx)) * 0.6f
            } else {
                abs(actor.x - g.x) < (abs(g.sx) + abs(actor.sx)) * 0.55f &&
                    abs(actor.z - g.z) < (abs(g.sz) + abs(actor.sz)) * 0.55f
            }
            if (on) {
                hit = true
                y = maxOf(y, g.y + abs(g.sy) * 0.5f + abs(actor.sy) * 0.5f)
            }
        }
        return if (hit) y else if (dimension.equals("2D", true)) -8f else 0f
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
            "Player", "Pawn", "Character" -> {
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
                    "addForce", "AddForce" -> ScriptInterpreter.Val.Host { args ->
                        actor.vx += args.getOrNull(0)?.num()?.toFloat() ?: 0f
                        actor.vy += args.getOrNull(1)?.num()?.toFloat() ?: 0f
                        actor.vz += args.getOrNull(2)?.num()?.toFloat() ?: 0f
                        ScriptInterpreter.Val.Null
                    }
                    "instantiate", "Instantiate" -> ScriptInterpreter.Val.Host { args ->
                        val p = args.getOrNull(0)?.str().orEmpty()
                        val raw = prefabs[p]
                        val src = if (!raw.isNullOrBlank()) runCatching { SceneObject.fromJson(org.json.JSONObject(raw)) }.getOrNull() else null
                        val base = src ?: SceneObject(p.ifBlank { "Spawn" }, "Mesh", actor.x, actor.y, actor.z)
                        var n = base.name
                        if (actors.any { it.name == n }) n = "${n}_${actors.size}"
                        val copy = Actor(base)
                        copy.name = n
                        copy.x = args.getOrNull(1)?.num()?.toFloat() ?: actor.x
                        copy.y = args.getOrNull(2)?.num()?.toFloat() ?: actor.y
                        copy.z = args.getOrNull(3)?.num()?.toFloat() ?: actor.z
                        actors += copy
                        actorVal(copy)
                    }
                    "getComponent" -> ScriptInterpreter.Val.Host { args ->
                        ScriptInterpreter.Val.Bool(hasComponent(actor.extra, args.getOrNull(0)?.str().orEmpty()))
                    }
                    "addComponent" -> ScriptInterpreter.Val.Host { args ->
                        addComponent(actor.extra, args.getOrNull(0)?.str().orEmpty())
                        ScriptInterpreter.Val.Null
                    }
                    "emit" -> ScriptInterpreter.Val.Host { args ->
                        signals.emit(args.getOrNull(0)?.str().orEmpty()); ScriptInterpreter.Val.Null
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
