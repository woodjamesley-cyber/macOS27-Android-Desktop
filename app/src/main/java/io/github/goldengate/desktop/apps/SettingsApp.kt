package io.github.goldengate.desktop.apps

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.BuildConfig
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.Appearance
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.DialogSpec
import io.github.goldengate.desktop.core.DockPosition
import io.github.goldengate.desktop.core.MinimizeEffect
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.SystemPanel
import io.github.goldengate.desktop.core.SystemSettings
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.glass.Accent
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassQuality
import io.github.goldengate.desktop.glass.GlassShaders
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Wallpaper
import io.github.goldengate.desktop.glass.WallpaperPainter
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.shell.menuBarMenus
import io.github.goldengate.desktop.shell.settingsPanes
import io.github.goldengate.desktop.shell.systemMenu
import io.github.goldengate.desktop.shell.hiddenShortcuts
import io.github.goldengate.desktop.ui.AppIcon
import io.github.goldengate.desktop.ui.DeskSlider
import io.github.goldengate.desktop.ui.DeskSwitch
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.FormRow
import io.github.goldengate.desktop.ui.FormSection
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.SidebarItem
import io.github.goldengate.desktop.ui.desktopGestures
import io.github.goldengate.desktop.core.allItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Pane(val id: String, val title: String, val glyph: Glyph, val tint: Color)

private val panes = listOf(
    Pane("general", "General", Glyph.Gear, Color(0xFF8E8E93)),
    Pane("appearance", "Appearance", Glyph.Paint, Color(0xFF1C1C1E)),
    Pane("wallpaper", "Wallpaper", Glyph.Image, Color(0xFF32ADE6)),
    Pane("dock", "Desktop & Dock", Glyph.Dock, Color(0xFF1C1C1E)),
    Pane("display", "Displays", Glyph.Display, Color(0xFF0A84FF)),
    Pane("sound", "Sound", Glyph.Speaker, Color(0xFFFF375F)),
    Pane("notifications", "Notifications", Glyph.Bell, Color(0xFFFF3B30)),
    Pane("focus", "Focus", Glyph.Moon, Color(0xFF5E5CE6)),
    Pane("controlcenter", "Control Center", Glyph.ControlCenter, Color(0xFF8E8E93)),
    Pane("privacy", "Privacy & Security", Glyph.Hand, Color(0xFF0A84FF)),
    Pane("accessibility", "Accessibility", Glyph.Person, Color(0xFF0A84FF)),
    Pane("keyboard", "Keyboard", Glyph.Keyboard, Color(0xFF8E8E93)),
    Pane("mouse", "Mouse & Trackpad", Glyph.Mouse, Color(0xFF8E8E93)),
    Pane("controllers", "Game Controllers", Glyph.Gamepad, Color(0xFF8E8E93)),
    Pane("battery", "Battery", Glyph.Battery, Color(0xFF34C759)),
    Pane("performance", "Performance", Glyph.Bolt, Color(0xFFFF9F0A)),
    Pane("about", "About", Glyph.Info, Color(0xFF8E8E93)),
)

@Composable
fun SettingsApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val arg = rememberWindowArgument(win)
    var pane by remember { mutableStateOf("appearance") }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(arg) { if (arg != null && panes.any { it.id == arg }) pane = arg }
    val current = panes.first { it.id == pane }
    LaunchedEffect(current) { win.title = current.title }
    WindowMenus(win, listOf(io.github.goldengate.desktop.core.menu("View") {
        panes.forEach { p -> item(p.title, checked = p.id == pane) { pane = p.id } }
    }))

    WindowScaffold(
        title = current.title,
        sidebarWidth = 220.dp,
        sidebar = {
            DeskTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), placeholder = "Search", glyph = Glyph.Search, rounded = 8.dp)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                UserCard(desk)
                panes.filter { filter.isBlank() || it.title.contains(filter, true) }.forEach { p ->
                    SidebarItem(p.title, p.glyph, p.tint, p.id == pane, { pane = p.id }, Modifier.testTag("settings-pane-${p.id}"), filledIcon = true)
                }
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).testTag("settings-content-$pane"),
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                when (pane) {
                    "general" -> GeneralPane(desk)
                    "appearance" -> AppearancePane(desk)
                    "wallpaper" -> WallpaperPane(desk)
                    "dock" -> DockPane(desk)
                    "display" -> DisplayPane(desk)
                    "sound" -> SoundPane(desk)
                    "notifications" -> NotificationsPane(desk)
                    "focus" -> FocusPane(desk)
                    "controlcenter" -> ControlCenterPane(desk)
                    "privacy" -> PrivacyPane(desk)
                    "accessibility" -> AccessibilityPane(desk)
                    "keyboard" -> KeyboardPane(desk)
                    "mouse" -> MousePane(desk)
                    "controllers" -> ControllersPane(desk)
                    "battery" -> BatteryPane(desk)
                    "performance" -> PerformancePane(desk)
                    "about" -> AboutPane(desk)
                }
            }
        }
    }
}

