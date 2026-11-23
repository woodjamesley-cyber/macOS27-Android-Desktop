package io.github.goldengate.desktop.core

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

@Immutable
data class CalEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val durationMin: Int,
    val calendar: Int, // index into CalendarStore.calendars
    val notes: String = "",
    val notified: Boolean = false,
)

/** Calendar events persisted as JSON in app-private storage. */
class CalendarStore(context: Context) {
    private val prefs = context.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    val events = mutableStateListOf<CalEvent>()
    val calendars = listOf("Home" to 0xFF0A84FF, "Work" to 0xFFFF9F0A, "Personal" to 0xFF32D74B, "Birthdays" to 0xFFBF5AF2)

    init {
        load()
        if (!prefs.getBoolean("seeded", false)) seed()
    }

    private fun load() {
        events.clear()
        val arr = JSONArray(prefs.getString("events", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            events += CalEvent(o.getLong("id"), o.getString("title"), o.getLong("start"), o.getInt("dur"), o.optInt("cal"), o.optString("notes"), o.optBoolean("notified"))
        }
    }

    private fun persist() {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("title", e.title).put("start", e.start).put("dur", e.durationMin).put("cal", e.calendar).put("notes", e.notes).put("notified", e.notified))
        }
        prefs.edit().putString("events", arr.toString()).apply()
    }

    private fun seed() {
        val c = Calendar.getInstance()
        fun at(dayOffset: Int, h: Int, m: Int): Long = (c.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, dayOffset); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        add("Design review", at(0, 14, 0), 60, 1, "Liquid Glass components walkthrough")
        add("Coffee with Sam", at(1, 9, 30), 30, 2)
        add("Sprint planning", at(2, 10, 0), 90, 1)
        add("Yoga", at(-1, 18, 0), 60, 2)
        add("Release v1.0", at(4, 16, 0), 30, 1, "Ship the APK")
        add("Family dinner", at(6, 19, 0), 120, 0)
        prefs.edit().putBoolean("seeded", true).apply()
    }

    fun add(title: String, start: Long, dur: Int, cal: Int, notes: String = ""): CalEvent {
        val e = CalEvent(System.nanoTime(), title, start, dur, cal, notes)
        events += e
        persist()
        return e
    }

    fun update(e: CalEvent) {
        val i = events.indexOfFirst { it.id == e.id }
        if (i >= 0) { events[i] = e; persist() }
    }

    fun delete(id: Long) { events.removeAll { it.id == id }; persist() }

    fun on(dayStart: Long): List<CalEvent> {
        val end = dayStart + 86_400_000L
        return events.filter { it.start in dayStart until end }.sortedBy { it.start }
    }

    fun color(cal: Int): Long = calendars.getOrNull(cal)?.second ?: 0xFF0A84FF
}

/** Countdown timers live here (not in the Clock window) so they keep running when it closes. */
class TimerService {
    var timerEnd by mutableLongStateOf(0L)
    var timerTotal by mutableLongStateOf(0L)
    var timerPausedRemaining by mutableLongStateOf(0L)
    var timerRunning by mutableStateOf(false)

    var stopwatchStart by mutableLongStateOf(0L)
    var stopwatchAccum by mutableLongStateOf(0L)
    var stopwatchRunning by mutableStateOf(false)
    val laps = mutableStateListOf<Long>()

    fun startTimer(ms: Long) { timerTotal = ms; timerEnd = System.currentTimeMillis() + ms; timerRunning = true; timerPausedRemaining = 0 }
    fun pauseTimer() { if (timerRunning) { timerPausedRemaining = (timerEnd - System.currentTimeMillis()).coerceAtLeast(0); timerRunning = false } }
    fun resumeTimer() { if (!timerRunning && timerPausedRemaining > 0) { timerEnd = System.currentTimeMillis() + timerPausedRemaining; timerRunning = true; timerPausedRemaining = 0 } }
    fun cancelTimer() { timerRunning = false; timerTotal = 0; timerPausedRemaining = 0 }
    fun remaining(now: Long = System.currentTimeMillis()): Long = if (timerRunning) (timerEnd - now).coerceAtLeast(0) else timerPausedRemaining

    fun stopwatchElapsed(now: Long = System.currentTimeMillis()) = stopwatchAccum + if (stopwatchRunning) now - stopwatchStart else 0
    fun toggleStopwatch() {
        if (stopwatchRunning) { stopwatchAccum += System.currentTimeMillis() - stopwatchStart; stopwatchRunning = false }
        else { stopwatchStart = System.currentTimeMillis(); stopwatchRunning = true }
    }
    fun lapOrReset() { if (stopwatchRunning) laps.add(0, stopwatchElapsed()) else { stopwatchAccum = 0; laps.clear() } }
}
