package io.github.goldengate.desktop.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppIds {
    const val FINDER = "finder"
    const val SETTINGS = "settings"
    const val BROWSER = "browser"
    const val TERMINAL = "terminal"
    const val CALCULATOR = "calculator"
    const val CALENDAR = "calendar"
    const val CLOCK = "clock"
    const val NOTES = "notes"
    const val PHOTOS = "photos"
    const val FILES = "files"
    const val MUSIC = "music"
    const val SYSINFO = "sysinfo"
    const val HELP = "help"
    // Pseudo items shown in the Dock
    const val LAUNCHER = "launcher"
    const val MISSION = "mission"
    const val TRASH = "trash"
    const val TRASH_FULL = "trash_full"
    const val SYSTEM = "system"

    val defaultDock = listOf(FINDER, LAUNCHER, BROWSER, NOTES, CALENDAR, PHOTOS, MUSIC, TERMINAL, CALCULATOR, CLOCK, FILES, SETTINGS)
}

enum class Appearance { Light, Dark, Auto }
enum class DockPosition { Bottom, Left, Right }
enum class MinimizeEffect(val label: String) { Genie("Genie (approximation)"), Scale("Scale") }

/** Immutable snapshot of every user preference. Persisted with SharedPreferences. */
@Immutable
data class SystemSettings(
    val appearance: Appearance = Appearance.Auto,
    val accent: Int = 0,
    val glassTint: Float = 0.35f,
    val glassQuality: Int = 0,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val wallpaper: String = "GoldenHour",
    val customWallpaper: String? = null,
    val dockIconSize: Float = 50f,
    val dockMagnification: Boolean = true,
    val dockMagnifyScale: Float = 1.65f,
    val dockPosition: DockPosition = DockPosition.Bottom,
    val dockAutoHide: Boolean = false,
    val dockShowRecents: Boolean = true,
    val dockApps: List<String> = AppIds.defaultDock,
    val minimizeEffect: MinimizeEffect = MinimizeEffect.Genie,
    val uiSounds: Boolean = true,
    val uiVolume: Float = 0.55f,
    val bannerSeconds: Int = 5,
    val doNotDisturb: Boolean = false,
    val mutedApps: Set<String> = emptySet(),
    val pointerScale: Float = 1f,
    val pointerSpeed: Float = 1f,
    val naturalScrolling: Boolean = false,
    val longPressSecondaryClick: Boolean = true,
    val uiScale: Float = 0f, // 0 = automatic
    val showFps: Boolean = false,
    val reopenWindows: Boolean = true,
    val menuBarBackground: Boolean = false,
    val clock24h: Boolean = false,
    val showSeconds: Boolean = false,
    val showBatteryPercent: Boolean = true,
    val nightShift: Boolean = false,
    val nightShiftWarmth: Float = 0.5f,
    val desktopIconSize: Float = 64f,
    val showDesktopIcons: Boolean = true,
    val userName: String = "Guest",
    val computerName: String = "Android Desktop",
    val demoNotifications: Boolean = true,
    val spaces: Int = 2,
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)

    var value: SystemSettings by mutableStateOf(load())
        private set

    fun update(block: (SystemSettings) -> SystemSettings) {
        val next = block(value)
        if (next != value) {
            value = next
            save(next)
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
        value = SystemSettings()
    }

    private fun load(): SystemSettings {
        val d = SystemSettings()
        val p = prefs
        return SystemSettings(
            appearance = enumOr(p.getString("appearance", null), d.appearance),
            accent = p.getInt("accent", d.accent),
            glassTint = p.getFloat("glassTint", d.glassTint),
            glassQuality = p.getInt("glassQuality", d.glassQuality),
            reduceTransparency = p.getBoolean("reduceTransparency", d.reduceTransparency),
            increaseContrast = p.getBoolean("increaseContrast", d.increaseContrast),
            reduceMotion = p.getBoolean("reduceMotion", d.reduceMotion),
            wallpaper = p.getString("wallpaper", d.wallpaper) ?: d.wallpaper,
            customWallpaper = p.getString("customWallpaper", null),
            dockIconSize = p.getFloat("dockIconSize", d.dockIconSize),
            dockMagnification = p.getBoolean("dockMagnification", d.dockMagnification),
            dockMagnifyScale = p.getFloat("dockMagnifyScale", d.dockMagnifyScale),
            dockPosition = enumOr(p.getString("dockPosition", null), d.dockPosition),
            dockAutoHide = p.getBoolean("dockAutoHide", d.dockAutoHide),
            dockShowRecents = p.getBoolean("dockShowRecents", d.dockShowRecents),
            dockApps = p.getString("dockApps", null)?.split(',')?.filter { it.isNotBlank() } ?: d.dockApps,
            minimizeEffect = enumOr(p.getString("minimizeEffect", null), d.minimizeEffect),
            uiSounds = p.getBoolean("uiSounds", d.uiSounds),
            uiVolume = p.getFloat("uiVolume", d.uiVolume),
            bannerSeconds = p.getInt("bannerSeconds", d.bannerSeconds),
            doNotDisturb = p.getBoolean("doNotDisturb", d.doNotDisturb),
            mutedApps = p.getStringSet("mutedApps", emptySet())?.toSet() ?: emptySet(),
            pointerScale = p.getFloat("pointerScale", d.pointerScale),
            pointerSpeed = p.getFloat("pointerSpeed", d.pointerSpeed),
            naturalScrolling = p.getBoolean("naturalScrolling", d.naturalScrolling),
            longPressSecondaryClick = p.getBoolean("longPressSecondaryClick", d.longPressSecondaryClick),
            uiScale = p.getFloat("uiScale", d.uiScale),
            showFps = p.getBoolean("showFps", d.showFps),
            reopenWindows = p.getBoolean("reopenWindows", d.reopenWindows),
            menuBarBackground = p.getBoolean("menuBarBackground", d.menuBarBackground),
            clock24h = p.getBoolean("clock24h", d.clock24h),
            showSeconds = p.getBoolean("showSeconds", d.showSeconds),
            showBatteryPercent = p.getBoolean("showBatteryPercent", d.showBatteryPercent),
            nightShift = p.getBoolean("nightShift", d.nightShift),
            nightShiftWarmth = p.getFloat("nightShiftWarmth", d.nightShiftWarmth),
            desktopIconSize = p.getFloat("desktopIconSize", d.desktopIconSize),
            showDesktopIcons = p.getBoolean("showDesktopIcons", d.showDesktopIcons),
            userName = p.getString("userName", d.userName) ?: d.userName,
            computerName = p.getString("computerName", d.computerName) ?: d.computerName,
            demoNotifications = p.getBoolean("demoNotifications", d.demoNotifications),
            spaces = p.getInt("spaces", d.spaces).coerceIn(1, 6),
        )
    }

    private fun save(s: SystemSettings) {
        prefs.edit().apply {
            putString("appearance", s.appearance.name)
            putInt("accent", s.accent)
            putFloat("glassTint", s.glassTint)
            putInt("glassQuality", s.glassQuality)
            putBoolean("reduceTransparency", s.reduceTransparency)
            putBoolean("increaseContrast", s.increaseContrast)
            putBoolean("reduceMotion", s.reduceMotion)
            putString("wallpaper", s.wallpaper)
            putString("customWallpaper", s.customWallpaper)
            putFloat("dockIconSize", s.dockIconSize)
            putBoolean("dockMagnification", s.dockMagnification)
            putFloat("dockMagnifyScale", s.dockMagnifyScale)
            putString("dockPosition", s.dockPosition.name)
            putBoolean("dockAutoHide", s.dockAutoHide)
            putBoolean("dockShowRecents", s.dockShowRecents)
            putString("dockApps", s.dockApps.joinToString(","))
            putString("minimizeEffect", s.minimizeEffect.name)
            putBoolean("uiSounds", s.uiSounds)
            putFloat("uiVolume", s.uiVolume)
            putInt("bannerSeconds", s.bannerSeconds)
            putBoolean("doNotDisturb", s.doNotDisturb)
            putStringSet("mutedApps", s.mutedApps)
            putFloat("pointerScale", s.pointerScale)
            putFloat("pointerSpeed", s.pointerSpeed)
            putBoolean("naturalScrolling", s.naturalScrolling)
            putBoolean("longPressSecondaryClick", s.longPressSecondaryClick)
            putFloat("uiScale", s.uiScale)
            putBoolean("showFps", s.showFps)
            putBoolean("reopenWindows", s.reopenWindows)
            putBoolean("menuBarBackground", s.menuBarBackground)
            putBoolean("clock24h", s.clock24h)
            putBoolean("showSeconds", s.showSeconds)
            putBoolean("showBatteryPercent", s.showBatteryPercent)
            putBoolean("nightShift", s.nightShift)
            putFloat("nightShiftWarmth", s.nightShiftWarmth)
            putFloat("desktopIconSize", s.desktopIconSize)
            putBoolean("showDesktopIcons", s.showDesktopIcons)
            putString("userName", s.userName)
            putString("computerName", s.computerName)
            putBoolean("demoNotifications", s.demoNotifications)
            putInt("spaces", s.spaces)
        }.apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
