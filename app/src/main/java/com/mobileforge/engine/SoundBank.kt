package com.mobileforge.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

class SoundBank(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<String, Int>()
    val loaded = mutableListOf<String>()

    fun loadFrom(projectDir: File): List<String> {
        releaseKeepPool()
        val roots = listOf(File(projectDir, "Assets/Audio"), File(projectDir, "Audio"))
        roots.forEach { root ->
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension.equals("wav", true) }.forEach { wav ->
                runCatching {
                    val id = pool.load(wav.absolutePath, 1)
                    val key = wav.nameWithoutExtension
                    ids[key] = id
                    ids[wav.name] = id
                    loaded += wav.name
                }
            }
        }
        return loaded.toList()
    }

    fun play(name: String): Boolean {
        val id = ids[name] ?: ids[name.removeSuffix(".wav")] ?: ids.values.firstOrNull() ?: return false
        pool.play(id, 1f, 1f, 1, 0, 1f)
        return true
    }

    fun release() {
        runCatching { pool.release() }
        ids.clear()
        loaded.clear()
    }

    private fun releaseKeepPool() {
        ids.clear()
        loaded.clear()
    }
}
