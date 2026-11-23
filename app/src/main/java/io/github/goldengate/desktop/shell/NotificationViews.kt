package io.github.goldengate.desktop.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskNotification
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.NotificationCenter
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.AppIcon
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Transient banners in the top-right corner. Swipe right to dismiss, tap to open. */
@Composable
fun NotificationBanners(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val banners = desk.notifications.banners
    // Banners step aside while a top-right panel (Notification Center, Control Center) is open.
    val showing = desk.overlay != Overlay.NotificationCenter && desk.overlay != Overlay.ControlCenter
    Column(modifier.width(360.dp).padding(top = Tokens.menuBarHeight + 8.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in banners.toList()) {
            key(n.id) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                LaunchedEffect(n.id, n.persistent) {
                    if (!n.persistent) {
                        delay(desk.settings.bannerSeconds * 1000L)
                        visible = false; delay(350); desk.notifications.dismissBanner(n.id)
                    }
                }
                AnimatedVisibility(
                    visible && showing,
                    enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 380f)) { it + 40 } + fadeIn(),
                    exit = slideOutHorizontally { it + 40 } + fadeOut(),
                ) {
                    NotificationCard(n, desk, banner = true, onDismiss = { visible = false; desk.notifications.dismissBanner(n.id) })
                }
            }
        }
    }
}

@Composable
fun NotificationCard(n: DeskNotification, desk: DesktopController, banner: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Desk.colors
    var dx by remember { mutableFloatStateOf(0f) }
    var hover by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    GlassSurface(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(dx.roundToInt().coerceAtLeast(0), 0) }
            .graphicsLayer { alpha = 1f - (dx / 500f).coerceIn(0f, 0.9f) }
            .pointerInput(n.id) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dx > 120f) onDismiss() else dx = 0f },
                    onDragCancel = { dx = 0f },
                ) { ch, amt -> ch.consume(); dx += amt }
            }
            .pointerInput(n.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) hover = true
                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) hover = false
                    }
                }
            }
            .desktopGestures(key = "notif-${n.id}", onTap = {
                onDismiss()
                if (desk.overlay == Overlay.NotificationCenter) desk.overlay = Overlay.None
                n.onClick?.invoke()
            })
            .testTag("notification-${n.id}"),
        cornerRadius = Tokens.notificationRadius, material = GlassMaterial.Regular, elevation = if (banner) 16.dp else 2.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(n.appId, 36.dp, dark = c.dark)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeskText(n.appName.uppercase(), Desk.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryText, maxLines = 1, modifier = Modifier.weight(1f))
                    DeskText(NotificationCenter.relativeTime(n.time, now), Desk.type.small, color = c.secondaryText, maxLines = 1)
                }
                DeskText(n.title, Desk.type.bodyStrong, color = c.text, maxLines = 1)
                DeskText(n.body, Desk.type.body, color = c.text.copy(alpha = 0.85f), maxLines = 3)
                if (n.actions.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.actions.forEach { a ->
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(c.controlFillPressed)
                                    .desktopGestures(key = "na-${n.id}-${a.label}", onTap = { onDismiss(); a.run() })
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                    .testTag("notification-action-${a.label}"),
                            ) { DeskText(a.label, Desk.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text) }
                        }
                    }
                }
            }
        }
        if (hover || !desk.input.lastPointerWasMouse) {
            Box(
                Modifier.align(Alignment.TopStart).offset((-2).dp, (-2).dp).padding(4.dp).size(20.dp).clip(CircleShape)
                    .background(if (c.dark) Color(0xFF3A3A3C) else Color(0xFFF2F2F7))
                    .desktopGestures(key = "nx-${n.id}", onTap = { if (banner) onDismiss() else desk.notifications.remove(n.id) })
                    .testTag("notification-close-${n.id}"),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Close, c.secondaryText, 9.dp, weight = 2.6f) }
        }
    }
}

/** Notification Center: grouped history, clear controls and a column of widgets. */
@Composable
fun NotificationCenterPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val groups = desk.notifications.groups()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier.width(360.dp).fillMaxHeight().padding(top = Tokens.menuBarHeight + 6.dp, end = 10.dp, bottom = 10.dp)
            .verticalScroll(rememberScrollState()).testTag("notification-center"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeskText("Notifications", Desk.type.headline, color = Color.White, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (groups.isNotEmpty()) GlassSurface(
                Modifier.desktopGestures(key = "clearall", onTap = { desk.notifications.clearAll() }).testTag("notifications-clear-all"),
                cornerRadius = 12.dp, material = GlassMaterial.Regular,
            ) { DeskText("Clear All", Desk.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) }
        }
        if (groups.isEmpty()) {
            GlassSurface(Modifier.fillMaxWidth(), cornerRadius = Tokens.notificationRadius) {
                DeskText("No Notifications", Desk.type.body, color = c.secondaryText, modifier = Modifier.align(Alignment.Center).padding(18.dp))
            }
        }
        for (g in groups) {
            val open = expanded[g.appId] == true || g.items.size == 1
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (g.items.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        DeskText(g.appName, Desk.type.bodyStrong, color = Color.White, modifier = Modifier.weight(1f))
                        DeskText(if (open) "Show Less" else "${g.items.size} notifications", Desk.type.caption, color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.desktopGestures(key = "exp-${g.appId}", onTap = { expanded[g.appId] = !open }).padding(4.dp))
                        Box(Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
                            .desktopGestures(key = "clr-${g.appId}", onTap = { desk.notifications.clearApp(g.appId) }), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Close, Color.White, 9.dp, weight = 2.6f)
                        }
                    }
                }
                if (open) g.items.forEach { n -> key(n.id) { NotificationCard(n, desk, banner = false, onDismiss = { desk.notifications.remove(n.id) }) } }
                else Box {
                    // Stacked group appearance.
                    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 14.dp).offset(y = 10.dp).height(40.dp), cornerRadius = Tokens.notificationRadius) {}
                    NotificationCard(g.items.first(), desk, banner = false, onDismiss = { desk.notifications.remove(g.items.first().id) },
                        modifier = Modifier.desktopGestures(key = "stack-${g.appId}", onTap = { expanded[g.appId] = true }))
                }
            }
        }
        // Widgets.
        DeskText("Widgets", Desk.type.headline, color = Color.White, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalendarWidget(desk, Modifier.weight(1f))
            ClockWidget(Modifier.weight(1f))
        }
        UpNextWidget(desk)
        NowPlayingWidget(desk)
    }
}

