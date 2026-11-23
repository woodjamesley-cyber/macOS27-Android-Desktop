package io.github.goldengate.desktop.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min

/** Window bounds in dp. */
data class Bounds(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    fun contains(px: Float, py: Float) = px >= x && px <= right && py >= y && py <= bottom
}

enum class SnapZone { None, Left, Right, Fill, TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * A desktop window. All mutable fields are Compose snapshot state so the UI recomposes when
 * they change. The window's content stays composed while it is minimized or on another Space,
 * which is how every app keeps its state when you switch away from it.
 */
@Stable
class DeskWindow(
    val id: Int,
    val appId: String,
    title: String,
    bounds: Bounds,
    space: Int,
    val argument: String? = null,
) {
    var title by mutableStateOf(title)
    var x by mutableFloatStateOf(bounds.x)
    var y by mutableFloatStateOf(bounds.y)
    var w by mutableFloatStateOf(bounds.w)
    var h by mutableFloatStateOf(bounds.h)
    var minimized by mutableStateOf(false)
    var zoomed by mutableStateOf(false)
    var space by mutableStateOf(space)
    var restore: Bounds? = null
    var closing by mutableStateOf(false)
    /** Menus contributed by the app content for the menu bar (File/Edit/View/…). */
    var menus by mutableStateOf<List<MenuDef>>(emptyList())
    /** Set by the app to handle ⌘N / ⌘F etc. inside the window. */
    var documentEdited by mutableStateOf(false)

    val bounds get() = Bounds(x, y, w, h)
    fun setBounds(b: Bounds) { x = b.x; y = b.y; w = b.w; h = b.h }
}

/**
 * Pure window-management logic (z-order, focus, geometry, snapping, Spaces). Kept free of
 * Android dependencies so it is covered by local JVM unit tests.
 */
class WindowManager {
    val windows = mutableStateListOf<DeskWindow>()
    var focusedId by mutableStateOf<Int?>(null)
        private set
    var currentSpace by mutableStateOf(0)
    var spaceCount by mutableStateOf(2)

    /** Usable desktop area (dp): below the menu bar, above the Dock. */
    var area by mutableStateOf(Bounds(0f, 26f, 1280f, 720f))

    private var nextId = 1

    val focused: DeskWindow? get() = windows.firstOrNull { it.id == focusedId }

    fun open(appId: String, title: String, w: Float, h: Float, argument: String? = null, at: Bounds? = null): DeskWindow {
        val a = area
        val ww = min(w, a.w - 16f).coerceAtLeast(160f)
        val hh = min(h, a.h - 16f).coerceAtLeast(120f)
        val cascade = (windows.count { it.space == currentSpace && !it.minimized } % 6) * 26f
        val bx = at?.x ?: (a.x + (a.w - ww) / 2f - 60f + cascade).coerceIn(a.x, max(a.x, a.right - ww))
        val by = at?.y ?: (a.y + (a.h - hh) / 2.6f + cascade).coerceIn(a.y, max(a.y, a.bottom - hh))
        val win = DeskWindow(nextId++, appId, title, Bounds(bx, by, at?.w ?: ww, at?.h ?: hh), currentSpace, argument)
        windows.add(win)
        focus(win.id)
        return win
    }

    fun byId(id: Int) = windows.firstOrNull { it.id == id }

    fun focus(id: Int?) {
        if (id == null) { focusedId = null; return }
        val win = byId(id) ?: return
        val idx = windows.indexOf(win)
        if (idx != windows.lastIndex) {
            windows.removeAt(idx)
            windows.add(win)
        }
        if (win.space != currentSpace) currentSpace = win.space
        win.minimized = false
        focusedId = id
    }

    fun close(id: Int) {
        val win = byId(id) ?: return
        windows.remove(win)
        if (focusedId == id) focusTopmost()
    }

    fun focusTopmost() {
        focusedId = windows.lastOrNull { it.space == currentSpace && !it.minimized }?.id
    }

    fun minimize(id: Int) {
        val win = byId(id) ?: return
        win.minimized = true
        if (focusedId == id) focusTopmost()
    }

    fun move(id: Int, dx: Float, dy: Float) {
        val win = byId(id) ?: return
        if (win.zoomed) unzoomForDrag(win)
        val a = area
        win.x = (win.x + dx).coerceIn(a.x - win.w + 80f, a.right - 80f)
        win.y = (win.y + dy).coerceIn(a.y, a.bottom + 40f - 40f.coerceAtMost(win.h))
    }

    private fun unzoomForDrag(win: DeskWindow) {
        val r = win.restore
        win.zoomed = false
        if (r != null) {
            win.w = r.w; win.h = r.h
        }
        win.restore = null
    }

