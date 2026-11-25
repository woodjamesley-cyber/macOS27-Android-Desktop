package io.github.goldengate.desktop.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.MenuDef
import io.github.goldengate.desktop.ui.LocalDesktop

/** Maps an app id to its window content. */
@Composable
fun AppContent(win: DeskWindow) {
    when (win.appId) {
        AppIds.FINDER -> FinderApp(win)
        AppIds.SETTINGS -> SettingsApp(win)
        AppIds.BROWSER -> BrowserApp(win)
        AppIds.TERMINAL -> TerminalApp(win)
        AppIds.CALCULATOR -> CalculatorApp(win)
        AppIds.CALENDAR -> CalendarApp(win)
        AppIds.CLOCK -> ClockApp(win)
        AppIds.NOTES -> NotesApp(win)
        AppIds.PHOTOS -> PhotosApp(win)
        AppIds.FILES -> FilesApp(win)
        AppIds.MUSIC -> MusicApp(win)
        AppIds.SYSINFO -> SysInfoApp(win)
        AppIds.HELP -> TipsApp(win)
        AppIds.STORE -> AppStoreApp(win)
    }
}

/**
 * The argument this window was opened with, updated when a single-window app is asked to
 * show something new (e.g. Settings ▸ Wallpaper from the desktop menu).
 */
@Composable
fun rememberWindowArgument(win: DeskWindow): String? {
    val desk = LocalDesktop.current
    var arg by remember { mutableStateOf(win.argument) }
    val pending = desk.pendingArguments[win.id]
    LaunchedEffect(pending) {
        if (pending != null) { arg = pending; desk.pendingArguments.remove(win.id) }
    }
    return arg
}

/** Publishes app-specific menus to the menu bar while this window exists. */
@Composable
fun WindowMenus(win: DeskWindow, menus: List<MenuDef>) {
    androidx.compose.runtime.SideEffect { win.menus = menus }
    DisposableEffect(Unit) { onDispose { win.menus = emptyList() } }
}
