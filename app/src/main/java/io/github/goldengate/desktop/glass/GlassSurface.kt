package io.github.goldengate.desktop.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.glass.GlassShaders.toCompose
import kotlin.math.roundToInt

/**
 * Holds the live "backdrop" render layers that glass surfaces sample from.
 *  - [wallpaper] contains only the wallpaper (sampled by glass *inside* windows, e.g. sidebars).
 *  - [desktop] contains wallpaper + desktop icons + all windows (sampled by the menu bar, Dock,
 *    menus, notifications, Control Center, etc., which are drawn on top of it).
 * Layers are GPU RenderNodes, so sampling them costs no extra recording work.
 */
@Stable
class Backdrops(val desktop: GraphicsLayer, val wallpaper: GraphicsLayer) {
    /** Heavily downscaled copy of the wallpaper used for the "Efficient" / pre-Android-12 path. */
    var wallpaperLowRes: ImageBitmap? by mutableStateOf(null)
    var rootSize: IntSize by mutableStateOf(IntSize.Zero)
}

val LocalBackdrops = staticCompositionLocalOf<Backdrops?> { null }

enum class GlassSource { Desktop, Wallpaper }

/**
 * Which backdrop layer the current composable is being recorded into. A glass surface must never
 * sample a layer that contains itself (that creates a cyclic RenderNode tree and overflows the
 * RenderThread stack), so surfaces inside the desktop layer fall back to the wallpaper layer and
 * surfaces inside the wallpaper layer render opaque.
 */
val LocalRecordingInto = staticCompositionLocalOf<GlassSource?> { null }

/** Records the content into [layer] and draws it, turning it into a backdrop source. */
fun Modifier.backdropSource(layer: GraphicsLayer): Modifier = this.drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Material variants (how thick/tinted the glass is relative to the user slider). */
enum class GlassMaterial(val tintBias: Float, val blurDp: Float, val refraction: Float) {
    /** Dock, floating controls: clearest, strongest lens edge. */
    Clear(0.0f, 14f, 1.0f),
    /** Notifications, Control Center modules. */
    Regular(0.18f, 24f, 0.7f),
    /** Menus and popovers need high legibility. */
    Menu(0.42f, 30f, 0.45f),
    /** Sidebars inside windows. */
    Sidebar(0.30f, 34f, 0.0f),
    /** Menu bar background when the user enables it. */
    Bar(0.10f, 20f, 0.0f),
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    material: GlassMaterial = GlassMaterial.Regular,
    source: GlassSource = GlassSource.Desktop,
    elevation: Dp = 0.dp,
    tintColor: Color? = null,
    drawRim: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val theme = Desk.theme
    val colors = theme.colors
    val glass = theme.glass
    val backdrops = LocalBackdrops.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(cornerRadius)
    val holder = remember { GlassGeometry() }

    val tint = (glass.tint + material.tintBias).coerceIn(0f, 1f)
    val quality = when {
        glass.quality == GlassQuality.Full && GlassShaders.supportsRefraction -> GlassQuality.Full
        glass.quality != GlassQuality.Efficient && GlassShaders.supportsBlur -> GlassQuality.Balanced
        else -> GlassQuality.Efficient
    }
    val recordingInto = LocalRecordingInto.current
    val effectiveSource = if (recordingInto == GlassSource.Desktop) GlassSource.Wallpaper else source
    val opaque = glass.reduceTransparency || backdrops == null || recordingInto == GlassSource.Wallpaper