@Composable
private fun UserCard(desk: DesktopController) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF8E8E93)), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Person, Color.White, 20.dp) }
        Spacer(Modifier.width(8.dp))
        Column {
            DeskText(desk.settings.userName, Desk.type.bodyStrong, maxLines = 1)
            DeskText("Local Account", Desk.type.small, color = Desk.colors.secondaryText)
        }
    }
}

private fun DesktopController.set(block: (SystemSettings) -> SystemSettings) = updateSettings(block)

@Composable
private fun Toggle(label: String, value: Boolean, detail: String? = null, divider: Boolean = true, tag: String? = null, onChange: (Boolean) -> Unit) {
    FormRow(label, detail = detail, divider = divider) { DeskSwitch(value, onChange, if (tag != null) Modifier.testTag(tag) else Modifier) }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, divider: Boolean = true, tag: String? = null, leading: String? = null, trailing: String? = null, steps: Int = 0, onChange: (Float) -> Unit) {
    FormRow(label, divider = divider) {
        if (leading != null) DeskText(leading, Desk.type.small, color = Desk.colors.secondaryText)
        Spacer(Modifier.width(6.dp))
        val norm = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        DeskSlider(norm, { v -> onChange(range.start + v * (range.endInclusive - range.start)) }, Modifier.width(180.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier), height = 18.dp, steps = steps)
        Spacer(Modifier.width(6.dp))
        if (trailing != null) DeskText(trailing, Desk.type.small, color = Desk.colors.secondaryText)
    }
}

