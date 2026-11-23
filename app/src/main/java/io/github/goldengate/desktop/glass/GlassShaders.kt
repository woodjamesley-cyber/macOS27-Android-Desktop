package io.github.goldengate.desktop.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * GPU effects that make up the Liquid Glass material:
 *  1. Gaussian blur of whatever is behind the surface (RenderEffect, Android 12+).
 *  2. A saturation boost ("vibrancy") so colors bleed through the frost.
 *  3. An AGSL lens shader (Android 13+) that bends the backdrop near the rounded edge with a
 *     little chromatic dispersion, approximating light refracting through a thick glass rim.
 */
object GlassShaders {

    const val REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float chroma;

float sdRound(float2 p) {
    float2 h = size * 0.5;
    float r = min(radius, min(h.x, h.y));
    float2 q = abs(p - h) - (h - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float d = sdRound(p);
    float t = clamp(1.0 + d / bevel, 0.0, 1.0);
    if (t <= 0.001) {
        return content.eval(p);
    }
    float e = 1.0;
    float2 g = float2(sdRound(p + float2(e, 0.0)) - sdRound(p - float2(e, 0.0)),
                      sdRound(p + float2(0.0, e)) - sdRound(p - float2(0.0, e)));
    float2 n = g / max(length(g), 0.0001);
    float k = t * t * strength;
    float2 base = p - n * k;
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    float2 disp = n * k * chroma;
    half4 cr = content.eval(clamp(base + disp, lo, hi));
    half4 cg = content.eval(clamp(base, lo, hi));
    half4 cb = content.eval(clamp(base - disp, lo, hi));
    return half4(cr.r, cg.g, cb.b, cg.a);
}
"""

    val supportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsRefraction: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @RequiresApi(Build.VERSION_CODES.S)
    fun blurWithVibrancy(blurPx: Float, saturation: Float): RenderEffect {
        val blur = RenderEffect.createBlurEffect(blurPx.coerceAtLeast(0.5f), blurPx.coerceAtLeast(0.5f), Shader.TileMode.CLAMP)
        val cm = ColorMatrix().apply { setSaturation(saturation) }
        return RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)), blur)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun liquid(
        widthPx: Float, heightPx: Float, radiusPx: Float, blurPx: Float, saturation: Float,
        bevelPx: Float, strengthPx: Float, chroma: Float,
    ): RenderEffect {
        val shader = RuntimeShader(REFRACTION_AGSL)
        shader.setFloatUniform("size", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        shader.setFloatUniform("radius", radiusPx)
        shader.setFloatUniform("bevel", bevelPx.coerceAtLeast(1f))
        shader.setFloatUniform("strength", strengthPx)
        shader.setFloatUniform("chroma", chroma)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return RenderEffect.createChainEffect(lens, blurWithVibrancy(blurPx, saturation))
    }

    fun RenderEffect.toCompose() = this.asComposeRenderEffect()
}
