package io.github.goldengate.desktop.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.audio.UiSound
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.DockPosition
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.AppIcon
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** One slot in the Dock. */
sealed class DockItem(val key: String) {
    class App(val appId: String, val pinned: Boolean) : DockItem("app:$appId")
    class Minimized(val windowId: Int, val appId: String) : DockItem("min:$windowId")
    data object Separator : DockItem("sep")
    data object Trash : DockItem("trash")
}

fun dockItems(desk: DesktopController): List<DockItem> {
    val s = desk.settings
    val running = desk.runningApps()
    val list = mutableListOf<DockItem>()
    s.dockApps.forEach { list += DockItem.App(it, true) }
    val extra = running.filter { it !in s.dockApps }
    if (s.dockShowRecents && extra.isNotEmpty()) {
        list += DockItem.Separator
        extra.forEach { list += DockItem.App(it, false) }
    }
    list += DockItem.Separator
    desk.wm.windows.filter { it.minimized }.forEach { list += DockItem.Minimized(it.id, it.appId) }
    list += DockItem.Trash
    return list
}

/** Total Dock thickness (without magnification) used by the window manager for its work area. */
fun dockReservedDp(desk: DesktopController): Float =
    if (desk.settings.dockAutoHide) 4f else desk.settings.dockIconSize + 26f