@Composable
private fun GeneralPane(desk: DesktopController) {
    val s = desk.settings
    var isDefaultHome by remember { mutableStateOf(desk.bridge?.isDefaultLauncher() == true) }
    fun passcodeDialog() {
        desk.dialog = DialogSpec(
            "Set a lock screen passcode",
            "Choose 4–8 digits. The lock screen asks for them before unlocking.",
            input = "", confirm = "Set Passcode", glyph = Glyph.Lock,
        ) { raw ->
            val digits = raw.filter { it.isDigit() }.take(8)
            if (digits.length < 4) desk.error("A passcode needs at least 4 digits.")
            else desk.set { it.copy(lockPin = digits) }
        }
    }
    FormSection("Identity") {
        FormRow("Your name") { DeskTextField(s.userName, { v -> desk.set { it.copy(userName = v.take(32)) } }, Modifier.width(200.dp)) }
        FormRow("Computer name", divider = false) { DeskTextField(s.computerName, { v -> desk.set { it.copy(computerName = v.take(40)) } }, Modifier.width(200.dp)) }
    }
    FormSection("Desktop") {
        FormRow("Desktops (Spaces)") { SegmentedControl((1..6).map { it.toString() }, s.spaces - 1, { i -> desk.set { it.copy(spaces = i + 1) }; desk.wm.spaceCount = i + 1; desk.wm.switchSpace(desk.wm.currentSpace) }) }
        Toggle("Reopen windows when returning", s.reopenWindows, "Restores open apps and window positions the next time you start Golden Gate Desktop.", divider = false) { v -> desk.set { it.copy(reopenWindows = v) } }
    }
    FormSection("Date & Time") {
        Toggle("24-hour time", s.clock24h) { v -> desk.set { it.copy(clock24h = v) } }
        Toggle("Show seconds in the menu bar", s.showSeconds, divider = false) { v -> desk.set { it.copy(showSeconds = v) } }
    }
    FormSection("Lock Screen", footer = "The passcode is stored on this device and guards the desktop's own lock screen. It is not Android's device lock.") {
        Toggle("Require passcode", s.lockPin.isNotEmpty(), divider = s.lockPin.isNotEmpty()) { on ->
            if (on) passcodeDialog() else desk.set { it.copy(lockPin = "") }
        }
        if (s.lockPin.isNotEmpty()) {
            FormRow("Passcode", detail = "${s.lockPin.length} digits") { PushButton("Change…", { passcodeDialog() }) }
        }
        FormRow("Lock now", divider = false) {
            PushButton("Lock Screen", { desk.overlay = Overlay.Locked }, glyph = Glyph.Lock)
        }
    }
    FormSection("Home App", footer = "Android decides which Home app starts with the Home button. Choosing Golden Gate Desktop replaces One UI Home; switch back any time in Android Settings ▸ Apps ▸ Default apps ▸ Home app.") {
        FormRow("Default Home app", detail = if (isDefaultHome) "Golden Gate Desktop" else "One UI Home or another launcher") {
            PushButton(
                if (isDefaultHome) "Change…" else "Set as Default…",
                {
                    desk.bridge?.requestDefaultLauncher { granted ->
                        isDefaultHome = granted || desk.bridge?.isDefaultLauncher() == true
                    }
                },
                primary = !isDefaultHome, glyph = Glyph.House,
            )
        }
        FormRow("Android Home settings", divider = false) { PushButton("Open…", { desk.bridge?.openHomeSettings() }) }
    }
    FormSection("Setup Assistant", footer = "Reopens the first-run guide to change appearance, account, passcode and Home app.") {
        FormRow("Setup Assistant", divider = false) { PushButton("Open…", { desk.showSetupAssistant = true }) }
    }
    FormSection("Restart", footer = "Resetting restores every setting to its default. Your files are kept.") {
        FormRow("Restart Desktop", detail = "Closes all windows and rebuilds the desktop.") { PushButton("Restart…", { desk.restartDesktop() }) }
        FormRow("Reset All Settings", divider = false) {
            PushButton("Reset…", {
                desk.dialog = DialogSpec("Reset all settings?", "Appearance, Dock, wallpaper and other preferences return to their defaults.", confirm = "Reset", destructive = true, glyph = Glyph.Gear) {
                    desk.settingsStore.reset(); desk.updateSettings { it }
                }
            }, destructive = true)
        }
    }
}

