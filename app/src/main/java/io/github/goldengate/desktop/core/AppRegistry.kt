package io.github.goldengate.desktop.core

/** Static description of an app. Content composables are mapped in apps/AppHost.kt. */
data class AppSpec(
    val id: String,
    val name: String,
    val width: Float,
    val height: Float,
    val minWidth: Float = 320f,
    val minHeight: Float = 220f,
    val singleWindow: Boolean = false,
    val category: String = "Utilities",
    val keywords: List<String> = emptyList(),
)

object AppRegistry {
    val apps: List<AppSpec> = listOf(
        AppSpec(AppIds.FINDER, "Finder", 820f, 500f, 460f, 280f, category = "System", keywords = listOf("files", "folders", "browse")),
        AppSpec(AppIds.SETTINGS, "System Settings", 760f, 560f, 600f, 380f, singleWindow = true, category = "System", keywords = listOf("preferences", "appearance", "wallpaper", "dock")),
        AppSpec(AppIds.BROWSER, "Browser", 940f, 620f, 420f, 300f, category = "Internet", keywords = listOf("web", "internet", "safari", "url")),
        AppSpec(AppIds.TERMINAL, "Terminal", 640f, 400f, 360f, 220f, category = "Developer", keywords = listOf("shell", "console", "command")),
        AppSpec(AppIds.CALCULATOR, "Calculator", 260f, 400f, 240f, 360f, singleWindow = true, category = "Utilities", keywords = listOf("math", "calc")),
        AppSpec(AppIds.CALENDAR, "Calendar", 860f, 580f, 560f, 400f, singleWindow = true, category = "Productivity", keywords = listOf("events", "schedule", "date")),
        AppSpec(AppIds.CLOCK, "Clock", 560f, 440f, 420f, 360f, singleWindow = true, category = "Utilities", keywords = listOf("timer", "stopwatch", "world", "alarm")),
        AppSpec(AppIds.NOTES, "Notes", 780f, 520f, 480f, 300f, category = "Productivity", keywords = listOf("text", "write", "textedit")),
        AppSpec(AppIds.PHOTOS, "Photos", 880f, 580f, 480f, 340f, category = "Creativity", keywords = listOf("images", "pictures", "gallery", "preview")),
        AppSpec(AppIds.FILES, "Files", 720f, 500f, 480f, 340f, singleWindow = true, category = "System", keywords = listOf("storage", "documents", "android")),
        AppSpec(AppIds.MUSIC, "Music", 860f, 560f, 560f, 380f, singleWindow = true, category = "Entertainment", keywords = listOf("songs", "audio", "player")),
        AppSpec(AppIds.SYSINFO, "System Information", 640f, 500f, 480f, 360f, singleWindow = true, category = "System", keywords = listOf("about", "device", "hardware", "cpu", "gpu")),
        AppSpec(AppIds.HELP, "Tips", 560f, 520f, 420f, 360f, singleWindow = true, category = "System", keywords = listOf("help", "shortcuts", "guide")),
        AppSpec(AppIds.STORE, "App Store", 900f, 620f, 520f, 380f, singleWindow = true, category = "System", keywords = listOf("apps", "appstore", "install", "apk", "store", "play", "download")),
    )

    private val byId = apps.associateBy { it.id }

    fun get(id: String): AppSpec? = byId[id]
    fun name(id: String): String = when (id) {
        AppIds.LAUNCHER -> "Apps"
        AppIds.MISSION -> "Mission Control"
        AppIds.TRASH, AppIds.TRASH_FULL -> "Trash"
        AppIds.SYSTEM -> "System"
        else -> byId[id]?.name ?: id
    }

    /** Things that may be pinned to the Dock. */
    val dockable: List<String> = apps.map { it.id } + listOf(AppIds.LAUNCHER, AppIds.MISSION)
}