@Composable
fun Dock(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val s = desk.settings
    val density = LocalDensity.current
    val vertical = s.dockPosition != DockPosition.Bottom
    val items = dockItems(desk)
    val gap = 6.dp
    val sepW = 13.dp
    val pad = 7.dp
    // Shrink tiles so the whole Dock always fits on narrow (phone / portrait) screens.
    val availDp = with(density) { (if (vertical) desk.rootSizePx.height - Tokens.menuBarHeight.roundToPx() else desk.rootSizePx.width).toDp() }.value - 16f
    val seps = items.count { it is DockItem.Separator }
    val tiles = items.size - seps
    val fitted = if (availDp > 0f && tiles > 0) (availDp - 2 * pad.value - seps * sepW.value - gap.value * (items.size - 1)) / tiles else s.dockIconSize
    // Bottom Docks shrink to fit the screen width; side Docks keep the size the user picked and
    // scroll instead, so the left/right Dock is just as resizable as the bottom one.
    val base = if (vertical) s.dockIconSize.dp else fitted.coerceIn(28f, s.dockIconSize).dp
    val maxScale = if (s.dockMagnification) s.dockMagnifyScale else 1f
    val dockScroll = rememberScrollState()
    val neededDp = base.value * tiles + sepW.value * seps + gap.value * (items.size - 1).coerceAtLeast(0) + 2 * pad.value
    // Only scroll when the chosen size really doesn't fit; scrolling clips magnification otherwise.
    val scrollable = vertical && availDp > 0f && neededDp > availDp

    // Pointer position along the dock axis (dp from dock start), or null when not hovering.
    var pointer by remember { mutableStateOf<Float?>(null) }
    var revealed by remember { mutableStateOf(!s.dockAutoHide) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragCross by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(s.dockAutoHide) { revealed = !s.dockAutoHide }
    // Scrolling a side Dock shouldn't leave a stale magnification focus.
    LaunchedEffect(dockScroll.value) { if (vertical) pointer = null }
    LaunchedEffect(pointer, s.dockAutoHide, desk.openMenu) {
        if (s.dockAutoHide && pointer == null && desk.openMenu == null) { delay(900); revealed = false }
    }

    // Base (un-magnified) centers along axis, in dp.
    val centers = remember(items, base) {
        var cur = pad.value
        items.map { item ->
            val w = if (item is DockItem.Separator) sepW.value else base.value
            val c = cur + w / 2f
            cur += w + gap.value
            c
        }
    }
    val range = base.value * 3.2f
    val scrollDp = if (vertical) with(density) { dockScroll.value.toDp().value } else 0f
    fun scaleFor(i: Int): Float {
        val p = (pointer ?: return 1f) + scrollDp
        if (items[i] is DockItem.Separator) return 1f
        val d = abs(p - centers[i])
        if (d > range) return 1f
        val f = (cos(d / range * PI) + 1.0) / 2.0
        return 1f + (maxScale - 1f) * f.toFloat()
    }

    val hide by animateFloatAsState(if (revealed) 0f else 1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "dockHide")
    val shift = with(density) { (base + 40.dp).toPx() } * hide

    Box(modifier, contentAlignment = when (s.dockPosition) {
        DockPosition.Bottom -> Alignment.BottomCenter
        DockPosition.Left -> Alignment.CenterStart
        DockPosition.Right -> Alignment.CenterEnd
    }) {
        // Reveal strip for auto-hide: hover, tap or swipe from the screen edge.
        if (s.dockAutoHide && !revealed) {
            Box(
                Modifier
                    .then(if (vertical) Modifier.fillMaxHeight().width(10.dp) else Modifier.fillMaxWidth().height(10.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.type == PointerEventType.Enter || ev.type == PointerEventType.Press || ev.type == PointerEventType.Move) revealed = true
                            }
                        }
                    }
                    .testTag("dock-reveal"),
            )
        }
        val bar: @Composable () -> Unit = {
            items.forEachIndexed { i, item ->
                val sc = scaleFor(i)
                when (item) {
                    DockItem.Separator -> Box(
                        Modifier.then(if (vertical) Modifier.width(base).height(sepW) else Modifier.width(sepW).height(base)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.then(if (vertical) Modifier.height(1.dp).fillMaxWidth(0.8f) else Modifier.width(1.dp).fillMaxHeight(0.8f)).background(Desk.colors.separator))
                    }
                    else -> DockSlot(
                        desk, item, base, sc, vertical, s.dockPosition,
                        dragging = dragKey == item.key, dragDelta = if (dragKey == item.key) dragDelta else 0f, dragCross = if (dragKey == item.key) dragCross else 0f,
                        onDragStart = { if (item is DockItem.App && item.pinned) { dragKey = item.key; dragDelta = 0f; dragCross = 0f } },
                        onDrag = { along, cross -> if (dragKey == item.key) { dragDelta += along; dragCross += cross } },
                        onDragEnd = {
                            if (dragKey == item.key && item is DockItem.App) {
                                val d = with(density) { dragDelta.toDp().value }
                                val crossDp = with(density) { dragCross.toDp().value }
                                val apps = desk.settings.dockApps.toMutableList()
                                val from = apps.indexOf(item.appId)
                                if (abs(crossDp) > base.value * 1.6f && from >= 0) {
                                    apps.removeAt(from)
                                    desk.play(UiSound.Trash)
                                } else if (from >= 0) {
                                    val to = (from + (d / (base.value + gap.value)).roundToInt()).coerceIn(0, apps.size - 1)
                                    apps.removeAt(from); apps.add(to, item.appId)
                                }
                                desk.updateSettings { it.copy(dockApps = apps) }
                            }
                            dragKey = null; dragDelta = 0f; dragCross = 0f
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    when (s.dockPosition) {
                        DockPosition.Bottom -> translationY = shift
                        DockPosition.Left -> translationX = -shift
                        DockPosition.Right -> translationX = shift
                    }
                }
                .padding(if (vertical) 6.dp else 0.dp, if (vertical) 0.dp else 6.dp)
                .pointerInput(items.size, base) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull() ?: continue
                            val along = (if (vertical) ch.position.y else ch.position.x) / density.density
                            val isMouse = ch.type == PointerType.Mouse || ch.type == PointerType.Stylus
                            when (ev.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    revealed = true
                                    if (isMouse || ch.pressed) pointer = along
                                }
                                PointerEventType.Press -> { revealed = true; pointer = along }
                                PointerEventType.Exit -> pointer = null
                                PointerEventType.Release -> if (!isMouse) pointer = null
                                else -> {}
                            }
                        }
                    }
                }
                .testTag("dock"),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // The glass platter stays at base height; icons magnify out of it (like macOS).
            GlassSurface(
                Modifier.matchParentSize().matchParentSizeAligned(vertical, base + pad * 2, s.dockPosition),
                cornerRadius = Tokens.dockRadius, material = GlassMaterial.Clear, elevation = 14.dp,
            )
            if (vertical) Column(
                Modifier.fillMaxHeight().then(if (scrollable) Modifier.verticalScroll(dockScroll) else Modifier)
                    .padding(vertical = pad).padding(horizontal = pad),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = if (s.dockPosition == DockPosition.Left) Alignment.Start else Alignment.End,
            ) { bar() }
            else Row(
                Modifier.padding(horizontal = pad).padding(vertical = pad),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) { bar() }
            DockResizeGrip(desk, vertical, s.dockPosition)
        }
    }
}