@Composable
private fun AppearancePane(desk: DesktopController) {
    val s = desk.settings
    val c = Desk.colors
    FormSection("Appearance") {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Appearance.Light to "Light", Appearance.Dark to "Dark", Appearance.Auto to "Auto").forEach { (a, label) ->
                Column(
                    Modifier.desktopGestures(key = "ap-$a", onTap = { desk.set { it.copy(appearance = a) } }).testTag("appearance-$label"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 86.dp, height = 56.dp).clip(RoundedCornerShape(8.dp))
                            .background(when (a) { Appearance.Light -> Color(0xFFE9E9EE); Appearance.Dark -> Color(0xFF2C2C2E); Appearance.Auto -> Color(0xFF8E8E93) })
                            .border(if (s.appearance == a) 2.5.dp else 0.5.dp, if (s.appearance == a) c.accent else c.separator, RoundedCornerShape(8.dp)),
                    ) {
                        Box(Modifier.padding(10.dp).size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (a == Appearance.Dark) Color(0xFF1C1C1E) else Color.White))
                    }
                    DeskText(label, Desk.type.caption, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    FormSection("Liquid Glass", footer = "Clear shows more of what's behind glass surfaces; Tinted increases opacity and contrast. (macOS 27 adds a similar slider.)") {
        FormRow("Glass style") {
            DeskText("Clear", Desk.type.small, color = c.secondaryText)
            Spacer(Modifier.width(6.dp))
            DeskSlider(s.glassTint, { v -> desk.set { it.copy(glassTint = v) } }, Modifier.width(200.dp).testTag("glass-slider"), height = 18.dp)
            Spacer(Modifier.width(6.dp))
            DeskText("Tinted", Desk.type.small, color = c.secondaryText)
        }
        // Live preview of the current material.
        Box(Modifier.fillMaxWidth().height(90.dp).padding(12.dp)) {
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFFB36B), Color(0xFFB45AC9), Color(0xFF4A3FB8))))) {}
            GlassSurface(Modifier.align(Alignment.Center).size(width = 220.dp, height = 46.dp), cornerRadius = 23.dp, material = GlassMaterial.Clear, source = io.github.goldengate.desktop.glass.GlassSource.Desktop) {
                DeskText("Preview", Desk.type.bodyStrong, modifier = Modifier.align(Alignment.Center))
            }
        }
        FormRow("Rendering quality", detail = GlassQuality.entries[s.glassQuality.coerceIn(0, 2)].description, divider = false) {
            SegmentedControl(GlassQuality.entries.map { it.label }, s.glassQuality.coerceIn(0, 2), { i -> desk.set { it.copy(glassQuality = i) } })
        }
    }
    FormSection("Theme") {
        FormRow("Accent color", divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Accent.entries.forEachIndexed { i, a ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(if (c.dark) a.dark else a.light)
                            .border(if (s.accent == i) 2.dp else 0.dp, if (s.accent == i) c.text.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
                            .desktopGestures(key = "accent-$i", onTap = { desk.set { it.copy(accent = i) } }).testTag("accent-${a.label}"),
                    )
                }
            }
        }
    }
    FormSection("Menu Bar") {
        Toggle("Show menu bar background", s.menuBarBackground, "By default the menu bar is transparent, as in macOS 26 and 27.", divider = false) { v -> desk.set { it.copy(menuBarBackground = v) } }
    }
}

