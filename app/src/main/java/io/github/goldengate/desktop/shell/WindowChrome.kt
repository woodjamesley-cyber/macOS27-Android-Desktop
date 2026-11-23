package io.github.goldengate.desktop.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.SnapZone
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSource
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalWindow = staticCompositionLocalOf<DeskWindow?> { null }
val LocalWindowActive = staticCompositionLocalOf { true }

/**
 * Standard window layout used by every app: optional edge-to-edge glass sidebar (macOS 27),
 * a uniform toolbar holding the traffic lights, title and controls, and the content area.
 */
@Composable
fun WindowScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sidebar: (@Composable ColumnScope.() -> Unit)? = null,
    sidebarWidth: Dp = Tokens.sidebarWidth,
    toolbar: @Composable RowScope.() -> Unit = {},
    toolbarLeading: @Composable RowScope.() -> Unit = {},
    showTitle: Boolean = true,
    contentBackground: Color? = null,
    darkToolbar: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val win = LocalWindow.current
    val c = Desk.colors
    var sidebarOpen by remember { mutableStateOf(true) }
    val narrow = (win?.w ?: 900f) < 520f
    val showSidebar = sidebar != null && sidebarOpen && !narrow

    Row(modifier.fillMaxSize()) {
        AnimatedVisibility(showSidebar, enter = expandHorizontally(), exit = shrinkHorizontally()) {
            GlassSurface(
                Modifier.width(sidebarWidth).fillMaxHeight(),
                cornerRadius = 0.dp, material = GlassMaterial.Sidebar, source = GlassSource.Wallpaper, drawRim = false,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(Tokens.toolbarHeight).windowDragArea()) {
                        TrafficLights(Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
                    }
                    sidebar?.invoke(this)
                }
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(0.5.dp).background(c.separator))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(contentBackground ?: c.contentBackground)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Tokens.toolbarHeight)
                    .background(if (darkToolbar) Color(0xFF1E1E1E) else Color.Transparent)
                    .windowDragArea()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!showSidebar) {
                    TrafficLights(Modifier.padding(start = 6.dp, end = 10.dp))
                }
                if (sidebar != null) {
                    ToolbarButton(Glyph.Sidebar, { sidebarOpen = !sidebarOpen }, description = "Toggle Sidebar", enabled = !narrow)
                    Spacer(Modifier.width(4.dp))
                }
                toolbarLeading()
                if (showTitle) {
                    Column(Modifier.padding(horizontal = 6.dp)) {
                        DeskText(title, Desk.type.title.copy(fontSize = Desk.type.title.fontSize), color = if (LocalWindowActive.current) (if (darkToolbar) Color.White else c.text) else c.tertiaryText, maxLines = 1)
                        if (subtitle != null) DeskText(subtitle, Desk.type.caption, color = c.secondaryText, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                toolbar()
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Makes a region drag the window (with edge snapping), double-click to zoom, right-click for tiling. */
@Composable
fun Modifier.windowDragArea(): Modifier {
    val win = LocalWindow.current ?: return this
    val desk = LocalDesktop.current
    val density = LocalDensity.current
    val origin = remember { arrayOf(Offset.Zero) }
    var lastZone by remember { mutableStateOf(SnapZone.None) }
    return this
        .onGloballyPositioned { origin[0] = it.positionInRoot() }
        .pointerInput(win.id) {
            detectTapGestures(
                onPress = { desk.wm.focus(win.id) },
                onDoubleTap = { desk.wm.toggleZoom(win.id) },
                onLongPress = { pos -> desk.showContextMenu(origin[0] + pos, tilingMenu(desk, win)) },
            )
        }
        .pointerInput(win.id) {
            detectDragGestures(
                onDragStart = { desk.wm.focus(win.id); desk.closeMenus() },
                onDragEnd = {
                    if (lastZone != SnapZone.None) desk.wm.snap(win.id, lastZone)
                    lastZone = SnapZone.None
                    desk.snapPreview = null
                },
                onDragCancel = { lastZone = SnapZone.None; desk.snapPreview = null },
            ) { change, drag ->
                change.consume()
                desk.wm.move(win.id, drag.x / density.density, drag.y / density.density)
                val root = origin[0] + change.position
                val zone = desk.wm.zoneAt(root.x / density.density, root.y / density.density)
                if (zone != lastZone) {
                    lastZone = zone
                    desk.snapPreview = desk.wm.snapBoundsFor(zone)
                }
            }
        }
}

fun tilingMenu(desk: io.github.goldengate.desktop.core.DesktopController, win: DeskWindow) = entries {
    header("Move & Resize")
    item("Fill", glyph = Glyph.Expand) { desk.wm.snap(win.id, SnapZone.Fill) }
    item("Center", glyph = Glyph.Contract) { desk.wm.center(win.id) }
    separator()
    item("Left") { desk.wm.snap(win.id, SnapZone.Left) }
    item("Right") { desk.wm.snap(win.id, SnapZone.Right) }
    item("Top Left") { desk.wm.snap(win.id, SnapZone.TopLeft) }
    item("Top Right") { desk.wm.snap(win.id, SnapZone.TopRight) }
    item("Bottom Left") { desk.wm.snap(win.id, SnapZone.BottomLeft) }
    item("Bottom Right") { desk.wm.snap(win.id, SnapZone.BottomRight) }
    if (desk.wm.spaceCount > 1) {
        separator()
        for (i in 0 until desk.wm.spaceCount) if (i != win.space) item("Move to Desktop ${i + 1}") { desk.wm.moveToSpace(win.id, i) }
    }
}

/** Close / minimize / zoom buttons rendered as small glass beads that bounce when pressed. */
@Composable
fun TrafficLights(modifier: Modifier = Modifier) {
    val win = LocalWindow.current ?: return
    val desk = LocalDesktop.current
    val active = LocalWindowActive.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val showGlyphs = hovered || !desk.input.lastPointerWasMouse && active
    Row(modifier.hoverable(src).testTag("traffic-${win.id}"), verticalAlignment = Alignment.CenterVertically) {
        Light(Color(0xFFFF5F57), Color(0xFFE0443E), active || hovered, if (showGlyphs) Glyph.Close else null, "close-${win.id}") { desk.close(win.id) }
        Spacer(Modifier.width(Tokens.trafficLightGap))
        Light(Color(0xFFFEBC2E), Color(0xFFDEA123), active || hovered, if (showGlyphs) Glyph.Minus else null, "minimize-${win.id}") { desk.minimize(win.id) }
        Spacer(Modifier.width(Tokens.trafficLightGap))
        Light(Color(0xFF28C840), Color(0xFF1AAB29), active || hovered, if (showGlyphs) (if (win.zoomed) Glyph.Contract else Glyph.Expand) else null, "zoom-${win.id}",
            onSecondary = { pos -> desk.showContextMenu(pos, tilingMenu(desk, win)) }) { desk.wm.toggleZoom(win.id) }
    }
}

@Composable
private fun Light(color: Color, rim: Color, colored: Boolean, glyph: Glyph?, tag: String, onSecondary: ((Offset) -> Unit)? = null, onClick: () -> Unit) {
    val c = Desk.colors
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val base = if (colored) color else if (c.dark) Color(0xFF4A4A4E) else Color(0xFFD5D5D8)
    Box(
        Modifier
            .size(Tokens.trafficLight + 8.dp)
            .testTag(tag)
            .desktopGestures(
                key = tag,
                onPress = { scope.launch { scale.animateTo(0.82f, tween(70)) } },
                onTap = {
                    scope.launch { scale.animateTo(1f, spring(0.35f, Spring.StiffnessMedium)) }
                    onClick()
                },
                onSecondary = onSecondary,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Tokens.trafficLight)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(base.copy(alpha = 1f), base), center = Offset.Unspecified))
                .drawBehind {
                    // glass bead: top highlight + darker rim
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (colored) 0.55f else 0.35f), Color.Transparent), endY = size.height * 0.6f), radius = size.minDimension * 0.42f, center = Offset(size.width / 2, size.height * 0.42f))
                    drawCircle(if (colored) rim else Color(0x33000000), style = androidx.compose.ui.graphics.drawscope.Stroke(0.6.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null && colored) GlyphIcon(glyph, Color(0x99000000), 8.dp, weight = 3f)
        }
    }
}

