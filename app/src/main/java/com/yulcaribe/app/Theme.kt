package com.yulcaribe.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val YcVoid = Color(0xFF05070A)
val YcSurface = Color(0xFF0B1016)
val YcSurfaceHigh = Color(0xFF111821)
val YcHairline = Color(0xFF1A2630)
val YcCyan = Color(0xFF00D9FF)
val YcCyanSoft = Color(0xFF7AEAFF)
val YcText = Color(0xFFF5F7FA)
val YcMuted = Color(0xFF8E9AA7)
val YcGreen = Color(0xFF4BE28C)
val YcAmber = Color(0xFFFFB84D)
val YcRed = Color(0xFFFF5F6D)

@Composable
fun YulCaribeTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = YcCyan,
        onPrimary = YcVoid,
        secondary = YcCyanSoft,
        background = YcVoid,
        onBackground = YcText,
        surface = YcSurface,
        onSurface = YcText,
        surfaceVariant = YcSurfaceHigh,
        onSurfaceVariant = YcMuted,
        error = YcRed
    )
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = (-0.4).sp
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        ),
        content = content
    )
}
