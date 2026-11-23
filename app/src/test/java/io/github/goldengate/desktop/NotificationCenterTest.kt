package io.github.goldengate.desktop

import io.github.goldengate.desktop.core.NotificationCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCenterTest {
    private var clock = 1_000_000L
    private val nc = NotificationCenter { clock }

    @Test fun postAddsHistoryAndBanner() {
        val n = nc.post("mail", "Mail", "Hello", "Body")
        assertNotNull(n)
        assertEquals(1, nc.history.size)
        assertEquals(1, nc.banners.size)
    }

    @Test fun doNotDisturbAndMutedKeepHistoryWithoutBanner() {
        assertNull(nc.post("a", "A", "t", "b", doNotDisturb = true))
        assertNull(nc.post("b", "B", "t", "b", muted = true))
        assertEquals(2, nc.history.size)
        assertTrue(nc.banners.isEmpty())
    }

    @Test fun bannersAreCappedAtThree() {
        repeat(5) { nc.post("a", "A", "t$it", "b") }
        assertEquals(3, nc.banners.size)
        assertEquals("t4", nc.banners.first().title)
    }

    @Test fun groupsByAppNewestFirst() {
        nc.post("a", "A", "1", ""); clock += 10
        nc.post("b", "B", "2", ""); clock += 10
        nc.post("a", "A", "3", "")
        val g = nc.groups()
        assertEquals(listOf("a", "b"), g.map { it.appId })
        assertEquals(listOf("3", "1"), g.first().items.map { it.title })
    }

    @Test fun dismissClearAppAndClearAll() {
        val n = nc.post("a", "A", "1", "")!!
        nc.post("b", "B", "2", "")
        nc.dismissBanner(n.id)
        assertEquals(1, nc.banners.size)
        assertEquals(2, nc.history.size)
        nc.clearApp("b")
        assertEquals(1, nc.history.size)
        nc.clearAll()
        assertTrue(nc.history.isEmpty() && nc.banners.isEmpty())
    }

    @Test fun relativeTimeFormatting() {
        assertEquals("now", NotificationCenter.relativeTime(0, 30_000))
        assertEquals("5m ago", NotificationCenter.relativeTime(0, 300_000))
        assertEquals("2h ago", NotificationCenter.relativeTime(0, 7_200_000))
        assertEquals("1d ago", NotificationCenter.relativeTime(0, 90_000_000))
    }
}
