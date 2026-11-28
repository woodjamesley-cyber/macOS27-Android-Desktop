package io.github.goldengate.desktop.core

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import io.github.goldengate.desktop.audio.MusicPlayer
import io.github.goldengate.desktop.audio.UiSound
import io.github.goldengate.desktop.audio.UiSounds
import io.github.goldengate.desktop.fs.FileKind
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.fs.FsEntry
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.ui.Glyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Activity-provided capabilities (pickers, system panels, brightness). */
interface PlatformBridge {
    fun pickFolder(onResult: (Uri?) -> Unit)
    fun pickImage(onResult: (Uri?) -> Unit)
    fun pickDocument(mimeTypes: Array<String>, onResult: (Uri?) -> Unit)
    fun openSystemPanel(panel: SystemPanel)
    fun openExternally(ref: FsRef)
    fun setAppBrightness(value: Float)
    fun appBrightness(): Float
    fun requestBluetoothPermission(onResult: (Boolean) -> Unit)
    fun quitApp()
    fun toastFallback(text: String)

    // ---------------- App Store & Home app ----------------
    /** Picks an .apk file (the store copies it into its private library before installing). */
    fun pickApk(onResult: (Uri?) -> Unit)
    fun isPackageInstalled(packageName: String): Boolean
    fun launchPackage(packageName: String): Boolean
    fun openStoreListing(packageName: String)
    /** Installs a retained APK through Android's package installer. Returns false if it couldn't start.
     *  When [launchAfterInstall] is set, the app opens by itself once Android finishes installing. */
    fun installApk(file: File, launchAfterInstall: String? = null): Boolean
    /** The real launcher icon of an installed app, if it is visible to this app. */
    fun appIcon(packageName: String): Bitmap? = null
    fun openAppSettings(packageName: String)
    fun isDefaultLauncher(): Boolean
    /** Opens Android's "Home app" chooser (or its Settings page) and reports whether we became Home. */
    fun requestDefaultLauncher(onResult: (Boolean) -> Unit)
    fun openHomeSettings()
}

enum class SystemPanel { Wifi, Bluetooth, Internet, Volume, Display, Cast, AppDetails, Nfc }

sealed class Overlay {
    data object None : Overlay()
    data object ControlCenter : Overlay()
    data object NotificationCenter : Overlay()
    data object Spotlight : Overlay()
    data object Launcher : Overlay()
    data object MissionControl : Overlay()
    data class AppSwitcher(val index: Int) : Overlay()
    data object ForceQuit : Overlay()
    data object Locked : Overlay()
    data object Sleep : Overlay()
}

@Immutable
data class OpenMenu(val key: String, val anchor: Offset, val entries: List<MenuEntry>, val fromMenuBar: Int = -1)

@Immutable
data class DialogSpec(
    val title: String,
    val message: String = "",
    val input: String? = null,
    val confirm: String = "OK",
    val cancel: String? = "Cancel",
    val destructive: Boolean = false,
    val glyph: Glyph? = null,
    val onConfirm: (String) -> Unit = {},
)

@Immutable
data class Hud(val glyph: Glyph, val value: Float, val label: String, val at: Long = System.currentTimeMillis())

/**
 * Central state of the desktop environment. Owned by [DesktopViewModel], so it survives
 * configuration changes (rotation, keyboard attach, window resizing on foldables).
 */
class DesktopController(val app: Application, val scope: CoroutineScope) {
    val settingsStore = SettingsStore(app)
    val settings: SystemSettings get() = settingsStore.value
    val wm = WindowManager().apply { spaceCount = settingsStore.value.spaces }
    val notifications = NotificationCenter()
    val fs = FileSystem(app)
    val sounds = UiSounds(app, scope)
    val music = MusicPlayer(app, scope)
    val status = SystemStatus(app)
    val calendar = CalendarStore(app)
    val timers = TimerService()
    val input = InputState()

    var bridge: PlatformBridge? = null

