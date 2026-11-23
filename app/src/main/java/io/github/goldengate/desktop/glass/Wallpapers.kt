package io.github.goldengate.desktop.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Original, procedurally painted wallpapers (no Apple artwork is bundled). Each has a light and
 * a dark variant so the desktop follows the appearance setting, like dynamic wallpapers.
 */
enum class Wallpaper(
    val label: String,
    val lightStops: IntArray,
    val darkStops: IntArray,
    val blobs: IntArray,
    val darkBlobs: IntArray,
    val waves: Boolean,
) {
    GoldenHour(
        "Golden Hour",
        intArrayOf(0xFFFFB36B.toInt(), 0xFFFF7A6B.toInt(), 0xFFB45AC9.toInt(), 0xFF4A3FB8.toInt()),
        intArrayOf(0xFF2B1A4F.toInt(), 0xFF5A2466.toInt(), 0xFF9A3A55.toInt(), 0xFF1A1033.toInt()),
        intArrayOf(0xFFFFD36E.toInt(), 0xFFFF5F8F.toInt(), 0xFF7B5CFF.toInt()),
        intArrayOf(0xFFFF8A4C.toInt(), 0xFFB3328A.toInt(), 0xFF3E2CA8.toInt()),
        true,
    ),
    BayFog(
        "Bay Fog",
        intArrayOf(0xFFDCE8F5.toInt(), 0xFFA8C3E6.toInt(), 0xFF7FA0D9.toInt(), 0xFF5875C2.toInt()),
        intArrayOf(0xFF0E1A2E.toInt(), 0xFF1B2F52.toInt(), 0xFF2A4A7A.toInt(), 0xFF0A1220.toInt()),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFF9EC9FF.toInt(), 0xFFB8A8FF.toInt()),
        intArrayOf(0xFF3B6FB8.toInt(), 0xFF27508F.toInt(), 0xFF5B4DA8.toInt()),
        true,
    ),
    Aurora(
        "Aurora",
        intArrayOf(0xFFB8F2E6.toInt(), 0xFF6FD3C7.toInt(), 0xFF5A8FE0.toInt(), 0xFF7A5BD6.toInt()),
        intArrayOf(0xFF04161C.toInt(), 0xFF0B3A40.toInt(), 0xFF1C2F6B.toInt(), 0xFF120A2E.toInt()),
        intArrayOf(0xFF7CFFCB.toInt(), 0xFF4FC3FF.toInt(), 0xFFC07CFF.toInt()),
        intArrayOf(0xFF1FD19B.toInt(), 0xFF2B7FD6.toInt(), 0xFF8A3FD1.toInt()),
        true,
    ),
    MidnightBloom(
        "Midnight Bloom",
        intArrayOf(0xFFF6D5F0.toInt(), 0xFFE39AD8.toInt(), 0xFF9C6BE0.toInt(), 0xFF4D49B8.toInt()),
        intArrayOf(0xFF0B0820.toInt(), 0xFF24103F.toInt(), 0xFF4A1457.toInt(), 0xFF07051A.toInt()),
        intArrayOf(0xFFFF9BE0.toInt(), 0xFF8E7BFF.toInt(), 0xFFFFC2A8.toInt()),
        intArrayOf(0xFFD4338F.toInt(), 0xFF5B2FD9.toInt(), 0xFF1E46B0.toInt()),
        false,
    ),
    Citrus(
        "Citrus",
        intArrayOf(0xFFFFF4C2.toInt(), 0xFFFFD66B.toInt(), 0xFFFF9F45.toInt(), 0xFFF2625A.toInt()),
        intArrayOf(0xFF2A1606.toInt(), 0xFF5C2D0A.toInt(), 0xFF8F3E13.toInt(), 0xFF1E0D05.toInt()),
        intArrayOf(0xFFFFF07A.toInt(), 0xFFFFA14F.toInt(), 0xFFFF6B8A.toInt()),
        intArrayOf(0xFFE88A1A.toInt(), 0xFFC2410C.toInt(), 0xFF9C1D4F.toInt()),
        false,
    ),
    Graphite(
        "Graphite",
        intArrayOf(0xFFEDEEF2.toInt(), 0xFFCFD2DA.toInt(), 0xFFA9AEBB.toInt(), 0xFF7E8494.toInt()),
        intArrayOf(0xFF111214.toInt(), 0xFF1E2024.toInt(), 0xFF2D3038.toInt(), 0xFF0A0B0D.toInt()),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFB7C0D6.toInt(), 0xFFD9D2E8.toInt()),
        intArrayOf(0xFF3A4152.toInt(), 0xFF262B36.toInt(), 0xFF4A4458.toInt()),
        true,
    );

    companion object {
        fun byId(id: String): Wallpaper = entries.firstOrNull { it.name == id } ?: GoldenHour
    }
}

