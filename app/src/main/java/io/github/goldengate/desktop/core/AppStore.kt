package io.github.goldengate.desktop.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * A real Android app that the store can show, launch when installed, and hand off to Google Play
 * when it isn't. Icons are drawn from the monogram/gradient rather than using anyone's artwork.
 */
data class StoreApp(
    val packageName: String,
    val name: String,
    val tagline: String,
    val category: String,
    val monogram: String,
    val colorStart: Long,
    val colorEnd: Long,
)

object StoreCatalog {
    val categories = listOf("All", "Music & Video", "Photos & Social", "Productivity", "Internet", "Utilities", "Learning")

    val all: List<StoreApp> = listOf(
        StoreApp("com.spotify.music", "Spotify", "Music, podcasts and playlists", "Music & Video", "S", 0xFF1DB954, 0xFF0E5C2B),
        StoreApp("com.google.android.youtube", "YouTube", "Watch videos and live streams", "Music & Video", "YT", 0xFFFF5A5A, 0xFFC4302B),
        StoreApp("org.videolan.vlc", "VLC", "Plays almost any video or audio file", "Music & Video", "V", 0xFFFF9F0A, 0xFFE05A00),
        StoreApp("com.netflix.mediaclient", "Netflix", "Films and series", "Music & Video", "N", 0xFFE50914, 0xFF7A0508),
        StoreApp("com.google.android.apps.photos", "Google Photos", "Back up and browse your photos", "Photos & Social", "GP", 0xFF4285F4, 0xFF34A853),
        StoreApp("com.instagram.android", "Instagram", "Photos, stories and reels", "Photos & Social", "IG", 0xFFF77737, 0xFFC13584),
        StoreApp("com.whatsapp", "WhatsApp", "Messages and calls", "Photos & Social", "W", 0xFF25D366, 0xFF0B7C3C),
        StoreApp("org.telegram.messenger", "Telegram", "Fast cloud messaging", "Photos & Social", "T", 0xFF2AABEE, 0xFF1A6E9C),
        StoreApp("org.thoughtcrime.securesms", "Signal", "Private messaging", "Photos & Social", "S", 0xFF3A76F0, 0xFF1B3C8C),
        StoreApp("com.discord", "Discord", "Voice, video and text chat", "Photos & Social", "D", 0xFF5865F2, 0xFF2C368C),
        StoreApp("com.google.android.gm", "Gmail", "Email by Google", "Productivity", "M", 0xFFEA4335, 0xFFB31412),
        StoreApp("com.google.android.apps.docs", "Google Drive", "Files and documents", "Productivity", "GD", 0xFF34A853, 0xFF0F6B33),
        StoreApp("com.google.android.calendar", "Google Calendar", "Events and reminders", "Productivity", "31", 0xFF4285F4, 0xFF174EA6),
        StoreApp("notion.id", "Notion", "Notes, docs and databases", "Productivity", "N", 0xFF4A4A4A, 0xFF191919),
        StoreApp("md.obsidian", "Obsidian", "Markdown knowledge base", "Productivity", "O", 0xFF7C3AED, 0xFF4C1D95),
        StoreApp("com.Slack", "Slack", "Team messaging", "Productivity", "S", 0xFF611F69, 0xFF3B1142),
        StoreApp("com.google.android.apps.maps", "Google Maps", "Navigation and places", "Internet", "M", 0xFF34A853, 0xFF4285F4),
        StoreApp("com.android.chrome", "Chrome", "Google's web browser", "Internet", "C", 0xFF4285F4, 0xFF0B57D0),
        StoreApp("org.mozilla.firefox", "Firefox", "Private, open web browsing", "Internet", "F", 0xFFFF7139, 0xFFB33C00),
        StoreApp("org.wikipedia", "Wikipedia", "The free encyclopedia", "Internet", "W", 0xFF636466, 0xFF202122),
        StoreApp("com.termux", "Termux", "A real Linux terminal on Android", "Utilities", ">_", 0xFF3A3D45, 0xFF0D0E12),
        StoreApp("org.fdroid.fdroid", "F-Droid", "Free and open source apps", "Utilities", "FD", 0xFF1976D2, 0xFF0D47A1),
        StoreApp("com.aurora.store", "Aurora Store", "Open client for Play packages", "Utilities", "A", 0xFF4F46E5, 0xFF1E1B4B),
        StoreApp("com.adobe.reader", "Acrobat Reader", "Read and sign PDFs", "Utilities", "PDF", 0xFFFF3B30, 0xFF8C1D18),
        StoreApp("com.duolingo", "Duolingo", "Learn a language", "Learning", "Du", 0xFF58CC02, 0xFF2F7A00),
    )

    fun filter(query: String, category: String): List<StoreApp> {
        val q = query.trim()
        return all.filter { app ->
            (category == "All" || app.category == category) &&
                (q.isEmpty() || app.name.contains(q, true) || app.tagline.contains(q, true) ||
                    app.packageName.contains(q, true) || app.category.contains(q, true))
        }
    }

    fun byPackage(packageName: String): StoreApp? = all.firstOrNull { it.packageName == packageName }
}

/** An APK that lives inside the desktop's own private storage so it can be reinstalled later. */
data class LibraryApk(
    val file: File,
    val packageName: String?,
    val label: String,
    val versionName: String?,
    val sizeBytes: Long,
)

object AppLibrary {
    /** Every imported APK is copied here; nothing is deleted after installing. */
    const val DIR_NAME = "appstore"
    private const val MAX_BYTES = 400L * 1024 * 1024

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun list(context: Context): List<LibraryApk> = (dir(context).listFiles() ?: emptyArray())
        .filter { it.isFile && it.name.endsWith(".apk", true) }
        .sortedByDescending { it.lastModified() }
        .map { read(context, it) }

    fun totalSize(context: Context): Long = (dir(context).listFiles() ?: emptyArray()).sumOf { it.length() }

    fun read(context: Context, file: File): LibraryApk {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()
        val label = info?.applicationInfo?.let { app ->
            runCatching {
                app.sourceDir = file.absolutePath
                app.publicSourceDir = file.absolutePath
                pm.getApplicationLabel(app).toString()
            }.getOrNull()
        } ?: file.nameWithoutExtension.substringAfter('-')
        return LibraryApk(file, info?.packageName, label, info?.versionName, file.length())
    }

    /** Copies a picked APK into the app's private App Library and returns its record. */
    fun importFrom(context: Context, uri: Uri): LibraryApk {
        val resolver = context.contentResolver
        val display = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val raw = (display ?: "Imported.apk").substringAfterLast('/').ifBlank { "Imported.apk" }
        val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".apk", true)) it else "$it.apk" }
        val out = File(dir(context), "${System.currentTimeMillis()}-$safe")
        val copied = resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_BYTES) throw IllegalStateException("That APK is larger than 400 MB.")
                    output.write(buffer, 0, n)
                }
                total
            }
        } ?: throw IllegalStateException("Android couldn't read that file.")
        if (copied <= 0L) {
            out.delete()
            throw IllegalStateException("That file is empty.")
        }
        return read(context, out)
    }

    fun delete(apk: LibraryApk): Boolean = apk.file.delete()
}
