package io.github.goldengate.desktop.robo

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import io.github.goldengate.desktop.MainActivity
import org.junit.rules.ExternalResource
import java.io.File

typealias DeskRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

class CleanStateRule : ExternalResource() {
    override fun before() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        ctx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        // Robolectric's native AGSL compiler lags the device one, so use the blur-free path here.
        ctx.getSharedPreferences("system_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("demoNotifications", false).putBoolean("uiSounds", false).commit()
    }
}

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitTag(tag: String, timeout: Long = 8_000): SemanticsNodeInteraction {
    waitUntil(timeout) { advanceLooper(); onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    waitForIdle()
    return onNodeWithTag(tag, useUnmergedTree = true)
}

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitGone(tag: String, timeout: Long = 8_000) = waitUntil(timeout) { advanceLooper(); onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

fun DeskRule.tap(tag: String) { waitTag(tag).performTouchInput { click() }; waitForIdle() }

fun DeskRule.desk() = activity.desk

/** Robolectric's paused looper does not run delayed coroutines (e.g. the close animation delay) on its own. */
private fun advanceLooper() =
    org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)

/** Taps [tag] and waits for [done]; re-taps once if the first tap landed mid-animation. */
fun DeskRule.tapUntil(tag: String, timeout: Long = 4_000, done: () -> Boolean) {
    repeat(3) {
        tap(tag)
        try { waitUntil(timeout) { done() }; return } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {}
    }
    waitUntil(timeout) { done() }
}
