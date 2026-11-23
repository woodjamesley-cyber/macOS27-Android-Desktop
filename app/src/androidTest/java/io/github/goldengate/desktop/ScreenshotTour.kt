package io.github.goldengate.desktop

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.goldengate.desktop.core.Appearance
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.SnapZone
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Stages representative scenes and saves real device screenshots (pulled by CI as an artifact). */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(CleanStateRule(keepDemoNotifications = true)).around(compose)

    private fun ui(block: () -> Unit) { compose.runOnUiThread(block); compose.waitForIdle() }
    private fun open(id: String, arg: String? = null) { ui { compose.desk().launch(id, arg) }; compose.waitTag("window-$id", 15_000) }
    private fun win(id: String) = compose.desk().wm.windows.last { it.appId == id }
    private fun closeAll() = ui { compose.desk().wm.windows.toList().forEach { compose.desk().wm.close(it.id) }; compose.desk().overlay = Overlay.None }

    @Test fun tour() {
        compose.waitTag("desktop")
        Thread.sleep(7_000) // demo banners arrive at ~2.5 s and ~5.7 s
        compose.screenshot("01-desktop-banners")

        open("finder")
        open("notes")
        ui { compose.desk().wm.snap(win("finder").id, SnapZone.Left); compose.desk().wm.snap(win("notes").id, SnapZone.Right) }
        compose.screenshot("02-finder-notes-snapped")

        compose.tap("status-controlcenter"); compose.waitTag("control-center")
        compose.screenshot("03-control-center")
        compose.tap("status-controlcenter")

        compose.tap("status-clock"); compose.waitTag("notification-center")
        compose.screenshot("04-notification-center")
        ui { compose.desk().overlay = Overlay.None }

        compose.tap("menubar-system"); compose.waitTag("menu-panel-0")
        compose.screenshot("05-system-menu")
        ui { compose.desk().dismissAll() }

        open("calculator"); open("terminal")
        compose.waitTag("terminal-input").apply { performTextInput("uname -a"); performImeAction(); performTextInput("ls"); performImeAction() }
        ui { compose.desk().toggleOverlay(Overlay.MissionControl) }
        compose.waitTag("mission-control")
        compose.screenshot("06-mission-control")
        ui { compose.desk().overlay = Overlay.None }
        compose.screenshot("07-terminal-calculator")

        closeAll()
        open("settings", "appearance")
        compose.screenshot("08-settings-appearance")
        ui { compose.desk().updateSettings { it.copy(appearance = Appearance.Dark) } }
        compose.screenshot("09-settings-dark")

        closeAll()
        open("photos"); compose.waitTag("photos-grid", 40_000)
        compose.screenshot("10-photos")
        closeAll()
        open("music")
        compose.screenshot("11-music")
        closeAll()
        open("calendar")
        compose.screenshot("12-calendar")
        closeAll()
        open("clock")
        compose.screenshot("13-clock")
        closeAll()
        open("browser")
        compose.screenshot("14-browser")
        // A real page rendered by the system WebView (local file, so no network is needed).
        val page = File(compose.desk().fs.home, "Documents/hello.html").apply {
            writeText("<html><body style='font-family:sans-serif;padding:24px;background:linear-gradient(#fdf6e3,#e8d8ff)'>" +
                "<h1>Hello from WebView</h1><p>This page is rendered by Android System WebView inside a desktop window.</p>" +
                "<ul><li>Back / forward history</li><li>Tabs</li><li>Downloads handed to Android</li></ul></body></html>")
        }
        closeAll()
        open("browser", "url:file://" + page.absolutePath)
        Thread.sleep(3_000)
        compose.screenshot("14b-browser-page")
        closeAll()
        open("sysinfo")
        compose.screenshot("15-system-information")
        closeAll()

        ui { compose.desk().toggleOverlay(Overlay.Launcher) }; compose.waitTag("launcher")
        compose.screenshot("16-launcher")
        ui { compose.desk().overlay = Overlay.None }
        ui { compose.desk().toggleOverlay(Overlay.Spotlight) }
        compose.waitTag("spotlight-field").performTextInput("12*(3+4)")
        compose.screenshot("17-spotlight-calc")
        ui { compose.desk().overlay = Overlay.None }

        ui { compose.desk().updateSettings { it.copy(appearance = Appearance.Light, glassTint = 0.9f) } }
        open("finder")
        compose.screenshot("18-light-tinted-glass")
    }
}