private val thumbCache = mutableStateMapOf<String, ImageBitmap>()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperPane(desk: DesktopController) {
    val s = desk.settings
    val c = Desk.colors
    val dark = c.dark
    FormSection("Wallpaper", footer = "All wallpapers are original, procedurally generated artwork. Choose your own photo with the system photo picker — only the photo you pick is shared with this app.") {
        FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Wallpaper.entries.forEach { w ->
                val key = "${w.name}-$dark"
                LaunchedEffect(key) {
                    if (thumbCache[key] == null) thumbCache[key] = withContext(Dispatchers.Default) { WallpaperPainter.paint(w, dark, 240, 150).asImageBitmap() }
                }
                val selected = s.customWallpaper == null && s.wallpaper == w.name
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.desktopGestures(key = "wp-${w.name}", onTap = {
                    desk.set { it.copy(wallpaper = w.name, customWallpaper = null) }
                }).testTag("wallpaper-${w.name}")) {
                    Box(Modifier.size(width = 120.dp, height = 76.dp).clip(RoundedCornerShape(8.dp)).background(c.controlFill)
                        .border(if (selected) 3.dp else 0.5.dp, if (selected) c.accent else c.separator, RoundedCornerShape(8.dp))) {
                        thumbCache[key]?.let { Image(it, w.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    DeskText(w.label, Desk.type.caption, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        FormRow("Custom photo", detail = if (s.customWallpaper != null) "Using your photo" else null, divider = false) {
            if (s.customWallpaper != null) { PushButton("Remove", { desk.set { it.copy(customWallpaper = null) } }); Spacer(Modifier.width(6.dp)) }
            PushButton("Choose Photo…", {
                desk.bridge?.pickImage { uri ->
                    if (uri != null) {
                        try {
                            val dir = File(desk.app.filesDir, "wallpapers").apply { mkdirs() }
                            val out = File(dir, "custom-${System.currentTimeMillis()}.img")
                            desk.app.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                            dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
                            desk.set { it.copy(customWallpaper = out.absolutePath) }
                        } catch (t: Throwable) { desk.error(t.message ?: "Couldn't use that photo.") }
                    }
                }
            }, primary = true)
        }
    }
}

@Composable
private fun DockPane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Dock") {
        SliderRow("Size", s.dockIconSize, 32f..80f, tag = "dock-size", leading = "Small", trailing = "Large") { v -> desk.set { it.copy(dockIconSize = v) } }
        Toggle("Magnification", s.dockMagnification, tag = "dock-magnification") { v -> desk.set { it.copy(dockMagnification = v) } }
        if (s.dockMagnification) SliderRow("Magnification amount", s.dockMagnifyScale, 1.1f..2.2f, leading = "Less", trailing = "More") { v -> desk.set { it.copy(dockMagnifyScale = v) } }
        FormRow("Position on screen") {
            SegmentedControl(listOf("Left", "Bottom", "Right"), when (s.dockPosition) { DockPosition.Left -> 0; DockPosition.Bottom -> 1; DockPosition.Right -> 2 }, { i ->
                desk.set { it.copy(dockPosition = listOf(DockPosition.Left, DockPosition.Bottom, DockPosition.Right)[i]) }
            }, Modifier.testTag("dock-position"))
        }
        FormRow("Minimize windows using") {
            SegmentedControl(MinimizeEffect.entries.map { it.label }, s.minimizeEffect.ordinal, { i -> desk.set { it.copy(minimizeEffect = MinimizeEffect.entries[i]) } })
        }
        Toggle("Automatically hide and show the Dock", s.dockAutoHide, tag = "dock-autohide") { v -> desk.set { it.copy(dockAutoHide = v) } }
        Toggle("Show suggested and recent apps in Dock", s.dockShowRecents, divider = false) { v -> desk.set { it.copy(dockShowRecents = v) } }
    }
    FormSection("Apps in the Dock", footer = "Tip: right-click or long-press a Dock icon for options, or drag icons with a mouse to rearrange them.") {
        val all = AppRegistry.dockable
        all.forEachIndexed { i, id ->
            val pinned = id in s.dockApps
            FormRow(AppRegistry.name(id), divider = i < all.lastIndex) {
                AppIcon(id, 22.dp)
                Spacer(Modifier.width(10.dp))
                DeskSwitch(pinned, { v -> desk.set { st -> st.copy(dockApps = if (v) st.dockApps + id else st.dockApps - id) } })
            }
        }
    }
    FormSection("Desktop") {
        Toggle("Show items on desktop", s.showDesktopIcons) { v -> desk.set { it.copy(showDesktopIcons = v) } }
        SliderRow("Icon size", s.desktopIconSize, 40f..96f, divider = false) { v -> desk.set { it.copy(desktopIconSize = v) } }
    }
}

@Composable
private fun DisplayPane(desk: DesktopController) {
    val s = desk.settings
    var brightness by remember { mutableStateOf(desk.bridge?.appBrightness() ?: 0.7f) }
    FormSection("Display", footer = "Android doesn't let regular apps change system-wide brightness without special access, so this slider controls brightness while Golden Gate Desktop is in front.") {
        FormRow("Brightness") {
            DeskSlider(brightness, { v -> brightness = v; desk.bridge?.setAppBrightness(v) }, Modifier.width(200.dp), glyph = Glyph.Sun, height = 20.dp)
        }
        FormRow("Interface scale", detail = if (s.uiScale <= 0f) "Automatic (based on screen size)" else "${(s.uiScale * 100).toInt()}%") {
            SegmentedControl(listOf("Auto", "75%", "85%", "100%", "115%"), when (s.uiScale) { 0.75f -> 1; 0.85f -> 2; 1f -> 3; 1.15f -> 4; else -> 0 }, { i ->
                desk.set { it.copy(uiScale = listOf(0f, 0.75f, 0.85f, 1f, 1.15f)[i]) }
            }, Modifier.testTag("ui-scale"))
        }
        FormRow("System display settings", divider = false) { PushButton("Open…", { desk.bridge?.openSystemPanel(SystemPanel.Display) }) }
    }
    FormSection("Night Shift", footer = "Approximation: a warm color overlay is drawn over the desktop. For true color temperature control use Android's Night Light.") {
        Toggle("Night Shift", s.nightShift, tag = "night-shift") { v -> desk.set { it.copy(nightShift = v) } }
        SliderRow("Color temperature", s.nightShiftWarmth, 0f..1f, divider = false, leading = "Less warm", trailing = "More warm") { v -> desk.set { it.copy(nightShiftWarmth = v) } }
    }
}

@Composable
private fun SoundPane(desk: DesktopController) {
    val s = desk.settings
    LaunchedEffect(Unit) { desk.status.refreshVolume() }
    FormSection("Output") {
        FormRow("Output volume (media)") {
            DeskSlider(desk.status.volume, { v -> desk.status.changeVolume(v) }, Modifier.width(200.dp), glyph = Glyph.Speaker, height = 20.dp)
        }
        FormRow("Output device", divider = false) { PushButton("Choose…", { desk.bridge?.openSystemPanel(SystemPanel.Volume) }) }
    }
    FormSection("Sound Effects", footer = "Interface sounds are original, synthesized tones.") {
        Toggle("Play user interface sound effects", s.uiSounds, tag = "ui-sounds") { v -> desk.set { it.copy(uiSounds = v) } }
        SliderRow("Alert volume", s.uiVolume, 0f..1f) { v -> desk.set { it.copy(uiVolume = v) } }
        FormRow("Preview", divider = false) {
            PushButton("Play", { desk.play(io.github.goldengate.desktop.audio.UiSound.Notification) })
        }
    }
}

@Composable
private fun NotificationsPane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Notification Center") {
        FormRow("Banner duration") {
            SegmentedControl(listOf("3s", "5s", "8s", "12s"), listOf(3, 5, 8, 12).indexOf(s.bannerSeconds).coerceAtLeast(0), { i -> desk.set { it.copy(bannerSeconds = listOf(3, 5, 8, 12)[i]) } })
        }
        Toggle("Show welcome notifications at startup", s.demoNotifications) { v -> desk.set { it.copy(demoNotifications = v) } }
        FormRow("Test", detail = "Posts a sample notification with actions.", divider = false) {
            PushButton("Send Test Notification", {
                desk.notify(AppIds.SETTINGS, "Test notification", "This is how banners look with your current Liquid Glass style.",
                    actions = listOf(io.github.goldengate.desktop.core.NotificationAction("Open Settings") { desk.launch(AppIds.SETTINGS, "notifications") }))
            }, Modifier.testTag("send-test-notification"))
        }
    }
    FormSection("Application Notifications", footer = "Muted apps still appear in Notification Center history but don't show banners or play sounds.") {
        AppRegistry.apps.forEachIndexed { i, a ->
            FormRow(a.name, divider = i < AppRegistry.apps.lastIndex) {
                AppIcon(a.id, 20.dp); Spacer(Modifier.width(10.dp))
                DeskSwitch(a.id !in s.mutedApps, { on -> desk.set { st -> st.copy(mutedApps = if (on) st.mutedApps - a.id else st.mutedApps + a.id) } })
            }
        }
    }
}

@Composable
private fun FocusPane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Focus", footer = "Do Not Disturb silences banners and sounds inside Golden Gate Desktop. It doesn't change Android's own Do Not Disturb, which requires special system access.") {
        Toggle("Do Not Disturb", s.doNotDisturb, divider = false, tag = "dnd") { v -> desk.set { it.copy(doNotDisturb = v) } }
    }
}

