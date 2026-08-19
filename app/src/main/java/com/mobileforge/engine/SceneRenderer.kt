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
        val dim = scene.dimension.uppercase()
        val camObj = scene.objects.firstOrNull { it.type == "Camera" }
        var camX = camObj?.x ?: 0f
        var camY = camObj?.y ?: 5f
        var camZ = camObj?.z ?: 12f
        var camRx = (camObj?.rx ?: -18f) + orbit.pitch
        var camRy = (camObj?.ry ?: 0f) + orbit.yaw
        if (follow) {
            scene.objects.firstOrNull { it.type == "Player" }?.let { p ->
                if (dim == "3D") {
                    camX = p.x; camY = p.y + 5f; camZ = p.z + 11f; camRx = -22f
                } else {
                    camX = p.x; camY = p.y
                }
            }
        }
        if (dim == "2D") draw2d(scope, scene, camX, camY, selected, measurer)
        else draw3d(scope, scene, camX, camY, camZ, camRx, camRy, selected, measurer)
        val visible = scene.objects.any { it.type != "Camera" && it.type != "Light" && it.enabled }
        if (!visible && measurer != null) {
            scope.drawText(
                measurer,
                "сцена пуста — вкладка Сцена: ＋ Ground / Player",
                Offset(16f, h - 48f),
                TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp),
            )
        }
    }

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
        scene.objects.filter { it.type != "Camera" && it.type != "Light" }.forEach { obj ->
            val x = w / 2f + (obj.x - camX) * scale
            val y = h / 2f - (obj.y - camY) * scale
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
        scene.objects.filter { it.type != "Camera" }.forEach { obj ->
            faces += cubeFaces(obj, camX, camY, camZ, camRx, camRy, w, h, selected == obj.name)
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
        scene.objects.filter { it.type != "Camera" && it.type != "Light" && it.enabled }.forEach { obj ->
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
        val hw = kotlin.math.abs(obj.sx) * 0.5f
        val hh = if (obj.type == "Ground") 0.08f else kotlin.math.abs(obj.sy) * 0.5f
        val hd = kotlin.math.abs(obj.sz) * 0.5f
        val corners = listOf(
            Triple(-hw, -hh, -hd), Triple(hw, -hh, -hd), Triple(hw, hh, -hd), Triple(-hw, hh, -hd),
            Triple(-hw, -hh, hd), Triple(hw, -hh, hd), Triple(hw, hh, hd), Triple(-hw, hh, hd),
        ).map { (x, y, z) ->
            project(obj.x + x, obj.y + y, obj.z + z, camX, camY, camZ, camRx, camRy, w, h)
        }
        val quads = listOf(
            listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(0, 1, 5, 4),
            listOf(2, 3, 7, 6), listOf(1, 2, 6, 5), listOf(0, 3, 7, 4),
        )
        val base = parseColor(obj.color)
        return quads.mapIndexedNotNull { qi, idx ->
            val pts = idx.map { corners[it] }
            if (pts.any { it == null }) return@mapIndexedNotNull null
            val ready = pts.filterNotNull()
            val z = ready.map { it.z }.average().toFloat()
            Face(z, ready.map { it.offset }, shade(base, (qi - 2) * 18), selected)
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
