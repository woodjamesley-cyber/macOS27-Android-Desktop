package io.github.goldengate.desktop.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Small in-memory loader for real app icons from the F-Droid repository. */
object RemoteIcons {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "GoldenGateDesktop/1.0 (Android)")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val bitmap: Bitmap = BitmapFactory.decodeStream(connection.inputStream) ?: return@runCatching null
                    val scaled = if (bitmap.width > 256) Bitmap.createScaledBitmap(bitmap, 256, 256, true) else bitmap
                    scaled.asImageBitmap().also { cache.put(url, it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
}

@Composable
fun rememberRemoteIcon(url: String?): ImageBitmap? {
    if (url == null) return null
    var image by remember(url) { mutableStateOf(RemoteIcons.cached(url)) }
    LaunchedEffect(url) {
        if (image == null) image = RemoteIcons.load(url)
    }
    return image
}
