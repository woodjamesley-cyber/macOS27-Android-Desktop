package io.github.goldengate.desktop

import io.github.goldengate.desktop.apps.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlTest {
    @Test fun normalizesAddresses() {
        assertEquals("https://example.com", normalizeUrl("example.com"))
        assertEquals("http://x.org/a", normalizeUrl("http://x.org/a"))
        assertTrue(normalizeUrl("liquid glass design").startsWith("https://duckduckgo.com/?q=liquid+glass"))
        assertEquals("", normalizeUrl("   "))
    }
}
