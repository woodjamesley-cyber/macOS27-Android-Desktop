package io.github.goldengate.desktop.apps

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val cityZones = listOf(
    "Cupertino" to "America/Los_Angeles", "New York" to "America/New_York", "London" to "Europe/London",
    "Paris" to "Europe/Paris", "Berlin" to "Europe/Berlin", "Dubai" to "Asia/Dubai", "Mumbai" to "Asia/Kolkata",
    "Singapore" to "Asia/Singapore", "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney",
    "São Paulo" to "America/Sao_Paulo", "Mexico City" to "America/Mexico_City", "Honolulu" to "Pacific/Honolulu",
    "Reykjavík" to "Atlantic/Reykjavik", "Cairo" to "Africa/Cairo", "Seoul" to "Asia/Seoul",
)

fun formatStopwatch(ms: Long): String {
    val cs = (ms / 10) % 100; val s = (ms / 1000) % 60; val m = ms / 60_000
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
}

fun formatTimer(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
fun ClockApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val arg = rememberWindowArgument(win)
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(arg) { when (arg) { "stopwatch" -> tab = 1; "timer" -> tab = 2 } }
    val prefs = remember { desk.app.getSharedPreferences("clock", Context.MODE_PRIVATE) }
    val cities = remember { mutableStateListOf<String>().apply { addAll((prefs.getString("cities", null) ?: "Cupertino,New York,London,Tokyo").split(',').filter { n -> cityZones.any { it.first == n } }) } }
    fun saveCities() = prefs.edit().putString("cities", cities.joinToString(",")).apply()
    var addAnchor by remember { mutableStateOf(Offset.Zero) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tab) { while (true) { now = System.currentTimeMillis(); delay(if (tab == 1) 33L else 250L) } }
    val titles = listOf("World Clock", "Stopwatch", "Timer")
    LaunchedEffect(tab) { win.title = titles[tab] }
    WindowMenus(win, listOf(menu("View") { titles.forEachIndexed { i, t -> item(t, checked = tab == i) { tab = i } } }))

    WindowScaffold(titles[tab], toolbar = {
        SegmentedControl(titles, tab, { tab = it }, Modifier.testTag("clock-tabs"))
        if (tab == 0) {
            Spacer(Modifier.width(8.dp))
            ToolbarButton(Glyph.Plus, {
                desk.showContextMenu(addAnchor, entries {
                    cityZones.filter { it.first !in cities }.forEach { (n, _) -> item(n) { cities.add(n); saveCities() } }
                })
            }, Modifier.onGloballyPositioned { addAnchor = it.positionInRoot() + Offset(0f, it.size.height.toFloat()) }, description = "Add City")
        }
    }) {
        when (tab) {
            0 -> WorldClocks(now, cities, onRemove = { cities.remove(it); saveCities() })
            1 -> StopwatchView(now)
            else -> TimerView(now)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldClocks(now: Long, cities: List<String>, onRemove: (String) -> Unit) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val localOffset = TimeZone.getDefault().getOffset(now)
    FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("world-clocks"), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        cities.forEach { name ->
            val tz = TimeZone.getTimeZone(cityZones.first { it.first == name }.second)
            val cal = Calendar.getInstance(tz).apply { timeInMillis = now }
            val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE); val s = cal.get(Calendar.SECOND)
            val diffH = (tz.getOffset(now) - localOffset) / 3_600_000f
            val night = h < 6 || h >= 19
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp).desktopGestures(key = name, onSecondary = { at ->
                desk.showContextMenu(at, entries { item("Remove $name", destructive = true) { onRemove(name) } })
            })) {
                Canvas(Modifier.size(110.dp)) { drawClockFace(h, m, s, night) }
                Spacer(Modifier.height(6.dp))
                DeskText(name, Desk.type.bodyStrong)
                val rel = when { diffH == 0f -> "Same time"; diffH > 0 -> "+${fmtH(diffH)} hrs"; else -> "${fmtH(diffH)} hrs" }
                val dayRel = run {
                    val here = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
                    when (cal.get(Calendar.DAY_OF_YEAR) - here) { 0 -> "Today"; 1, -364, -365 -> "Tomorrow"; else -> "Yesterday" }
                }
                DeskText("$dayRel, $rel", Desk.type.small, color = c.secondaryText)
                DeskText(String.format(Locale.US, if (desk.settings.clock24h) "%02d:%02d" else "%d:%02d", if (desk.settings.clock24h) h else (if (h % 12 == 0) 12 else h % 12), m) + if (desk.settings.clock24h) "" else if (h < 12) " AM" else " PM", Desk.type.small, color = c.secondaryText)
            }
        }
    }
}