@Composable
private fun ControlCenterPane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Menu Bar Items") {
        Toggle("Show battery percentage", s.showBatteryPercent) { v -> desk.set { it.copy(showBatteryPercent = v) } }
        Toggle("Show seconds", s.showSeconds) { v -> desk.set { it.copy(showSeconds = v) } }
        Toggle("Show menu bar background", s.menuBarBackground, divider = false) { v -> desk.set { it.copy(menuBarBackground = v) } }
    }
    FormSection("What works in Control Center", footer = null) {
        FormRow("Wi‑Fi", detail = "Shows live status. Android 10+ doesn't allow apps to switch Wi‑Fi, so the tile opens the system Wi‑Fi panel.")
        FormRow("Bluetooth", detail = "Shows on/off state (needs the Nearby devices permission on Android 12+). The tile opens Bluetooth settings.")
        FormRow("Sound", detail = "Changes the real media volume.")
        FormRow("Display", detail = "Changes brightness for this app's window.", divider = false)
    }
}

@Composable
private fun PrivacyPane(desk: DesktopController) {
    val fs = desk.fs
    val grants = remember(desk.fsVersion) { runCatching { fs.grantedTrees() }.getOrDefault(emptyList()) }
    FormSection("Files & Folders", footer = "Golden Gate Desktop can only see device folders you explicitly connect through the Android folder picker. Revoking removes access immediately.") {
        if (grants.isEmpty()) FormRow("No folders connected", detail = "Your private home folder needs no permission.")
        grants.forEachIndexed { i, uri ->
            val name = remember(uri) { runCatching { fs.treeRoot(uri).name }.getOrDefault(uri.lastPathSegment ?: "Folder") }
            FormRow(name, detail = uri.toString().take(64)) {
                PushButton("Revoke", { fs.revokeGrant(uri); desk.refreshFs() }, destructive = true)
            }
        }
        FormRow("Connect a folder", divider = false) { PushButton("Connect…", { io.github.goldengate.desktop.shell.connectFolder(desk) }) }
    }
    FormSection("Permissions") {
        FormRow("Nearby devices (Bluetooth status)", detail = if (desk.status.hasBluetoothPermission) "Allowed" else "Not allowed") {
            if (!desk.status.hasBluetoothPermission) PushButton("Allow…", { desk.bridge?.requestBluetoothPermission { desk.status.refreshBluetooth() } })
        }
        FormRow("Network state & Internet", detail = "Used for the Wi‑Fi indicator and the Browser.")
        FormRow("App permissions in Android Settings", divider = false) { PushButton("Open…", { desk.bridge?.openSystemPanel(SystemPanel.AppDetails) }) }
    }
    FormSection("Data", footer = "Nothing leaves your device. There are no analytics, accounts or trackers.") {
        FormRow("Home folder size", detail = FileSystem.formatSize(fs.home.walkTopDown().filter { it.isFile }.sumOf { it.length() }), divider = false)
    }
}

