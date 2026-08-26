package com.mobileforge.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class V3(var x: Float, var y: Float, var z: Float) {
    fun add(o: V3) { x += o.x; y += o.y; z += o.z }
    fun scaled(s: Float) = V3(x * s, y * s, z * s)
}

data class Face(val idx: MutableList<Int>)

class EditMesh(
    var name: String = "Mesh",
    var color: String = "#8ab4ff",
    val verts: MutableList<V3> = mutableListOf(),
    val faces: MutableList<Face> = mutableListOf(),
) {
    var selected: Int = -1
    val epoch get() = verts.size * 31 + faces.size

    fun clear() {
        verts.clear(); faces.clear(); selected = -1
    }

    fun addPrimitive(kind: String, ox: Float = 0f, oy: Float = 0f, oz: Float = 0f, s: Float = 1f) {
        val base = verts.size
        fun v(x: Float, y: Float, z: Float) { verts += V3(ox + x * s, oy + y * s, oz + z * s) }
        fun f(vararg i: Int) { faces += Face(i.map { it + base }.toMutableList()) }
        when (kind.lowercase()) {
            "plane", "ground" -> {
                v(-1f, 0f, -1f); v(1f, 0f, -1f); v(1f, 0f, 1f); v(-1f, 0f, 1f)
                f(0, 1, 2, 3)
            }
            "sphere", "uv" -> {
                val seg = 8; val ring = 6
                for (j in 0..ring) {
                    val ph = Math.PI * j / ring
                    val y = cos(ph).toFloat()
                    val r = sin(ph).toFloat()
                    for (i in 0 until seg) {
                        val th = 2 * Math.PI * i / seg
                        v(r * cos(th).toFloat(), y, r * sin(th).toFloat())
                    }
                }
                for (j in 0 until ring) {
                    for (i in 0 until seg) {
                        val a = j * seg + i
                        val b = j * seg + (i + 1) % seg
                        f(a, b, b + seg, a + seg)
                    }
                }
            }
            "cylinder", "capsule" -> {
                val seg = 10
                for (i in 0 until seg) {
                    val th = 2 * Math.PI * i / seg
                    v(0.5f * cos(th).toFloat(), -1f, 0.5f * sin(th).toFloat())
                }
                for (i in 0 until seg) {
                    val th = 2 * Math.PI * i / seg
                    v(0.5f * cos(th).toFloat(), 1f, 0.5f * sin(th).toFloat())
                }
                for (i in 0 until seg) f(i, (i + 1) % seg, seg + (i + 1) % seg, seg + i)
                f(*(0 until seg).toList().toIntArray())
                f(*(seg until seg * 2).reversed().toList().toIntArray())
            }
            "cone", "pyramid" -> {
                v(-1f, 0f, -1f); v(1f, 0f, -1f); v(1f, 0f, 1f); v(-1f, 0f, 1f); v(0f, 1.4f, 0f)
                f(0, 1, 2, 3); f(0, 1, 4); f(1, 2, 4); f(2, 3, 4); f(3, 0, 4)
            }
            "wedge" -> {
                v(-1f, 0f, -1f); v(1f, 0f, -1f); v(1f, 0f, 1f); v(-1f, 0f, 1f)
                v(-1f, 1f, -1f); v(-1f, 1f, 1f)
                f(0, 1, 2, 3); f(0, 4, 5, 3); f(1, 2, 5, 4)
            }
            else -> {
                v(-1f, -1f, -1f); v(1f, -1f, -1f); v(1f, 1f, -1f); v(-1f, 1f, -1f)
                v(-1f, -1f, 1f); v(1f, -1f, 1f); v(1f, 1f, 1f); v(-1f, 1f, 1f)
                f(0, 1, 2, 3); f(4, 5, 6, 7); f(0, 1, 5, 4); f(2, 3, 7, 6); f(1, 2, 6, 5); f(0, 3, 7, 4)
            }
        }
        selected = verts.lastIndex
    }

    fun nudge(dx: Float, dy: Float, dz: Float) {
        val i = if (selected in verts.indices) selected else return
        verts[i].x += dx; verts[i].y += dy; verts[i].z += dz
    }

    fun scale(s: Float) {
        verts.forEach { it.x *= s; it.y *= s; it.z *= s }
    }

    fun extrude(amount: Float = 0.4f) {
        val face = faces.lastOrNull() ?: return
        val n = faceNormal(face)
        val start = verts.size
        face.idx.forEach { i ->
            val v = verts[i]
            verts += V3(v.x + n.x * amount, v.y + n.y * amount, v.z + n.z * amount)
        }
        val m = face.idx.size
        for (k in 0 until m) {
            val a = face.idx[k]
            val b = face.idx[(k + 1) % m]
            val c = start + (k + 1) % m
            val d = start + k
            faces += Face(mutableListOf(a, b, c, d))
        }
        faces += Face((0 until m).map { start + it }.toMutableList())
        selected = start
    }

    fun smooth() {
        if (verts.isEmpty()) return
        val acc = Array(verts.size) { V3(0f, 0f, 0f) }
        val cnt = IntArray(verts.size)
        faces.forEach { f ->
            f.idx.forEach { i ->
                f.idx.forEach { j ->
                    if (i != j) {
                        acc[i].add(verts[j]); cnt[i]++
                    }
                }
            }
        }
        verts.forEachIndexed { i, v ->
            if (cnt[i] == 0) return@forEachIndexed
            v.x = (v.x + acc[i].x / cnt[i]) * 0.5f
            v.y = (v.y + acc[i].y / cnt[i]) * 0.5f
            v.z = (v.z + acc[i].z / cnt[i]) * 0.5f
        }
    }

    fun invert() {
        faces.forEach { it.idx.reverse() }
    }

    private fun faceNormal(face: Face): V3 {
        if (face.idx.size < 3) return V3(0f, 1f, 0f)
        val a = verts[face.idx[0]]; val b = verts[face.idx[1]]; val c = verts[face.idx[2]]
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        return V3(nx / len, ny / len, nz / len)
    }

    fun pick(sx: Float, sy: Float, w: Float, h: Float, orbit: Orbit): Int {
        var best = -1
        var bestD = 28f
        verts.forEachIndexed { i, v ->
            val p = Projection.project(v.x, v.y, v.z, 0f, 1.2f, 5.5f, orbit.pitch, orbit.yaw, w, h) ?: return@forEachIndexed
            val d = kotlin.math.hypot(p.x - sx, p.y - sy)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) selected = best
        return best
    }

    fun toObj(): String = buildString {
        appendLine("# MobileForge mesh $name")
        verts.forEach { appendLine("v ${it.x} ${it.y} ${it.z}") }
        faces.forEach { f ->
            append("f")
            f.idx.forEach { append(" ${it + 1}") }
            appendLine()
        }
    }

    fun toSceneMesh(): String = when {
        faces.size <= 2 -> "Plane"
        verts.size <= 8 -> "Cube"
        else -> "Sphere"
    }

    companion object {
        fun parseObj(raw: String, name: String = "Mesh"): EditMesh {
            val m = EditMesh(name)
            raw.lineSequence().forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                when (p.firstOrNull()) {
                    "v" -> if (p.size >= 4) m.verts += V3(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                    "f" -> {
                        val idx = p.drop(1).map { it.substringBefore('/').toInt() - 1 }
                        if (idx.size >= 3) m.faces += Face(idx.toMutableList())
                    }
                }
            }
            return m
        }
    }
}