/** Invisible resize handles along the window edges; larger on touch screens. */
@Composable
fun BoxScope.ResizeHandles(win: DeskWindow, minW: Float, minH: Float) {
    val desk = LocalDesktop.current
    val density = LocalDensity.current
    val edge = if (desk.input.lastPointerWasMouse) 6.dp else 12.dp
    val corner = if (desk.input.lastPointerWasMouse) 14.dp else 26.dp
    @Composable
    fun handle(mod: Modifier, edges: Int, icon: Int) {
        Box(
            mod
                .pointerHoverIcon(PointerIcon(icon))
                .pointerInput(win.id, edges) {
                    detectDragGestures(onDragStart = { desk.wm.focus(win.id) }) { ch, d ->
                        ch.consume()
                        desk.wm.resize(win.id, edges, d.x / density.density, d.y / density.density, minW, minH)
                    }
                },
        )
    }
    val h = android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
    val v = android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
    val d1 = android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
    val d2 = android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
    handle(Modifier.align(Alignment.CenterStart).width(edge).fillMaxHeight().padding(vertical = corner), 1, h)
    handle(Modifier.align(Alignment.CenterEnd).width(edge).fillMaxHeight().padding(vertical = corner), 4, h)
    handle(Modifier.align(Alignment.BottomCenter).height(edge).fillMaxWidth().padding(horizontal = corner), 8, v)
    handle(Modifier.align(Alignment.TopCenter).height(4.dp).fillMaxWidth().padding(horizontal = corner), 2, v)
    handle(Modifier.align(Alignment.BottomEnd).size(corner), 4 or 8, d1)
    handle(Modifier.align(Alignment.BottomStart).size(corner), 1 or 8, d2)
    handle(Modifier.align(Alignment.TopStart).size(corner / 2), 1 or 2, d1)
    handle(Modifier.align(Alignment.TopEnd).size(corner / 2), 4 or 2, d2)
}

/** Focus a window as soon as any pointer goes down inside it (before children handle it). */
fun Modifier.focusOnPress(onPress: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress()
    }
}

@Composable
fun WindowTitleText(text: String) {
    DeskText(text, Desk.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
}

