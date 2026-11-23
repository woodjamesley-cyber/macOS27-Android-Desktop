package io.github.goldengate.desktop

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.rules.ExternalResource
import java.io.File

typealias DeskRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Wipes app state before the activity launches so every test starts from a clean desktop. */
class CleanStateRule(private val keepDemoNotifications: Boolean = false) : ExternalResource() {
    override fun before() {
        dismissSystemDialogs()
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        ctx.filesDir.listFiles()?.filter { it.name != "screenshots" }?.forEach { it.deleteRecursively() }
        if (!keepDemoNotifications) {
            ctx.getSharedPreferences("system_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("demoNotifications", false).putBoolean("uiSounds", false).commit()
        } else {
            ctx.getSharedPreferences("system_settings", Context.MODE_PRIVATE).edit().putBoolean("uiSounds", false).commit()
        }
    }
}

/** Fresh emulators sometimes show "isn't responding" or full-screen hint dialogs over the app. */
fun dismissSystemDialogs() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    listOf("Wait", "Got it").forEach { label ->
        runCatching { device.findObject(By.text(label))?.click() }
    }
}

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitTag(tag: String, timeout: Long = 8_000): SemanticsNodeInteraction {
    waitUntil(timeout) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    waitForIdle()
    return onNodeWithTag(tag, useUnmergedTree = true)
}

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitGone(tag: String, timeout: Long = 8_000) = waitUntil(timeout) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

/** Desktop widgets use custom pointer handling, so tap with real touch input rather than semantics clicks. */
fun DeskRule.tap(tag: String) { waitTag(tag).performTouchInput { click() }; waitForIdle() }

fun DeskRule.desk() = activity.desk

/** Captures the full device screen (including RenderEffect blur) via UiAutomator. */
fun DeskRule.screenshot(name: String) {
    waitForIdle()
    Thread.sleep(700) // let spring animations settle
    val inst = InstrumentationRegistry.getInstrumentation()
    val dir = File(inst.targetContext.filesDir, "screenshots").apply { mkdirs() }
    val file = File(dir, "$name.png")
    val ok = UiDevice.getInstance(inst).takeScreenshot(file, 1f, 90)
    if (!ok) {
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
}

/** Taps [tag] and waits for [done]; re-taps once if the first tap landed mid-animation. */
fun DeskRule.tapUntil(tag: String, timeout: Long = 4_000, done: () -> Boolean) {
    repeat(3) {
        tap(tag)
        try { waitUntil(timeout) { done() }; return } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {}
    }
    screenshot("fail-${tag.replace(':', '_')}")
    val d = desk()
    throw AssertionError(
        "tapUntil($tag) never satisfied; windows=" + d.wm.windows.map { "${it.id}:${it.appId} min=${it.minimized} zoom=${it.zoomed} closing=${it.closing}" } +
            " focused=${d.wm.focusedId} overlay=${d.overlay} menu=${d.openMenu} node=" +
            runCatching { onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()
    )
}
