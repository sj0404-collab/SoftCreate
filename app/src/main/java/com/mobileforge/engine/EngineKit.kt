package com.mobileforge.engine

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object EngineKit {
    val UNITY = listOf(
        "Transform", "Rigidbody", "BoxCollider", "SphereCollider", "CapsuleCollider",
        "MeshCollider", "CharacterController", "Animator", "Animation",
        "ParticleSystem", "TrailRenderer", "LineRenderer",
        "AudioSource", "AudioListener", "Light", "Camera",
        "NavMeshAgent", "NavMeshObstacle",
        "Canvas", "Text", "Image", "Button",
        "SpriteRenderer", "MeshRenderer", "SkinnedMeshRenderer",
        "Terrain", "Skybox", "WindZone",
        "FixedJoint", "HingeJoint", "SpringJoint",
        "ConstantForce", "Projectile",
    )
    val GODOT = listOf(
        "Node3D", "Node2D", "CharacterBody3D", "RigidBody3D", "StaticBody3D",
        "Area3D", "CollisionShape3D", "AnimationPlayer", "GPUParticles3D",
        "CPUParticles3D", "AudioStreamPlayer", "Camera3D", "DirectionalLight3D",
        "OmniLight3D", "NavigationAgent3D", "Path3D", "CSGBox3D",
        "Sprite2D", "CharacterBody2D", "TileMap", "Control", "Label",
        "RayCast3D",
    )
    val UNREAL = listOf(
        "Actor", "Pawn", "Character", "GameMode", "PlayerController",
        "ProjectileMovement", "FloatingPawnMovement", "SpringArm",
        "StaticMesh", "SkeletalMesh", "Niagara", "Decal",
        "Landscape", "PostProcess", "TriggerBox", "AIController",
    )

    fun allTypes(): List<String> = (UNITY + GODOT + UNREAL).distinct()

    fun alias(raw: String): String {
        val s = raw.trim()
        return when (s.lowercase()) {
            "rigidbody3d", "rigidbody2d" -> "Rigidbody"
            "characterbody3d", "characterbody2d", "character", "pawn" -> "CharacterController"
            "staticbody3d", "staticbody2d" -> "BoxCollider"
            "area3d", "triggerbox", "boxcollider2d" -> "BoxCollider"
            "spheretrigger", "spherecollider" -> "SphereCollider"
            "animationplayer", "animation" -> "Animator"
            "gpuparticles3d", "cpuparticles3d", "niagara", "particles" -> "ParticleSystem"
            "audiostreamplayer", "audiosource" -> "AudioSource"
            "camera3d" -> "Camera"
            "directionallight3d", "omnilight3d" -> "Light"
            "navigationagent3d", "navmeshagent", "aicontroller" -> "NavMeshAgent"
            "label", "text" -> "Text"
            "control", "canvas" -> "Canvas"
            "sprite2d", "spriterenderer" -> "SpriteRenderer"
            "projectilesmovement", "projectilemovement" -> "Projectile"
            "constantforce" -> "ConstantForce"
            "node3d", "node2d", "actor", "transform" -> "Transform"
            else -> s
        }
    }

    fun defaultJson(type: String): JSONObject {
        val t = alias(type)
        return JSONObject().put("type", t).put("enabled", true).also { o ->
            when (t) {
                "Rigidbody" -> o.put("mass", 1).put("useGravity", true).put("drag", 0.05).put("bounce", 0.0)
                "BoxCollider", "SphereCollider", "CapsuleCollider", "MeshCollider" ->
                    o.put("isTrigger", false).put("size", 1)
                "CharacterController" -> o.put("height", 2).put("speed", 6).put("jump", 7)
                "Animator" -> o.put("clip", "spin").put("speed", 1).put("playing", true)
                "ParticleSystem" -> o.put("count", 18).put("life", 0.8).put("speed", 3).put("color", "#ffcc66")
                "AudioSource" -> o.put("clip", "").put("loop", false).put("volume", 1)
                "NavMeshAgent" -> o.put("target", "").put("speed", 4)
                "Canvas", "Text" -> o.put("text", "").put("anchor", "tl")
                "Projectile" -> o.put("speed", 12).put("life", 3)
                "ConstantForce" -> o.put("fx", 0).put("fy", 0).put("fz", 0)
                "Light" -> o.put("intensity", 1)
                "Camera" -> o.put("fov", 60)
                "Terrain", "Landscape" -> o.put("size", 32)
                "Skybox" -> o.put("top", "#87b5ff").put("bottom", "#101820")
            }
        }
    }
}

data class Particle(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, var color: String,
)

class SignalBus {
    private val slots = HashMap<String, MutableList<(JSONObject) -> Unit>>()
    fun connect(name: String, fn: (JSONObject) -> Unit) {
        slots.getOrPut(name) { mutableListOf() } += fn
    }
    fun emit(name: String, payload: JSONObject = JSONObject()) {
        slots[name]?.toList()?.forEach { it(payload) }
    }
}

interface ActorLike {
    val name: String
    var x: Float
    var y: Float
    var z: Float
    val parent: String
}

fun componentsOf(extra: JSONObject): JSONArray {
    extra.optJSONArray("components")?.let { return it }
    val arr = JSONArray()
    extra.put("components", arr)
    return arr
}

fun hasComponent(extra: JSONObject, type: String): Boolean {
    val want = EngineKit.alias(type)
    val arr = extra.optJSONArray("components") ?: return false
    for (i in 0 until arr.length()) {
        if (EngineKit.alias(arr.optJSONObject(i)?.optString("type").orEmpty()) == want) return true
    }
    return false
}

fun addComponent(extra: JSONObject, type: String, fields: JSONObject = JSONObject()): JSONObject {
    val arr = componentsOf(extra)
    val body = EngineKit.defaultJson(type)
    fields.keys().forEach { if (it != "type") body.put(it, fields.get(it)) }
    arr.put(body)
    return body
}
