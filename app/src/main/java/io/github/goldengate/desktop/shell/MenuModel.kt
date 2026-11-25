package io.github.goldengate.desktop.shell

import androidx.compose.ui.input.key.Key
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.DialogSpec
import io.github.goldengate.desktop.core.DockPosition
import io.github.goldengate.desktop.core.MenuDef
import io.github.goldengate.desktop.core.MenuEntry
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.SnapZone
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.ui.Glyph
import java.io.File

/** The system (emblem) menu at the far left of the menu bar. */
fun systemMenu(desk: DesktopController): MenuDef = MenuDef("", glyph = Glyph.Emblem, entries = entries {
    item("About This Device") { desk.launch(AppIds.SYSINFO) }
    separator()
    item("System Settings…", Shortcut(Key.Comma, alt = true)) { desk.launch(AppIds.SETTINGS) }
    item("App Store…") { desk.launch(AppIds.STORE) }
    separator()
    submenu("Recent Items") {
        if (desk.recentItems.isEmpty()) item("No Recent Items", enabled = false) {}
        desk.recentItems.take(8).forEach { key ->
            val ref = desk.refFromKey(key)
            if (ref != null) item(ref.name) { if (ref is FsRef.Local && ref.file.exists()) desk.openEntry(desk.fs.localEntry(ref.file)) }
        }
        separator()
        item("Clear Menu", enabled = desk.recentItems.isNotEmpty()) { desk.recentItems.clear() }
    }
    separator()
    item("Force Quit…", Shortcut(Key.Escape, alt = true)) { desk.overlay = Overlay.ForceQuit }
    separator()
    item("Sleep") { desk.overlay = Overlay.Sleep }
    item("Restart…") {
        desk.dialog = DialogSpec("Are you sure you want to restart the desktop now?", "All open windows will be closed.", confirm = "Restart", glyph = Glyph.Power) { desk.restartDesktop() }
    }
    item("Shut Down…") {
        desk.dialog = DialogSpec("Are you sure you want to shut down?", "The desktop app will close. Your windows will reopen next time if that option is enabled.", confirm = "Shut Down", glyph = Glyph.Power) {
            desk.saveSession(); desk.bridge?.quitApp()
        }
    }
    separator()
    item("Lock Screen", Shortcut(Key.Q, ctrl = true)) { desk.overlay = Overlay.Locked }
    item("Log Out ${desk.settings.userName}…", Shortcut(Key.Q, shift = true)) {
        desk.dialog = DialogSpec("Are you sure you want to quit all apps and log out now?", confirm = "Log Out", glyph = Glyph.Person) { desk.restartDesktop(); desk.overlay = Overlay.Locked }
    }
})

/** Desktop (Finder) menus used when no window is focused. */
fun finderDesktopMenus(desk: DesktopController): List<MenuDef> = listOf(
    menu("File") {
        item("New Finder Window", Shortcut(Key.N)) { desk.launch(AppIds.FINDER, newWindow = true) }
        item("New Folder", Shortcut(Key.N, shift = true)) {
            desk.fs.createFolder(FsRef.Local(desk.fs.desktop), "untitled folder"); desk.refreshFs()
        }
        item("New Text Document") { desk.fs.createFile(FsRef.Local(desk.fs.desktop), "Untitled.txt", ""); desk.refreshFs() }
        separator()
        item("Connect Folder…") { connectFolder(desk) }
    },
    menu("Edit") {
        item("Paste Item", Shortcut(Key.V), enabled = desk.fileClipboard != null) { pasteInto(desk, FsRef.Local(desk.fs.desktop)) }
        item("Select All", Shortcut(Key.A)) {
            desk.desktopSelection.clear()
            desk.fs.list(FsRef.Local(desk.fs.desktop)).forEach { desk.desktopSelection.add(it.ref.key) }
        }
    },
    menu("View") {
        item("Clean Up") { desk.iconPositions.clear(); desk.saveIconPositions() }
        item(if (desk.settings.showDesktopIcons) "Hide Desktop Icons" else "Show Desktop Icons") { desk.updateSettings { it.copy(showDesktopIcons = !it.showDesktopIcons) } }
        separator()
        item("Mission Control", Shortcut(Key.DirectionUp, cmd = false, ctrl = true)) { desk.toggleOverlay(Overlay.MissionControl) }
    },
    menu("Go") {
        item("Desktop", Shortcut(Key.D, shift = true)) { desk.launch(AppIds.FINDER, "file:" + desk.fs.desktop.absolutePath, true) }
        item("Documents", Shortcut(Key.O, shift = true)) { desk.launch(AppIds.FINDER, "file:" + desk.fs.documents.absolutePath, true) }
        item("Downloads", Shortcut(Key.L, alt = true)) { desk.launch(AppIds.FINDER, "file:" + desk.fs.downloads.absolutePath, true) }
        item("Home", Shortcut(Key.H, shift = true)) { desk.launch(AppIds.FINDER, "file:" + desk.fs.home.absolutePath, true) }
        item("Applications", Shortcut(Key.A, shift = true)) { desk.toggleOverlay(Overlay.Launcher) }
        separator()
        item("Trash") { desk.launch(AppIds.FINDER, "trash", true) }
    },
)