    var overlay by mutableStateOf<Overlay>(Overlay.None)
    /** Re-opens the Setup Assistant from System Settings even after setup is complete. */
    var showSetupAssistant by mutableStateOf(false)
    /** Step the Setup Assistant opens on (Settings jumps straight to the account step). */
    var setupStartStep by mutableIntStateOf(0)
    var openMenu by mutableStateOf<OpenMenu?>(null)
    var dialog by mutableStateOf<DialogSpec?>(null)
    var hud by mutableStateOf<Hud?>(null)
    var snapPreview by mutableStateOf<Bounds?>(null)
    var fsVersion by mutableIntStateOf(0)
        private set
    var trashCount by mutableIntStateOf(0)
        private set

    val dockBounce = mutableStateMapOf<String, Long>()
    /** Dock icon centers in root pixels, used as the minimize/launch animation target. */
    val dockIconCenters = mutableStateMapOf<String, Offset>()
    val desktopSelection = mutableStateListOf<String>()
    val recentItems = mutableStateListOf<String>()
    var fileClipboard by mutableStateOf<Pair<List<FsRef>, Boolean>?>(null) // refs, isCut
    var rootSizePx: Rect by mutableStateOf(Rect.Zero)

    private val prefs = app.getSharedPreferences("desktop_state", Context.MODE_PRIVATE)

    init {
        seedHome()
        refreshFs()
        music.loadLibrary(fs.music)
        sounds.enabled = settings.uiSounds
        sounds.volume = settings.uiVolume
        startTickers()
    }

    // ---------------- settings helpers ----------------
    fun updateSettings(block: (SystemSettings) -> SystemSettings) {
        settingsStore.update(block)
        sounds.enabled = settings.uiSounds
        sounds.volume = settings.uiVolume
        wm.spaceCount = settings.spaces.coerceAtLeast(wm.spaceCount.coerceAtMost(settings.spaces))
    }

    fun play(s: UiSound) = sounds.play(s)

    // ---------------- apps & windows ----------------
    fun launch(appId: String, argument: String? = null, newWindow: Boolean = false) {
        closeMenus()
        when (appId) {
            AppIds.LAUNCHER -> { toggleOverlay(Overlay.Launcher); return }
            AppIds.MISSION -> { toggleOverlay(Overlay.MissionControl); return }
            AppIds.TRASH, AppIds.TRASH_FULL -> { launch(AppIds.FINDER, "trash", newWindow = true); return }
        }
        val spec = AppRegistry.get(appId) ?: return
        if (overlay != Overlay.None && overlay !is Overlay.AppSwitcher) overlay = Overlay.None
        val existing = wm.windows.filter { it.appId == appId }
        if (!newWindow && argument == null && existing.isNotEmpty()) {
            val target = existing.lastOrNull { !it.minimized && it.space == wm.currentSpace } ?: existing.last()
            wm.focus(target.id)
            return
        }
        if (spec.singleWindow && existing.isNotEmpty()) {
            val w = existing.last()
            wm.focus(w.id)
            if (argument != null) pendingArguments[w.id] = argument
            return
        }
        dockBounce[appId] = System.currentTimeMillis()
        play(UiSound.Open)
        wm.open(appId, spec.name, spec.width, spec.height, argument)
        argument?.let { addRecent(it) }
    }

    /** Arguments delivered to an already-open single-window app (e.g. Settings pane to show). */
    val pendingArguments = mutableStateMapOf<Int, String>()

    fun close(id: Int) {
        val w = wm.byId(id) ?: return
        w.closing = true
        play(UiSound.Close)
        scope.launch {
            delay(if (settings.reduceMotion) 0 else 180)
            wm.close(id)
        }
    }

    fun minimize(id: Int) { play(UiSound.Pop); wm.minimize(id) }

    fun quit(appId: String) {
        wm.windows.filter { it.appId == appId }.forEach { close(it.id) }
        if (appId == AppIds.MUSIC) music.pause()
    }

    fun hideOthers() {
        val f = wm.focused ?: return
        wm.windows.filter { it.appId != f.appId && it.space == wm.currentSpace }.forEach { wm.minimize(it.id) }
        wm.focus(f.id)
    }

