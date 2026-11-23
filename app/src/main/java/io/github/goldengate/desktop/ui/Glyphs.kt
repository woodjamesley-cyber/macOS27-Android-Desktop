package io.github.goldengate.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An original line-glyph set drawn on a 24x24 grid in a rounded, SF-like stroke style.
 * None of these are copies of SF Symbols; they are simple geometric drawings.
 */
enum class Glyph {
    Emblem, Wifi, WifiOff, Bluetooth, Speaker, SpeakerMute, Battery, ControlCenter, Search, Moon, Sun,
    Display, Mirror, ChevronLeft, ChevronRight, ChevronDown, ChevronUp, Plus, Minus, Close, Check,
    Sidebar, Grid, ListView, Columns, Share, Gear, Star, StarFill, Play, Pause, Next, Prev, Shuffle, Repeat,
    Trash, Folder, Doc, Image, Note, House, Download, Clock, Globe, Refresh, Lock, Keyboard, Mouse, Gamepad,
    Bell, Hand, Eye, Paint, Dock, Power, Info, Desktop, Tag, Cloud, Terminal, Pencil, Bolt, Airplane,
    Calendar, Music, Timer, Stopwatch, Alarm, Copy, Scissors, Sort, Expand, Contract, Monitor, Cpu, Flag, Person,
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier, weight: Float = 1.7f) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color, weight) }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color, weight: Float = 1.7f) {
    val u = size.minDimension / 24f
    val sw = weight * u
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
    fun path(block: Path.() -> Unit, fill: Boolean = false) {
        val pa = Path().apply(block)
        drawPath(pa, color, style = if (fill) Fill else st)
    }
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(x1 * u, y1 * u, x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x * u, y * u)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) =
        drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = if (fill) Fill else st)
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) = drawCircle(color, r * u, p(x, y), style = if (fill) Fill else st)
    fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float) =
        drawArc(color, start, sweep, false, p(x - r, y - r), Size(2 * r * u, 2 * r * u), style = st)

    when (glyph) {
        Glyph.Emblem -> { // original "bridge" emblem: two towers and a suspension cable
            line(7f, 4f, 7f, 20f); line(17f, 4f, 17f, 20f)
            path({ m(2f, 7f); q(7f, 4f, 7f, 4f); q(12f, 15f, 17f, 4f); q(17f, 4f, 22f, 7f) })
            line(2f, 15f, 22f, 15f)
        }
        Glyph.Wifi, Glyph.WifiOff -> {
            arc(12f, 19f, 13f, 225f, 90f); arc(12f, 19f, 8.5f, 225f, 90f); arc(12f, 19f, 4f, 225f, 90f)
            circle(12f, 19f, 1.2f, true)
            if (glyph == Glyph.WifiOff) line(4f, 4f, 20f, 20f)
        }
        Glyph.Bluetooth -> path({ m(7f, 7.5f); l(16f, 16f); l(12f, 20f); l(12f, 4f); l(16f, 8f); l(7f, 16.5f) })
        Glyph.Speaker, Glyph.SpeakerMute -> {
            path({ m(4f, 9.5f); l(7.5f, 9.5f); l(12f, 5f); l(12f, 19f); l(7.5f, 14.5f); l(4f, 14.5f); close() })
            if (glyph == Glyph.Speaker) { arc(12f, 12f, 4f, -45f, 90f); arc(12f, 12f, 7.5f, -50f, 100f) }
            else { line(15.5f, 9.5f, 20.5f, 14.5f); line(20.5f, 9.5f, 15.5f, 14.5f) }
        }
        Glyph.Battery -> { rrect(2.5f, 7f, 17f, 10f, 3f); rrect(21f, 10f, 1.2f, 4f, 0.6f, true) }
        Glyph.ControlCenter -> {
            rrect(3f, 5f, 18f, 6f, 3f); circle(18f, 8f, 1.6f, true)
            rrect(3f, 13f, 18f, 6f, 3f); circle(6f, 16f, 1.6f, true)
        }
        Glyph.Search -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
        Glyph.Moon -> path({ m(15f, 3.5f); c(8f, 4.5f, 6f, 14f, 11f, 18f); c(14f, 20.5f, 18.5f, 20f, 21f, 17f); c(14f, 17f, 10.5f, 9f, 15f, 3.5f); close() })
        Glyph.Sun -> {
            circle(12f, 12f, 4f)
            for (i in 0 until 8) {
                val a = (i * 45.0) * PI / 180
                line((12 + cos(a) * 7).toFloat(), (12 + sin(a) * 7).toFloat(), (12 + cos(a) * 9.5).toFloat(), (12 + sin(a) * 9.5).toFloat())
            }
        }
        Glyph.Display, Glyph.Monitor -> { rrect(3f, 4f, 18f, 12f, 2f); line(9f, 20f, 15f, 20f); line(12f, 16f, 12f, 20f) }
        Glyph.Mirror -> { rrect(3f, 4f, 18f, 12f, 2f); path({ m(8f, 20f); l(12f, 15f); l(16f, 20f); close() }, fill = true) }
        Glyph.ChevronLeft -> path({ m(15f, 5f); l(8f, 12f); l(15f, 19f) })
        Glyph.ChevronRight -> path({ m(9f, 5f); l(16f, 12f); l(9f, 19f) })
        Glyph.ChevronDown -> path({ m(5f, 9f); l(12f, 16f); l(19f, 9f) })
        Glyph.ChevronUp -> path({ m(5f, 15f); l(12f, 8f); l(19f, 15f) })
        Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Glyph.Minus -> line(5f, 12f, 19f, 12f)
        Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.Check -> path({ m(5f, 12.5f); l(10f, 17.5f); l(19f, 6.5f) })
        Glyph.Sidebar -> { rrect(3f, 5f, 18f, 14f, 3f); line(9f, 5f, 9f, 19f) }
        Glyph.Grid -> { rrect(4f, 4f, 6.5f, 6.5f, 1.5f); rrect(13.5f, 4f, 6.5f, 6.5f, 1.5f); rrect(4f, 13.5f, 6.5f, 6.5f, 1.5f); rrect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f) }
        Glyph.ListView -> { for (y in listOf(6f, 12f, 18f)) { circle(5f, y, 1.1f, true); line(9f, y, 20f, y) } }
        Glyph.Columns -> { rrect(3f, 5f, 18f, 14f, 2.5f); line(9f, 5f, 9f, 19f); line(15f, 5f, 15f, 19f) }
        Glyph.Share -> { path({ m(8f, 9f); l(5f, 9f); l(5f, 20f); l(19f, 20f); l(19f, 9f); l(16f, 9f) }); line(12f, 3f, 12f, 14f); path({ m(8.5f, 6.5f); l(12f, 3f); l(15.5f, 6.5f) }) }
        Glyph.Gear -> {
            circle(12f, 12f, 3f)
            for (i in 0 until 8) {
                val a = (i * 45.0 + 22.5) * PI / 180
                line((12 + cos(a) * 6).toFloat(), (12 + sin(a) * 6).toFloat(), (12 + cos(a) * 8.8).toFloat(), (12 + sin(a) * 8.8).toFloat())
            }
            circle(12f, 12f, 6.2f)
        }
        Glyph.Star, Glyph.StarFill -> {
            val pa = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 9f else 4f
                val a = (-90.0 + i * 36.0) * PI / 180
                val x = (12 + cos(a) * r).toFloat() * u
                val y = (12.8 + sin(a) * r).toFloat() * u
                if (i == 0) pa.moveTo(x, y) else pa.lineTo(x, y)
            }
            pa.close()
            drawPath(pa, color, style = if (glyph == Glyph.StarFill) Fill else st)
        }
        Glyph.Play -> path({ m(7f, 4.5f); l(19.5f, 12f); l(7f, 19.5f); close() }, fill = true)
        Glyph.Pause -> { rrect(6f, 4.5f, 4f, 15f, 1.2f, true); rrect(14f, 4.5f, 4f, 15f, 1.2f, true) }
        Glyph.Next -> { path({ m(3f, 6f); l(11f, 12f); l(3f, 18f); close() }, fill = true); path({ m(11.5f, 6f); l(19.5f, 12f); l(11.5f, 18f); close() }, fill = true); rrect(19.5f, 6f, 1.8f, 12f, 0.6f, true) }
        Glyph.Prev -> { path({ m(21f, 6f); l(13f, 12f); l(21f, 18f); close() }, fill = true); path({ m(12.5f, 6f); l(4.5f, 12f); l(12.5f, 18f); close() }, fill = true); rrect(2.7f, 6f, 1.8f, 12f, 0.6f, true) }
        Glyph.Shuffle -> { path({ m(3f, 7f); l(7f, 7f); c(12f, 7f, 12f, 17f, 17f, 17f); l(20f, 17f) }); path({ m(3f, 17f); l(7f, 17f); c(12f, 17f, 12f, 7f, 17f, 7f); l(20f, 7f) }); path({ m(18f, 4.5f); l(20.5f, 7f); l(18f, 9.5f) }); path({ m(18f, 14.5f); l(20.5f, 17f); l(18f, 19.5f) }) }
        Glyph.Repeat -> { path({ m(4f, 12f); l(4f, 9f); q(4f, 6.5f, 7f, 6.5f); l(19f, 6.5f) }); path({ m(20f, 12f); l(20f, 15f); q(20f, 17.5f, 17f, 17.5f); l(5f, 17.5f) }); path({ m(16.5f, 4f); l(19f, 6.5f); l(16.5f, 9f) }); path({ m(7.5f, 15f); l(5f, 17.5f); l(7.5f, 20f) }) }
        Glyph.Trash -> { line(4f, 6.5f, 20f, 6.5f); path({ m(9f, 6.5f); l(9.5f, 4f); l(14.5f, 4f); l(15f, 6.5f) }); path({ m(6f, 6.5f); l(7f, 20f); l(17f, 20f); l(18f, 6.5f) }); line(10f, 10f, 10.3f, 16.5f); line(14f, 10f, 13.7f, 16.5f) }
        Glyph.Folder -> path({ m(3f, 7f); q(3f, 5f, 5f, 5f); l(9f, 5f); l(11f, 7f); l(19f, 7f); q(21f, 7f, 21f, 9f); l(21f, 17f); q(21f, 19f, 19f, 19f); l(5f, 19f); q(3f, 19f, 3f, 17f); close() })
        Glyph.Doc -> { path({ m(6f, 3f); l(14f, 3f); l(19f, 8f); l(19f, 21f); l(6f, 21f); close() }); path({ m(14f, 3f); l(14f, 8f); l(19f, 8f) }) }
        Glyph.Image -> { rrect(3f, 5f, 18f, 14f, 2.5f); circle(8.5f, 9.5f, 1.6f); path({ m(3.5f, 17f); l(9f, 12f); l(13f, 15.5f); l(16f, 13f); l(20.5f, 17f) }) }
        Glyph.Note, Glyph.Music -> { circle(7.5f, 17.5f, 2.6f, true); circle(16.5f, 15.5f, 2.6f, true); line(10f, 17.5f, 10f, 5.5f); line(19f, 15.5f, 19f, 3.5f); line(10f, 5.5f, 19f, 3.5f) }
        Glyph.House -> { path({ m(3f, 11f); l(12f, 3.5f); l(21f, 11f) }); path({ m(5.5f, 9.5f); l(5.5f, 20f); l(18.5f, 20f); l(18.5f, 9.5f) }); path({ m(10f, 20f); l(10f, 14f); l(14f, 14f); l(14f, 20f) }) }
        Glyph.Download -> { line(12f, 4f, 12f, 15f); path({ m(7.5f, 10.5f); l(12f, 15f); l(16.5f, 10.5f) }); line(5f, 20f, 19f, 20f) }
        Glyph.Clock, Glyph.Timer -> { circle(12f, 12f, 8.5f); line(12f, 12f, 12f, 7f); line(12f, 12f, 15.5f, 14f) }
        Glyph.Stopwatch -> { circle(12f, 13.5f, 7.5f); line(12f, 13.5f, 12f, 9.5f); line(10f, 3f, 14f, 3f); line(12f, 3f, 12f, 6f) }
        Glyph.Alarm -> { circle(12f, 13f, 7f); line(12f, 13f, 12f, 9.5f); line(12f, 13f, 14.5f, 14.5f); line(4f, 6f, 7f, 3.5f); line(20f, 6f, 17f, 3.5f) }
        Glyph.Globe -> { circle(12f, 12f, 8.5f); line(3.5f, 12f, 20.5f, 12f); drawOval(color, p(8f, 3.5f), Size(8f * u, 17f * u), style = st); path({ m(5f, 7f); q(12f, 9.5f, 19f, 7f) }); path({ m(5f, 17f); q(12f, 14.5f, 19f, 17f) }) }
        Glyph.Refresh -> { arc(12f, 12f, 7.5f, -60f, 300f); path({ m(16f, 3.5f); l(16.3f, 7.8f); l(12.2f, 8.2f) }) }
        Glyph.Lock -> { rrect(5f, 10.5f, 14f, 10f, 2.5f); path({ m(8f, 10.5f); l(8f, 7.5f); q(8f, 3.5f, 12f, 3.5f); q(16f, 3.5f, 16f, 7.5f); l(16f, 10.5f) }) }
        Glyph.Keyboard -> { rrect(2.5f, 6f, 19f, 12f, 2.5f); for (x in listOf(6f, 9.5f, 13f, 16.5f)) circle(x, 10f, 0.9f, true); line(7f, 14.5f, 17f, 14.5f) }
        Glyph.Mouse -> { rrect(7f, 3f, 10f, 18f, 5f); line(12f, 6f, 12f, 9.5f) }
        Glyph.Gamepad -> { path({ m(7f, 7f); l(17f, 7f); q(21.5f, 7f, 21.5f, 13f); q(21.5f, 19f, 18f, 18f); l(15f, 15f); l(9f, 15f); l(6f, 18f); q(2.5f, 19f, 2.5f, 13f); q(2.5f, 7f, 7f, 7f); close() }); line(6f, 11f, 10f, 11f); line(8f, 9f, 8f, 13f); circle(16f, 10f, 0.9f, true); circle(18f, 12f, 0.9f, true) }
        Glyph.Bell -> { path({ m(6f, 17f); l(6f, 11f); q(6f, 5f, 12f, 5f); q(18f, 5f, 18f, 11f); l(18f, 17f); l(20f, 18.5f); l(4f, 18.5f); close() }); path({ m(10f, 20.5f); q(12f, 22.5f, 14f, 20.5f) }) }
        Glyph.Hand -> { path({ m(8f, 13f); l(8f, 6f); q(8f, 4.5f, 9.5f, 4.5f); q(11f, 4.5f, 11f, 6f); l(11f, 11f); l(11f, 4f); q(11f, 2.5f, 12.5f, 2.5f); q(14f, 2.5f, 14f, 4f); l(14f, 11f); l(14f, 5f); q(14f, 3.5f, 15.5f, 3.5f); q(17f, 3.5f, 17f, 5f); l(17f, 14f); q(17f, 21f, 11.5f, 21f); q(7.5f, 21f, 5f, 16f); l(3.5f, 12.5f); q(4.5f, 10.5f, 6.5f, 12f); close() }) }
        Glyph.Eye -> { path({ m(2.5f, 12f); q(12f, 2f, 21.5f, 12f); q(12f, 22f, 2.5f, 12f); close() }); circle(12f, 12f, 3f) }
        Glyph.Paint -> { circle(12f, 12f, 8.5f); circle(8f, 10f, 1.3f, true); circle(12f, 7.5f, 1.3f, true); circle(16f, 10f, 1.3f, true); circle(11f, 16f, 2f) }
        Glyph.Dock -> { rrect(3f, 4f, 18f, 16f, 3f); rrect(6f, 14f, 12f, 3f, 1.5f, true) }
        Glyph.Power -> { arc(12f, 13f, 7.5f, -50f, 280f); line(12f, 3.5f, 12f, 11f) }
        Glyph.Info -> { circle(12f, 12f, 8.5f); line(12f, 11f, 12f, 16.5f); circle(12f, 7.8f, 1.1f, true) }
        Glyph.Desktop -> { rrect(3f, 4f, 18f, 13f, 2f); line(8f, 20f, 16f, 20f); rrect(5.5f, 6.5f, 4f, 3.5f, 0.8f, true) }
        Glyph.Tag -> { path({ m(3.5f, 4f); l(11f, 4f); l(20.5f, 13.5f); l(13.5f, 20.5f); l(4f, 11f); close() }); circle(8f, 8.5f, 1.3f, true) }
        Glyph.Cloud -> path({ m(7f, 18f); q(2.5f, 18f, 2.5f, 14f); q(2.5f, 10f, 7f, 10f); q(8f, 5f, 13f, 5f); q(18f, 5f, 18.5f, 10f); q(21.5f, 10.5f, 21.5f, 14f); q(21.5f, 18f, 17.5f, 18f); close() })
        Glyph.Terminal -> { rrect(3f, 4f, 18f, 16f, 3f); path({ m(7f, 9f); l(10f, 12f); l(7f, 15f) }); line(12f, 15f, 16.5f, 15f) }
        Glyph.Pencil -> { path({ m(4f, 20f); l(5f, 15.5f); l(15.5f, 5f); q(17f, 3.5f, 18.5f, 5f); l(19f, 5.5f); q(20.5f, 7f, 19f, 8.5f); l(8.5f, 19f); close() }); line(13.5f, 7f, 17f, 10.5f) }
        Glyph.Bolt -> path({ m(13.5f, 2.5f); l(5f, 13.5f); l(11.5f, 13.5f); l(10.5f, 21.5f); l(19f, 10.5f); l(12.5f, 10.5f); close() }, fill = true)
        Glyph.Airplane -> path({ m(12f, 2.5f); q(13.5f, 2.5f, 13.5f, 5f); l(13.5f, 9.5f); l(21f, 14f); l(21f, 16f); l(13.5f, 13.5f); l(13.5f, 18f); l(16f, 20f); l(16f, 21.5f); l(12f, 20.5f); l(8f, 21.5f); l(8f, 20f); l(10.5f, 18f); l(10.5f, 13.5f); l(3f, 16f); l(3f, 14f); l(10.5f, 9.5f); l(10.5f, 5f); q(10.5f, 2.5f, 12f, 2.5f); close() })
        Glyph.Calendar -> { rrect(3.5f, 5f, 17f, 15.5f, 3f); line(3.5f, 9.5f, 20.5f, 9.5f); line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f) }
        Glyph.Copy -> { rrect(8f, 8f, 12f, 12f, 2.5f); path({ m(16f, 5.5f); l(16f, 5f); q(16f, 4f, 15f, 4f); l(5f, 4f); q(4f, 4f, 4f, 5f); l(4f, 15f); q(4f, 16f, 5f, 16f); l(5.5f, 16f) }) }
        Glyph.Scissors -> { circle(6.5f, 17.5f, 2.8f); circle(17.5f, 17.5f, 2.8f); line(8.5f, 15.5f, 18f, 3.5f); line(15.5f, 15.5f, 6f, 3.5f) }
        Glyph.Sort -> { line(7f, 4f, 7f, 20f); path({ m(3.5f, 7.5f); l(7f, 4f); l(10.5f, 7.5f) }); line(17f, 4f, 17f, 20f); path({ m(13.5f, 16.5f); l(17f, 20f); l(20.5f, 16.5f) }) }
        Glyph.Expand -> { path({ m(14f, 4f); l(20f, 4f); l(20f, 10f) }); path({ m(10f, 20f); l(4f, 20f); l(4f, 14f) }); line(20f, 4f, 13.5f, 10.5f); line(4f, 20f, 10.5f, 13.5f) }
        Glyph.Contract -> { path({ m(13.5f, 4.5f); l(13.5f, 10.5f); l(19.5f, 10.5f) }); path({ m(10.5f, 19.5f); l(10.5f, 13.5f); l(4.5f, 13.5f) }); line(13.5f, 10.5f, 20f, 4f); line(10.5f, 13.5f, 4f, 20f) }
        Glyph.Cpu -> { rrect(6f, 6f, 12f, 12f, 2f); rrect(9.5f, 9.5f, 5f, 5f, 1f, true); for (k in listOf(9f, 12f, 15f)) { line(k, 3f, k, 6f); line(k, 18f, k, 21f); line(3f, k, 6f, k); line(18f, k, 21f, k) } }
        Glyph.Flag -> { line(5f, 3f, 5f, 21f); path({ m(5f, 4f); q(9f, 2f, 12f, 4f); q(15f, 6f, 19f, 4f); l(19f, 13f); q(15f, 15f, 12f, 13f); q(9f, 11f, 5f, 13f) }) }
        Glyph.Person -> { circle(12f, 8f, 4f); path({ m(4f, 20.5f); q(4.5f, 14f, 12f, 14f); q(19.5f, 14f, 20f, 20.5f) }) }
    }
    @Suppress("UNUSED_VARIABLE") val unused = Rect.Zero
}
