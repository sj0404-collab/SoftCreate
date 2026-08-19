package com.mobileforge.engine

/** Camera looks toward world -Z (Unity). Positive view-Z = in front of camera. */
object Projection {
    data class Point(val x: Float, val y: Float, val depth: Float)

    fun project(
        x: Float,
        y: Float,
        z: Float,
        cx: Float,
        cy: Float,
        cz: Float,
        rxDeg: Float,
        ryDeg: Float,
        w: Float,
        h: Float,
        fov: Double = 420.0,
    ): Point? {
        val yaw = Math.toRadians(ryDeg.toDouble())
        val pitch = Math.toRadians(rxDeg.toDouble())
        val dx = (x - cx).toDouble()
        val dy = (y - cy).toDouble()
        val dz = (cz - z).toDouble()
        val cosY = kotlin.math.cos(yaw)
        val sinY = kotlin.math.sin(yaw)
        val rx = dx * cosY + dz * sinY
        var rz = -dx * sinY + dz * cosY
        val cosP = kotlin.math.cos(pitch)
        val sinP = kotlin.math.sin(pitch)
        val ry = dy * cosP - rz * sinP
        rz = dy * sinP + rz * cosP
        if (rz < 0.35) return null
        return Point(
            (w / 2f + rx * fov / rz).toFloat(),
            (h / 2f - ry * fov / rz).toFloat(),
            rz.toFloat(),
        )
    }
}
