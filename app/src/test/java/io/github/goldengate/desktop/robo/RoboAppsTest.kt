package io.github.goldengate.desktop.robo

import io.github.goldengate.desktop.MainActivity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Exercises real behaviour inside the bundled apps. */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
class RoboAppsTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(CleanStateRule()).around(compose)

    private fun launch(id: String, arg: String? = null) {
        compose.runOnUiThread { compose.desk().launch(id, arg) }
        compose.waitTag("window-$id")
    }

    @Test fun calculatorComputes() {
        launch("calculator")
        listOf("7", "×", "6", "=").forEach { compose.tap("calc-key-$it") }
        compose.waitTag("calc-display").assertTextEquals("42")
    }

    @Test fun terminalRunsCommandsInSandbox() {
        launch("terminal")
        val input = compose.waitTag("terminal-input")
        input.performTextInput("mkdir ci-test")
        input.performImeAction()
        compose.waitUntil(3_000) { File(compose.desk().fs.home, "ci-test").isDirectory }
    }

    @Test fun notesCreatesAndSavesNote() {
        launch("notes")
        compose.tap("notes-new")
        compose.waitTag("notes-editor").performTextInput("Shopping list\nmilk")
        compose.waitUntil(5_000) {
            File(compose.desk().fs.home, "Documents/Notes").listFiles()?.any { it.readText().contains("milk") } == true
        }
    }

    @Test fun settingsPersistAppearance() {
        launch("settings")
        compose.tap("settings-pane-appearance")
        compose.waitTag("settings-content-appearance")
        compose.tap("appearance-Dark")
        compose.waitUntil(3_000) { compose.desk().settings.appearance == io.github.goldengate.desktop.core.Appearance.Dark }
        // A fresh store reads exactly what a cold start would read from disk.
        val reloaded = io.github.goldengate.desktop.core.SettingsStore(compose.desk().app)
        assertEquals(io.github.goldengate.desktop.core.Appearance.Dark, reloaded.value.appearance)
    }

    @Test fun clockStopwatchRuns() {
        launch("clock", "stopwatch")
        compose.tap("stopwatch-toggle")
        Thread.sleep(1_500)
        compose.tap("stopwatch-toggle")
        compose.waitTag("stopwatch-display").assertIsDisplayed()
    }

    @Test fun calendarAddsEvent() {
        launch("calendar")
        compose.tap("calendar-add")
        compose.waitTag("event-title").performTextInput("Design review")
        compose.tap("event-save")
        compose.waitUntil(3_000) { compose.desk().calendar.events.any { it.title == "Design review" } }
    }

    @Test fun photosMusicFilesBrowserSysInfoOpen() {
        launch("photos"); compose.waitTag("photos-grid", 15_000).assertIsDisplayed()
        launch("music"); compose.waitTag("music-list").assertIsDisplayed()
        launch("files"); compose.waitTag("files-app").assertIsDisplayed()
        launch("browser"); compose.waitTag("browser-address").assertIsDisplayed()
        launch("sysinfo"); compose.waitTag("window-sysinfo").assertIsDisplayed()
        assertTrue(compose.desk().wm.windows.size >= 5)
    }

    @Test fun finderCreatesFolderFromMenu() {
        launch("finder")
        compose.tap("menubar-File")
        compose.tap("menu-item-New Folder")
        compose.waitUntil(5_000) { compose.desk().fs.home.walkTopDown().any { it.isDirectory && it.name.startsWith("untitled folder") } }
    }
}