@Composable
private fun AccessibilityPane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Display") {
        Toggle("Reduce motion", s.reduceMotion, "Disables springs, the genie effect and Space sliding.", tag = "reduce-motion") { v -> desk.set { it.copy(reduceMotion = v) } }
        Toggle("Reduce transparency", s.reduceTransparency, "Replaces Liquid Glass with solid surfaces.", tag = "reduce-transparency") { v -> desk.set { it.copy(reduceTransparency = v) } }
        Toggle("Increase contrast", s.increaseContrast, "Adds borders around glass and controls.", divider = false) { v -> desk.set { it.copy(increaseContrast = v) } }
    }
    FormSection("Pointer") {
        SliderRow("Controller pointer size", s.pointerScale, 0.75f..2f, divider = false) { v -> desk.set { it.copy(pointerScale = v) } }
    }
}

@Composable
private fun KeyboardPane(desk: DesktopController) {
    val shortcuts = remember(desk.wm.focusedId) {
        (listOf(systemMenu(desk)) + menuBarMenus(desk) + hiddenShortcuts(desk)).flatMap { m -> m.allItems().filter { it.shortcut != null }.map { (if (m.title == "hidden") "System" else m.title.ifBlank { "System" }) to it } }
            .distinctBy { it.second.label }
    }
    FormSection("Keyboard", footer = "⌘ works with either the Meta (Windows/Search) key or Ctrl on Android keyboards. Shortcuts follow the app in front.") {
        FormRow("Connected keyboard", detail = if (desk.input.keyboard) "Detected" else "None detected — the on-screen keyboard is used for text", divider = false)
    }
    FormSection("Keyboard Shortcuts") {
        shortcuts.forEachIndexed { i, (menu, item) ->
            FormRow(item.label, detail = menu, divider = i < shortcuts.lastIndex) {
                DeskText(item.shortcut!!.label, Desk.type.mono, color = Desk.colors.secondaryText)
            }
        }
    }
}

