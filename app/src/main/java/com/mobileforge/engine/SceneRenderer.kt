package com.mobileforge.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.mobileforge.GameScene
import com.mobileforge.SceneObject

data class Orbit(var yaw: Float = 0f, var pitch: Float = 0f)

object SceneRenderer {
    fun draw(
        scope: DrawScope,
        scene: GameScene?,
        selected: String? = null,
        orbit: Orbit = Orbit(),
        follow: Boolean = false,
        measurer: TextMeasurer? = null,
    ) {
        val w = scope.size.width
        val h = scope.size.height
        scope.drawRect(
            Brush.verticalGradient(listOf(Color(0xFF15202B), Color(0xFF0B0D14))),
        )
        if (scene == null) return
        try {
            drawUnsafe(scope, scene, selected, orbit, follow, measurer, w, h)
        } catch (_: Throwable) {
            return
        }
    }

    private fun drawUnsafe(
        scope: DrawScope,
        scene: GameScene,
        selected: String?,
        orbit: Orbit,
        follow: Boolean,
        measurer: TextMeasurer?,
        w: Float,
        h: Float,
    ) {
        val dim = scene.dimension.uppercase()
        val camObj = snapObjects(scene).firstOrNull { it.type == "Camera" }
        var camX = camObj?.x ?: 0f
        var camY = camObj?.y ?: 5f
        var camZ = camObj?.z ?: 12f
        var camRx = (camObj?.rx ?: -18f) + orbit.pitch
        var camRy = (camObj?.ry ?: 0f) + orbit.yaw
        if (follow) {
            snapObjects(scene).firstOrNull { it.type == "Player" }?.let { p ->
                if (dim == "3D") {
                    camX = p.x; camY = p.y + 5f; camZ = p.z + 11f; camRx = -22f
                } else {
                    camX = p.x; camY = p.y
                }
            }
        }
        if (dim == "2D") draw2d(scope, scene, camX, camY, selected, measurer)
        else draw3d(scope, scene, camX, camY, camZ, camRx, camRy, selected, measurer)
        val visible = snapObjects(scene).any { it.type != "Camera" && it.type != "Light" && it.enabled }
        if (!visible && measurer != null) {
            scope.drawText(
                measurer,
                "сцена пуста — вкладка Сцена: ＋ Ground / Player",
                Offset(16f, h - 48f),
                TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp),
            )
        }
    }

    private fun snapObjects(scene: GameScene): List<SceneObject> =
        runCatching { scene.objects.toList() }.getOrDefault(emptyList())

    private fun world(scene: GameScene, obj: SceneObject): Triple<Float, Float, Float> {
        val map = snapObjects(scene).associateBy { it.name }
        var x = obj.x; var y = obj.y; var z = obj.z
        var p = map[obj.parent]
        val seen = hashSetOf(obj.name)
        while (p != null && seen.add(p.name)) {
            x += p.x; y += p.y; z += p.z
            p = map[p.parent]
        }
        return Triple(x, y, z)
    }

    private fun extraStr(obj: SceneObject, key: String, def: String): String =
        runCatching { obj.extra.optString(key, def).ifBlank { def } }.getOrDefault(def)

    private fun draw2d(
        scope: DrawScope,
        scene: GameScene,
        camX: Float,
        camY: Float,
        selected: String?,
        measurer: TextMeasurer?,
    ) {
        val w = scope.size.width
        val h = scope.size.height
        val scale = 36f
        for (i in -20..20) {
            val x = w / 2f + (i - camX) * scale
            scope.drawLine(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, h))
        }
        snapObjects(scene).filter { it.type != "Camera" && it.type != "Light" }.forEach { obj ->
            val (wx, wy, _) = world(scene, obj)
            val x = w / 2f + (wx - camX) * scale
            val y = h / 2f - (wy - camY) * scale
            val bw = maxOf(8f, kotlin.math.abs(obj.sx) * scale)
            val bh = maxOf(8f, kotlin.math.abs(obj.sy) * scale)
            val color = parseColor(obj.color)
            if (obj.type == "Coin") {
                scope.drawCircle(color, bw * 0.4f, Offset(x, y))
            } else {
                scope.drawRoundRect(
                    color,
                    Offset(x - bw / 2f, y - bh / 2f),
                    Size(bw, bh),
                    androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
            }
            if (selected == obj.name) {
                scope.drawRect(
                    Color.White,
                    Offset(x - bw / 2f, y - bh / 2f),
                    Size(bw, bh),
                    style = Stroke(3f),
                )
            }
            measurer?.let {
                scope.drawText(
                    it,
                    obj.name,
                    Offset(x - bw / 2f, y - bh / 2f - 18f),
                    TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp),
                )
            }
        }
    }

    private data class Face(val z: Float, val pts: List<Offset>, val color: Color, val selected: Boolean)

    private fun draw3d(
        scope: DrawScope,
        scene: GameScene,
        camX: Float,
        camY: Float,
        camZ: Float,
        camRx: Float,
        camRy: Float,
        selected: String?,
        measurer: TextMeasurer?,
    ) {
        val w = scope.size.width
        val h = scope.size.height
        for (gz in -20..20 step 2) {
            val a = project(-16f, 0f, gz.toFloat(), camX, camY, camZ, camRx, camRy, w, h)
            val b = project(16f, 0f, gz.toFloat(), camX, camY, camZ, camRx, camRy, w, h)
            if (a != null && b != null) {
                scope.drawLine(Color.White.copy(alpha = 0.16f), a.offset, b.offset, strokeWidth = 2f)
            }
        }
        for (gx in -16..16 step 2) {
            val a = project(gx.toFloat(), 0f, -20f, camX, camY, camZ, camRx, camRy, w, h)
            val b = project(gx.toFloat(), 0f, 20f, camX, camY, camZ, camRx, camRy, w, h)
            if (a != null && b != null) {
                scope.drawLine(Color.White.copy(alpha = 0.10f), a.offset, b.offset, strokeWidth = 1.5f)
            }
        }
        val faces = mutableListOf<Face>()
        snapObjects(scene).filter { it.type != "Camera" }.forEach { obj ->
            val (wx, wy, wz) = world(scene, obj)
            val placed = obj.copy(x = wx, y = wy, z = wz)
            faces += cubeFaces(placed, camX, camY, camZ, camRx, camRy, w, h, selected == obj.name)
        }
        faces.sortedByDescending { it.z }.forEach { face ->
            val path = Path().apply {
                moveTo(face.pts[0].x, face.pts[0].y)
                face.pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            scope.drawPath(path, face.color)
            scope.drawPath(path, if (face.selected) Color.White else Color.Black.copy(alpha = 0.35f), style = Stroke(if (face.selected) 3f else 1f))
        }
        snapObjects(scene).filter { it.type != "Camera" && it.type != "Light" && it.enabled }.forEach { obj ->
            val tip = project(obj.x, obj.y + kotlin.math.abs(obj.sy) * 0.6f, obj.z, camX, camY, camZ, camRx, camRy, w, h)
            if (tip != null && measurer != null) {
                scope.drawText(
                    measurer,
                    obj.name,
                    Offset(tip.offset.x - 20f, tip.offset.y - 16f),
                    TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp),
                )
            }
        }
    }

    private fun cubeFaces(
        obj: SceneObject,
        camX: Float,
        camY: Float,
        camZ: Float,
        camRx: Float,
        camRy: Float,
        w: Float,
        h: Float,
        selected: Boolean,
    ): List<Face> {
        val mesh = obj.mesh.ifBlank { SceneObject.defaultMesh(obj.type) }
        val hw = kotlin.math.abs(obj.sx) * (if (mesh.equals("Capsule", true) || obj.type == "Player") 0.35f else 0.5f)
        val hh = when {
            obj.type == "Ground" || mesh.equals("Plane", true) -> 0.08f
            mesh.equals("Capsule", true) || obj.type == "Player" -> kotlin.math.abs(obj.sy) * 0.75f
            else -> kotlin.math.abs(obj.sy) * 0.5f
        }
        val hd = kotlin.math.abs(obj.sz) * (if (mesh.equals("Capsule", true) || obj.type == "Player") 0.35f else 0.5f)
        val locals = meshLocals(mesh, hw, hh, hd)
        val corners = locals.map { (lx, ly, lz) ->
            project(obj.x + lx, obj.y + ly, obj.z + lz, camX, camY, camZ, camRx, camRy, w, h)
        }
        val quads = meshQuads(mesh, locals.size)
        val base = parseColor(obj.color)
        val accent = parseColor(extraStr(obj, "accent", obj.color))
        val pattern = extraStr(obj, "pattern", "flat")
        return quads.mapIndexedNotNull { qi, idx ->
            val pts = idx.map { corners.getOrNull(it) }
            if (pts.any { it == null }) return@mapIndexedNotNull null
            val ready = pts.filterNotNull()
            if (ready.size < 3) return@mapIndexedNotNull null
            val z = ready.map { it.z }.average().toFloat()
            Face(z, ready.map { it.offset }, patternColor(base, accent, pattern, qi), selected)
        }
    }

    private data class Proj(val offset: Offset, val z: Float)

    private fun project(
        x: Float, y: Float, z: Float,
        cx: Float, cy: Float, cz: Float,
        rxDeg: Float, ryDeg: Float,
        w: Float, h: Float,
    ): Proj? {
        val p = Projection.project(x, y, z, cx, cy, cz, rxDeg, ryDeg, w, h) ?: return null
        return Proj(Offset(p.x, p.y), p.depth)
    }

    private fun meshLocals(mesh: String, hw: Float, hh: Float, hd: Float): List<Triple<Float, Float, Float>> {
        return when (mesh.lowercase()) {
            "wedge", "ramp" -> listOf(
                Triple(-hw, -hh, -hd), Triple(hw, -hh, -hd), Triple(hw, -hh, hd), Triple(-hw, -hh, hd),
                Triple(-hw, hh, -hd), Triple(-hw, hh, hd),
            )
            "pyramid", "crystal" -> listOf(
                Triple(-hw, -hh, -hd), Triple(hw, -hh, -hd), Triple(hw, -hh, hd), Triple(-hw, -hh, hd),
                Triple(0f, hh, 0f),
            )
            else -> listOf(
                Triple(-hw, -hh, -hd), Triple(hw, -hh, -hd), Triple(hw, hh, -hd), Triple(-hw, hh, -hd),
                Triple(-hw, -hh, hd), Triple(hw, -hh, hd), Triple(hw, hh, hd), Triple(-hw, hh, hd),
            )
        }
    }

    private fun meshQuads(mesh: String, count: Int): List<List<Int>> = when {
        count == 6 && mesh.equals("wedge", true) || mesh.equals("ramp", true) -> listOf(
            listOf(0, 1, 2, 3), listOf(0, 4, 5, 3), listOf(1, 2, 5, 4), listOf(0, 1, 4, 4), listOf(3, 2, 5, 5),
        )
        count == 5 -> listOf(
            listOf(0, 1, 2, 3), listOf(0, 1, 4, 4), listOf(1, 2, 4, 4), listOf(2, 3, 4, 4), listOf(3, 0, 4, 4),
        )
        else -> listOf(
            listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(0, 1, 5, 4),
            listOf(2, 3, 7, 6), listOf(1, 2, 6, 5), listOf(0, 3, 7, 4),
        )
    }

    private fun patternColor(base: Color, accent: Color, pattern: String, face: Int): Color {
        val mix = when (pattern.lowercase()) {
            "stripes" -> if (face % 2 == 0) 0.15f else 0.55f
            "checker" -> if ((face / 2) % 2 == 0) 0.2f else 0.6f
            "noise" -> ((face * 37 + 13) % 10) / 14f
            "gradient" -> face / 6f
            else -> (face - 2) * 0.07f + 0.35f
        }.coerceIn(0f, 1f)
        return Color(
            base.red * (1 - mix) + accent.red * mix,
            base.green * (1 - mix) + accent.green * mix,
            base.blue * (1 - mix) + accent.blue * mix,
        )
    }

    fun parseColor(hex: String): Color {
        val h = hex.removePrefix("#")
        val n = h.padStart(6, '0').take(6).toLongOrNull(16) ?: 0xB69CFF
        return Color(
            red = ((n shr 16) and 0xFF) / 255f,
            green = ((n shr 8) and 0xFF) / 255f,
            blue = (n and 0xFF) / 255f,
        )
    }

    private fun shade(color: Color, amt: Int): Color {
        fun ch(v: Float) = (v + amt / 255f).coerceIn(0f, 1f)
        return Color(ch(color.red), ch(color.green), ch(color.blue))
    }
}
