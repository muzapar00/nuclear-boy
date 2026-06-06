package com.nuclearboy.app.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Hacker theme — dark only
private val HackerColorScheme = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF08090B),
    primaryContainer = Color(0x1400E676),
    onPrimaryContainer = Color(0xFF00E676),
    secondary = Color(0xFF0A84FF),
    onSecondary = Color.White,
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E5E8),
    surfaceVariant = Color(0xFF1C2028),
    onSurfaceVariant = Color(0xFF838896),
    background = Color(0xFF08090B),
    onBackground = Color(0xFFE3E5E8),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0x1AFF453A),
    onErrorContainer = Color(0xFFFF453A),
    outline = Color(0xFF2A2D35),
    outlineVariant = Color(0xFF1E2230),
)

val HackerTypography = Typography(
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun NuclearBoyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = HackerColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF08090B).toArgb()
            window.navigationBarColor = Color(0xFF08090B).toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = HackerTypography, content = content)
}
