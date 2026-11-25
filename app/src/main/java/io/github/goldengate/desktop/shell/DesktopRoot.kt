package io.github.goldengate.desktop.shell

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.Appearance
import io.github.goldengate.desktop.core.Bounds
import io.github.goldengate.desktop.core.DockPosition
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.SystemSettings
import io.github.goldengate.desktop.glass.Accent
import io.github.goldengate.desktop.glass.Backdrops
import io.github.goldengate.desktop.glass.DeskColors
import io.github.goldengate.desktop.glass.DeskTheme
import io.github.goldengate.desktop.glass.DeskType
import io.github.goldengate.desktop.glass.GlassConfig
import io.github.goldengate.desktop.glass.GlassQuality
import io.github.goldengate.desktop.glass.LocalBackdrops
import io.github.goldengate.desktop.glass.LocalRecordingInto
import io.github.goldengate.desktop.glass.GlassSource
import io.github.goldengate.desktop.glass.LocalDeskTheme
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.glass.Wallpaper
import io.github.goldengate.desktop.glass.WallpaperPainter
import io.github.goldengate.desktop.glass.backdropSource
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun themeFor(s: SystemSettings, systemDark: Boolean): DeskTheme {
    val dark = when (s.appearance) { Appearance.Light -> false; Appearance.Dark -> true; Appearance.Auto -> systemDark }
    val accentEnum = Accent.entries.getOrElse(s.accent) { Accent.Blue }
    val accent = if (dark) accentEnum.dark else accentEnum.light
    val colors = if (dark) DeskColors.dark(accent) else DeskColors.light(accent)
    return DeskTheme(
        colors, DeskType.create(colors.text),
        GlassConfig(s.glassTint, GlassQuality.entries.getOrElse(s.glassQuality) { GlassQuality.Full }, s.reduceTransparency, s.increaseContrast),
        s.reduceMotion,
    )
}

/** Automatic interface scale: phones get a denser desktop so more fits on screen. */
fun autoScale(widthDp: Float, heightDp: Float): Float {
    val shortest = minOf(widthDp, heightDp)
    return when {
        shortest < 480f -> 0.78f
        shortest < 600f -> 0.85f
        shortest < 840f -> 0.92f
        else -> 1f
    }
}

@Composable
fun DesktopRoot(desk: DesktopController) {
    val s = desk.settings
    val theme = themeFor(s, isSystemInDarkTheme())
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(LocalDesktop provides desk, LocalDeskTheme provides theme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val physW = maxWidth.value; val physH = maxHeight.value
            val scale = if (s.uiScale > 0f) s.uiScale else autoScale(physW, physH)
            val density = Density(baseDensity.density * scale, baseDensity.fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                Scene(desk, theme)
            }
        }
    }
}