/**
 * Drag the Dock's inner edge to resize it. Works for the bottom, left and right Dock;
 * the size is persisted in Settings ▸ Desktop & Dock.
 */
@Composable
private fun BoxScope.DockResizeGrip(desk: DesktopController, vertical: Boolean, pos: DockPosition) {
    val density = LocalDensity.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        Modifier
            .align(
                if (vertical) (if (pos == DockPosition.Left) Alignment.CenterEnd else Alignment.CenterStart)
                else Alignment.TopCenter,
            )
            .then(if (vertical) Modifier.fillMaxHeight().width(8.dp) else Modifier.fillMaxWidth().height(8.dp))
            .hoverable(src)
            .pointerHoverIcon(PointerIcon(if (vertical) android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW else android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW))
            .pointerInput(pos) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val delta = when (pos) {
                        DockPosition.Bottom -> -drag.y
                        DockPosition.Left -> drag.x
                        DockPosition.Right -> -drag.x
                    } / density.density
                    desk.updateSettings { st -> st.copy(dockIconSize = (st.dockIconSize + delta).coerceIn(32f, 84f)) }
                }
            }
            .semantics { contentDescription = "Resize Dock" }
            .testTag("dock-resize"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = if (hovered) 0.65f else 0.16f))
                .then(if (vertical) Modifier.width(2.5.dp).height(38.dp) else Modifier.height(2.5.dp).width(38.dp)),
        )
    }
}

/** The glass platter spans the full length but only the base thickness on the screen-edge side. */
private fun Modifier.matchParentSizeAligned(vertical: Boolean, thickness: Dp, pos: DockPosition): Modifier =
    this.then(DockPlatterModifier(vertical, thickness, pos))

private class DockPlatterModifier(val vertical: Boolean, val thickness: Dp, val pos: DockPosition) : androidx.compose.ui.layout.LayoutModifier {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurable: androidx.compose.ui.layout.Measurable,
        constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val t = thickness.roundToPx()
        val w = if (vertical) t else constraints.maxWidth
        val h = if (vertical) constraints.maxHeight else t
        val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
        val fullW = if (constraints.hasBoundedWidth) constraints.maxWidth else w
        val fullH = if (constraints.hasBoundedHeight) constraints.maxHeight else h
        return layout(fullW, fullH) {
            val x = when { !vertical -> 0; pos == DockPosition.Left -> 0; else -> fullW - w }
            val y = if (vertical) 0 else fullH - h
            p.place(x, y)
        }
    }
}