object WallpaperPainter {

    fun paint(wallpaper: Wallpaper, dark: Boolean, width: Int, height: Int, seed: Int = wallpaper.ordinal * 31 + 7): Bitmap {
        val w = max(1, width)
        val h = max(1, height)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stops = if (dark) wallpaper.darkStops else wallpaper.lightStops
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), stops, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        val rnd = Random(seed)
        val blobs = if (dark) wallpaper.darkBlobs else wallpaper.blobs
        val dim = max(w, h).toFloat()
        for (i in 0 until 7) {
            val color = blobs[i % blobs.size]
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            val r = dim * (0.25f + rnd.nextFloat() * 0.35f)
            p.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(color, if (dark) 150 else 170), withAlpha(color, 0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }

        if (wallpaper.waves) {
            // Soft sweeping bands, loosely evoking cables of a suspension bridge in the fog.
            val band = Paint(Paint.ANTI_ALIAS_FLAG)
            band.maskFilter = BlurMaskFilter(dim * 0.02f, BlurMaskFilter.Blur.NORMAL)
            for (i in 0 until 4) {
                val path = Path()
                val y0 = h * (0.45f + i * 0.12f)
                path.moveTo(-w * 0.1f, y0)
                path.cubicTo(w * 0.25f, y0 - h * (0.32f - i * 0.04f), w * 0.6f, y0 + h * 0.18f, w * 1.1f, y0 - h * 0.12f)
                path.lineTo(w * 1.1f, h * 1.2f)
                path.lineTo(-w * 0.1f, h * 1.2f)
                path.close()
                val col = blobs[(i + 1) % blobs.size]
                band.shader = LinearGradient(0f, y0 - h * 0.3f, 0f, h.toFloat(),
                    intArrayOf(withAlpha(col, if (dark) 70 else 85), withAlpha(stops.last(), if (dark) 120 else 60)), null, Shader.TileMode.CLAMP)
                c.drawPath(path, band)
            }
        }

        // Gentle vignette for depth.
        p.shader = RadialGradient(w / 2f, h * 0.45f, dim * 0.85f,
            intArrayOf(0x00000000, if (dark) 0x66000000 else 0x22000000), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Fine grain to avoid banding on 8-bit panels.
        val grain = Paint()
        val gr = Random(seed + 1)
        val count = (w * h / 90).coerceAtMost(60000)
        for (i in 0 until count) {
            grain.color = if (gr.nextBoolean()) 0x0AFFFFFF else 0x0A000000
            c.drawPoint(gr.nextFloat() * w, gr.nextFloat() * h, grain)
        }
        return bmp
    }

    fun lowRes(src: Bitmap, factor: Int = 14): Bitmap {
        val w = max(4, src.width / factor)
        val h = max(4, src.height / factor)
        // Two-step downscale for a smoother result that reads as blur once upscaled.
        val mid = Bitmap.createScaledBitmap(src, max(8, src.width / 4), max(8, src.height / 4), true)
        return Bitmap.createScaledBitmap(mid, w, h, true)
    }

    fun decodeCustom(path: String, maxW: Int, maxH: Int): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= maxW && opts.outHeight / (sample * 2) >= maxH) sample *= 2
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: null
        raw?.let { centerCrop(it, maxW, maxH) }
    } catch (t: Throwable) {
        null
    }

    fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
        val sw = (w / scale).toInt().coerceIn(1, src.width)
        val sh = (h / scale).toInt().coerceIn(1, src.height)
        val x = (src.width - sw) / 2
        val y = (src.height - sh) / 2
        val cropped = Bitmap.createBitmap(src, x, y, sw, sh)
        return Bitmap.createScaledBitmap(cropped, max(1, min(w, 4096)), max(1, min(h, 4096)), true)
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)
}
