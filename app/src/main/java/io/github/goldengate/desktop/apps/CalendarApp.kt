package io.github.goldengate.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.CalEvent
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.SidebarHeader
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.ToolbarGroup
import io.github.goldengate.desktop.ui.desktopGestures
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private class EventDraft(val id: Long?, title: String, start: Long, dur: Int, cal: Int, notes: String) {
    var title by mutableStateOf(title)
    var start by mutableLongStateOf(start)
    var duration by mutableIntStateOf(dur)
    var calendar by mutableIntStateOf(cal)
    var notes by mutableStateOf(notes)
}

@Composable
fun CalendarApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val store = desk.calendar
    val c = Desk.colors
    var month by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }) }
    var selected by remember { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var hidden by remember { mutableStateOf(setOf<Int>()) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }
    var view by remember { mutableIntStateOf(1) } // 0 day, 1 month

    LaunchedEffect(Unit) { win.title = "Calendar" }
    fun newEvent(day: Long = selected) {
        val start = day + 10 * 3_600_000L
        draft = EventDraft(null, "New Event", start, 60, 0, "")
    }
    fun goToday() {
        val now = Calendar.getInstance(); month = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH); selected = dayStart(now.timeInMillis)
    }
    WindowMenus(win, listOf(
        menu("File") {
            item("New Event", Shortcut(Key.N)) { newEvent() }
            item("Delete Event", Shortcut(Key.Backspace), enabled = draft?.id != null) { draft?.id?.let { store.delete(it) }; draft = null }
        },
        menu("View") {
            item("Day", Shortcut(Key.One), checked = view == 0) { view = 0 }
            item("Month", Shortcut(Key.Three), checked = view == 1) { view = 1 }
            separator()
            item("Go to Today", Shortcut(Key.T)) { goToday() }
            item("Next Month", Shortcut(Key.DirectionRight)) { month++ }
            item("Previous Month", Shortcut(Key.DirectionLeft)) { month-- }
        },
    ))

    val year = month / 12
    val mon = month % 12
    val cal = Calendar.getInstance().apply { clear(); set(year, mon, 1) }
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)

    WindowScaffold(
        title = title,
        sidebarWidth = 190.dp,
        sidebar = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                SidebarHeader("Calendars")
                store.calendars.forEachIndexed { i, (name, color) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).desktopGestures(key = "cal$i", onTap = { hidden = if (i in hidden) hidden - i else hidden + i }), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(if (i in hidden) Color.Transparent else Color(color)).border(1.5.dp, Color(color), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(8.dp))
                        DeskText(name, Desk.type.body)
                    }
                }
                SidebarHeader("Upcoming")
                val now = System.currentTimeMillis()
                store.events.filter { it.start >= now && it.calendar !in hidden }.sortedBy { it.start }.take(6).forEach { e ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).desktopGestures(key = "up${e.id}", onTap = {
                        selected = dayStart(e.start); val k = Calendar.getInstance().apply { timeInMillis = e.start }; month = k.get(Calendar.YEAR) * 12 + k.get(Calendar.MONTH)
                        draft = EventDraft(e.id, e.title, e.start, e.durationMin, e.calendar, e.notes)
                    })) {
                        DeskText(e.title, Desk.type.bodyStrong, maxLines = 1)
                        DeskText(SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(e.start), Desk.type.small, color = c.secondaryText)
                    }
                }
            }
        },
        toolbar = {
            SegmentedControl(listOf("Day", "Month"), view, { view = it })
            Spacer(Modifier.width(8.dp))
            ToolbarGroup {
                ToolbarButton(Glyph.ChevronLeft, { if (view == 1) month-- else { selected -= 86_400_000L } }, description = "Previous")
                PushButton("Today", { goToday() })
                ToolbarButton(Glyph.ChevronRight, { if (view == 1) month++ else { selected += 86_400_000L } }, description = "Next")
            }
            Spacer(Modifier.width(8.dp))
            ToolbarButton(Glyph.Plus, { newEvent() }, Modifier.testTag("calendar-add"), description = "New Event")
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (view == 1) MonthGrid(year, mon, selected, hidden, onSelect = { selected = it }, onOpen = { e -> draft = EventDraft(e.id, e.title, e.start, e.durationMin, e.calendar, e.notes) }, onNew = { newEvent(it) })
                else DayView(selected, hidden, onOpen = { e -> draft = EventDraft(e.id, e.title, e.start, e.durationMin, e.calendar, e.notes) }, onNewAt = { t -> draft = EventDraft(null, "New Event", t, 60, 0, "") })
            }
            val d = draft
            if (d != null) EventInspector(d, onSave = {
                if (d.title.isBlank()) d.title = "Untitled"
                if (d.id == null) store.add(d.title, d.start, d.duration, d.calendar, d.notes)
                else store.update(CalEvent(d.id, d.title, d.start, d.duration, d.calendar, d.notes))
                selected = dayStart(d.start)
                draft = null
            }, onDelete = { d.id?.let { store.delete(it) }; draft = null }, onCancel = { draft = null })
        }
    }
}

