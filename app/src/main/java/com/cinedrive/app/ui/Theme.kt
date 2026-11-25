package com.cinedrive.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val SurfaceHigh = Color(0xFF222226)
    val Accent = Color(0xFFE53935)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA7A7AD)
}

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.SurfaceHigh,
    onSecondary = Palette.TextPrimary,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    error = Color(0xFFFF6E6E),
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun CineDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
