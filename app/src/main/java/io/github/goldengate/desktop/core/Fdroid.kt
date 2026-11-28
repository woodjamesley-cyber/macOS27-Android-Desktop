package io.github.goldengate.desktop.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An app published in the F-Droid repository, which serves real APKs over HTTPS. */
data class FdroidApp(
    val packageName: String,
    val name: String,
    val tagline: String,
    val category: String,
    val color: Long,
)

data class FdroidVersion(val versionName: String, val versionCode: Int)

/** A Google Play app. Android won't let another app install these silently, so the store hands
 *  them to Google Play (or to Aurora Store when Play isn't available). */
data class GoogleApp(val packageName: String, val name: String, val tagline: String)

/**
 * Talks to f-droid.org directly: the package API gives the current version, the repo serves the
 * signed APK and the real app icon. Downloads land in the desktop's private App Library.
 */
object Fdroid {
    const val BASE = "https://f-droid.org"
    const val AURORA = "com.aurora.store"

    val categories = listOf("All", "Essentials", "Media", "Internet", "Productivity", "Privacy", "Navigation")

    val catalog: List<FdroidApp> = listOf(
        FdroidApp("com.aurora.store", "Aurora Store", "Install Google Play apps without Google services", "Essentials", 0xFF4F46E5),
        FdroidApp("org.fdroid.fdroid", "F-Droid", "Free and open-source app store", "Essentials", 0xFF1976D2),
        FdroidApp("com.termux", "Termux", "A real Linux terminal and package manager", "Essentials", 0xFF37474F),
        FdroidApp("com.looker.droidify", "Droid-ify", "Fast, modern F-Droid client", "Essentials", 0xFF00897B),
        FdroidApp("io.github.muntashirakon.AppManager", "App Manager", "Advanced app and APK manager", "Essentials", 0xFF455A64),
        FdroidApp("org.videolan.vlc", "VLC", "Plays almost any video or audio file", "Media", 0xFFFF8F00),
        FdroidApp("org.schabi.newpipe", "NewPipe", "Lightweight YouTube front end", "Media", 0xFFE53935),
        FdroidApp("com.github.libretube", "LibreTube", "Private YouTube client", "Media", 0xFFD32F2F),
        FdroidApp("de.danoeh.antennapod", "AntennaPod", "Podcasts, downloads and playback", "Media", 0xFF1565C0),
        FdroidApp("org.wikipedia", "Wikipedia", "The free encyclopedia, offline too", "Internet", 0xFF37474F),
        FdroidApp("org.mozilla.fennec_fdroid", "Fennec F-Droid", "Firefox built with free software only", "Internet", 0xFFFF6D00),
        FdroidApp("me.zhanghai.android.files", "Material Files", "Open-source file manager", "Productivity", 0xFF2E7D32),
        FdroidApp("com.fsck.k9", "K-9 Mail", "Email that respects your privacy", "Productivity", 0xFF1E88E5),
        FdroidApp("org.fossify.calendar", "Fossify Calendar", "Offline calendar and events", "Productivity", 0xFF00897B),
        FdroidApp("org.fossify.gallery", "Fossify Gallery", "Fast offline photo gallery", "Productivity", 0xFF8E24AA),
        FdroidApp("com.kunzisoft.keepass.libre", "KeePassDX", "Offline password manager", "Privacy", 0xFF3949AB),
        FdroidApp("app.organicmaps", "Organic Maps", "Offline maps and navigation", "Navigation", 0xFF43A047),
        FdroidApp("net.osmand.plus", "OsmAnd", "Offline maps with navigation", "Navigation", 0xFF7CB342),
    )

    val googleApps: List<GoogleApp> = listOf(
        GoogleApp("com.android.vending", "Google Play Store", "Google's app store"),
        GoogleApp("com.google.android.youtube", "YouTube", "Videos and live streams"),
        GoogleApp("com.google.android.gm", "Gmail", "Email by Google"),
        GoogleApp("com.android.chrome", "Chrome", "Google's web browser"),
        GoogleApp("com.google.android.apps.maps", "Google Maps", "Navigation and places"),
        GoogleApp("com.google.android.apps.photos", "Google Photos", "Photo backup and search"),
        GoogleApp("com.google.android.apps.docs", "Google Drive", "Files and documents"),
        GoogleApp("com.google.android.calendar", "Google Calendar", "Events and reminders"),
    )

    fun iconUrl(packageName: String): String = "$BASE/repo/$packageName/en-US/icon.png"

    fun apkUrl(packageName: String, versionCode: Int): String = "$BASE/repo/${packageName}_$versionCode.apk"

    fun byPackage(packageName: String): FdroidApp? = catalog.firstOrNull { it.packageName == packageName }

    fun filter(query: String, category: String): List<FdroidApp> {
        val q = query.trim()
        return catalog.filter { app ->
            (category == "All" || app.category == category) &&
                (q.isEmpty() || app.name.contains(q, true) || app.tagline.contains(q, true) ||
                    app.packageName.contains(q, true) || app.category.contains(q, true))
        }
    }

    /** Asks the F-Droid API which version is current right now. */
    suspend fun latestVersion(packageName: String): FdroidVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get("$BASE/api/v1/packages/$packageName"))
            val code = json.optInt("suggestedVersionCode", 0)
            if (code <= 0) return@runCatching null
            var versionName: String? = null
            val versions = json.optJSONArray("packages")
            if (versions != null) {
                for (i in 0 until versions.length()) {
                    val item = versions.optJSONObject(i) ?: continue
                    if (item.optInt("versionCode") == code) {
                        versionName = item.optString("versionName").ifBlank { null }
                        break
                    }
                }
            }
            FdroidVersion(versionName ?: "version $code", code)
        }.getOrNull()
    }

    /**
     * Downloads the signed APK to the App Library, reporting progress from 0 to 1. A previous
     * download of the same version is reused so repeat installs don't hit the network.
     */
    suspend fun download(
        context: Context,
        app: FdroidApp,
        version: FdroidVersion,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = AppLibrary.dir(context)
        val target = File(dir, "${app.packageName}_${version.versionCode}.apk")
        if (target.isFile && target.length() > 0L &&
            runCatching { context.packageManager.getPackageArchiveInfo(target.absolutePath, 0) }.getOrNull() != null
        ) {
            onProgress(1f)
            return@withContext target
        }

        val temp = File(dir, "${target.name}.part")
        val connection = (URL(apkUrl(app.packageName, version.versionCode)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GoldenGateDesktop/1.0 (Android)")
        }
        try {
            if (connection.responseCode !in 200..299) error("F-Droid returned HTTP ${connection.responseCode}.")
            val length = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        total += n
                        if (total > 300L * 1024 * 1024) error("That APK is larger than 300 MB.")
                        output.write(buffer, 0, n)
                        onProgress(if (length > 0) (total.toFloat() / length).coerceIn(0f, 1f) else 0f)
                    }
                }
            }
            if (temp.length() <= 0L) error("The download was empty.")
            val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
                ?: error("The downloaded file isn't a valid APK.")
            if (info.packageName != app.packageName) {
                error("F-Droid returned a different app (${info.packageName}).")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            onProgress(1f)
            target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GoldenGateDesktop/1.0 (Android)")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
