package com.mobileforge.engine

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {
    @Test
    fun originIsInFrontOfDefaultCamera() {
        val p = Projection.project(
            x = 0f, y = 0f, z = 0f,
            cx = 0f, cy = 5f, cz = 10f,
            rxDeg = -18f, ryDeg = 0f,
            w = 400f, h = 800f,
        )
        assertNotNull(p)
        assertTrue(p!!.depth > 0.35f)
    }

    @Test
    fun playerInFrontWhenFollowCameraBehind() {
        val p = Projection.project(
            x = 0f, y = 1f, z = 4f,
            cx = 0f, cy = 6f, cz = 15f,
            rxDeg = -22f, ryDeg = 0f,
            w = 400f, h = 800f,
        )
        assertNotNull("player was behind camera", p)
        assertTrue(p!!.depth > 0.35f)
    }
}
