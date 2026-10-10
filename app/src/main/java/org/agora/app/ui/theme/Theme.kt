package org.agora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.data.local.ThemeMode

// Design tokens of the Agora PWA (assets/style.css)
private val Cyan500 = Color(0xFF06B6D4)
private val Cyan400 = Color(0xFF22D3EE)
private val Cyan700 = Color(0xFF0E7490)
private val Emerald500 = Color(0xFF10B981)
private val Emerald400 = Color(0xFF34D399)
private val Indigo500 = Color(0xFF6366F1)
private val Indigo400 = Color(0xFF818CF8)
private val Red500 = Color(0xFFEF4444)
private val Amber500 = Color(0xFFF59E0B)

private val LightColors = lightColorScheme(
    primary = Cyan500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = Emerald500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),
    background = Color(0xFFE6F2FA),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE9EEF4),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Red500,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = Cyan400,
    onPrimary = Color(0xFF083344),
    primaryContainer = Color(0xFF155E75),
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = Emerald400,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Indigo400,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF0F172A),
    surfaceContainerLow = Color(0xFF1A2436),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF273449),
    surfaceContainerHighest = Color(0xFF334155),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

/** Colours outside the M3 scheme that the PWA uses for status and highlights. */
@Immutable
data class AgoraExtraColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val duty: Color,
    val heroGradient: Brush,
    val eventGradient: Brush,
    val pinnedGradient: Brush,
    val managerRing: Brush,
    /** --gradient-primary, left to right: buttons, FAB, brand name, active switches */
    val brandGradient: Brush,
    /** .pill-tabs track */
    val tabTrack: Color,
    /** .pill-tab.active */
    val tabThumb: Color,
    /** --surface-alt: inputs, info tiles */
    val surfaceAlt: Color,
    /** Bottom navigation icons/labels */
    val navInactive: Color,
    /** Stronger text for the heavy PWA headings */
    val heading: Color
)

private fun diagonal(from: Color, to: Color) = Brush.linearGradient(listOf(from, to), start = Offset.Zero, end = Offset.Infinite)
private fun horizontal(from: Color, to: Color) = Brush.horizontalGradient(listOf(from, to))

private val LightExtra = AgoraExtraColors(
    success = Emerald500, warning = Amber500, danger = Red500, duty = Indigo500,
    heroGradient = diagonal(Cyan500, Emerald500),
    eventGradient = diagonal(Cyan500, Color(0xFF0891B2)),
    pinnedGradient = diagonal(Color(0xFF6366F1), Color(0xFF7C3AED)),
    managerRing = diagonal(Color(0xFFEAB308), Red500),
    brandGradient = horizontal(Cyan500, Emerald500),
    tabTrack = Color(0xFFEDF2F7),
    tabThumb = Color.White,
    surfaceAlt = Color(0xFFF8FAFC),
    navInactive = Color(0xFF64748B),
    heading = Color(0xFF0F172A)
)

private val DarkExtra = LightExtra.copy(
    success = Emerald400, danger = Color(0xFFF87171), duty = Indigo400,
    heroGradient = diagonal(Color(0xFF0891B2), Color(0xFF059669)),
    brandGradient = horizontal(Cyan400, Emerald400),
    tabTrack = Color(0x14FFFFFF),
    tabThumb = Color(0xFF334155),
    surfaceAlt = Color(0xFF334155),
    navInactive = Color(0xFF9CA3AF),
    heading = Color(0xFFF3F4F6)
)

val LocalAgoraColors = staticCompositionLocalOf { LightExtra }

object Agora {
    val colors: AgoraExtraColors
        @Composable get() = LocalAgoraColors.current
}

// Radii of the PWA: buttons 12px, inputs/tiles 14px, cards 20px (--radius), hero/modals 28px
private val AgoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// The PWA sets headings at weight 800 in the system sans-serif (Roboto on Android).
// Sizes follow the PWA on a phone (1rem = 16px), a bit smaller than the Material defaults.
private val AgoraTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 27.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp)
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AgoraTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    CompositionLocalProvider(LocalAgoraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AgoraTypography,
            shapes = AgoraShapes,
            content = content
        )
    }
}
