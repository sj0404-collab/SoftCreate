package com.mobileforge.ui.blender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.engine.Projection
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.theme.MfBg
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun BlenderScreen(vm: AppViewModel) {
    val mesh = vm.editMesh
    Column(Modifier.fillMaxSize().background(MfBg)) {
        Text("Blender", color = MfText, fontSize = 18.sp, modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp))
        Text(
            "${mesh.name} · v ${mesh.verts.size}  f ${mesh.faces.size}" +
                (if (mesh.selected >= 0) "  · вершина ${mesh.selected}" else "  · тап по точке"),
            color = MfMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(mesh.epoch, mesh.selected) {
                    detectTapGestures { off ->
                        mesh.pick(off.x, off.y, size.width.toFloat(), size.height.toFloat(), vm.orbit)
                        vm.meshTick++
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        vm.orbit.yaw += drag.x * 0.4f
                        vm.orbit.pitch = (vm.orbit.pitch + drag.y * 0.3f).coerceIn(-70f, 70f)
                        vm.meshTick++
                    }
                },
        ) {
            val tick = vm.meshTick
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE")
                val _t = tick
                drawRect(Color(0xFF0C1018))
                val w = size.width
                val h = size.height
                val faces = mesh.faces.mapNotNull { face ->
                    val pts = face.idx.map { i ->
                        val v = mesh.verts.getOrNull(i) ?: return@mapNotNull null
                        Projection.project(v.x, v.y, v.z, 0f, 1.2f, 5.5f, vm.orbit.pitch, vm.orbit.yaw, w, h)
                    }
                    if (pts.any { it == null } || pts.size < 3) return@mapNotNull null
                    val ready = pts.filterNotNull()
                    val z = ready.map { it.depth }.average().toFloat()
                    Triple(z, ready, face.idx.any { it == mesh.selected })
                }.sortedByDescending { it.first }
                faces.forEach { (_, pts, sel) ->
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(path, Color(0x668AB4FF))
                    drawPath(path, if (sel) MfPurple else Color.White.copy(alpha = 0.35f), style = Stroke(if (sel) 3f else 1.2f))
                }
                mesh.verts.forEachIndexed { i, v ->
                    val p = Projection.project(v.x, v.y, v.z, 0f, 1.2f, 5.5f, vm.orbit.pitch, vm.orbit.yaw, w, h) ?: return@forEachIndexed
                    val r = if (i == mesh.selected) 7f else 3.5f
                    drawCircle(if (i == mesh.selected) MfPurple else Color.White, r, Offset(p.x, p.y))
                }
            }
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("cube" to "Куб", "plane" to "Плоск.", "sphere" to "Сфера", "cylinder" to "Цил.", "cone" to "Конус", "wedge" to "Клин").forEach { (k, l) ->
                    MfButton("+ $l") { vm.meshAdd(k) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("←") { vm.meshNudge(-0.15f, 0f, 0f) }
                MfButton("→") { vm.meshNudge(0.15f, 0f, 0f) }
                MfButton("↑") { vm.meshNudge(0f, 0.15f, 0f) }
                MfButton("↓") { vm.meshNudge(0f, -0.15f, 0f) }
                MfButton("ближ") { vm.meshNudge(0f, 0f, 0.15f) }
                MfButton("даль") { vm.meshNudge(0f, 0f, -0.15f) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("Extrude") { vm.meshExtrude() }
                MfButton("Smooth") { vm.meshSmooth() }
                MfButton("×0.8") { vm.meshScale(0.8f) }
                MfButton("×1.25") { vm.meshScale(1.25f) }
                MfButton("Очистить") { vm.meshClear() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfField(vm.editMesh.name, { vm.editMesh.name = it; vm.meshTick++ }, "имя", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("Сохранить OBJ", primary = true) { vm.meshSave() }
                MfButton("На объект сцены") { vm.meshApplyToSelected() }
            }
        }
    }
}
