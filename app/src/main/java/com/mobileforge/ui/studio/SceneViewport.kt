package com.mobileforge.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import com.mobileforge.GameScene
import com.mobileforge.engine.Orbit
import com.mobileforge.engine.SceneRenderer

@Composable
fun SceneViewport(
    scene: GameScene?,
    selected: String?,
    orbit: Orbit,
    follow: Boolean = false,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxSize()
            .then(
                if (interactive) {
                    Modifier.pointerInput(orbit) {
                        detectDragGestures { _, drag ->
                            orbit.yaw += drag.x * 0.35f
                            orbit.pitch += drag.y * 0.2f
                        }
                    }
                } else Modifier,
            ),
    ) {
        SceneRenderer.draw(this, scene, selected, orbit, follow, measurer)
    }
}
