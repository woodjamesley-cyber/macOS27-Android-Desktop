package io.github.goldengate.desktop.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.fs.FileKind
import io.github.goldengate.desktop.fs.FsEntry
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory thumbnail cache for image files (decoded off the main thread). */
object Thumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(context: Context, ref: FsRef, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = ref.key + "@" + maxPx
        cache.get(key)?.let { return@withContext it }
        try {
            fun open() = when (ref) {
                is FsRef.Local -> ref.file.inputStream()
                is FsRef.Saf -> context.contentResolver.openInputStream(ref.uri)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            bmp?.asImageBitmap()?.also { cache.put(key, it) }
        } catch (_: Throwable) { null }
    }
}

/** Finder-style icon for a file or folder. Images show a real thumbnail. */
@Composable
fun FileIcon(entry: FsEntry, size: Dp, modifier: Modifier = Modifier) {
    if (entry.kind == FileKind.Image) {
        val ctx = LocalContext.current
        val px = (size.value * 2.5f).toInt().coerceAtLeast(64)
        var bmp by remember(entry.ref.key) { mutableStateOf(Thumbnails.cached(entry.ref.key + "@" + px)) }
        LaunchedEffect(entry.ref.key, entry.modified) { if (bmp == null) bmp = Thumbnails.load(ctx, entry.ref, px) }
        val b = bmp
        if (b != null) {
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                val ratio = b.width / b.height.toFloat()
                val w = if (ratio >= 1f) size * 0.92f else size * 0.92f * ratio
                val h = if (ratio >= 1f) size * 0.92f / ratio else size * 0.92f
                Image(
                    b, entry.name,
                    Modifier.size(w, h).shadow(3.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            return
        }
    }
    val ext = entry.name.substringAfterLast('.', "").take(4).uppercase()
    val dark = Desk.colors.dark
    Canvas(modifier.size(size)) {
        if (entry.isDirectory) drawFolder(entry.name) else drawDocument(entry.kind, ext, dark)
    }
}

fun DrawScope.drawFolder(name: String = "") {
    val w = size.width; val h = size.height
    val top = h * 0.2f
    val back = Path().apply {
        moveTo(w * 0.06f, top + h * 0.06f)
        quadraticTo(w * 0.06f, top, w * 0.12f, top)
        lineTo(w * 0.38f, top)
        lineTo(w * 0.46f, top + h * 0.08f)
        lineTo(w * 0.88f, top + h * 0.08f)
        quadraticTo(w * 0.94f, top + h * 0.08f, w * 0.94f, top + h * 0.14f)
        lineTo(w * 0.94f, h * 0.84f)
        lineTo(w * 0.06f, h * 0.84f)
        close()
    }
    drawPath(back, Brush.verticalGradient(listOf(Color(0xFF5AB4F5), Color(0xFF3D97E6)), top, h * 0.84f))
    val frontTop = top + h * 0.16f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF8DD0FF), Color(0xFF58AEF2)), frontTop, h * 0.86f),
        topLeft = Offset(w * 0.04f, frontTop), size = Size(w * 0.92f, h * 0.86f - frontTop), cornerRadius = CornerRadius(w * 0.06f),
    )
    drawLine(Color.White.copy(alpha = 0.55f), Offset(w * 0.08f, frontTop + 1.5f), Offset(w * 0.92f, frontTop + 1.5f), 1.2f)
    // Special folders get an embossed symbol, like Finder.
    val glyph = when (name) {
        "Documents" -> Glyph.Doc; "Downloads" -> Glyph.Download; "Pictures" -> Glyph.Image
        "Music" -> Glyph.Music; "Desktop" -> Glyph.Desktop; else -> null
    }
    if (glyph != null) {
        val gs = w * 0.34f
        translate(w / 2f - gs / 2f, frontTop + (h * 0.86f - frontTop) / 2f - gs / 2f) {
            drawIntoGlyph(glyph, gs, Color(0xFF2F7FC9).copy(alpha = 0.75f))
        }
    }
}

private fun DrawScope.drawIntoGlyph(glyph: Glyph, s: Float, color: Color) {
    val old = drawContext.size
    drawContext.size = Size(s, s)
    drawGlyph(glyph, color, 2f)
    drawContext.size = old
}

fun DrawScope.drawDocument(kind: FileKind, ext: String, dark: Boolean) {
    val w = size.width; val h = size.height
    val l = w * 0.18f; val r = w * 0.82f; val t = h * 0.06f; val b = h * 0.94f
    val fold = w * 0.2f
    val page = Path().apply {
        moveTo(l + 3f, t); lineTo(r - fold, t); lineTo(r, t + fold); lineTo(r, b - 3f)
        quadraticTo(r, b, r - 3f, b); lineTo(l + 3f, b); quadraticTo(l, b, l, b - 3f); lineTo(l, t + 3f); quadraticTo(l, t, l + 3f, t); close()
    }
    drawPath(page, Color(0x33000000), style = Stroke(2.5f))
    drawPath(page, Color.White)
    val ear = Path().apply { moveTo(r - fold, t); lineTo(r - fold, t + fold - 2f); quadraticTo(r - fold, t + fold, r - fold + 2f, t + fold); lineTo(r, t + fold); close() }
    drawPath(ear, Color(0xFFDADDE3))
    val accent = when (kind) {
        FileKind.Text, FileKind.Markdown -> Color(0xFF8E8E93)
        FileKind.Csv -> Color(0xFF30A14E)
        FileKind.Pdf -> Color(0xFFE5453A)
        FileKind.Audio -> Color(0xFFFF375F)
        FileKind.Video -> Color(0xFF5E5CE6)
        FileKind.Web -> Color(0xFF0A84FF)
        FileKind.Archive -> Color(0xFFB08A4E)
        FileKind.Image -> Color(0xFF34C759)
        else -> Color(0xFF8E8E93)
    }
    // Faux text lines.
    if (kind == FileKind.Text || kind == FileKind.Markdown || kind == FileKind.Other) {
        val lh = h * 0.07f
        var y = t + fold + lh
        var i = 0
        while (y < b - h * 0.28f) {
            val len = if (i % 4 == 3) 0.5f else 0.9f
            drawLine(Color(0xFFC7C7CC), Offset(l + w * 0.08f, y), Offset(l + (r - l - w * 0.16f) * len + w * 0.08f, y), 1.6f)
            y += lh; i++
        }
    } else {
        val gs = w * 0.36f
        translate(w / 2f - gs / 2f, t + fold * 0.9f) { drawIntoGlyph(when (kind) {
            FileKind.Audio -> Glyph.Music; FileKind.Video -> Glyph.Play; FileKind.Web -> Glyph.Globe
            FileKind.Csv -> Glyph.Grid; FileKind.Archive -> Glyph.Folder; FileKind.Pdf -> Glyph.Doc; else -> Glyph.Doc
        }, gs, accent) }
    }
    if (ext.isNotBlank()) {
        val bandH = h * 0.16f
        drawRoundRect(accent, Offset(l + w * 0.06f, b - bandH - h * 0.06f), Size(r - l - w * 0.12f, bandH), CornerRadius(3f))
    }
}

/** File extension badge text drawn over [FileIcon] by callers that need it. */
@Composable
fun ExtBadge(ext: String, size: Dp) {
    DeskText(ext, Desk.type.caption.copy(fontSize = (size.value * 0.13f).coerceAtLeast(6f).sp, fontWeight = FontWeight.Bold), color = Color.White)
}
