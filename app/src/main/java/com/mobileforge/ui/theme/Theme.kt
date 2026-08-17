package com.mobileforge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MfBg = Color(0xFF100E15)
val MfPanel = Color(0xFF171520)
val MfPanel2 = Color(0xFF211E2C)
val MfLine = Color(0xFF363142)
val MfText = Color(0xFFEEEAF7)
val MfMuted = Color(0xFFAAA5B7)
val MfPurple = Color(0xFFB69CFF)
val MfCyan = Color(0xFF75E6DA)
val MfPink = Color(0xFFFFB2C8)
val MfYellow = Color(0xFFF4C95D)
val MfDanger = Color(0xFFFF7D8A)
val MfOk = Color(0xFF7DDEA5)

private val scheme = darkColorScheme(
    primary = MfPurple,
    onPrimary = Color(0xFF1A1228),
    secondary = MfCyan,
    background = MfBg,
    surface = MfPanel,
    onBackground = MfText,
    onSurface = MfText,
    outline = MfLine,
    error = MfDanger,
)

@Composable
fun MobileForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
