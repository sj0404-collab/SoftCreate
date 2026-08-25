package com.mobileforge.ui.studio

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfText

@Composable
fun InspectorPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val obj = vm.selectedObject()
    androidx.compose.foundation.layout.Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("Inspector" + (obj?.let { " — ${it.name}" } ?: ""), color = MfText, fontSize = 12.sp)
        if (obj == null) {
            Text("Выберите объект в Hierarchy", color = MfMuted, fontSize = 12.sp)
            return@Column
        }
        fun setNum(raw: String, apply: (Float) -> Unit) {
            raw.toFloatOrNull()?.let(apply)
        }
        MfField(obj.name, { vm.updateSelected { o -> o.name = it } }, "name")
        MfField(obj.type, { vm.updateSelected { o -> o.type = it } }, "type")
        MfField(obj.x.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.x = n } } }, "x", numeric = true)
        MfField(obj.y.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.y = n } } }, "y", numeric = true)
        MfField(obj.z.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.z = n } } }, "z", numeric = true)
        MfField(obj.rx.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.rx = n } } }, "rx", numeric = true)
        MfField(obj.ry.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.ry = n } } }, "ry", numeric = true)
        MfField(obj.rz.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.rz = n } } }, "rz", numeric = true)
        MfField(obj.sx.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.sx = n } } }, "sx", numeric = true)
        MfField(obj.sy.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.sy = n } } }, "sy", numeric = true)
        MfField(obj.sz.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.sz = n } } }, "sz", numeric = true)
        MfField(obj.color, { vm.updateSelected { o -> o.color = it } }, "color")
        MfField(obj.mesh, { vm.updateSelected { o -> o.mesh = it } }, "mesh")
        MfField(obj.material, { vm.updateSelected { o -> o.material = it } }, "material")
        MfField(obj.asset, { vm.updateSelected { o -> o.asset = it } }, "model")
        MfField(obj.script, { vm.updateSelected { o -> o.script = it } }, "script (.cs)")
        MfField(obj.speed.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.speed = n } } }, "speed", numeric = true)
        MfField(obj.mass.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.mass = n } } }, "mass", numeric = true)
        if (obj.type == "Light") {
            MfField(obj.lightType, { vm.updateSelected { o -> o.lightType = it } }, "lightType")
            MfField(obj.intensity.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.intensity = n } } }, "intensity", numeric = true)
        }
        if (obj.type == "Camera") {
            MfField(obj.fov.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.fov = n } } }, "fov", numeric = true)
            MfField(obj.near.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.near = n } } }, "near", numeric = true)
            MfField(obj.far.toString(), { setNum(it) { n -> vm.updateSelected { o -> o.far = n } } }, "far", numeric = true)
        }
        MfField(obj.tag, { vm.updateSelected { o -> o.tag = it } }, "tag")
        MfField(obj.layer, { vm.updateSelected { o -> o.layer = it } }, "layer")
        MfButton(if (obj.solid) "Solid: ON" else "Solid: OFF") { vm.updateSelected { o -> o.solid = !o.solid } }
        MfButton(if (obj.enabled) "Enabled: ON" else "Enabled: OFF") { vm.updateSelected { o -> o.enabled = !o.enabled } }
        MfField(obj.parent, { vm.updateSelected { o -> o.parent = it } }, "parent")
        Text("Компоненты", color = MfMuted, fontSize = 11.sp)
        listOf("Rigidbody", "CharacterController", "Animator", "ParticleSystem", "NavMeshAgent", "AudioSource").forEach { t ->
            MfButton("+ $t") { vm.addComponentToSelected(t) }
        }
    }
}