    fun activeAppId(): String = wm.focused?.appId ?: AppIds.FINDER

    fun runningApps() = wm.runningApps()

    // ---------------- overlays & menus ----------------
    fun toggleOverlay(o: Overlay) {
        openMenu = null
        overlay = if (overlay == o) Overlay.None else o
        if (overlay == Overlay.ControlCenter) { status.refreshVolume(); status.refreshBluetooth() }
        if (overlay != Overlay.None) play(UiSound.Click)
    }

    fun showMenu(menu: OpenMenu) {
        if (overlay != Overlay.None && overlay != Overlay.Locked) overlay = Overlay.None
        openMenu = if (openMenu?.key == menu.key) null else menu
    }

    fun showContextMenu(at: Offset, entries: List<MenuEntry>) {
        if (entries.isEmpty()) return
        overlay = Overlay.None
        openMenu = OpenMenu("ctx" + System.nanoTime(), at, entries)
    }

    fun closeMenus() { openMenu = null }

    fun dismissAll() {
        openMenu = null
        if (overlay !is Overlay.Locked && overlay !is Overlay.Sleep) overlay = Overlay.None
    }

    fun showHud(glyph: Glyph, value: Float, label: String) { hud = Hud(glyph, value, label) }

    // ---------------- notifications ----------------
    fun notify(
        appId: String, title: String, body: String,
        actions: List<NotificationAction> = emptyList(), persistent: Boolean = false, onClick: (() -> Unit)? = null,
    ) {
        val n = notifications.post(
            appId, AppRegistry.name(appId), title, body, actions, persistent,
            doNotDisturb = settings.doNotDisturb, muted = appId in settings.mutedApps,
            onClick = onClick ?: { launch(appId) },
        )
        if (n != null) play(UiSound.Notification)
    }

