package io.github.goldengate.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import io.github.goldengate.desktop.core.AppIds
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original app icons in a "layered glass" squircle style: gradient base, a foreground symbol
 * on its own glass layer, top sheen and a specular rim. Designed for this project.
 */
@Composable
fun AppIcon(appId: String, size: Dp, modifier: Modifier = Modifier, dark: Boolean = false) {
    Canvas(modifier.size(size)) { drawAppIcon(appId, dark) }
}

private fun squircle(s: Size): Path {
    // Superellipse-like rounded square (continuous corners approximation).
    val w = s.width
    val h = s.height
    val r = w * 0.235f
    val k = r * 0.62f
    return Path().apply {
        moveTo(r, 0f)
        lineTo(w - r, 0f)
        cubicTo(w - r + k, 0f, w, r - k, w, r)
        lineTo(w, h - r)
        cubicTo(w, h - r + k, w - r + k, h, w - r, h)
        lineTo(r, h)
        cubicTo(r - k, h, 0f, h - r + k, 0f, h - r)
        lineTo(0f, r)
        cubicTo(0f, r - k, r - k, 0f, r, 0f)
        close()
    }
}

fun DrawScope.drawAppIcon(appId: String, dark: Boolean = false) {
    val s = size
    val pad = s.minDimension * 0.06f
    inset(pad) {
        val shape = squircle(size)
        // drop shadow
        withTransform({ translate(0f, size.height * 0.03f) }) {
            drawPath(shape, Color.Black.copy(alpha = 0.22f))
        }
        clipPath(shape) {
            val (c1, c2) = iconColors(appId, dark)
            drawRect(Brush.linearGradient(listOf(c1, c2), start = Offset(0f, 0f), end = Offset(size.width * 0.3f, size.height)))
            drawSymbol(appId, dark)
            // glass sheen
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.45f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent),
            )
        }
        // specular rim
        drawPath(
            shape,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.75f), 0.35f to Color.White.copy(alpha = 0.10f),
                0.75f to Color.White.copy(alpha = 0.05f), 1f to Color.White.copy(alpha = 0.40f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
            style = Stroke(size.minDimension * 0.022f),
        )
    }
}

private fun iconColors(appId: String, dark: Boolean): Pair<Color, Color> = when (appId) {
    AppIds.FINDER -> Color(0xFF5AC8FA) to Color(0xFF1E6FE8)
    AppIds.SETTINGS -> Color(0xFFB8BCC6) to Color(0xFF5E636E)
    AppIds.BROWSER -> Color(0xFF7FD4FF) to Color(0xFF2155E8)
    AppIds.TERMINAL -> Color(0xFF3A3D45) to Color(0xFF0D0E12)
    AppIds.CALCULATOR -> Color(0xFF4A4A50) to Color(0xFF1C1C20)
    AppIds.CALENDAR -> Color(0xFFFFFFFF) to Color(0xFFE9E9EE)
    AppIds.CLOCK -> Color(0xFF2E2E33) to Color(0xFF050507)
    AppIds.NOTES -> Color(0xFFFFF3B0) to Color(0xFFFFD84D)
    AppIds.PHOTOS -> Color(0xFFFFFFFF) to Color(0xFFEDEFF5)
    AppIds.FILES -> Color(0xFF6FB6FF) to Color(0xFF2F6CF6)
    AppIds.MUSIC -> Color(0xFFFF6FB5) to Color(0xFF7A3CFF)
    AppIds.SYSINFO -> Color(0xFF4DD6C3) to Color(0xFF0F6F86)
    AppIds.LAUNCHER -> Color(0xFFE6E8EE) to Color(0xFFB9BECB)
    AppIds.MISSION -> Color(0xFF7A86A8) to Color(0xFF353C55)
    AppIds.TRASH, AppIds.TRASH_FULL -> Color(0x00FFFFFF) to Color(0x00FFFFFF)
    AppIds.HELP -> Color(0xFFFFE27A) to Color(0xFFFFA928)
    AppIds.STORE -> Color(0xFF3E8BFF) to Color(0xFF1338B8)
    else -> Color(0xFF9AA0AE) to Color(0xFF555B69)
}

