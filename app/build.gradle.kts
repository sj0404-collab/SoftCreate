plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mobileforge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mobileforge"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

tasks.register("generateStudioPack") {
    val outDir = layout.projectDirectory.dir("src/main/assets/StudioPack")
    outputs.dir(outDir)
    doLast {
        val root = outDir.asFile
        fun total(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (File(root, ".ready").exists() && total() >= 56L * 1024 * 1024) {
            logger.lifecycle("StudioPack cache ${total() / 1024 / 1024} MB")
            return@doLast
        }
        listOf("Textures", "Heightmaps", "Audio", "Meshes", "Docs", "Plugins").forEach { File(root, it).mkdirs() }

        fun hash(x: Int, y: Int, s: Int): Float {
            var n = x * 374761393 + y * 668265263 + s * 1274126177
            n = (n xor (n shl 13)) * 1274126177
            return ((n ushr 8) and 0xFFFFFF) / 16777215f
        }
        fun fbm(x: Int, y: Int, s: Int): Float {
            var v = 0f
            var a = 0.5f
            var f = 1
            repeat(5) {
                val nx = x / f
                val ny = y / f
                v += a * hash(nx, ny, s + f)
                a *= 0.5f
                f *= 2
            }
            return v.coerceIn(0f, 1f)
        }

        val size = 1024
        repeat(16) { idx ->
            val rgb = ByteArray(size * size * 3)
            val hue = idx * 21
            var p = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val n = fbm(x, y, 11 + idx)
                    val r = ((n * (140 + hue % 80) + 40) % 256).toInt()
                    val g = ((n * (90 + (hue * 3) % 100) + 30) % 256).toInt()
                    val b = ((80 + n * (60 + idx * 7)) % 256).toInt()
                    rgb[p++] = r.toByte()
                    rgb[p++] = g.toByte()
                    rgb[p++] = b.toByte()
                }
            }
            File(root, "Textures/Albedo_${idx.toString().padStart(2, '0')}.rgb").writeBytes(rgb)
        }
        repeat(6) { idx ->
            val rgb = ByteArray(size * size * 3)
            var p = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val n = fbm(x, y, 90 + idx)
                    rgb[p++] = (128 + n * 40).toInt().toByte()
                    rgb[p++] = (128 + n * 20).toInt().toByte()
                    rgb[p++] = (255).toByte()
                }
            }
            File(root, "Textures/Normal_${idx.toString().padStart(2, '0')}.rgb").writeBytes(rgb)
        }
        repeat(8) { idx ->
            val raw = ByteArray(size * size * 2)
            var p = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val h = (fbm(x, y, 200 + idx) * 65535).toInt().coerceIn(0, 65535)
                    raw[p++] = (h and 0xFF).toByte()
                    raw[p++] = (h ushr 8).toByte()
                }
            }
            File(root, "Heightmaps/H_${idx.toString().padStart(2, '0')}.r16").writeBytes(raw)
        }

        fun wav(name: String, freq: Double, seconds: Double, noise: Boolean = false) {
            val rate = 22050
            val n = (rate * seconds).toInt()
            val data = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i / rate.toDouble()
                val env = (1.0 - i / n.toDouble()).coerceAtLeast(0.0)
                val sample = if (noise) ((Math.random() * 2 - 1) * env * 8000) else (Math.sin(2 * Math.PI * freq * t) * env * 12000)
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
            File(root, "Audio/$name.wav").writeBytes(header + data)
        }
        listOf("ui_click" to 880.0, "ui_ok" to 1200.0, "jump" to 420.0, "coin" to 1320.0, "hit" to 180.0, "whoosh" to 90.0).forEach {
            wav(it.first, it.second, 0.35)
        }
        wav("wind", 80.0, 1.2, noise = true)

        File(root, "Meshes/Cube.obj").writeText(
            """
            # MobileForge unit cube
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
            """.trimIndent(),
        )
        File(root, "Docs/CSharpAPI.md").writeText(
            """
            # MobileForge C# API
            class ForgeBehaviour { void Start(); void Update(); void OnCollisionEnter(other); }
            Move, Jump, AddScore, Destroy, Find, Time.deltaTime, input.horizontal/vertical/jump
            Camera.fov/near/far  Light.intensity/lightType  transform.position
            """.trimIndent(),
        )
        File(root, "Plugins/manifest.json").writeText(
            """{"plugins":["PrimitiveFactory","BlockKit","LightingKit","CinemachineLite","GitHubCloudBuild","McpBridge"]}""",
        )
        File(root, "catalog.json").writeText(
            """{"format":"mobileforge.pack.v2","textures":16,"normals":6,"heightmaps":8,"audio":7}""",
        )
        File(root, ".ready").writeText("ok ${total()}\n")
        logger.lifecycle("StudioPack generated ${total() / 1024 / 1024} MB")
        if (total() < 50L * 1024 * 1024) {
            throw GradleException("StudioPack too small: ${total()} bytes")
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn("generateStudioPack") }
}