    fun postDemoNotifications() {
        scope.launch {
            delay(2500)
            notify(AppIds.HELP, "Welcome to Golden Gate Desktop", "Tap the Dock to open apps, drag title bars to move windows, and tap the clock for Notification Center.")
            delay(3200)
            val next = calendar.events.filter { it.start > System.currentTimeMillis() }.minByOrNull { it.start }
            notify(
                AppIds.CALENDAR, next?.title ?: "Design review", "Coming up " + (next?.let { java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault()).format(it.start) } ?: "today"),
                actions = listOf(NotificationAction("Snooze") { }, NotificationAction("Open") { launch(AppIds.CALENDAR) }),
            )
            delay(3200)
            notify(AppIds.MUSIC, "New in your library", "“Golden Hour Sessions” — five original generative tracks are ready to play.",
                actions = listOf(NotificationAction("Play") { music.library.firstOrNull()?.let { music.play(it) } }))
            delay(2600)
            notify(AppIds.NOTES, "Shared note updated", "“Ideas for the weekend” was edited 2 minutes ago.")
        }
    }

    // ---------------- files ----------------
    fun refreshFs() { fsVersion++; trashCount = fs.trashCount() }

    fun openEntry(entry: FsEntry) {
        addRecent(entry.ref.key)
        when (entry.kind) {
            FileKind.Folder -> launch(AppIds.FINDER, entry.ref.key, newWindow = true)
            FileKind.Text, FileKind.Markdown, FileKind.Csv -> launch(AppIds.NOTES, entry.ref.key, newWindow = true)
            FileKind.Image -> launch(AppIds.PHOTOS, entry.ref.key, newWindow = true)
            FileKind.Audio -> {
                when (val r = entry.ref) {
                    is FsRef.Local -> music.addExternal(Uri.fromFile(r.file), r.name)
                    is FsRef.Saf -> music.addExternal(r.uri, r.name)
                }
                launch(AppIds.MUSIC)
            }
            FileKind.Web -> launch(AppIds.BROWSER, entry.ref.key, newWindow = true)
            else -> bridge?.openExternally(entry.ref)
        }
    }

    fun refFromKey(key: String): FsRef? = when {
        key.startsWith("file:") -> FsRef.Local(File(key.removePrefix("file:")))
        key.startsWith("saf|") -> {
            val parts = key.split("|", limit = 5)
            if (parts.size == 5) FsRef.Saf(Uri.parse(parts[2]), Uri.parse(parts[1]), parts[4], parts[3] == "true") else null
        }
        else -> null
    }

    fun trash(refs: List<FsRef>) {
        var ok = 0
        for (r in refs) try { fs.moveToTrash(r); ok++ } catch (t: Throwable) { error(t.message ?: "Couldn't move to Trash") }
        if (ok > 0) play(UiSound.Trash)
        refreshFs()
    }

    fun confirmEmptyTrash() {
        dialog = DialogSpec(
            "Are you sure you want to permanently erase the items in the Trash?",
            "You can't undo this action.", confirm = "Empty Trash", destructive = true, glyph = Glyph.Trash,
        ) { fs.emptyTrash(); play(UiSound.Trash); refreshFs() }
    }

    fun error(message: String) {
        play(UiSound.Error)
        dialog = DialogSpec("Something went wrong", message, cancel = null, glyph = Glyph.Info)
    }

    fun addRecent(key: String) {
        recentItems.remove(key)
        recentItems.add(0, key)
        while (recentItems.size > 10) recentItems.removeAt(recentItems.lastIndex)
    }

    // ---------------- desktop icon positions ----------------
    val iconPositions = mutableStateMapOf<String, Offset>().apply {
        val o = JSONObject(prefs.getString("iconPositions", "{}") ?: "{}")
        o.keys().forEach { k -> val a = o.getJSONArray(k); put(k, Offset(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())) }
    }

    fun saveIconPositions() {
        val o = JSONObject()
        iconPositions.forEach { (k, v) -> o.put(k, JSONArray().put(v.x.toDouble()).put(v.y.toDouble())) }
        prefs.edit().putString("iconPositions", o.toString()).apply()
    }

    // ---------------- session persistence ----------------
    fun saveSession() {
        val arr = JSONArray()
        wm.windows.forEach { w ->
            arr.put(JSONObject().put("app", w.appId).put("x", w.x.toDouble()).put("y", w.y.toDouble()).put("w", w.w.toDouble()).put("h", w.h.toDouble())
                .put("space", w.space).put("min", w.minimized).put("arg", w.argument ?: ""))
        }
        prefs.edit().putString("session", arr.toString()).putInt("spaceCount", wm.spaceCount).apply()
    }

    private var restored = false
    fun restoreSession(): Boolean {
        if (restored) return false
        restored = true
        if (!settings.reopenWindows) return false
        val arr = JSONArray(prefs.getString("session", "[]"))
        if (arr.length() == 0) return false
        wm.spaceCount = prefs.getInt("spaceCount", wm.spaceCount).coerceIn(1, 6)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val spec = AppRegistry.get(o.getString("app")) ?: continue
            val w = wm.open(spec.id, spec.name, spec.width, spec.height, o.optString("arg").ifBlank { null },
                at = Bounds(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat()))
            w.space = o.optInt("space").coerceIn(0, wm.spaceCount - 1)
            if (o.optBoolean("min")) wm.minimize(w.id)
        }
        wm.switchSpace(0)
        wm.clampAll()
        return true
    }

    fun restartDesktop() {
        wm.windows.toList().forEach { wm.close(it.id) }
        overlay = Overlay.None
        openMenu = null
        play(UiSound.Open)
    }

    // ---------------- keyboard shortcuts ----------------
    /**
     * Runs the first enabled menu item whose shortcut matches. "cmd" shortcuts accept Meta or Ctrl
     * (Android keyboards rarely have a ⌘ key); "ctrl" shortcuts need Ctrl without Meta.
     */
    fun handleShortcut(key: Key, meta: Boolean, ctrl: Boolean, shift: Boolean, alt: Boolean, menus: List<MenuDef>): Boolean {
        for (m in menus) for (item in m.allItems()) {
            val s = item.shortcut ?: continue
            if (!item.enabled) continue
            if (s.key != key || s.shift != shift || s.alt != alt) continue
            val modsOk = when {
                s.cmd && s.ctrl -> meta && ctrl
                s.cmd -> meta || ctrl
                s.ctrl -> ctrl && !meta
                else -> !meta && !ctrl
            }
            if (modsOk) {
                closeMenus()
                item.action()
                return true
            }
        }
        return false
    }

    // ---------------- background tickers ----------------
    private fun startTickers() {
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000)
                if (timers.timerRunning && timers.remaining() <= 0L) {
                    timers.timerRunning = false
                    notify(AppIds.CLOCK, "Timer done", "Your ${formatDuration(timers.timerTotal)} timer has finished.",
                        actions = listOf(NotificationAction("Repeat") { timers.startTimer(timers.timerTotal) }), persistent = true)
                }
                val now = System.currentTimeMillis()
                calendar.events.filter { !it.notified && it.start - now in 0..10 * 60_000L }.forEach { e ->
                    calendar.update(e.copy(notified = true))
                    notify(AppIds.CALENDAR, e.title, "Starts in ${((e.start - now) / 60000).coerceAtLeast(1)} minutes",
                        actions = listOf(NotificationAction("Open") { launch(AppIds.CALENDAR) }))
                }
                hud?.let { if (now - it.at > 1400) hud = null }
            }
        }
    }

    private fun seedHome() {
        if (prefs.getBoolean("seeded_v1", false)) return
        fs.desktop; fs.downloads; fs.music
        File(fs.desktop, "Welcome.txt").writeText(
            """
            Welcome to Golden Gate Desktop
            ==============================

            An unofficial, fan-made desktop environment for Android, inspired by macOS 27.

            • Tap an app in the Dock to open it. Drag a window by its toolbar.
            • Long-press (or right-click) anywhere for a context menu.
            • Tap the clock for Notification Center and the switches icon for Control Center.
            • ⌘Space opens Spotlight, ⌘Tab switches apps, Ctrl+↑ opens Mission Control.

            This folder is your private home directory. Connect real device folders from
            Finder ▸ “Connect Folder…” (Android will ask you to choose and grant access).
            """.trimIndent()
        )
        File(fs.desktop, "Projects").mkdirs()
        File(fs.desktop, "Projects/Roadmap.md").writeText("# Roadmap\n\n- [x] Liquid Glass renderer\n- [x] Window manager\n- [ ] Widgets on the desktop\n")
        File(fs.documents, "Ideas for the weekend.txt").writeText("Ideas for the weekend\n\n- Ride the ferry\n- Try the new bakery\n- Finish the sketchbook\n")
        File(fs.documents, "Budget.csv").writeText("Item,Amount\nRent,1450\nGroceries,380\nTransit,96\nFun,150\n")
        File(fs.documents, "Notes").mkdirs()
        File(fs.documents, "Notes/Shopping list.txt").writeText("Shopping list\n\nOat milk\nLemons\nSourdough\nCoffee beans\n")
        File(fs.documents, "Notes/Meeting notes.txt").writeText("Meeting notes\n\nDesign review — glass slider defaults to 35% tint.\nAction: test on Snapdragon tablets.\n")
        File(fs.downloads, "readme.md").writeText("# Downloads\n\nFiles you save from the browser appear here.\n")
        prefs.edit().putBoolean("seeded_v1", true).apply()
    }

    fun onStart() { status.start() }
    fun onStop() { status.stop(); saveSession(); saveIconPositions() }
    fun release() { sounds.release(); music.release() }

    companion object {
        fun formatDuration(ms: Long): String {
            val s = ms / 1000
            val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }
}

/** Input devices currently attached (updated by the Activity via InputManager callbacks). */
class InputState {
    var keyboard by mutableStateOf(false)
    var mouse by mutableStateOf(false)
    var touchpad by mutableStateOf(false)
    var gamepad by mutableStateOf(false)
    var touchscreen by mutableStateOf(true)
    var lastPointerWasMouse by mutableStateOf(false)
    val deviceNames = mutableStateListOf<String>()
    /** Virtual cursor driven by a game controller (root px). */
    var controllerCursor by mutableStateOf<Offset?>(null)
}
