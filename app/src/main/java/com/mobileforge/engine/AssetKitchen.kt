package com.mobileforge.engine

import org.json.JSONObject
import java.io.File
import kotlin.math.sin

object AssetKitchen {
    fun material(color: String, pattern: String, accent: String, mesh: String, note: String): String =
        JSONObject()
            .put("format", "mobileforge.mat.v1")
            .put("color", color)
            .put("accent", accent)
            .put("pattern", pattern)
            .put("mesh", mesh)
            .put("note", note)
            .toString(2) + "\n"

    fun meshObj(kind: String): String = when (kind.lowercase()) {
        "wedge", "ramp" -> """
            # wedge
            v -0.5 -0.5 -0.5
            v 0.5 -0.5 -0.5
            v 0.5 -0.5 0.5
            v -0.5 -0.5 0.5
            v -0.5 0.5 -0.5
            v -0.5 0.5 0.5
            f 1 2 3 4
            f 1 5 6 4
            f 2 3 6 5
            f 1 2 5
            f 4 3 6
        """.trimIndent() + "\n"
        "pyramid", "crystal" -> """
            # pyramid
            v -0.5 -0.5 -0.5
            v 0.5 -0.5 -0.5
            v 0.5 -0.5 0.5
            v -0.5 -0.5 0.5
            v 0.0 0.7 0.0
            f 1 2 3 4
            f 1 2 5
            f 2 3 5
            f 3 4 5
            f 4 1 5
        """.trimIndent() + "\n"
        "capsule" -> """
            # capsule-ish
            v -0.3 -0.7 -0.3
            v 0.3 -0.7 -0.3
            v 0.3 -0.7 0.3
            v -0.3 -0.7 0.3
            v -0.3 0.7 -0.3
            v 0.3 0.7 -0.3
            v 0.3 0.7 0.3
            v -0.3 0.7 0.3
            f 1 2 3 4
            f 5 8 7 6
            f 1 5 6 2
            f 2 6 7 3
            f 3 7 8 4
            f 4 8 5 1
        """.trimIndent() + "\n"
        else -> """
            # cube
            v -0.5 -0.5 -0.5
            v 0.5 -0.5 -0.5
            v 0.5 0.5 -0.5
            v -0.5 0.5 -0.5
            v -0.5 -0.5 0.5
            v 0.5 -0.5 0.5
            v 0.5 0.5 0.5
            v -0.5 0.5 0.5
            f 1 2 3 4
            f 5 8 7 6
            f 1 5 6 2
            f 4 3 7 8
            f 1 4 8 5
            f 2 6 7 3
        """.trimIndent() + "\n"
    }

    fun textureRecipe(name: String, color: String, pattern: String, accent: String): String =
        "texture $name\ncolor $color\naccent $accent\npattern $pattern\n"

    fun wav(freq: Double, seconds: Double, noise: Boolean = false): ByteArray {
        val rate = 22050
        val n = (rate * seconds).toInt().coerceIn(800, 44000)
        val data = ByteArray(n * 2)
        for (i in 0 until n) {
            val t = i / rate.toDouble()
            val env = (1.0 - i / n.toDouble()).coerceAtLeast(0.0)
            val sample = if (noise) {
                ((Math.random() * 2 - 1) * env * 7000)
            } else {
                sin(2 * Math.PI * freq * t) * env * 11000
            }
            val v = sample.toInt().coerceIn(-32767, 32767)
            data[i * 2] = (v and 0xFF).toByte()
            data[i * 2 + 1] = (v shr 8).toByte()
        }
        fun leInt(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        )
        fun leShort(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        val header = "RIFF".toByteArray() + leInt(36 + data.size) + "WAVEfmt ".toByteArray() +
            leInt(16) + leShort(1) + leShort(1) + leInt(rate) + leInt(rate * 2) +
            leShort(2) + leShort(16) + "data".toByteArray() + leInt(data.size)
        return header + data
    }

    fun parseMat(text: String): Triple<String, String, String> {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val color = json?.optString("color").orEmpty().ifBlank { "#b69cff" }
        val pattern = json?.optString("pattern").orEmpty().ifBlank { "flat" }
        val accent = json?.optString("accent").orEmpty().ifBlank { color }
        return Triple(color, pattern, accent)
    }

    fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}