@Composable
private fun CalendarWidget(desk: DesktopController, modifier: Modifier) {
    val c = Desk.colors
    val cal = Calendar.getInstance()
    GlassSurface(modifier.height(150.dp).desktopGestures(key = "w-cal", onTap = { desk.launch(AppIds.CALENDAR) }), cornerRadius = Tokens.notificationRadius) {
        Column(Modifier.padding(14.dp)) {
            DeskText(SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time).uppercase(), Desk.type.small.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFF453A))
            DeskText(cal.get(Calendar.DAY_OF_MONTH).toString(), Desk.type.largeTitle.copy(fontWeight = FontWeight.Light), color = c.text)
            val dayStart = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val today = desk.calendar.on(dayStart)
            DeskText(if (today.isEmpty()) "No events today" else "${today.size} event${if (today.size == 1) "" else "s"} today", Desk.type.caption, color = c.secondaryText)
        }
    }
}

@Composable
private fun ClockWidget(modifier: Modifier) {
    val c = Desk.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    GlassSurface(modifier.height(150.dp), cornerRadius = Tokens.notificationRadius) {
        androidx.compose.foundation.Canvas(Modifier.align(Alignment.Center).size(112.dp)) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val r = size.minDimension / 2f
            val center = this.center
            drawCircle(if (c.dark) Color(0xFF1C1C1E) else Color.White, r)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90)
                val o = androidx.compose.ui.geometry.Offset((center.x + Math.cos(a) * r * 0.82).toFloat(), (center.y + Math.sin(a) * r * 0.82).toFloat())
                drawCircle(c.text.copy(alpha = 0.6f), if (i % 3 == 0) 2.6f else 1.6f, o)
            }
            fun hand(angleDeg: Double, len: Float, w: Float, col: Color) {
                val a = Math.toRadians(angleDeg - 90)
                drawLine(col, center, androidx.compose.ui.geometry.Offset((center.x + Math.cos(a) * r * len).toFloat(), (center.y + Math.sin(a) * r * len).toFloat()), w, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60.0
            val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60.0
            hand(h * 30, 0.5f, 5f, c.text)
            hand(m * 6, 0.75f, 3.5f, c.text)
            hand(cal.get(Calendar.SECOND) * 6.0, 0.8f, 1.5f, Color(0xFFFF9F0A))
            drawCircle(Color(0xFFFF9F0A), 3.5f, center)
        }
    }
}

@Composable
private fun UpNextWidget(desk: DesktopController) {
    val c = Desk.colors
    val upcoming = desk.calendar.events.filter { it.start > System.currentTimeMillis() }.sortedBy { it.start }.take(3)
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = Tokens.notificationRadius) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DeskText("Up Next", Desk.type.bodyStrong, color = c.text)
            if (upcoming.isEmpty()) DeskText("Nothing scheduled", Desk.type.caption, color = c.secondaryText)
            upcoming.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(Color(desk.calendar.color(e.calendar))))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        DeskText(e.title, Desk.type.body, color = c.text, maxLines = 1)
                        DeskText(SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(e.start)), Desk.type.caption, color = c.secondaryText)
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingWidget(desk: DesktopController, compact: Boolean = false) {
    val c = Desk.colors
    val m = desk.music
    val song = m.current ?: m.library.firstOrNull()
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = if (compact) Tokens.controlModuleRadius else Tokens.notificationRadius) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.hsv((song?.hue ?: 30f).mod(360f), 0.6f, 0.95f), Color.hsv(((song?.hue ?: 30f) + 60f).mod(360f), 0.7f, 0.6f))),
                ).desktopGestures(key = "np-art", onTap = { desk.launch(AppIds.MUSIC) }),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Music, Color.White.copy(alpha = 0.9f), 20.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                DeskText(song?.title ?: "Not Playing", Desk.type.bodyStrong, color = c.text, maxLines = 1)
                DeskText(song?.artist ?: "Music", Desk.type.caption, color = c.secondaryText, maxLines = 1)
            }
            listOf<Pair<Glyph, () -> Unit>>(Glyph.Prev to { m.previous() }, (if (m.playing) Glyph.Pause else Glyph.Play) to {
                if (m.current == null) song?.let { m.play(it) } else m.toggle()
            }, Glyph.Next to { m.next() }).forEach { (g, a) ->
                Box(Modifier.size(32.dp).clip(CircleShape).desktopGestures(key = "np-$g", onTap = a).testTag("nowplaying-$g"), contentAlignment = Alignment.Center) {
                    GlyphIcon(g, c.text, 16.dp)
                }
            }
        }
    }
}

@Suppress("unused")
private fun unusedAbs() = abs(0)
