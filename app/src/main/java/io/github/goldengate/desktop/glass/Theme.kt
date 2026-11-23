package io.github.goldengate.desktop.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.R

/**
 * Design tokens for the desktop. Values were chosen from public macOS 26/27 screenshots and
 * Apple's Human Interface Guidelines (see docs/DESIGN_SPEC.md). Where exact numbers are not
 * public they are documented approximations.
 */
object Tokens {
    /** macOS 27 unified the window corner radius across apps (and made it tighter than Tahoe). */
    val windowRadius: Dp = 12.dp
    val menuRadius: Dp = 10.dp
    val menuItemRadius: Dp = 6.dp
    val dockRadius: Dp = 24.dp
    val notificationRadius: Dp = 20.dp
    val controlModuleRadius: Dp = 18.dp
    val menuBarHeight: Dp = 26.dp
    val toolbarHeight: Dp = 52.dp
    val sidebarWidth: Dp = 200.dp
    val trafficLight: Dp = 12.dp
    val trafficLightGap: Dp = 8.dp
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

enum class Accent(val label: String, val light: Color, val dark: Color) {
    Blue("Blue", Color(0xFF0A7AFF), Color(0xFF0A84FF)),
    Purple("Purple", Color(0xFF953DCB), Color(0xFFA550D9)),
    Pink("Pink", Color(0xFFF74F9E), Color(0xFFFF5AA9)),
    Red("Red", Color(0xFFE5383B), Color(0xFFFF453A)),
    Orange("Orange", Color(0xFFF7821B), Color(0xFFFF9F0A)),
    Yellow("Yellow", Color(0xFFE8B500), Color(0xFFFFD60A)),
    Green("Green", Color(0xFF34A853), Color(0xFF32D74B)),
    Graphite("Graphite", Color(0xFF8C8C8C), Color(0xFF98989D)),
}

/** Semantic color palette (light/dark). */
@Immutable
data class DeskColors(
    val dark: Boolean,
    val accent: Color,
    val text: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val windowBackground: Color,
    val contentBackground: Color,
    val separator: Color,
    val controlFill: Color,
    val controlFillPressed: Color,
    val selection: Color,
    val glassTintBase: Color,
    val shadow: Color,
) {
    companion object {
        fun light(accent: Color) = DeskColors(
            dark = false, accent = accent,
            text = Color(0xF0000000), secondaryText = Color(0x8C000000), tertiaryText = Color(0x5C000000),
            windowBackground = Color(0xFFF5F5F7), contentBackground = Color(0xFFFFFFFF),
            separator = Color(0x1A000000), controlFill = Color(0x0F000000), controlFillPressed = Color(0x24000000),
            selection = accent.copy(alpha = 0.22f), glassTintBase = Color(0xFFFAFAFC), shadow = Color(0x66000000),
        )

        fun dark(accent: Color) = DeskColors(
            dark = true, accent = accent,
            text = Color(0xF2FFFFFF), secondaryText = Color(0x99FFFFFF), tertiaryText = Color(0x5CFFFFFF),
            windowBackground = Color(0xFF242426), contentBackground = Color(0xFF1C1C1E),
            separator = Color(0x24FFFFFF), controlFill = Color(0x1AFFFFFF), controlFillPressed = Color(0x33FFFFFF),
            selection = accent.copy(alpha = 0.35f), glassTintBase = Color(0xFF1E1E22), shadow = Color(0xAA000000),
        )
    }
}

@Immutable
data class DeskType(
    val menu: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val small: TextStyle,
    val title: TextStyle,
    val largeTitle: TextStyle,
    val headline: TextStyle,
    val mono: TextStyle,
) {
    companion object {
        fun create(color: Color): DeskType {
            val base = TextStyle(fontFamily = InterFamily, color = color, letterSpacing = (-0.08).sp)
            return DeskType(
                menu = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                body = base.copy(fontSize = 13.sp),
                bodyStrong = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                caption = base.copy(fontSize = 11.sp),
                small = base.copy(fontSize = 10.sp),
                title = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                headline = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
                largeTitle = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = color),
            )
        }
    }
}

/** Liquid Glass configuration derived from user settings. */
@Immutable
data class GlassConfig(
    /** 0 = clearest, 1 = fully tinted (the macOS 27 Liquid Glass slider). */
    val tint: Float,
    val quality: GlassQuality,
    val reduceTransparency: Boolean,
    val increaseContrast: Boolean,
)

enum class GlassQuality(val label: String, val description: String) {
    Full("Full", "Live blur, refraction and dispersion (Android 13+ GPU shader)"),
    Balanced("Balanced", "Live background blur without refraction (Android 12+)"),
    Efficient("Efficient", "Pre-blurred wallpaper sampling; works on every device"),
}

@Immutable
data class DeskTheme(
    val colors: DeskColors,
    val type: DeskType,
    val glass: GlassConfig,
    val reduceMotion: Boolean,
)

val LocalDeskTheme = staticCompositionLocalOf {
    val c = DeskColors.light(Accent.Blue.light)
    DeskTheme(c, DeskType.create(c.text), GlassConfig(0.35f, GlassQuality.Full, false, false), false)
}

object Desk {
    val theme: DeskTheme @Composable get() = LocalDeskTheme.current
    val colors: DeskColors @Composable get() = LocalDeskTheme.current.colors
    val type: DeskType @Composable get() = LocalDeskTheme.current.type
}
