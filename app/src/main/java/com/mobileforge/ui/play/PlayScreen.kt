package com.mobileforge.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.engine.ControlLayout
import com.mobileforge.engine.ControlWidget
import com.mobileforge.engine.Orbit
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.studio.SceneViewport
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun PlayScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val runtime = vm.runtime
    var frameScene by remember { mutableStateOf(runtime?.sceneSnapshot()) }
    LaunchedEffect(runtime) {
        var last = 0L
        while (runtime != null && runtime.playing) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    vm.tickPlay(dt)
                    frameScene = runtime.sceneSnapshot()
                }
                last = now
            }
        }
    }
    val focus = remember { FocusRequester() }
    val keys = remember { mutableSetOf<Key>() }
    fun syncKeys() {
        val rt = runtime ?: return
        rt.input.x = (if (Key.D in keys || Key.DirectionRight in keys) 1f else 0f) -
            (if (Key.A in keys || Key.DirectionLeft in keys) 1f else 0f)
        rt.input.y = (if (Key.W in keys || Key.DirectionUp in keys) 1f else 0f) -
            (if (Key.S in keys || Key.DirectionDown in keys) 1f else 0f)
        if (Key.Spacebar in keys) rt.input.jump = true
    }
    LaunchedEffect(runtime) { focus.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) keys += event.key
                if (event.type == KeyEventType.KeyUp) keys -= event.key
                syncKeys()
                true
            },
    ) {
        SceneViewport(
            scene = frameScene,
            selected = null,
            orbit = Orbit(),
            follow = true,
            interactive = false,
            modifier = Modifier.fillMaxSize(),
        )
        if (vm.playHud.isNotBlank()) {
            Text(vm.playHud, color = MfText, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
        DesignedControls(vm.playControls, vm)
        if (vm.playControls.items.isEmpty() && runtime != null) {
            Text(
                "Сенсор не задан. Попросите AI создать UI/Controls.json",
                color = MfMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.DesignedControls(layout: ControlLayout, vm: AppViewModel) {
    val runtime = vm.runtime
    layout.items.forEach { widget ->
        val align = when (widget.anchor) {
            "bl" -> Alignment.BottomStart
            "br" -> Alignment.BottomEnd
            "tl" -> Alignment.TopStart
            "tr" -> Alignment.TopEnd
            "bc" -> Alignment.BottomCenter
            else -> Alignment.BottomEnd
        }
        Box(Modifier.align(align).padding(16.dp)) {
            when (widget.type.lowercase()) {
                "joystick" -> DesignedJoystick { x, y ->
                    runtime?.input?.x = x
                    runtime?.input?.y = y
                }
                else -> MfButton(widget.label) { fire(widget, vm) }
            }
        }
    }
}

private fun fire(widget: ControlWidget, vm: AppViewModel) {
    when (widget.action.lowercase()) {
        "jump" -> vm.runtime?.input?.jump = true
        "action", "click" -> vm.runtime?.click()
        else -> vm.runtime?.click()
    }
}

@Composable
private fun DesignedJoystick(onVector: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(0f to 0f) }
    Box(
        Modifier
            .size(116.dp)
            .clip(CircleShape)
            .background(MfPurple.copy(alpha = 0.12f))
            .border(1.dp, MfLine, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { knob = 0f to 0f; onVector(0f, 0f) },
                    onDragCancel = { knob = 0f to 0f; onVector(0f, 0f) },
                ) { change, drag ->
                    change.consume()
                    var nx = knob.first + drag.x
                    var ny = knob.second + drag.y
                    val mag = hypot(nx, ny)
                    val limit = 48f
                    if (mag > limit) {
                        nx = nx / mag * limit
                        ny = ny / mag * limit
                    }
                    knob = nx to ny
                    onVector((nx / limit).coerceIn(-1f, 1f), (-ny / limit).coerceIn(-1f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.first.roundToInt(), knob.second.roundToInt()) }
                .size(40.dp)
                .clip(CircleShape)
                .background(MfPurple.copy(alpha = 0.8f)),
        )
    }
}
