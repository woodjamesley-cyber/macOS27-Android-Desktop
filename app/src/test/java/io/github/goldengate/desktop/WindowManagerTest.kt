package io.github.goldengate.desktop

import io.github.goldengate.desktop.core.Bounds
import io.github.goldengate.desktop.core.SnapZone
import io.github.goldengate.desktop.core.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WindowManagerTest {
    private lateinit var wm: WindowManager

    @Before fun setUp() { wm = WindowManager().apply { area = Bounds(0f, 26f, 1000f, 600f); spaceCount = 3 } }

    @Test fun openFocusesNewWindowAndFitsArea() {
        val a = wm.open("finder", "Finder", 2000f, 2000f)
        assertEquals(a.id, wm.focusedId)
        assertTrue(a.w <= 1000f && a.h <= 600f)
        assertTrue(a.x >= 0f && a.y >= 26f)
    }

    @Test fun focusRaisesWindowToTop() {
        val a = wm.open("a", "A", 300f, 200f)
        val b = wm.open("b", "B", 300f, 200f)
        assertEquals(b, wm.windows.last())
        wm.focus(a.id)
        assertEquals(a, wm.windows.last())
        assertEquals(a.id, wm.focusedId)
    }

    @Test fun closeAndMinimizeMoveFocusToNextWindow() {
        val a = wm.open("a", "A", 300f, 200f)
        val b = wm.open("b", "B", 300f, 200f)
        wm.minimize(b.id)
        assertTrue(b.minimized)
        assertEquals(a.id, wm.focusedId)
        wm.close(a.id)
        assertNull(wm.focusedId)
        wm.focus(b.id)
        assertFalse(b.minimized)
    }

    @Test fun zoomAndRestorePreservesBounds() {
        val a = wm.open("a", "A", 300f, 200f)
        val before = a.bounds
        wm.toggleZoom(a.id)
        assertEquals(wm.area, a.bounds)
        assertTrue(a.zoomed)
        wm.toggleZoom(a.id)
        assertEquals(before, a.bounds)
    }

    @Test fun snappingTilesHalvesAndQuarters() {
        val a = wm.open("a", "A", 300f, 200f)
        wm.snap(a.id, SnapZone.Left)
        assertEquals(Bounds(0f, 26f, 500f, 600f), a.bounds)
        wm.snap(a.id, SnapZone.BottomRight)
        assertEquals(Bounds(500f, 326f, 500f, 300f), a.bounds)
        assertEquals(SnapZone.Left, wm.zoneAt(2f, 300f))
        assertEquals(SnapZone.TopRight, wm.zoneAt(999f, 40f))
        assertEquals(SnapZone.Fill, wm.zoneAt(500f, 27f))
        assertEquals(SnapZone.None, wm.zoneAt(500f, 300f))
    }

    @Test fun resizeRespectsMinimumSize() {
        val a = wm.open("a", "A", 300f, 200f)
        wm.resize(a.id, 4 or 8, -1000f, -1000f, 200f, 150f)
        assertEquals(200f, a.w)
        assertEquals(150f, a.h)
        wm.resize(a.id, 4, 100f, 0f, 200f, 150f)
        assertEquals(300f, a.w)
    }

    @Test fun moveKeepsWindowReachable() {
        val a = wm.open("a", "A", 300f, 200f)
        wm.move(a.id, -5000f, -5000f)
        assertTrue(a.x + a.w >= 80f)
        assertEquals(26f, a.y)
    }

    @Test fun spacesSeparateWindows() {
        val a = wm.open("a", "A", 300f, 200f)
        wm.moveToSpace(a.id, 1)
        assertEquals(1, a.space)
        assertTrue(wm.visibleOrdered(0).isEmpty())
        wm.switchSpace(1)
        assertEquals(a.id, wm.focusedId)
        wm.removeSpace(1)
        assertEquals(0, a.space)
        assertEquals(2, wm.spaceCount)
    }

    @Test fun clampAllAfterAreaShrinks() {
        val a = wm.open("a", "A", 900f, 500f)
        wm.area = Bounds(0f, 26f, 400f, 300f)
        wm.clampAll()
        assertTrue(a.w <= 400f && a.h <= 300f)
        assertTrue(a.y >= 26f)
    }

    @Test fun cycleRotatesFocus() {
        val a = wm.open("a", "A", 300f, 200f)
        val b = wm.open("b", "B", 300f, 200f)
        wm.cycle()
        assertEquals(a.id, wm.focusedId)
        wm.cycle()
        assertEquals(b.id, wm.focusedId)
    }
}