@Composable
private fun Scene(desk: DesktopController, theme: DeskTheme) {
    val s = desk.settings
    val density = LocalDensity.current
    val desktopLayer = rememberGraphicsLayer()
    val wallpaperLayer = rememberGraphicsLayer()
    val backdrops = remember(desktopLayer, wallpaperLayer) { Backdrops(desktopLayer, wallpaperLayer) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var wallpaperDark by remember { mutableStateOf(true) }

    // Render the wallpaper off the main thread at screen resolution (and a tiny blurred copy).
    LaunchedEffect(rootSize, s.wallpaper, s.customWallpaper, theme.colors.dark) {
        if (rootSize.width <= 0) return@LaunchedEffect
        val w = rootSize.width; val h = rootSize.height
        val (full, low, lum) = withContext(Dispatchers.Default) {
            val bmp: Bitmap = s.customWallpaper?.let { WallpaperPainter.decodeCustom(it, w, h)?.let { b -> WallpaperPainter.centerCrop(b, w, h) } }
                ?: WallpaperPainter.paint(Wallpaper.byId(s.wallpaper), theme.colors.dark, w, h)
            val lowBmp = WallpaperPainter.lowRes(bmp)
            Triple(bmp.asImageBitmap(), lowBmp.asImageBitmap(), topLuminance(lowBmp))
        }
        wallpaper = full
        backdrops.wallpaperLowRes = low
        wallpaperDark = lum < 0.6f
    }

    CompositionLocalProvider(LocalBackdrops provides backdrops) {
        BoxWithConstraints(
            Modifier.fillMaxSize().onSizeChanged {
                rootSize = it
                backdrops.rootSize = it
                desk.rootSizePx = Rect(0f, 0f, it.width.toFloat(), it.height.toFloat())
            },
        ) {
            val wDp = maxWidth.value; val hDp = maxHeight.value
            // Work area for windows: below the menu bar, beside/above the Dock.
            val dockR = dockReservedDp(desk)
            val area = when (s.dockPosition) {
                DockPosition.Bottom -> Bounds(0f, Tokens.menuBarHeight.value, wDp, hDp - Tokens.menuBarHeight.value - dockR)
                DockPosition.Left -> Bounds(dockR, Tokens.menuBarHeight.value, wDp - dockR, hDp - Tokens.menuBarHeight.value)
                DockPosition.Right -> Bounds(0f, Tokens.menuBarHeight.value, wDp - dockR, hDp - Tokens.menuBarHeight.value)
            }
            LaunchedEffect(area) {
                desk.wm.area = area
                desk.wm.clampAll()
            }

            // Mission Control dims the desktop (beneath the windows) and shows the Spaces bar.
            val mission = desk.overlay == Overlay.MissionControl
            val mcDim by animateFloatAsState(if (mission) 1f else 0f, tween(if (s.reduceMotion) 0 else 250), label = "mcdim")

            // ---------------- Backdrop: wallpaper + desktop icons + windows
            Box(Modifier.fillMaxSize().backdropSource(desktopLayer)) {
                CompositionLocalProvider(LocalRecordingInto provides GlassSource.Desktop) {
                    Box(Modifier.fillMaxSize().backdropSource(wallpaperLayer)) {
                        CompositionLocalProvider(LocalRecordingInto provides GlassSource.Wallpaper) {
                            val wp = wallpaper
                            if (wp != null) Image(wp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Box(Modifier.fillMaxSize().background(Color(0xFF2B2140)))
                        }
                    }
                    DesktopSurface()
                    if (mcDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * mcDim)).desktopGesturesDismiss(desk).testTag("mission-control"))
                    WindowsLayer()
                }
            }


            MenuBar(onDarkWallpaper = wallpaperDark || theme.colors.dark && s.menuBarBackground, modifier = Modifier.align(Alignment.TopCenter))

            if (mission) Box(Modifier.align(Alignment.TopCenter)) { MissionControlBar() }

            Dock(Modifier.fillMaxSize())

            // ---------------- Overlays
            val ov = desk.overlay
            val panelOpen = ov == Overlay.ControlCenter || ov == Overlay.NotificationCenter || ov == Overlay.Spotlight || ov == Overlay.ForceQuit
            if (panelOpen) Box(Modifier.fillMaxSize().desktopGesturesDismiss(desk).testTag("overlay-scrim"))

            AnimatedVisibility(ov == Overlay.Launcher, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) { LauncherPanel() }
            }
            AnimatedVisibility(
                ov == Overlay.NotificationCenter, Modifier.align(Alignment.TopEnd),
                enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f)) { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
            ) { NotificationCenterPanel() }
            AnimatedVisibility(
                ov == Overlay.ControlCenter, Modifier.align(Alignment.TopEnd),
                enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeIn(),
                exit = scaleOut(targetScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeOut(),
            ) { ControlCenterPanel() }
            AnimatedVisibility(
                ov == Overlay.Spotlight, Modifier.align(Alignment.TopCenter).padding(top = (hDp * 0.2f).dp),
                enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 600f), initialScale = 0.94f) + fadeIn(),
                exit = fadeOut(),
            ) { SpotlightPanel() }
            if (ov is Overlay.AppSwitcher) Box(Modifier.align(Alignment.Center)) { AppSwitcherPanel(ov.index) }
            if (ov == Overlay.ForceQuit) Box(Modifier.align(Alignment.Center)) { ForceQuitPanel() }

            Box(Modifier.align(Alignment.TopEnd)) { NotificationBanners() }

            // Context menus & menu bar menus.
            desk.openMenu?.let { m ->
                Box(Modifier.fillMaxSize().desktopGesturesDismiss(desk).testTag("menu-scrim"))
                MenuPanel(m.entries, m.anchor, rootSize, onDismiss = { desk.closeMenus() })
            }

            if (desk.dialog != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).desktopGesturesDismiss(desk, closeDialog = false))
                Box(Modifier.align(Alignment.Center)) { DialogPanel() }
            }
            if (desk.hud != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)) { HudPanel() }

            // Night Shift: a warm multiply-like overlay (approximation; Android apps can't change display color temperature).
            if (s.nightShift) Box(Modifier.fillMaxSize().background(Color(0xFFFF9A3C).copy(alpha = 0.10f + 0.16f * s.nightShiftWarmth)))

            if (!s.setupComplete || desk.showSetupAssistant) SetupAssistant()
            if (ov == Overlay.Locked) LockScreen()
            if (ov == Overlay.Sleep) SleepScreen()

            // Virtual cursor for game controllers.
            desk.input.controllerCursor?.let { p -> ControllerCursor(p) }

            if (s.showFps) FpsMeter(Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

/** A full-screen transparent layer that dismisses menus/overlays when tapped. */
private fun Modifier.desktopGesturesDismiss(desk: DesktopController, closeDialog: Boolean = true): Modifier =
    this.desktopGestures(
        key = "dismiss-$closeDialog",
        onTap = { if (closeDialog) desk.dismissAll() },
        onSecondary = { if (closeDialog) desk.dismissAll() },
    )

@Composable
private fun ControllerCursor(p: Offset) {
    val density = LocalDensity.current
    Canvas(Modifier.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.size(22.dp).testTag("controller-cursor")) {
        val u = size.width / 22f
        val path = Path().apply {
            moveTo(1f * u, 1f * u); lineTo(1f * u, 17f * u); lineTo(5.5f * u, 13f * u); lineTo(8.5f * u, 20f * u)
            lineTo(11.5f * u, 18.8f * u); lineTo(8.6f * u, 12f * u); lineTo(14.5f * u, 12f * u); close()
        }
        drawPath(path, Color.Black)
        drawPath(path, Color.White, style = Stroke(1.4f * u))
    }
    @Suppress("UNUSED_EXPRESSION") density
}

@Composable
private fun FpsMeter(modifier: Modifier) {
    var fps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last == 0L) last = t
                frames++
                if (t - last >= 1_000_000_000L) { fps = frames; frames = 0; last = t }
            }
        }
    }
    Box(modifier.background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        DeskText("$fps fps", io.github.goldengate.desktop.glass.Desk.type.mono, color = Color.Green)
    }
}

private fun topLuminance(b: Bitmap): Float {
    val rows = (b.height * 0.06f).toInt().coerceAtLeast(1)
    var sum = 0.0; var n = 0
    for (y in 0 until rows) for (x in 0 until b.width) {
        val c = b.getPixel(x, y)
        val r = (c shr 16 and 0xFF) / 255.0; val g = (c shr 8 and 0xFF) / 255.0; val bl = (c and 0xFF) / 255.0
        sum += 0.2126 * r + 0.7152 * g + 0.0722 * bl; n++
    }
    return if (n == 0) 0f else (sum / n).toFloat()
}
