package io.github.goldengate.desktop.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.allItems
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.shell.hiddenShortcuts
import io.github.goldengate.desktop.shell.menuBarMenus
import io.github.goldengate.desktop.shell.systemMenu
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.FormRow
import io.github.goldengate.desktop.ui.FormSection
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SidebarItem

private val topics = listOf(
    "welcome" to "Welcome", "windows" to "Windows", "dock" to "Dock", "menus" to "Menu Bar & Menus",
    "input" to "Touch, Mouse, Keyboard & Controllers", "glass" to "Liquid Glass", "files" to "Files & Privacy", "shortcuts" to "Keyboard Shortcuts", "apps" to "Apps",
)

private val tipText = mapOf(
    "welcome" to listOf(
        "Golden Gate Desktop" to "An unofficial desktop environment for Android inspired by the look of macOS 27. Everything you see — windows, the Dock, menus, Notification Center and Control Center — is interactive.",
        "Get around" to "Tap Dock icons to open apps. Tap the clock in the menu bar for Notification Center and the switches icon for Control Center. Use ⌘Space (or Ctrl+Space) for Spotlight.",
    ),
    "windows" to listOf(
        "Move & resize" to "Drag a window by its toolbar. Drag any edge or corner to resize. Double-click (or double-tap) a toolbar to zoom.",
        "Traffic lights" to "Red closes, yellow minimizes to the Dock, green fills the screen. Long-press or right-click the toolbar for tiling options.",
        "Snapping" to "Drag a window to the left or right edge to tile it to half the screen, or to the top to fill. Drag into a corner for a quarter.",
        "Mission Control & Spaces" to "Swipe up with three fingers is not available on Android, so use Ctrl+↑, F3, the Dock's Mission Control icon or the controller's Y button. Add desktops in Mission Control and move windows between them from the Window menu.",
    ),
    "dock" to listOf(
        "Magnification" to "With a mouse, icons grow as you hover. On touch screens, the icon under your finger magnifies while you slide along the Dock.",
        "Customize" to "Long-press or right-click a Dock icon to keep it in the Dock, remove it, or quit the app. With a mouse, drag icons to rearrange them. Drop files on the Trash to delete them.",
    ),
    "menus" to listOf(
        "App menus" to "The menu bar shows the menus of the app in front. Every menu item performs a real action; unavailable items are dimmed.",
        "Status items" to "Wi‑Fi, battery, Bluetooth and volume reflect your device. Where Android doesn't let apps change a setting directly, the control opens the matching Android panel.",
    ),
    "input" to listOf(
        "Automatic input detection" to "The desktop notices whether you are using touch, a mouse, a touchpad, a keyboard or a game controller and adapts automatically — hover effects for mice, long-press menus for touch.",
        "Keyboard" to "⌘ shortcuts work with the Meta/Search key or Ctrl. ⌘Tab switches apps, ⌘W closes windows, ⌘Q quits apps, ⌘, opens Settings.",
        "Controllers" to "Move the pointer with the left stick, press A to click, X to right-click, B to go back, Y for Mission Control and L1/R1 to switch desktops.",
    ),
    "glass" to listOf(
        "Liquid Glass" to "Glass surfaces blur and bend what's behind them, with bright rims and soft shadows. On Android 13+ the refraction runs as a GPU shader; older devices use a lighter path.",
        "Make it yours" to "In Settings ▸ Appearance, slide Liquid Glass from Clear to Tinted, choose an accent color, or reduce transparency in Accessibility.",
    ),
    "files" to listOf(
        "Your home folder" to "Desktop, Documents, Downloads, Pictures and Music live in private app storage and need no permissions.",
        "Device folders" to "Use Finder ▸ Connect Folder… to grant access to a folder on your device through Android's picker. You can revoke access in Settings ▸ Privacy & Security.",
    ),
    "apps" to AppRegistry.apps.map { it.name to (it.keywords.joinToString(", ").ifEmpty { it.category }) },
)

@Composable
fun TipsApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val arg = rememberWindowArgument(win)
    var topic by remember { mutableStateOf("welcome") }
    LaunchedEffect(arg) {
        topic = when {
            arg == null -> topic
            topics.any { it.first == arg } -> arg
            AppRegistry.apps.any { it.id == arg } -> "apps"
            else -> topic
        }
    }
    LaunchedEffect(topic) { win.title = "Tips — " + topics.first { it.first == topic }.second }
    WindowScaffold(
        title = topics.first { it.first == topic }.second,
        sidebarWidth = 230.dp,
        sidebar = {
            topics.forEach { (id, label) -> SidebarItem(label, if (id == "shortcuts") Glyph.Keyboard else Glyph.Star, Color(0xFFFF9F0A), topic == id, { topic = id }, Modifier.testTag("tips-$id")) }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Column(Modifier.widthIn(max = 620.dp)) {
                if (topic == "shortcuts") {
                    val all = (listOf(systemMenu(desk)) + menuBarMenus(desk) + hiddenShortcuts(desk))
                    all.forEach { m ->
                        val items = m.allItems().filter { it.shortcut != null }.toList()
                        if (items.isNotEmpty()) FormSection(if (m.title == "hidden") "Desktop" else m.title.ifBlank { "System" }) {
                            items.forEachIndexed { i, it -> FormRow(it.label, divider = i < items.lastIndex) { DeskText(it.shortcut!!.label, Desk.type.mono, color = Desk.colors.secondaryText) } }
                        }
                    }
                } else {
                    tipText[topic].orEmpty().forEach { (h, body) ->
                        DeskText(h, Desk.type.headline, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        DeskText(body, Desk.type.body, color = Desk.colors.secondaryText)
                    }
                }
            }
        }
    }
}