private fun DrawScope.drawSymbol(appId: String, dark: Boolean) {
    val w = size.width
    val h = size.height
    val u = w / 24f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val white = Color.White
    when (appId) {
        AppIds.FINDER -> {
            // A folder with a magnifier lens.
            val folder = Path().apply {
                moveTo(4f * u, 8f * u); lineTo(9f * u, 8f * u); lineTo(10.5f * u, 9.5f * u); lineTo(20f * u, 9.5f * u)
                lineTo(20f * u, 18f * u); lineTo(4f * u, 18f * u); close()
            }
            drawPath(folder, white.copy(alpha = 0.92f))
            drawRect(Color(0x33006BD6), o(4f, 11f), Size(16f * u, 0.8f * u))
            drawCircle(Color(0xFF1E6FE8), 3.2f * u, o(14.5f, 14f), style = Stroke(1.4f * u))
            drawLine(Color(0xFF1E6FE8), o(16.8f, 16.3f), o(18.6f, 18.1f), 1.6f * u)
        }
        AppIds.SETTINGS -> {
            val cx = w / 2; val cy = h / 2
            for (i in 0 until 12) {
                rotate(i * 30f, Offset(cx, cy)) { drawRoundRect(Color(0xFFE9EBF0), Offset(cx - 1.1f * u, cy - 8.6f * u), Size(2.2f * u, 3.6f * u), CornerRadius(0.6f * u)) }
            }
            drawCircle(Color(0xFFE9EBF0), 6.2f * u, Offset(cx, cy))
            drawCircle(Color(0xFF6C717C), 4.4f * u, Offset(cx, cy))
            drawCircle(Color(0xFFD6D9E0), 2.2f * u, Offset(cx, cy))
        }
        AppIds.BROWSER -> {
            val c = Offset(w / 2, h / 2)
            drawCircle(white.copy(alpha = 0.95f), 8.2f * u, c, style = Stroke(1.3f * u))
            drawOval(white.copy(alpha = 0.9f), o(8.6f, 3.8f), Size(6.8f * u, 16.4f * u), style = Stroke(1.1f * u))
            drawLine(white.copy(alpha = 0.9f), o(3.8f, 12f), o(20.2f, 12f), 1.1f * u)
            rotate(-25f, c) { drawOval(Color(0xFFFFD36E), o(1.5f, 10.4f), Size(21f * u, 3.2f * u), style = Stroke(1.2f * u)) }
        }
        AppIds.TERMINAL -> {
            val green = Color(0xFFE6E6E6)
            drawLine(green, o(6f, 8.5f), o(10f, 12f), 1.6f * u); drawLine(green, o(10f, 12f), o(6f, 15.5f), 1.6f * u)
            drawLine(green, o(12f, 15.5f), o(18f, 15.5f), 1.6f * u)
            drawRect(Color(0x22FFFFFF), o(2f, 2f), Size(20f * u, 3f * u))
        }
        AppIds.CALCULATOR -> {
            val cols = listOf(Color(0xFF8E8E93), Color(0xFF8E8E93), Color(0xFFFF9F0A))
            for (r in 0 until 3) for (c in 0 until 3) {
                drawCircle(if (c == 2) cols[2] else if (r == 0) Color(0xFFB8B8BD) else Color(0xFF5A5A60), 2.4f * u, o(6.5f + c * 5.5f, 6.5f + r * 5.5f))
            }
        }
        AppIds.CALENDAR -> {
            val cal = Calendar.getInstance()
            drawRect(Color(0xFFFF3B30), Offset.Zero, Size(w, 6.5f * u))
            val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            paint.color = android.graphics.Color.WHITE; paint.textSize = 3.6f * u
            drawContext.canvas.nativeCanvas.drawText(days[cal.get(Calendar.DAY_OF_WEEK) - 1], w / 2, 4.9f * u, paint)
            paint.color = 0xFF1C1C1E.toInt(); paint.textSize = 12.5f * u; paint.isFakeBoldText = false
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            drawContext.canvas.nativeCanvas.drawText(cal.get(Calendar.DAY_OF_MONTH).toString(), w / 2, 19.5f * u, paint)
        }
        AppIds.CLOCK -> {
            val c = Offset(w / 2, h / 2)
            drawCircle(Color(0xFFF7F7F7), 8.6f * u, c)
            for (i in 0 until 12) {
                val a = i * 30.0 * PI / 180
                drawLine(Color(0xFF1C1C1E), Offset(c.x + (cos(a) * 7.4f * u).toFloat(), c.y + (sin(a) * 7.4f * u).toFloat()),
                    Offset(c.x + (cos(a) * 8.1f * u).toFloat(), c.y + (sin(a) * 8.1f * u).toFloat()), 0.6f * u)
            }
            val cal = Calendar.getInstance()
            val ha = ((cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f) * 30f - 90f) * PI / 180
            val ma = (cal.get(Calendar.MINUTE) * 6f - 90f) * PI / 180
            drawLine(Color(0xFF1C1C1E), c, Offset(c.x + (cos(ha) * 4.5f * u).toFloat(), c.y + (sin(ha) * 4.5f * u).toFloat()), 1.2f * u)
            drawLine(Color(0xFF1C1C1E), c, Offset(c.x + (cos(ma) * 6.6f * u).toFloat(), c.y + (sin(ma) * 6.6f * u).toFloat()), 0.9f * u)
            drawLine(Color(0xFFFF9F0A), c, Offset(c.x + 6.8f * u, c.y - 1.5f * u), 0.4f * u)
            drawCircle(Color(0xFFFF9F0A), 0.7f * u, c)
        }
        AppIds.NOTES -> {
            drawRect(Color(0xFFFFFFFF), o(0f, 7f), Size(w, h))
            for (i in 0 until 5) drawLine(Color(0x22000000), o(3f, 10.5f + i * 2.6f), o(21f, 10.5f + i * 2.6f), 0.35f * u)
            drawRect(Color(0xFFFFC93C), o(0f, 0f), Size(w, 7f * u))
            for (i in 0 until 7) drawCircle(Color(0x33000000), 0.4f * u, o(2.8f + i * 3.1f, 5.2f))
        }
        AppIds.PHOTOS -> {
            // Sun over layered hills.
            drawCircle(Color(0xFFFFB020), 2.8f * u, o(16.5f, 7.5f))
            val hill1 = Path().apply { moveTo(0f, 17f * u); quadraticTo(7f * u, 9f * u, 15f * u, 16f * u); lineTo(w, 13f * u); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(hill1, Brush.verticalGradient(listOf(Color(0xFF34C759), Color(0xFF0E8A4A)), startY = 10f * u, endY = h))
            val hill2 = Path().apply { moveTo(0f, 20f * u); quadraticTo(12f * u, 13f * u, w, 19f * u); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(hill2, Brush.verticalGradient(listOf(Color(0xFF3FA9FF), Color(0xFF1E5FD8)), startY = 14f * u, endY = h))
        }
        AppIds.FILES -> {
            val back = Path().apply { moveTo(4f * u, 7f * u); lineTo(10f * u, 7f * u); lineTo(11.5f * u, 8.6f * u); lineTo(20f * u, 8.6f * u); lineTo(20f * u, 18.5f * u); lineTo(4f * u, 18.5f * u); close() }
            drawPath(back, Color(0xFF9FD0FF))
            drawRoundRect(white.copy(alpha = 0.95f), o(4f, 10.5f), Size(16f * u, 8f * u), CornerRadius(1.2f * u))
            drawLine(Color(0xFF2F6CF6), o(9f, 14.5f), o(15f, 14.5f), 1.1f * u)
        }
        AppIds.MUSIC -> {
            val c = Offset(w / 2, h / 2)
            for (i in 0 until 24) {
                val a = i * 15.0 * PI / 180
                val len = (1.2f + ((i * 7) % 5) * 0.55f) * u
                drawLine(white.copy(alpha = 0.55f), Offset(c.x + (cos(a) * 7f * u).toFloat(), c.y + (sin(a) * 7f * u).toFloat()),
                    Offset(c.x + (cos(a) * (7f * u + len)).toFloat(), c.y + (sin(a) * (7f * u + len)).toFloat()), 0.6f * u)
            }
            drawCircle(white.copy(alpha = 0.95f), 1.7f * u, o(10f, 15.5f)); drawCircle(white.copy(alpha = 0.95f), 1.7f * u, o(15.2f, 14.3f))
            drawLine(white, o(11.5f, 15.5f), o(11.5f, 7.5f), 0.9f * u); drawLine(white, o(16.7f, 14.3f), o(16.7f, 6.4f), 0.9f * u)
            drawLine(white, o(11.5f, 7.6f), o(16.7f, 6.4f), 1.4f * u)
        }
        AppIds.SYSINFO -> {
            drawRoundRect(white.copy(alpha = 0.92f), o(6f, 6f), Size(12f * u, 12f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFF0F6F86), o(9f, 9f), Size(6f * u, 6f * u), CornerRadius(1f * u))
            for (k in listOf(8.5f, 12f, 15.5f)) {
                drawLine(white.copy(alpha = 0.9f), o(k, 3f), o(k, 6f), 0.9f * u); drawLine(white.copy(alpha = 0.9f), o(k, 18f), o(k, 21f), 0.9f * u)
                drawLine(white.copy(alpha = 0.9f), o(3f, k), o(6f, k), 0.9f * u); drawLine(white.copy(alpha = 0.9f), o(18f, k), o(21f, k), 0.9f * u)
            }
        }
        AppIds.LAUNCHER -> {
            val cs = listOf(Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF32D74B), Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F))
            for (r in 0 until 3) for (c in 0 until 3) drawRoundRect(cs[r * 3 + c], o(5f + c * 5.2f, 5f + r * 5.2f), Size(3.8f * u, 3.8f * u), CornerRadius(1.1f * u))
        }
        AppIds.MISSION -> {
            drawRoundRect(white.copy(alpha = 0.85f), o(4f, 5f), Size(8f * u, 6f * u), CornerRadius(1f * u))
            drawRoundRect(white.copy(alpha = 0.65f), o(13f, 5f), Size(7f * u, 6f * u), CornerRadius(1f * u))
            drawRoundRect(white.copy(alpha = 0.75f), o(4f, 12.5f), Size(16f * u, 7f * u), CornerRadius(1f * u))
        }
        AppIds.TRASH, AppIds.TRASH_FULL -> {
            // Translucent glass bin.
            val bin = Path().apply { moveTo(5f * u, 6f * u); lineTo(19f * u, 6f * u); lineTo(17.5f * u, 22f * u); lineTo(6.5f * u, 22f * u); close() }
            drawPath(bin, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x33C8D0DC))))
            drawPath(bin, Color(0xAAFFFFFF), style = Stroke(0.5f * u))
            for (i in 0 until 5) drawLine(Color(0x55FFFFFF), o(7.5f + i * 2.25f, 8f), o(8.2f + i * 1.9f, 21f), 0.5f * u)
            if (appId == AppIds.TRASH_FULL) {
                drawCircle(Color(0xFFF2F2F7), 2.4f * u, o(10f, 5.5f)); drawCircle(Color(0xFFDDE3EC), 2.2f * u, o(13.5f, 4.8f)); drawCircle(Color(0xFFE9EDF3), 2f * u, o(15.5f, 6.2f))
            }
            drawRoundRect(Color(0xCCFFFFFF), o(4f, 5f), Size(16f * u, 1.6f * u), CornerRadius(0.8f * u))
        }
        AppIds.STORE -> {
            // A glass shopping bag with a plus, drawn for this project.
            val bag = Path().apply {
                moveTo(4.5f * u, 8f * u); lineTo(19.5f * u, 8f * u); lineTo(18.5f * u, 20f * u)
                lineTo(5.5f * u, 20f * u); close()
            }
            drawPath(bag, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))))
            drawPath(bag, white.copy(alpha = 0.9f), style = Stroke(0.7f * u))
            drawArc(white.copy(alpha = 0.9f), -180f, 180f, false, o(8f, 4.4f), Size(8f * u, 7.2f * u), style = Stroke(0.9f * u))
            drawLine(white, o(12f, 11.5f), o(12f, 16.5f), 1.4f * u)
            drawLine(white, o(9.5f, 14f), o(14.5f, 14f), 1.4f * u)
        }
        AppIds.HELP -> {
            drawCircle(white.copy(alpha = 0.95f), 5.5f * u, o(12f, 10f))
            drawRoundRect(white.copy(alpha = 0.95f), o(9.5f, 15f), Size(5f * u, 3f * u), CornerRadius(1f * u))
            drawRoundRect(Color(0xFF8A6100), o(10f, 18.5f), Size(4f * u, 1.3f * u), CornerRadius(0.6f * u))
        }
        else -> drawGlyph(Glyph.Doc, white)
    }
}