@Composable
private fun DockSlot(
    desk: DesktopController,
    item: DockItem,
    base: Dp,
    scale: Float,
    vertical: Boolean,
    pos: DockPosition,
    dragging: Boolean,
    dragDelta: Float,
    dragCross: Float,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val animScale by animateFloatAsState(scale, spring(dampingRatio = 0.8f, stiffness = 900f), label = "mag")
    val size = base * animScale
    val appId = when (item) {
        is DockItem.App -> item.appId
        is DockItem.Minimized -> item.appId
        DockItem.Trash -> if (desk.trashCount > 0) AppIds.TRASH_FULL else AppIds.TRASH
        else -> ""
    }
    val running = item is DockItem.App && desk.wm.windows.any { it.appId == item.appId }
    val name = when (item) {
        is DockItem.Minimized -> desk.wm.byId(item.windowId)?.title ?: AppRegistry.name(appId)
        else -> AppRegistry.name(appId)
    }
    // Launch bounce: two hops, like the classic Dock (respecting Reduce Motion).
    val bounce = remember { Animatable(0f) }
    val bounceAt = if (item is DockItem.App) desk.dockBounce[item.appId] else null
    LaunchedEffect(bounceAt) {
        if (bounceAt != null && System.currentTimeMillis() - bounceAt < 1500 && !desk.settings.reduceMotion) {
            repeat(2) {
                bounce.animateTo(1f, tween(170))
                bounce.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 700f))
            }
        }
    }
    var hovered by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bouncePx = with(density) { (base * 0.45f).toPx() } * bounce.value

    Box(
        Modifier.then(if (vertical) Modifier.height(size).width(size) else Modifier.width(size).height(size)),
        contentAlignment = Alignment.Center,
    ) {
        // Tooltip with the app name while hovering (mouse) — shown above/beside the icon.
        if (hovered && !dragging) {
            val off = with(density) { (size + 10.dp).roundToPx() }
            Box(Modifier.requiredSize(0.dp).offset { when (pos) {
                DockPosition.Bottom -> IntOffset(0, -off / 2 - 18)
                DockPosition.Left -> IntOffset(off, 0)
                DockPosition.Right -> IntOffset(-off, 0)
            } }, contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.requiredSize(width = (name.length * 7 + 26).dp, height = 24.dp), cornerRadius = 8.dp, material = GlassMaterial.Menu) {
                    DeskText(name, Desk.type.caption, color = Desk.colors.text, maxLines = 1, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        Box(
            Modifier
                .size(size)
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    origin = p
                    val c = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                    when (item) {
                        is DockItem.App -> desk.dockIconCenters[item.appId] = c
                        is DockItem.Minimized -> desk.dockIconCenters["win:${item.windowId}"] = c
                        DockItem.Trash -> desk.dockIconCenters[AppIds.TRASH] = c
                        else -> {}
                    }
                }
                .graphicsLayer {
                    val (along, cross) = dragDelta to dragCross
                    when (pos) {
                        DockPosition.Bottom -> { translationY = -bouncePx + cross; translationX = along }
                        DockPosition.Left -> { translationX = bouncePx + cross; translationY = along }
                        DockPosition.Right -> { translationX = -bouncePx + cross; translationY = along }
                    }
                    if (dragging) { alpha = 0.85f; scaleX = 1.06f; scaleY = 1.06f }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val t = ev.changes.firstOrNull()?.type
                            if (t == PointerType.Mouse) {
                                if (ev.type == PointerEventType.Enter) hovered = true
                                if (ev.type == PointerEventType.Exit || ev.type == PointerEventType.Press) hovered = false
                            }
                        }
                    }
                }
                .pointerInput(item.key) {
                    // Only mouse drags reorder; touch drags slide the magnification instead.
                    var mouseDrag = false
                    detectDragGestures(
                        onDragStart = { mouseDrag = desk.input.lastPointerWasMouse; if (mouseDrag) onDragStart() },
                        onDragEnd = { if (mouseDrag) onDragEnd() },
                        onDragCancel = { if (mouseDrag) onDragEnd() },
                    ) { ch, amt ->
                        if (mouseDrag) {
                            ch.consume()
                            if (vertical) onDrag(amt.y, amt.x) else onDrag(amt.x, amt.y)
                        }
                    }
                }
                .desktopGestures(
                    key = item.key,
                    onTap = { onDockTap(desk, item) },
                    onSecondary = { p -> desk.showContextMenu(p, dockMenu(desk, item)) },
                )
                .semantics { contentDescription = name }
                .testTag("dock-${item.key}"),
        ) {
            AppIcon(appId, size, dark = Desk.colors.dark)
            if (item is DockItem.Minimized) {
                Box(Modifier.align(Alignment.BottomEnd).size(size * 0.42f).clip(RoundedCornerShape(30)).background(Color.White.copy(alpha = 0.9f)).padding(2.dp)) {
                    AppIcon(appId, size * 0.38f)
                }
            }
        }
        if (running) {
            val dot: Modifier = Modifier.size(4.dp).clip(CircleShape).background(if (Desk.colors.dark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.7f))
            when (pos) {
                DockPosition.Bottom -> Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).then(dot))
                DockPosition.Left -> Box(Modifier.align(Alignment.CenterStart).offset(x = (-6).dp).then(dot))
                DockPosition.Right -> Box(Modifier.align(Alignment.CenterEnd).offset(x = 6.dp).then(dot))
            }
        }
    }
}

