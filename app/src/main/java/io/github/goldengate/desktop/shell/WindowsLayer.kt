package io.github.goldengate.desktop.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.apps.AppContent
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.Bounds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.MinimizeEffect
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders every window. Windows are never removed from composition while they exist (even when
 * minimized or on another Space), so their in-app state (scroll position, text, web page…) is
 * preserved exactly like a real desktop.
 */
@Composable
fun WindowsLayer(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val wm = desk.wm
    val mission = desk.overlay == Overlay.MissionControl
    val reduce = desk.settings.reduceMotion
    val spaceAnim by animateFloatAsState(
        wm.currentSpace.toFloat(),
        if (reduce) tween(0) else spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "space",
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenW = maxWidth.value
        val layout = if (mission) missionLayout(wm.visibleOrdered(), wm.area) else emptyMap()

        // Live snap preview (glass) while dragging a window to an edge.
        desk.snapPreview?.let { b ->
            GlassSurface(
                Modifier.offset(b.x.dp + 4.dp, b.y.dp + 4.dp).size((b.w - 8f).dp, (b.h - 8f).dp),
                cornerRadius = Tokens.windowRadius, material = GlassMaterial.Clear,
            )
        }
        for (win in wm.windows.toList()) {
            key(win.id) {
                WindowFrame(win, screenW, spaceAnim, layout[win.id])
            }
        }
    }
}

/** Mission Control: arrange windows in a grid that preserves aspect ratios. */
fun missionLayout(windows: List<DeskWindow>, area: Bounds): Map<Int, Bounds> {
    if (windows.isEmpty()) return emptyMap()
    val top = area.y + 96f // leave room for the Spaces bar
    val availW = area.w - 60f
    val availH = area.bottom - top - 30f
    val n = windows.size
    val cols = max(1, ceil(sqrt(n * availW / availH.coerceAtLeast(1f) / 1.4f)).toInt()).coerceAtMost(n)
    val rows = ceil(n / cols.toFloat()).toInt()
    val cellW = availW / cols
    val cellH = availH / rows
    val sorted = windows.sortedBy { it.x }
    return sorted.mapIndexed { i, w ->
        val c = i % cols; val r = i / cols
        val s = min((cellW - 28f) / w.w, (cellH - 40f) / w.h).coerceAtMost(0.8f).coerceAtLeast(0.05f)
        val bw = w.w * s; val bh = w.h * s
        w.id to Bounds(area.x + 30f + c * cellW + (cellW - bw) / 2f, top + r * cellH + (cellH - 24f - bh) / 2f, bw, bh)
    }.toMap()
}

@Composable
private fun WindowFrame(win: DeskWindow, screenW: Float, spaceAnim: Float, missionTarget: Bounds?) {
    val desk = LocalDesktop.current
    val density = LocalDensity.current
    val c = Desk.colors
    val active = desk.wm.focusedId == win.id && desk.overlay != Overlay.MissionControl
    val reduce = desk.settings.reduceMotion
    val spec = AppRegistry.get(win.appId)

    // Open animation.
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    // Close animation.
    val closeP by animateFloatAsState(if (win.closing) 1f else 0f, tween(if (reduce) 0 else 170), label = "close")
    // Minimize (genie approximation): 0 = normal, 1 = in the Dock.
    val minP by animateFloatAsState(
        if (win.minimized) 1f else 0f,
        if (reduce) tween(0) else tween(380, easing = FastOutSlowInEasing), label = "min",
    )
    // Mission Control interpolation.
    val mcP by animateFloatAsState(if (missionTarget != null) 1f else 0f, if (reduce) tween(0) else spring(dampingRatio = 0.86f, stiffness = 300f), label = "mc")
    val target = missionTarget ?: win.bounds
    val radius = if (win.zoomed) Tokens.windowRadius else Tokens.windowRadius
    val shape = RoundedCornerShape(radius)
    val elevation = if (active) 30.dp else 12.dp
    val dockTarget = desk.dockIconCenters["win:${win.id}"] ?: desk.dockIconCenters[win.appId] ?: desk.dockIconCenters["trash"]

    Box(
        Modifier
            .offset { IntOffset((win.x * density.density).roundToInt(), (win.y * density.density).roundToInt()) }
            .size(win.w.dp, win.h.dp)
            .graphicsLayer {
                val wPx = win.w * density.density; val hPx = win.h * density.density
                transformOrigin = TransformOrigin(0f, 0f)
                // Base: Space offset.
                var tx = (win.space - spaceAnim) * screenW * density.density
                var ty = 0f
                var sx = 1f; var sy = 1f
                // Mission Control.
                if (mcP > 0f) {
                    val s = target.w / win.w
                    val ms = 1f + (s - 1f) * mcP
                    sx *= ms; sy *= ms
                    tx += (target.x - win.x) * density.density * mcP
                    ty += (target.y - win.y) * density.density * mcP
                }
                // Open / close.
                val oa = 0.9f + 0.1f * appear.value - 0.06f * closeP
                if (oa != 1f) {
                    tx += wPx * (1f - oa) / 2f; ty += hPx * (1f - oa) / 2f
                    sx *= oa; sy *= oa
                }
                alpha = (appear.value.coerceIn(0f, 1f) * (1f - closeP)) * (1f - minP * 0.6f)
                // Minimize: shrink toward the Dock icon; Genie squeezes width faster than height.
                if (minP > 0f) {
                    val tgt = dockTarget ?: Offset(wPx / 2f, (desk.rootSizePx.height))
                    val startCx = win.x * density.density + wPx / 2f
                    val startBottom = win.y * density.density + hPx
                    val genie = desk.settings.minimizeEffect == MinimizeEffect.Genie
                    val sxm = 1f - minP * (if (genie) 0.97f else 0.93f)
                    val sym = 1f - (if (genie) minP * minP else minP) * 0.93f
                    sx *= sxm; sy *= sym
                    tx += (tgt.x - startCx) * minP + wPx * (1f - sxm) / 2f
                    ty += (tgt.y - startBottom) * minP + hPx * (1f - sym)
                }
                translationX = tx; translationY = ty
                scaleX = sx; scaleY = sy
            }
            .shadow(if (mcP > 0.5f) 18.dp else elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.windowBackground)
            .border(0.5.dp, if (c.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f), shape)
            .focusOnPress { if (!win.minimized) desk.wm.focus(win.id) }
            .testTag("window-${win.appId}"),
    ) {
        CompositionLocalProvider(LocalWindow provides win, LocalWindowActive provides active) {
            AppContent(win)
            if (!win.zoomed) ResizeHandles(win, spec?.minWidth ?: 320f, spec?.minHeight ?: 220f)
        }
        if (mcP > 0.01f) {
            // In Mission Control the window becomes a single big tap target.
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.001f))
                    .desktopGestures(key = "mc-${win.id}", onTap = {
                        desk.toggleOverlay(Overlay.MissionControl)
                        desk.wm.focus(win.id)
                    })
                    .testTag("mc-window-${win.appId}"),
            )
        }
    }
    if (mcP > 0.6f && missionTarget != null) {
        Box(
            Modifier.offset { IntOffset(((missionTarget.x) * density.density).roundToInt(), ((missionTarget.bottom + 8f) * density.density).roundToInt()) }
                .size(missionTarget.w.dp, 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(cornerRadius = 8.dp, material = GlassMaterial.Menu) {
                DeskText(win.title.ifBlank { spec?.name ?: "" }, Desk.type.caption, color = c.text, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