@Composable
private fun MonthGrid(year: Int, mon: Int, selected: Long, hidden: Set<Int>, onSelect: (Long) -> Unit, onOpen: (CalEvent) -> Unit, onNew: (Long) -> Unit) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val first = Calendar.getInstance().apply { clear(); set(year, mon, 1) }
    val firstDow = Calendar.getInstance().firstDayOfWeek
    val offset = ((first.get(Calendar.DAY_OF_WEEK) - firstDow) + 7) % 7
    val gridStart = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -offset) }
    val today = dayStart(System.currentTimeMillis())
    val dowFmt = SimpleDateFormat("EEE", Locale.getDefault())
    Column(Modifier.fillMaxSize().testTag("calendar-month")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            for (i in 0 until 7) {
                val d = (gridStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                DeskText(dowFmt.format(d.time), Desk.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryText, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (dow in 0 until 7) {
                    val d = (gridStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, week * 7 + dow) }
                    val ds = dayStart(d.timeInMillis)
                    val inMonth = d.get(Calendar.MONTH) == mon
                    val events = desk.calendar.on(ds).filter { it.calendar !in hidden }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().border(0.25.dp, c.separator)
                            .background(if (ds == selected) c.accent.copy(alpha = 0.10f) else Color.Transparent)
                            .desktopGestures(key = "d$ds", onTap = { onSelect(ds) }, onDoubleTap = { onNew(ds) })
                            .padding(3.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (ds == today) Color(0xFFFF3B30) else Color.Transparent), contentAlignment = Alignment.Center) {
                                DeskText("${d.get(Calendar.DAY_OF_MONTH)}", Desk.type.small.copy(fontWeight = if (ds == today) FontWeight.Bold else FontWeight.Normal),
                                    color = if (ds == today) Color.White else if (inMonth) c.text else c.tertiaryText)
                            }
                        }
                        events.take(3).forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(top = 1.dp).clip(RoundedCornerShape(3.dp)).background(Color(desk.calendar.color(e.calendar)).copy(alpha = 0.22f))
                                .desktopGestures(key = "e${e.id}", onTap = { onSelect(ds); onOpen(e) }, onSecondary = { at ->
                                    desk.showContextMenu(at, entries {
                                        item("Edit…") { onOpen(e) }
                                        item("Delete", destructive = true) { desk.calendar.delete(e.id) }
                                    })
                                }).padding(horizontal = 3.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 3.dp, height = 10.dp).clip(RoundedCornerShape(2.dp)).background(Color(desk.calendar.color(e.calendar))))
                                Spacer(Modifier.width(3.dp))
                                DeskText(e.title, Desk.type.small, maxLines = 1)
                            }
                        }
                        if (events.size > 3) DeskText("${events.size - 3} more", Desk.type.small, color = c.secondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayView(day: Long, hidden: Set<Int>, onOpen: (CalEvent) -> Unit, onNewAt: (Long) -> Unit) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val events = desk.calendar.on(day).filter { it.calendar !in hidden }
    val hourH = 44.dp
    Column(Modifier.fillMaxSize()) {
        DeskText(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(day), Desk.type.headline, modifier = Modifier.padding(12.dp))
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(8 * 120))) {
            Column {
                for (h in 0 until 24) {
                    Row(Modifier.fillMaxWidth().height(hourH).desktopGestures(key = "h$h", onDoubleTap = { onNewAt(day + h * 3_600_000L) })) {
                        DeskText(String.format(Locale.US, "%02d:00", h), Desk.type.small, color = c.secondaryText, modifier = Modifier.width(52.dp).padding(start = 8.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                    }
                }
            }
            events.forEach { e ->
                val startMin = ((e.start - day) / 60_000L).toInt().coerceIn(0, 24 * 60)
                Box(
                    Modifier.padding(start = 56.dp, end = 10.dp, top = hourH * (startMin / 60f))
                        .fillMaxWidth().height(hourH * (e.durationMin.coerceAtLeast(20) / 60f))
                        .clip(RoundedCornerShape(5.dp)).background(Color(desk.calendar.color(e.calendar)).copy(alpha = 0.28f))
                        .desktopGestures(key = "de${e.id}", onTap = { onOpen(e) }).padding(5.dp),
                ) {
                    Column {
                        DeskText(e.title, Desk.type.bodyStrong, maxLines = 1)
                        DeskText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(e.start) + " · ${e.durationMin} min", Desk.type.small, color = c.secondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventInspector(d: EventDraft, onSave: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val k = Calendar.getInstance().apply { timeInMillis = d.start }
    Column(Modifier.width(240.dp).fillMaxHeight().background(c.contentBackground).border(0.5.dp, c.separator).padding(12.dp).verticalScroll(rememberScrollState()).testTag("event-inspector")) {
        DeskText(if (d.id == null) "New Event" else "Edit Event", Desk.type.headline)
        Spacer(Modifier.height(10.dp))
        DeskTextField(d.title, { d.title = it }, Modifier.fillMaxWidth().testTag("event-title"), placeholder = "Title")
        Spacer(Modifier.height(10.dp))
        DeskText(SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(d.start), Desk.type.body)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            ToolbarButton(Glyph.ChevronLeft, { d.start -= 86_400_000L }, description = "Previous day")
            ToolbarButton(Glyph.ChevronRight, { d.start += 86_400_000L }, description = "Next day")
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeskText("Starts", Desk.type.body, modifier = Modifier.width(56.dp))
            ToolbarButton(Glyph.Minus, { d.start -= 15 * 60_000L }, description = "Earlier")
            DeskText(String.format(Locale.US, "%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)), Desk.type.mono, modifier = Modifier.padding(horizontal = 6.dp))
            ToolbarButton(Glyph.Plus, { d.start += 15 * 60_000L }, description = "Later")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeskText("Length", Desk.type.body, modifier = Modifier.width(56.dp))
            ToolbarButton(Glyph.Minus, { d.duration = (d.duration - 15).coerceAtLeast(15) }, description = "Shorter")
            DeskText("${d.duration} min", Desk.type.mono, modifier = Modifier.padding(horizontal = 6.dp))
            ToolbarButton(Glyph.Plus, { d.duration = (d.duration + 15).coerceAtMost(24 * 60) }, description = "Longer")
        }
        Spacer(Modifier.height(10.dp))
        DeskText("Calendar", Desk.type.small, color = c.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            desk.calendar.calendars.forEachIndexed { i, (_, color) ->
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)).border(if (d.calendar == i) 2.dp else 0.dp, if (d.calendar == i) c.text else Color.Transparent, CircleShape)
                    .desktopGestures(key = "pick$i", onTap = { d.calendar = i }))
            }
        }
        DeskText(desk.calendar.calendars[d.calendar.coerceIn(0, desk.calendar.calendars.lastIndex)].first, Desk.type.small, color = c.secondaryText)
        Spacer(Modifier.height(10.dp))
        DeskTextField(d.notes, { d.notes = it }, Modifier.fillMaxWidth().height(70.dp), placeholder = "Notes", singleLine = false)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PushButton("Cancel", onCancel)
            PushButton("Save", onSave, Modifier.testTag("event-save"), primary = true)
        }
        if (d.id != null) { Spacer(Modifier.height(8.dp)); PushButton("Delete Event", onDelete, destructive = true) }
    }
}