fun connectFolder(desk: DesktopController) {
    desk.bridge?.pickFolder { uri ->
        if (uri != null) {
            desk.fs.persistGrant(uri)
            desk.refreshFs()
            desk.notify(AppIds.FINDER, "Folder connected", "“${desk.fs.treeRoot(uri).name}” is now available in the Finder sidebar.")
        }
    }
}

fun pasteInto(desk: DesktopController, dest: FsRef) {
    val clip = desk.fileClipboard ?: return
    try {
        for (r in clip.first) if (clip.second) desk.fs.move(r, dest) else desk.fs.copy(r, dest)
        if (clip.second) desk.fileClipboard = null
    } catch (t: Throwable) { desk.error(t.message ?: "Couldn't paste.") }
    desk.refreshFs()
}

/** Complete menu bar contents for the active app (app menu, File … Help). */
fun menuBarMenus(desk: DesktopController): List<MenuDef> {
    val win = desk.wm.focused
    val appId = win?.appId ?: AppIds.FINDER
    val name = AppRegistry.name(appId)
    val appMenus = win?.menus?.takeIf { it.isNotEmpty() } ?: if (win == null) finderDesktopMenus(desk) else emptyList()
    val out = ArrayList<MenuDef>()
    out += menu(name, bold = true) {
        item("About $name") {
            desk.dialog = DialogSpec(name, "Version 1.0 — part of Golden Gate Desktop, an unofficial macOS‑inspired desktop for Android.", cancel = null, glyph = Glyph.Info)
        }
        separator()
        item("Settings…", Shortcut(Key.Comma)) { desk.launch(AppIds.SETTINGS) }
        separator()
        item("Hide $name", Shortcut(Key.H), enabled = win != null) { desk.wm.windows.filter { it.appId == appId }.forEach { desk.wm.minimize(it.id) } }
        item("Hide Others", Shortcut(Key.H, alt = true), enabled = win != null) { desk.hideOthers() }
        item("Show All") { desk.wm.windows.forEach { it.minimized = false } }
        separator()
        item("Quit $name", Shortcut(Key.Q), enabled = win != null) { desk.quit(appId) }
    }
    val file = appMenus.firstOrNull { it.title == "File" }
    out += MenuDef("File", (file?.entries ?: entries { item("New Window", Shortcut(Key.N)) { desk.launch(appId, newWindow = true) } }) + entries {
        separator()
        item("Close Window", Shortcut(Key.W), enabled = win != null) { win?.let { desk.close(it.id) } }
    })
    out += appMenus.firstOrNull { it.title == "Edit" } ?: menu("Edit") {
        item("Undo", Shortcut(Key.Z), enabled = false) {}
        item("Redo", Shortcut(Key.Z, shift = true), enabled = false) {}
        separator()
        item("Cut", Shortcut(Key.X), enabled = false) {}
        item("Copy", Shortcut(Key.C), enabled = false) {}
        item("Paste", Shortcut(Key.V), enabled = false) {}
        item("Select All", Shortcut(Key.A), enabled = false) {}
    }
    out += appMenus.filter { it.title != "File" && it.title != "Edit" }
    out += windowMenu(desk)
    out += menu("Help") {
        item("$name Help") { desk.launch(AppIds.HELP, appId) }
        item("Keyboard Shortcuts") { desk.launch(AppIds.HELP, "shortcuts") }
    }
    return out
}