    /** Resize by dragging an edge. edges: bitmask L=1, T=2, R=4, B=8. */
    fun resize(id: Int, edges: Int, dx: Float, dy: Float, minW: Float, minH: Float) {
        val win = byId(id) ?: return
        win.zoomed = false
        var x = win.x; var y = win.y; var w = win.w; var h = win.h
        if (edges and 4 != 0) w = max(minW, w + dx)
        if (edges and 8 != 0) h = max(minH, h + dy)
        if (edges and 1 != 0) { val nw = max(minW, w - dx); x += w - nw; w = nw }
        if (edges and 2 != 0) { val nh = max(minH, h - dy); val ny = y + (h - nh); if (ny >= area.y) { y = ny; h = nh } }
        win.x = x; win.y = y; win.w = w; win.h = h
    }

    fun toggleZoom(id: Int) {
        val win = byId(id) ?: return
        if (win.zoomed) {
            win.restore?.let { win.setBounds(it) }
            win.zoomed = false
            win.restore = null
        } else {
            win.restore = win.bounds
            win.setBounds(area)
            win.zoomed = true
        }
    }

    fun snapBoundsFor(zone: SnapZone): Bounds? {
        val a = area
        val hw = a.w / 2f
        val hh = a.h / 2f
        return when (zone) {
            SnapZone.None -> null
            SnapZone.Left -> Bounds(a.x, a.y, hw, a.h)
            SnapZone.Right -> Bounds(a.x + hw, a.y, hw, a.h)
            SnapZone.Fill -> a
            SnapZone.TopLeft -> Bounds(a.x, a.y, hw, hh)
            SnapZone.TopRight -> Bounds(a.x + hw, a.y, hw, hh)
            SnapZone.BottomLeft -> Bounds(a.x, a.y + hh, hw, hh)
            SnapZone.BottomRight -> Bounds(a.x + hw, a.y + hh, hw, hh)
        }
    }

    /** Which tiling zone a pointer (dp, desktop coordinates) falls into while dragging a window. */
    fun zoneAt(px: Float, py: Float, edge: Float = 18f): SnapZone {
        val a = area
        val left = px <= a.x + edge
        val right = px >= a.right - edge
        val top = py <= a.y + 4f
        val upper = py < a.y + a.h * 0.25f
        val lower = py > a.y + a.h * 0.75f
        return when {
            left && upper -> SnapZone.TopLeft
            left && lower -> SnapZone.BottomLeft
            right && upper -> SnapZone.TopRight
            right && lower -> SnapZone.BottomRight
            left -> SnapZone.Left
            right -> SnapZone.Right
            top -> SnapZone.Fill
            else -> SnapZone.None
        }
    }

    fun snap(id: Int, zone: SnapZone) {
        val win = byId(id) ?: return
        val b = snapBoundsFor(zone) ?: return
        if (!win.zoomed && win.restore == null) win.restore = win.bounds
        win.setBounds(b)
        win.zoomed = zone == SnapZone.Fill
    }

    fun center(id: Int) {
        val win = byId(id) ?: return
        val a = area
        win.x = a.x + (a.w - win.w) / 2f
        win.y = a.y + (a.h - win.h) / 2f
    }

    /** Keep every window reachable after rotation or a window-size change. */
    fun clampAll() {
        val a = area
        for (win in windows) {
            if (win.zoomed) { win.setBounds(a); continue }
            win.w = min(win.w, a.w)
            win.h = min(win.h, a.h)
            win.x = win.x.coerceIn(a.x - win.w + 80f, max(a.x, a.right - 80f))
            win.y = win.y.coerceIn(a.y, max(a.y, a.bottom - 40f))
        }
    }

    fun windowsOn(space: Int) = windows.filter { it.space == space }

    fun visibleOrdered(space: Int = currentSpace) = windows.filter { it.space == space && !it.minimized }

    fun cycle(forward: Boolean = true) {
        val list = visibleOrdered()
        if (list.size < 2) return
        if (forward) focus(list.first().id) else {
            val top = list.last()
            windows.remove(top); windows.add(0, top)
            focusTopmost()
        }
    }

    fun switchSpace(index: Int) {
        currentSpace = index.coerceIn(0, spaceCount - 1)
        if (focused?.space != currentSpace) focusTopmost()
    }

    fun addSpace(): Int { spaceCount = min(6, spaceCount + 1); return spaceCount - 1 }

    fun removeSpace(index: Int) {
        if (spaceCount <= 1) return
        for (w in windows) if (w.space == index) w.space = max(0, index - 1) else if (w.space > index) w.space -= 1
        spaceCount -= 1
        if (currentSpace >= spaceCount) currentSpace = spaceCount - 1
    }

    fun moveToSpace(id: Int, space: Int) {
        val win = byId(id) ?: return
        win.space = space.coerceIn(0, spaceCount - 1)
        if (focusedId == id && win.space != currentSpace) focusTopmost()
    }

    fun runningApps(): List<String> = windows.map { it.appId }.distinct()
}
