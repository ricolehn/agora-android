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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.agora.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.data.local.ThemeMode

// Design tokens of the Agora web app (assets/polish.css, palette "Stone & Lagoon"): warm stone neutrals and a
// deep lagoon teal that runs into emerald. The dark theme is tuned on its own: barely warm near-black, softer
// off-white text, dimmed accents and no coloured light.
private val Lagoon = Color(0xFF0D9E94)
private val LagoonDeep = Color(0xFF0B7D75)
private val LagoonText = Color(0xFF0C8D84)
private val Emerald = Color(0xFF14A87C)
private val LagoonDark = Color(0xFF2EA398)
private val LagoonDarkText = Color(0xFF63C0B4)
private val EmeraldDark = Color(0xFF3FAE84)
private val Indigo500 = Color(0xFF6366F1)
private val Indigo400 = Color(0xFF818CF8)

private val LightColors = lightColorScheme(
    primary = LagoonText,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCF1EC),
    onPrimaryContainer = Color(0xFF0A5049),
    secondary = Emerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF1EC),
    onSecondaryContainer = Color(0xFF0A5049),
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),
    background = Color(0xFFF5F4F1),
    onBackground = Color(0xFF1A1917),
    surface = Color.White,
    onSurface = Color(0xFF1A1917),
    surfaceVariant = Color(0xFFF1EFEB),
    onSurfaceVariant = Color(0xFF6D6861),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainer = Color(0xFFF1EFEB),
    surfaceContainerHigh = Color(0xFFEBE8E3),
    surfaceContainerHighest = Color(0xFFE8E5DF),
    outline = Color(0xFFD9D5CD),
    outlineVariant = Color(0xFFE8E5DF),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = LagoonDarkText,
    onPrimary = Color(0xFF032A26),
    primaryContainer = Color(0xFF22403B),
    onPrimaryContainer = Color(0xFFADDCD3),
    secondary = EmeraldDark,
    onSecondary = Color(0xFF02261A),
    secondaryContainer = Color(0xFF22403B),
    onSecondaryContainer = Color(0xFFADDCD3),
    tertiary = Indigo400,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF181817),
    onBackground = Color(0xFFEBE8E3),
    surface = Color(0xFF222120),
    onSurface = Color(0xFFEBE8E3),
    surfaceVariant = Color(0xFF2B2A28),
    onSurfaceVariant = Color(0xFFA3A09A),
    surfaceContainerLowest = Color(0xFF181817),
    surfaceContainerLow = Color(0xFF1E1D1C),
    surfaceContainer = Color(0xFF222120),
    surfaceContainerHigh = Color(0xFF2B2A28),
    surfaceContainerHighest = Color(0xFF343230),
    outline = Color(0xFF45433F),
    outlineVariant = Color(0xFF343230),
    error = Color(0xFFDE6F72),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4A1F20),
    onErrorContainer = Color(0xFFFDE2E2)
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
    val heading: Color,
    /** Shadows: warm stone in light, plain black in dark (never coloured light) */
    val shadow: Color,
    /** Hair-thin borders of cards and inputs */
    val hairline: Color,
    /** Tinted pill behind the active navigation item and its icon */
    val navPill: Color,
    val navPillContent: Color,
    /** Translucent fill of the frosted bars (header, navigation) */
    val frost: Color,
    /** RGB of the brand teal for soft tints */
    val brand: Color
)

private fun diagonal(from: Color, to: Color) = Brush.linearGradient(listOf(from, to), start = Offset.Zero, end = Offset.Infinite)
private fun horizontal(from: Color, to: Color) = Brush.horizontalGradient(listOf(from, to))

private val LightExtra = AgoraExtraColors(
    success = Color(0xFF0F9B6C), warning = Color(0xFFE0900F), danger = Color(0xFFE5484D), duty = Indigo500,
    heroGradient = diagonal(Color(0xFF0E9F96), Color(0xFF17AD80)),
    eventGradient = diagonal(Lagoon, LagoonDeep),
    pinnedGradient = diagonal(Color(0xFF6366F1), Color(0xFF7C3AED)),
    managerRing = diagonal(Color(0xFFEAB308), Color(0xFFEF4444)),
    brandGradient = horizontal(Color(0xFF0E9F96), Color(0xFF17AD80)),
    tabTrack = Color(0xFFF1EFEB),
    tabThumb = Color.White,
    surfaceAlt = Color(0xFFF1EFEB),
    navInactive = Color(0xFF6D6861),
    heading = Color(0xFF1A1917),
    shadow = Color(0xFF30261A),
    hairline = Color(0x1730261A),
    navPill = Color(0xFFDCF1EC),
    navPillContent = Color(0xFF0A5049),
    frost = Color(0xE0F5F4F1),
    brand = Lagoon
)

private val DarkExtra = LightExtra.copy(
    success = Color(0xFF5BB48B), warning = Color(0xFFD4A24E), danger = Color(0xFFDE6F72), duty = Indigo400,
    heroGradient = diagonal(Color(0xFF0F8E86), Color(0xFF159A72)),
    eventGradient = diagonal(LagoonDark, Color(0xFF268A80)),
    brandGradient = horizontal(Color(0xFF0F8E86), Color(0xFF159A72)),
    tabTrack = Color(0xFF2B2A28),
    tabThumb = Color(0xFF343230),
    surfaceAlt = Color(0xFF2B2A28),
    navInactive = Color(0xFFA3A09A),
    heading = Color(0xFFE4DED6),
    shadow = Color.Black,
    hairline = Color(0x12FFFCF5),
    navPill = Color(0xFF22403B),
    navPillContent = Color(0xFFADDCD3),
    frost = Color(0xE0181817),
    brand = LagoonDark
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

// Inter like the web app (bundled TTFs, SIL OFL - third_party/inter/OFL.txt); headings at 700, tighter
// tracking on large text. Sizes follow the web app on a phone (1rem = 16px).
private val Inter = FontFamily(
    Font(R.font.inter_400regular, FontWeight.Normal),
    Font(R.font.inter_500medium, FontWeight.Medium),
    Font(R.font.inter_600semibold, FontWeight.SemiBold),
    Font(R.font.inter_700bold, FontWeight.Bold),
    Font(R.font.inter_800extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_800extrabold, FontWeight.Black)
)

private val AgoraTypography = Typography().let { base ->
    fun TextStyle.inter() = copy(fontFamily = Inter)
    base.copy(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter().copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
        headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.6).sp),
        headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
        titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),
        titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp),
        titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.inter().copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.inter().copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.inter().copy(fontSize = 12.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.inter().copy(fontSize = 11.sp)
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