fun windowMenu(desk: DesktopController): MenuDef {
    val win = desk.wm.focused
    return menu("Window") {
        item("Minimize", Shortcut(Key.M), enabled = win != null) { win?.let { desk.minimize(it.id) } }
        item("Zoom", enabled = win != null) { win?.let { desk.wm.toggleZoom(it.id) } }
        item("Fill", Shortcut(Key.F, cmd = false, ctrl = true, alt = true), enabled = win != null) { win?.let { desk.wm.snap(it.id, SnapZone.Fill) } }
        item("Center", enabled = win != null) { win?.let { desk.wm.center(it.id) } }
        submenu("Move & Resize") {
            item("Left", Shortcut(Key.DirectionLeft, cmd = false, ctrl = true, alt = true)) { win?.let { desk.wm.snap(it.id, SnapZone.Left) } }
            item("Right", Shortcut(Key.DirectionRight, cmd = false, ctrl = true, alt = true)) { win?.let { desk.wm.snap(it.id, SnapZone.Right) } }
            item("Top Left") { win?.let { desk.wm.snap(it.id, SnapZone.TopLeft) } }
            item("Top Right") { win?.let { desk.wm.snap(it.id, SnapZone.TopRight) } }
            item("Bottom Left") { win?.let { desk.wm.snap(it.id, SnapZone.BottomLeft) } }
            item("Bottom Right") { win?.let { desk.wm.snap(it.id, SnapZone.BottomRight) } }
        }
        if (desk.wm.spaceCount > 1 && win != null) submenu("Move to") {
            for (i in 0 until desk.wm.spaceCount) item("Desktop ${i + 1}", checked = win.space == i) { desk.wm.moveToSpace(win.id, i) }
        }
        separator()
        item("Cycle Through Windows", Shortcut(Key.Grave, keyLabel = "`")) { desk.wm.cycle() }
        item("Bring All to Front") { desk.wm.windows.filter { it.space == desk.wm.currentSpace }.forEach { it.minimized = false } }
        separator()
        desk.wm.windows.filter { it.space == desk.wm.currentSpace }.forEach { w ->
            item(w.title, checked = w.id == desk.wm.focusedId) { desk.wm.focus(w.id) }
        }
    }
}

/** Shortcuts that are not shown in any menu. */
fun hiddenShortcuts(desk: DesktopController): List<MenuDef> = listOf(
    MenuDef("hidden", entries {
        item("Spotlight", Shortcut(Key.Spacebar)) { desk.toggleOverlay(Overlay.Spotlight) }
        item("Mission Control", Shortcut(Key.DirectionUp, cmd = false, ctrl = true)) { desk.toggleOverlay(Overlay.MissionControl) }
        item("Mission Control F3", Shortcut(Key.F3, cmd = false)) { desk.toggleOverlay(Overlay.MissionControl) }
        item("Previous Desktop", Shortcut(Key.DirectionLeft, cmd = false, ctrl = true)) { desk.wm.switchSpace(desk.wm.currentSpace - 1) }
        item("Next Desktop", Shortcut(Key.DirectionRight, cmd = false, ctrl = true)) { desk.wm.switchSpace(desk.wm.currentSpace + 1) }
        item("Toggle Dock Hiding", Shortcut(Key.D, alt = true)) { desk.updateSettings { it.copy(dockAutoHide = !it.dockAutoHide) } }
        item("Apps", Shortcut(Key.L, shift = true)) { desk.toggleOverlay(Overlay.Launcher) }
        item("Notification Center", Shortcut(Key.N, alt = true)) { desk.toggleOverlay(Overlay.NotificationCenter) }
        item("Control Center", Shortcut(Key.C, alt = true)) { desk.toggleOverlay(Overlay.ControlCenter) }
        item("Dock Left") { desk.updateSettings { it.copy(dockPosition = DockPosition.Left) } }
    })
)

@Suppress("unused")
private fun MenuEntry.Item.isEnabled() = enabled

@Suppress("unused")
private fun homeFile(desk: DesktopController, name: String) = File(desk.fs.home, name)
