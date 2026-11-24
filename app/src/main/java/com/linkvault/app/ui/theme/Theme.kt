package com.linkvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkvault.app.data.ThemeMode

/**
 * Accent colours of the neumorphic look. The names are kept from the old theme so every screen that
 * used them (stat cards, category tiles, pinned folders) picks up the new soft palette automatically.
 * `lime` is now the main blue accent; `ink` is the dark text colour used on the pastel tiles.
 */
object Neon {
    val lime = Color(0xFF6F9CF5)   // main accent (blue)
    val green = Color(0xFF6FCDA0)  // mint
    val coral = Color(0xFFF59A86)  // soft coral
    val teal = Color(0xFF9D8CF2)   // soft violet
    val ink = Color(0xFF1F2733)
}

/** Light and dark shadow pair used to draw the raised / pressed-in surfaces. */
@Immutable
class NeuPalette(val highlight: Color, val shadow: Color, val shadowSolid: Color, val dark: Boolean)

val LocalNeu = staticCompositionLocalOf {
    NeuPalette(Color.White, Color(0x55A3B1C6), Color(0xFFA3B1C6), false)
}

private val LightNeu = NeuPalette(Color(0xFFFFFFFF), Color(0x80A3B1C6), Color(0xFFA3B1C6), false)
private val DarkNeu = NeuPalette(Color(0xFF30363F), Color(0xCC14171C), Color(0xFF14171C), true)

// Soft grey canvas: every surface shares the background colour and gets its depth from shadows.
private val LightColors = lightColorScheme(
    primary = Neon.lime, onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FD), onPrimaryContainer = Color(0xFF1B2B4D),
    secondary = Color(0xFF7A8394), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3EC), onSecondaryContainer = Color(0xFF39414F),
    background = Color(0xFFE8ECF1), onBackground = Color(0xFF39414F),
    surface = Color(0xFFE8ECF1), onSurface = Color(0xFF39414F),
    surfaceVariant = Color(0xFFDFE4EC), onSurfaceVariant = Color(0xFF7A8394),
    surfaceContainer = Color(0xFFE8ECF1), surfaceContainerHigh = Color(0xFFE1E6EE),
    surfaceContainerLow = Color(0xFFEBEFF4), surfaceContainerHighest = Color(0xFFDCE2EA),
    outline = Color(0xFF9AA3B2), outlineVariant = Color(0xFFD3D9E3),
    error = Color(0xFFD9503F), onError = Color.White,
    errorContainer = Color(0xFFFFDAD3), onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA6FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF2B3A5C), onPrimaryContainer = Color(0xFFD7E3FD),
    secondary = Color(0xFF9AA3B2), onSecondary = Color(0xFF1F2733),
    secondaryContainer = Color(0xFF2E343D), onSecondaryContainer = Color(0xFFE6EAF0),
    background = Color(0xFF23272E), onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF23272E), onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1E2227), onSurfaceVariant = Color(0xFF9AA3B2),
    surfaceContainer = Color(0xFF23272E), surfaceContainerHigh = Color(0xFF292E36),
    surfaceContainerLow = Color(0xFF20242A), surfaceContainerHighest = Color(0xFF2F353E),
    outline = Color(0xFF6B7482), outlineVariant = Color(0xFF343A44),
    error = Color(0xFFFF8F7E), onError = Color(0xFF1F2733),
    errorContainer = Color(0xFF5A1F14), onErrorContainer = Color(0xFFFFDAD3)
)

/** Rounder than before: 14 for small controls, 22 for cards and sheets. */
object Radius {
    val small = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}

/** Rounded card with a raised tab on the left and a soft step down on the right, like a file folder. */
class FolderShape(
    private val corner: Dp = 22.dp,
    private val tabFraction: Float = 0.6f,
    private val step: Dp = 16.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(size.minDimension / 2f)
        val s = with(density) { step.toPx() }.coerceAtMost(size.height / 4f)
        val w = size.width
        val h = size.height
        val tabW = w * tabFraction
        val path = Path().apply {
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(tabW - s, 0f)
            cubicTo(tabW, 0f, tabW, s, tabW + s, s)
            lineTo(w - r, s)
            arcTo(Rect(w - 2 * r, s, w, s + 2 * r), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Global shape scale: text fields, dialogs, sheets, menus and cards all inherit these. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
)

@Composable
fun LinkVaultTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalNeu provides (if (dark) DarkNeu else LightNeu)) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