private fun onDockTap(desk: DesktopController, item: DockItem) {
    desk.closeMenus()
    when (item) {
        is DockItem.App -> {
            val wins = desk.wm.windows.filter { it.appId == item.appId }
            if (wins.isNotEmpty() && wins.all { it.minimized }) {
                desk.play(UiSound.Pop)
                desk.wm.focus(wins.last().id)
            } else desk.launch(item.appId)
        }
        is DockItem.Minimized -> { desk.play(UiSound.Pop); desk.wm.focus(item.windowId) }
        DockItem.Trash -> desk.launch(AppIds.TRASH)
        else -> {}
    }
}

fun dockMenu(desk: DesktopController, item: DockItem) = entries {
    when (item) {
        is DockItem.App -> {
            val wins = desk.wm.windows.filter { it.appId == item.appId }
            if (wins.isNotEmpty()) {
                header("Windows")
                wins.forEach { w -> item(w.title.ifBlank { AppRegistry.name(w.appId) }, checked = desk.wm.focusedId == w.id) { desk.wm.focus(w.id) } }
                separator()
            }
            if (AppRegistry.get(item.appId)?.singleWindow == false) item("New Window") { desk.launch(item.appId, newWindow = true) }
            if (wins.isEmpty()) item("Open") { desk.launch(item.appId) }
            separator()
            submenu("Options") {
                val pinned = item.appId in desk.settings.dockApps
                item("Keep in Dock", checked = pinned) {
                    desk.updateSettings { s -> s.copy(dockApps = if (pinned) s.dockApps - item.appId else s.dockApps + item.appId) }
                }
                if (pinned) {
                    item("Move Left") { moveDockApp(desk, item.appId, -1) }
                    item("Move Right") { moveDockApp(desk, item.appId, 1) }
                }
            }
            if (item.pinned) item("Remove from Dock") { desk.play(UiSound.Trash); desk.updateSettings { s -> s.copy(dockApps = s.dockApps - item.appId) } }
            if (wins.isNotEmpty()) {
                separator()
                item("Hide Others") { wins.lastOrNull()?.let { desk.wm.focus(it.id) }; desk.hideOthers() }
                item("Quit") { desk.quit(item.appId) }
            }
        }
        is DockItem.Minimized -> {
            item("Restore Window") { desk.wm.focus(item.windowId) }
            item("Close Window") { desk.close(item.windowId) }
        }
        DockItem.Trash -> {
            item("Open") { desk.launch(AppIds.TRASH) }
            separator()
            item("Empty Trash", enabled = desk.trashCount > 0) { desk.confirmEmptyTrash() }
        }
        else -> {}
    }
    separator()
    submenu("Dock") {
        item("Turn Magnification ${if (desk.settings.dockMagnification) "Off" else "On"}") { desk.updateSettings { it.copy(dockMagnification = !it.dockMagnification) } }
        item("Turn Hiding ${if (desk.settings.dockAutoHide) "Off" else "On"}") { desk.updateSettings { it.copy(dockAutoHide = !it.dockAutoHide) } }
        header("Position on Screen")
        DockPosition.entries.forEach { p -> item(p.name, checked = desk.settings.dockPosition == p) { desk.updateSettings { it.copy(dockPosition = p) } } }
        separator()
        item("Dock Settings…") { desk.launch(AppIds.SETTINGS, "dock") }
    }
}

private fun moveDockApp(desk: DesktopController, appId: String, dir: Int) {
    val apps = desk.settings.dockApps.toMutableList()
    val i = apps.indexOf(appId)
    if (i < 0) return
    val j = (i + dir).coerceIn(0, apps.size - 1)
    apps.removeAt(i); apps.add(j, appId)
    desk.updateSettings { it.copy(dockApps = apps) }
}

@Suppress("unused")
private fun BoxScope.unusedScope() = Unit