private fun fmtH(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

private fun DrawScope.drawClockFace(h: Int, m: Int, s: Int, night: Boolean) {
    val r = size.minDimension / 2
    val face = if (night) Color(0xFF1C1C1E) else Color(0xFFF7F7F7)
    val ink = if (night) Color.White else Color(0xFF1C1C1E)
    drawCircle(Color.Black.copy(alpha = 0.18f), r, center + Offset(0f, 2f))
    drawCircle(face, r)
    for (i in 0 until 60) {
        val major = i % 5 == 0
        rotate(i * 6f) {
            drawLine(ink.copy(alpha = if (major) 0.9f else 0.35f), Offset(center.x, center.y - r * 0.92f), Offset(center.x, center.y - r * if (major) 0.8f else 0.86f), strokeWidth = if (major) r * 0.035f else r * 0.015f)
        }
    }
    rotate((h % 12) * 30f + m * 0.5f) { drawLine(ink, center, Offset(center.x, center.y - r * 0.5f), strokeWidth = r * 0.07f, cap = StrokeCap.Round) }
    rotate(m * 6f + s * 0.1f) { drawLine(ink, center, Offset(center.x, center.y - r * 0.76f), strokeWidth = r * 0.05f, cap = StrokeCap.Round) }
    rotate(s * 6f) { drawLine(Color(0xFFFF9F0A), Offset(center.x, center.y + r * 0.14f), Offset(center.x, center.y - r * 0.84f), strokeWidth = r * 0.02f, cap = StrokeCap.Round) }
    drawCircle(Color(0xFFFF9F0A), r * 0.05f)
}

@Composable
private fun StopwatchView(now: Long) {
    val t = LocalDesktop.current.timers
    val c = Desk.colors
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DeskText(formatStopwatch(t.stopwatchElapsed(now)), Desk.type.largeTitle.copy(fontSize = 54.sp, fontWeight = FontWeight.Light), modifier = Modifier.padding(vertical = 18.dp).testTag("stopwatch-display"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PushButton(if (t.stopwatchRunning) "Lap" else "Reset", { t.lapOrReset() }, enabled = t.stopwatchRunning || t.stopwatchAccum > 0)
            PushButton(if (t.stopwatchRunning) "Stop" else "Start", { t.toggleStopwatch() }, Modifier.testTag("stopwatch-toggle"), primary = true, destructive = t.stopwatchRunning)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val laps = t.laps
            val diffs = laps.mapIndexed { i, v -> v - (laps.getOrNull(i + 1) ?: 0L) }
            val best = diffs.minOrNull(); val worst = diffs.maxOrNull()
            diffs.forEachIndexed { i, d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp)) {
                    DeskText("Lap ${laps.size - i}", Desk.type.body, modifier = Modifier.weight(1f),
                        color = if (diffs.size > 1 && d == best) Color(0xFF32D74B) else if (diffs.size > 1 && d == worst) Color(0xFFFF453A) else c.text)
                    DeskText(formatStopwatch(d), Desk.type.mono)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(0.5.dp).background(c.separator))
            }
        }
    }
}

@Composable
private fun TimerView(now: Long) {
    val t = LocalDesktop.current.timers
    val c = Desk.colors
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    val active = t.timerRunning || t.timerPausedRemaining > 0
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (active) {
            val rem = t.remaining(now)
            val frac = if (t.timerTotal > 0) rem / t.timerTotal.toFloat() else 0f
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.controlFillPressed, size.minDimension / 2 - 6.dp.toPx(), style = Stroke(6.dp.toPx()))
                    drawArc(Color(0xFFFF9F0A), -90f, 360f * frac, false, topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
                DeskText(formatTimer(rem), Desk.type.largeTitle.copy(fontSize = 40.sp, fontWeight = FontWeight.Light), modifier = Modifier.testTag("timer-display"))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PushButton("Cancel", { t.cancelTimer() })
                PushButton(if (t.timerRunning) "Pause" else "Resume", { if (t.timerRunning) t.pauseTimer() else t.resumeTimer() }, primary = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("min", minutes, 0..180) { minutes = it }
                DeskText(":", Desk.type.largeTitle, modifier = Modifier.padding(horizontal = 10.dp))
                Stepper("sec", seconds, 0..59, step = 5) { seconds = it }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 15, 30).forEach { p -> PushButton("${p}m", { minutes = p; seconds = 0 }) }
            }
            Spacer(Modifier.height(16.dp))
            PushButton("Start", { val ms = (minutes * 60L + seconds) * 1000L; if (ms > 0) t.startTimer(ms) }, Modifier.testTag("timer-start"), primary = true, enabled = minutes + seconds > 0)
            DeskText("You'll get a notification when the timer ends (while Golden Gate Desktop is running).", Desk.type.small, color = c.secondaryText, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    val c = Desk.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ToolbarButton(Glyph.ChevronUp, { onChange((value + step).coerceAtMost(range.last)) }, description = "More $label")
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(c.controlFill).padding(horizontal = 14.dp, vertical = 6.dp)) {
            DeskText(String.format(Locale.US, "%02d", value), Desk.type.largeTitle.copy(fontSize = 40.sp, fontWeight = FontWeight.Light))
        }
        ToolbarButton(Glyph.ChevronDown, { onChange((value - step).coerceAtLeast(range.first)) }, description = "Less $label")
        DeskText(label, Desk.type.small, color = c.secondaryText)
    }
}