@Composable
private fun MousePane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Pointing Devices", footer = "Input mode is detected automatically: a mouse shows hover effects and right-click menus; touch uses long-press for secondary click.") {
        FormRow("Mouse or trackpad", detail = if (desk.input.mouse || desk.input.touchpad) "Connected" else "Not connected")
        Toggle("Natural scrolling", s.naturalScrolling, "Content follows your finger direction on mice and trackpads.") { v -> desk.set { it.copy(naturalScrolling = v) } }
        Toggle("Long-press for secondary click (touch)", s.longPressSecondaryClick, divider = false) { v -> desk.set { it.copy(longPressSecondaryClick = v) } }
    }
    FormSection("Tracking") {
        SliderRow("Controller pointer speed", s.pointerSpeed, 0.4f..2.5f, divider = false, leading = "Slow", trailing = "Fast") { v -> desk.set { it.copy(pointerSpeed = v) } }
    }
}

@Composable
private fun ControllersPane(desk: DesktopController) {
    FormSection("Game Controllers", footer = "Left stick or D-pad moves the pointer · A clicks · X right-clicks · B goes back/escape · Y opens Mission Control · L1/R1 switch desktops · Start opens Spotlight.") {
        FormRow("Status", detail = if (desk.input.gamepad) "Controller connected" else "No controller connected")
        val names = desk.input.deviceNames
        if (names.isEmpty()) FormRow("Input devices", detail = "None reported", divider = false)
        names.forEachIndexed { i, n -> FormRow(n, divider = i < names.lastIndex) }
    }
}

@Composable
private fun BatteryPane(desk: DesktopController) {
    val st = desk.status
    FormSection("Battery") {
        FormRow("Level", detail = "${st.batteryLevel}%") {
            Box(Modifier.width(160.dp).height(10.dp).clip(CircleShape).background(Desk.colors.controlFillPressed)) {
                Box(Modifier.fillMaxWidth(st.batteryLevel / 100f).height(10.dp).clip(CircleShape).background(if (st.batteryLevel <= 20) Color(0xFFFF3B30) else Color(0xFF34C759)))
            }
        }
        FormRow("Power source", detail = if (st.charging) "Power adapter" else "Battery", divider = false)
    }
    FormSection("Energy", footer = "Lower the glass quality in Performance to save power on older devices.") {
        FormRow("Android battery settings", divider = false) { PushButton("Open…", { desk.bridge?.openSystemPanel(SystemPanel.AppDetails) }) }
    }
}

@Composable
private fun PerformancePane(desk: DesktopController) {
    val s = desk.settings
    FormSection("Rendering", footer = buildString {
        append("This device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}). ")
        append(if (GlassShaders.supportsRefraction) "GPU refraction shaders are supported." else if (GlassShaders.supportsBlur) "Live blur is supported; refraction needs Android 13." else "Live blur isn't available; the efficient mode is used.")
    }) {
        FormRow("Liquid Glass quality") {
            SegmentedControl(GlassQuality.entries.map { it.label }, s.glassQuality.coerceIn(0, 2), { i -> desk.set { it.copy(glassQuality = i) } }, Modifier.testTag("glass-quality"))
        }
        Toggle("Show frame rate", s.showFps, divider = false) { v -> desk.set { it.copy(showFps = v) } }
    }
}

@Composable
private fun AboutPane(desk: DesktopController) {
    val c = Desk.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF2A65A), Color(0xFFD9567A), Color(0xFF3A2C7A)))), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Emblem, Color.White, 50.dp)
        }
        DeskText("Golden Gate Desktop", Desk.type.headline, modifier = Modifier.padding(top = 10.dp))
        DeskText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Desk.type.caption, color = c.secondaryText)
    }
    FormSection {
        FormRow("Name", detail = desk.settings.computerName)
        FormRow("Device", detail = "${Build.MANUFACTURER} ${Build.MODEL}")
        FormRow("Android", detail = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        FormRow("More details", divider = false) { PushButton("System Information…", { desk.launch(AppIds.SYSINFO) }) }
    }
    FormSection(footer = "An unofficial, fan-made project inspired by the design of macOS 27. Not affiliated with, endorsed by, or sponsored by Apple Inc. macOS is a trademark of Apple Inc. All icons, wallpapers and sounds are original. Inter font © The Inter Project Authors (SIL OFL 1.1).") {
        FormRow("Licenses", divider = false) { PushButton("Tips & Shortcuts", { desk.launch(AppIds.HELP) }) }
    }
}

