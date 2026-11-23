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
import androidx.test.uiautomator.UiDevice
import org.junit.rules.ExternalResource
import java.io.File

typealias DeskRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Wipes app state before the activity launches so every test starts from a clean desktop. */
class CleanStateRule(private val keepDemoNotifications: Boolean = false) : ExternalResource() {
    override fun before() {
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

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitTag(tag: String, timeout: Long = 8_000): SemanticsNodeInteraction {
    waitUntilAtLeastOneExists(hasTestTag(tag), timeout)
    return onNodeWithTag(tag, useUnmergedTree = true)
}

@OptIn(ExperimentalTestApi::class)
fun DeskRule.waitGone(tag: String, timeout: Long = 8_000) = waitUntilDoesNotExist(hasTestTag(tag), timeout)

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