    val radiusPx = with(density) { cornerRadius.toPx() }
    val blurPx = with(density) { (material.blurDp * (0.75f + tint * 0.6f)).dp.toPx() }
    val saturation = 1.0f + (1f - tint) * 0.6f
    val size = holder.size
    val effect = remember(size, quality, blurPx, saturation, radiusPx, material, opaque) {
        if (opaque || size.width <= 0 || size.height <= 0) null
        else when (quality) {
            GlassQuality.Full -> if (Build.VERSION.SDK_INT >= 33) {
                val bevel = with(density) { (10f + material.refraction * 14f).dp.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
                val strength = with(density) { (material.refraction * 12f).dp.toPx() }
                if (material.refraction > 0f) GlassShaders.liquid(
                    size.width.toFloat(), size.height.toFloat(), radiusPx, blurPx, saturation, bevel, strength, 0.18f,
                ).toCompose() else GlassShaders.blurWithVibrancy(blurPx, saturation).toCompose()
            } else null
            GlassQuality.Balanced -> if (Build.VERSION.SDK_INT >= 31) GlassShaders.blurWithVibrancy(blurPx, saturation).toCompose() else null
            GlassQuality.Efficient -> null
        }
    }

    val baseTint = tintColor ?: colors.glassTintBase
    // Alpha of the frosted tint: from ~10% (ultra clear) up to ~82% (fully tinted).
    val tintAlpha = if (opaque) 0.97f else (0.10f + tint * 0.72f) * (if (colors.dark) 0.95f else 1f) +
        (if (quality == GlassQuality.Efficient) 0.12f else 0f)

    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow) else Modifier)
            .onGloballyPositioned {
                holder.origin = it.positionInRoot()
                holder.size = it.size
            }
            .clip(shape)
    ) {
        if (!opaque) {
            val layer = when (effectiveSource) {
                GlassSource.Desktop -> backdrops!!.desktop
                GlassSource.Wallpaper -> backdrops!!.wallpaper
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = effect; clip = true }
                    .drawBehind {
                        val o = holder.origin
                        if (quality == GlassQuality.Efficient || effect == null) {
                            drawLowRes(backdrops!!.wallpaperLowRes, backdrops.rootSize, o)
                        } else {
                            translate(-o.x, -o.y) { drawLayer(layer) }
                        }
                    }
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(baseTint.copy(alpha = tintAlpha.coerceIn(0f, 1f)))
                    if (drawRim && !opaque) drawGlassRim(radiusPx, colors.dark, glass.increaseContrast)
                    else if (glass.increaseContrast || opaque) {
                        drawRoundRect(colors.separator, cornerRadius = CornerRadius(radiusPx), style = Stroke(1.dp.toPx()))
                    }
                }
        )
        content()
    }
}

@Stable
private class GlassGeometry {
    var origin: Offset by mutableStateOf(Offset.Zero)
    var size: IntSize by mutableStateOf(IntSize.Zero)
}

private fun DrawScope.drawLowRes(bmp: ImageBitmap?, root: IntSize, origin: Offset) {
    if (bmp == null || root.width == 0) return
    val sx = bmp.width / root.width.toFloat()
    val sy = bmp.height / root.height.toFloat()
    val srcX = (origin.x * sx).roundToInt().coerceIn(0, bmp.width - 1)
    val srcY = (origin.y * sy).roundToInt().coerceIn(0, bmp.height - 1)
    val srcW = (size.width * sx).roundToInt().coerceIn(1, bmp.width - srcX)
    val srcH = (size.height * sy).roundToInt().coerceIn(1, bmp.height - srcY)
    drawImage(
        bmp, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

/**
 * The specular rim: a bright top-left highlight, a darker lower edge (macOS 27 added "a darkened
 * edge and brighter specular highlights") and a soft inner sheen near the top.
 */
fun DrawScope.drawGlassRim(radiusPx: Float, dark: Boolean, contrast: Boolean) {
    val w = size.width
    val h = size.height
    val cr = CornerRadius(radiusPx)
    // inner sheen
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.10f else 0.22f),
            0.35f to Color.White.copy(alpha = 0.0f),
            1f to Color.White.copy(alpha = 0.0f),
        ),
        cornerRadius = cr,
    )
    // specular highlight stroke (light comes from the top-left)
    val stroke = 1.2f * density
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.55f else 0.85f),
            0.3f to Color.White.copy(alpha = if (dark) 0.10f else 0.25f),
            0.7f to Color.White.copy(alpha = if (dark) 0.06f else 0.15f),
            1f to Color.White.copy(alpha = if (dark) 0.35f else 0.55f),
            start = Offset(0f, 0f), end = Offset(w, h),
        ),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(w - stroke, h - stroke),
        cornerRadius = CornerRadius((radiusPx - stroke / 2).coerceAtLeast(0f)),
        style = Stroke(stroke),
    )
    // darkened outer edge for separation from the content behind
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (contrast) 0.30f else 0.04f),
            1f to Color.Black.copy(alpha = if (contrast) 0.45f else if (dark) 0.35f else 0.16f),
        ),
        cornerRadius = cr,
        style = Stroke(0.8f * density),
    )
}
