package io.github.goldengate.desktop.robo

import io.github.goldengate.desktop.MainActivity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.goldengate.desktop.core.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** End-to-end checks of the desktop shell, driven through real touch input. */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
class RoboDesktopShellTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(CleanStateRule()).around(compose)

    @Test fun desktopBootsWithMenuBarAndDock() {
        compose.waitTag("desktop").assertIsDisplayed()
        compose.waitTag("menubar").assertIsDisplayed()
        compose.waitTag("dock").assertIsDisplayed()
        compose.waitTag("dock-app:finder").assertIsDisplayed()
        compose.waitTag("dock-trash").assertIsDisplayed()
    }

    @Test fun dockLaunchesAppAndTrafficLightsWork() {
        compose.tap("dock-app:calculator")
        compose.waitTag("window-calculator").assertIsDisplayed()
        val win = compose.desk().wm.windows.single { it.appId == "calculator" }
        assertEquals(win.id, compose.desk().wm.focusedId)
        // minimize → appears in Dock as a minimized tile
        compose.tapUntil("minimize-${win.id}") { win.minimized }
        compose.waitTag("dock-min:${win.id}")
        // click the minimized tile to restore
        compose.tapUntil("dock-min:${win.id}") { !win.minimized }
        // zoom fills the work area, close removes the window
        compose.tapUntil("zoom-${win.id}") { win.zoomed }
        compose.tapUntil("close-${win.id}") { win.closing || compose.desk().wm.windows.none { it.id == win.id } }
        compose.waitGone("window-calculator")
    }

    @Test fun menuBarMenusOpenAndExecute() {
        compose.tap("menubar-system")
        compose.waitTag("menu-item-About This Device")
        compose.tap("menu-item-About This Device")
        compose.waitTag("window-sysinfo").assertIsDisplayed()
        compose.tap("menubar-Window")
        compose.waitTag("menu-panel-0")
        compose.tap("menubar-Window") // toggles closed
        compose.waitGone("menu-panel-0")
    }

    @Test fun controlCenterAndNotificationCenter() {
        compose.tap("status-controlcenter")
        compose.waitTag("control-center").assertIsDisplayed()
        compose.waitTag("cc-volume").assertIsDisplayed()
        compose.waitTag("cc-brightness").assertIsDisplayed()
        compose.tap("cc-dark")
        compose.waitUntil(3_000) { compose.desk().settings.appearance == io.github.goldengate.desktop.core.Appearance.Dark ||
            compose.desk().settings.appearance == io.github.goldengate.desktop.core.Appearance.Light }
        compose.tap("status-controlcenter")
        compose.waitGone("control-center")

        compose.runOnUiThread { compose.desk().notify("notes", "Hello", "Instrumented notification") }
        compose.tap("status-clock")
        compose.waitTag("notification-center").assertIsDisplayed()
        assertTrue(compose.desk().notifications.history.isNotEmpty())
        compose.tap("notifications-clear-all")
        compose.waitUntil(3_000) { compose.desk().notifications.history.isEmpty() }
    }

    @Test fun missionControlSpacesAndSpotlight() {
        compose.tap("dock-app:notes")
        compose.waitTag("window-notes")
        compose.runOnUiThread { compose.desk().toggleOverlay(Overlay.MissionControl) }
        compose.waitTag("mission-control").assertIsDisplayed()
        compose.waitTag("spaces-bar")
        compose.tap("mc-window-notes")
        compose.waitGone("mission-control")

        compose.runOnUiThread { compose.desk().toggleOverlay(Overlay.Spotlight) }
        compose.waitTag("spotlight-field")
        compose.waitTag("spotlight-field").performTextInput("Termi")
        compose.tap("spotlight-result-0")
        compose.waitTag("window-terminal").assertIsDisplayed()
    }

    @Test fun launcherOpensApps() {
        compose.tap("dock-app:launcher")
        compose.waitTag("launcher").assertIsDisplayed()
        compose.tap("launcher-app-clock")
        compose.waitTag("window-clock").assertIsDisplayed()
    }

    @Test fun windowsSurviveRecreation() {
        compose.tap("dock-app:calendar")
        compose.waitTag("window-calendar")
        val before = compose.desk().wm.windows.map { it.appId to it.bounds }
        compose.activityRule.scenario.recreate()
        compose.waitTag("window-calendar")
        assertEquals(before, compose.desk().wm.windows.map { it.appId to it.bounds })
    }
}
