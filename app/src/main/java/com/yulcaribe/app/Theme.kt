package com.yulcaribe.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppearanceMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStored(value: String?): AppearanceMode =
            entries.firstOrNull { it.storedValue == value?.lowercase() } ?: System
    }
}

fun loadAppearanceMode(context: Context): AppearanceMode =
    AppearanceMode.fromStored(
        context.getSharedPreferences("yulcaribe_preferences", Context.MODE_PRIVATE)
            .getString("appearance", AppearanceMode.System.storedValue)
    )

private object YcRuntimePalette {
    var dark: Boolean = true
}

val YcVoid: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF05070A) else Color(0xFFF4F7F9)
val YcSurface: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF0B1016) else Color(0xFFFFFFFF)
val YcSurfaceHigh: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF111821) else Color(0xFFE9F0F4)
val YcHairline: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF1A2630) else Color(0xFFC8D3DA)
val YcCyan: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF00D9FF) else Color(0xFF007E9B)
val YcCyanSoft: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF7AEAFF) else Color(0xFF006F87)
val YcText: Color
    get() = if (YcRuntimePalette.dark) Color(0xFFF5F7FA) else Color(0xFF0A1117)
val YcMuted: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF8E9AA7) else Color(0xFF53636F)
val YcGreen: Color
    get() = if (YcRuntimePalette.dark) Color(0xFF4BE28C) else Color(0xFF0A7D43)
val YcAmber: Color
    get() = if (YcRuntimePalette.dark) Color(0xFFFFB84D) else Color(0xFF9A5A00)
val YcRed: Color
    get() = if (YcRuntimePalette.dark) Color(0xFFFF5F6D) else Color(0xFFB3261E)

@Composable
fun YulCaribeTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    YcRuntimePalette.dark = dark

    val colors = if (dark) {
        darkColorScheme(
            primary = YcCyan,
            onPrimary = YcVoid,
            secondary = YcCyanSoft,
            background = YcVoid,
            onBackground = YcText,
            surface = YcSurface,
            onSurface = YcText,
            surfaceVariant = YcSurfaceHigh,
            onSurfaceVariant = YcMuted,
            outline = YcHairline,
            error = YcRed
        )
    } else {
        lightColorScheme(
            primary = YcCyan,
            onPrimary = Color.White,
            secondary = YcCyanSoft,
            background = YcVoid,
            onBackground = YcText,
            surface = YcSurface,
            onSurface = YcText,
            surfaceVariant = YcSurfaceHigh,
            onSurfaceVariant = YcMuted,
            outline = YcHairline,
            error = YcRed
        )
    }

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
