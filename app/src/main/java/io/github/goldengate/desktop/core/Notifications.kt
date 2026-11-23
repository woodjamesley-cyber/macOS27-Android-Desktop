package io.github.goldengate.desktop.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf

@Immutable
data class NotificationAction(val label: String, val run: () -> Unit)

@Immutable
data class DeskNotification(
    val id: Long,
    val appId: String,
    val appName: String,
    val title: String,
    val body: String,
    val time: Long,
    val actions: List<NotificationAction> = emptyList(),
    /** Alerts stay on screen until dismissed; banners leave after the configured duration. */
    val persistent: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

data class NotificationGroup(val appId: String, val appName: String, val items: List<DeskNotification>)

/** Notification history, live banners, grouping and Do Not Disturb handling. */
class NotificationCenter(private val now: () -> Long = { System.currentTimeMillis() }) {
    val history = mutableStateListOf<DeskNotification>()
    val banners = mutableStateListOf<DeskNotification>()
    private var nextId = 1L

    /** Returns the posted notification, or null if suppressed by Focus/muting (still kept in history). */
    fun post(
        appId: String, appName: String, title: String, body: String,
        actions: List<NotificationAction> = emptyList(), persistent: Boolean = false,
        doNotDisturb: Boolean = false, muted: Boolean = false, onClick: (() -> Unit)? = null,
    ): DeskNotification? {
        val n = DeskNotification(nextId++, appId, appName, title, body, now(), actions, persistent, onClick)
        history.add(0, n)
        if (history.size > 200) history.removeAt(history.lastIndex)
        if (doNotDisturb || muted) return null
        banners.add(0, n)
        while (banners.size > 3) banners.removeAt(banners.lastIndex)
        return n
    }

    fun dismissBanner(id: Long) { banners.removeAll { it.id == id } }

    fun remove(id: Long) {
        history.removeAll { it.id == id }
        banners.removeAll { it.id == id }
    }

    fun clearApp(appId: String) {
        history.removeAll { it.appId == appId }
        banners.removeAll { it.appId == appId }
    }

    fun clearAll() { history.clear(); banners.clear() }

    fun groups(): List<NotificationGroup> = history
        .groupBy { it.appId }
        .map { (app, list) -> NotificationGroup(app, list.first().appName, list.sortedByDescending { it.time }) }
        .sortedByDescending { it.items.first().time }

    companion object {
        fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
            val s = (now - then) / 1000
            return when {
                s < 60 -> "now"
                s < 3600 -> "${s / 60}m ago"
                s < 86400 -> "${s / 3600}h ago"
                else -> "${s / 86400}d ago"
            }
        }
    }
}
