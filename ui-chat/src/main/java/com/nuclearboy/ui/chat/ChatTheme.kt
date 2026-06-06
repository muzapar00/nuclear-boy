package com.nuclearboy.ui.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

// ═══════════════════════════════════════════════════════════════════════
//  NUCLEAR BOY — Hacker Theme (Dark-only, Matrix-inspired)
// ═══════════════════════════════════════════════════════════════════════

object NuclearColors {
    // Core palette
    val Background = Color(0xFF08090B)          // Pitch black bg
    val Surface = Color(0xFF111318)              // Card surface
    val SurfaceElevated = Color(0xFF16191F)      // Elevated card
    val SurfaceOverlay = Color(0xFF1C2028)       // Input fields, panels

    val OnBackground = Color(0xFFE3E5E8)         // Primary text
    val OnBackgroundMuted = Color(0xFF838896)     // Muted/secondary text
    val OnBackgroundDim = Color(0xFF4E515B)       // Very dim text

    // Primary accent — Matrix Green
    val Primary = Color(0xFF00E676)
    val PrimaryDim = Color(0xFF00C853)
    val PrimaryContainer = Color(0x1400E676)      // 8% opacity
    val OnPrimary = Color(0xFF08090B)
    val OnPrimaryContainer = Color(0xFF00E676)

    // Secondary accent — Electric Blue
    val Secondary = Color(0xFF0A84FF)
    val SecondaryContainer = Color(0x140A84FF)

    // Error — Red
    val Error = Color(0xFFFF453A)
    val ErrorContainer = Color(0x1AFF453A)
    val OnError = Color.White
    val OnErrorContainer = Color(0xFFFF453A)

    // Success
    val Success = Color(0xFF30D158)
    val SuccessContainer = Color(0x1430D158)

    // Warning — Amber
    val Warning = Color(0xFFFFD60A)
    val WarningContainer = Color(0x14FFD60A)

    // Chat-specific
    val UserBubble = Color(0xFF0D5485)            // Blue-tinted user bubble
    val UserBubbleText = Color(0xFFE3E5E8)
    val AssistantBubble = Color(0xFF13161A)       // Dark assistant bubble
    val AssistantBubbleBorder = Color(0xFF1E2230) // Subtle border
    val AssistantBubbleShadow = Color(0x00000000) // No shadow in dark theme
    val SystemBubble = Color.Transparent
    val CodeBlockBackground = Color(0xFF0A0C10)   // Darker code blocks
    val CodeBlockBorder = Color(0xFF1E2230)
    val ThinkingDot = Color(0xFF00E676)           // Green pulsing dots
    val TokenBarBg = Color(0x800A0C10)

    // Status colors
    val StatusRunning = Color(0xFFFFD60A)
    val StatusComplete = Color(0xFF30D158)
    val StatusFailed = Color(0xFFFF453A)
}

data class NuclearColorScheme(
    val material: ColorScheme,
    val userBubble: Color,
    val userBubbleText: Color,
    val assistantBubble: Color,
    val assistantBubbleBorder: Color,
    val assistantBubbleShadow: Color,
    val systemBubble: Color,
    val codeBlockBackground: Color,
    val codeBlockBorder: Color,
    val thinkingDot: Color,
    val tokenBarBackground: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val isDark: Boolean,
)

val LocalNuclearColorScheme = staticCompositionLocalOf { hackerColorScheme() }

fun hackerColorScheme(): NuclearColorScheme {
    val scheme = darkColorScheme(
        primary = NuclearColors.Primary,
        onPrimary = NuclearColors.OnPrimary,
        primaryContainer = NuclearColors.PrimaryContainer,
        onPrimaryContainer = NuclearColors.OnPrimaryContainer,
        secondary = NuclearColors.Secondary,
        onSecondary = Color.White,
        secondaryContainer = NuclearColors.SecondaryContainer,
        onSecondaryContainer = NuclearColors.Secondary,
        surface = NuclearColors.Surface,
        onSurface = NuclearColors.OnBackground,
        surfaceVariant = NuclearColors.SurfaceOverlay,
        onSurfaceVariant = NuclearColors.OnBackgroundMuted,
        background = NuclearColors.Background,
        onBackground = NuclearColors.OnBackground,
        error = NuclearColors.Error,
        onError = NuclearColors.OnError,
        errorContainer = NuclearColors.ErrorContainer,
        onErrorContainer = NuclearColors.OnErrorContainer,
        outline = NuclearColors.AssistantBubbleBorder,
        outlineVariant = Color(0xFF2A2D35),
    )
    return NuclearColorScheme(
        material = scheme,
        userBubble = NuclearColors.UserBubble,
        userBubbleText = NuclearColors.UserBubbleText,
        assistantBubble = NuclearColors.AssistantBubble,
        assistantBubbleBorder = NuclearColors.AssistantBubbleBorder,
        assistantBubbleShadow = NuclearColors.AssistantBubbleShadow,
        systemBubble = NuclearColors.SystemBubble,
        codeBlockBackground = NuclearColors.CodeBlockBackground,
        codeBlockBorder = NuclearColors.CodeBlockBorder,
        thinkingDot = NuclearColors.ThinkingDot,
        tokenBarBackground = NuclearColors.TokenBarBg,
        success = NuclearColors.Success,
        successContainer = NuclearColors.SuccessContainer,
        warning = NuclearColors.Warning,
        isDark = true,
    )
}

// ═══════════════════════════════════════════════════════════════════════
//  Typography — Monospace-influenced, terminal aesthetic
// ═══════════════════════════════════════════════════════════════════════

object NuclearTypography {
    val Typography = Typography(
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
    )

    val CodeFont = FontFamily.Monospace
}

// ═══════════════════════════════════════════════════════════════════════
//  Shapes
// ═══════════════════════════════════════════════════════════════════════

object NuclearShapes {
    val ExtraSmall = RoundedCornerShape(3.dp)
    val Small = RoundedCornerShape(6.dp)
    val Medium = RoundedCornerShape(10.dp)
    val Large = RoundedCornerShape(14.dp)
    val ExtraLarge = RoundedCornerShape(20.dp)
    val ChatBubbleUser = RoundedCornerShape(topStart = 14.dp, topEnd = 3.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    val ChatBubbleAssistant = RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
}

object NuclearDimens {
    val AvatarSize = 32.dp
    val CodeBlockPadding = 12.dp
    val CodeBlockRadius = 8.dp
}

// ═══════════════════════════════════════════════════════════════════════
//  Theme composable
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun NuclearBoyTheme(
    darkTheme: Boolean = true, // Always dark
    content: @Composable () -> Unit,
) {
    val nuclearColors = hackerColorScheme()
    CompositionLocalProvider(LocalNuclearColorScheme provides nuclearColors) {
        MaterialTheme(
            colorScheme = nuclearColors.material,
            typography = NuclearTypography.Typography,
            content = content,
        )
    }
}

object NuclearBoyTheme {
    val colorScheme: NuclearColorScheme
        @Composable get() = LocalNuclearColorScheme.current
}
