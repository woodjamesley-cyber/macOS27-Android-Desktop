package io.github.goldengate.desktop.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tiny offline synthesizer. Every interface sound and every bundled music track is generated
 * from code at first launch, so the project contains no third-party audio.
 */
object Synth {
    const val RATE = 22050

    fun writeWav(file: File, samples: FloatArray, rate: Int = RATE) {
        file.parentFile?.mkdirs()
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2)
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
    }

    private fun env(t: Float, attack: Float, decay: Float) = if (t < attack) t / attack else exp(-(t - attack) / decay)

    fun click(): FloatArray = FloatArray((RATE * 0.035f).toInt()) { i ->
        val t = i / RATE.toFloat()
        (sin(2 * PI * 1850 * t).toFloat() * 0.5f + sin(2 * PI * 3700 * t).toFloat() * 0.15f) * env(t, 0.001f, 0.006f) * 0.6f
    }

    fun pop(): FloatArray = FloatArray((RATE * 0.12f).toInt()) { i ->
        val t = i / RATE.toFloat()
        val f = 520f + 380f * exp(-t / 0.03f)
        sin(2 * PI * f * t).toFloat() * env(t, 0.003f, 0.04f) * 0.45f
    }

    fun chime(): FloatArray = FloatArray((RATE * 0.9f).toInt()) { i ->
        val t = i / RATE.toFloat()
        val a = sin(2 * PI * 1046.5 * t).toFloat() * env(t, 0.004f, 0.25f)
        val b = if (t > 0.11f) sin(2 * PI * 1568.0 * (t - 0.11f)).toFloat() * env(t - 0.11f, 0.004f, 0.35f) else 0f
        (a * 0.35f + b * 0.32f) * 0.8f
    }

    fun whoosh(): FloatArray {
        val r = Random(3)
        var lp = 0f
        return FloatArray((RATE * 0.28f).toInt()) { i ->
            val t = i / RATE.toFloat()
            val cut = 0.05f + 0.35f * (t / 0.28f)
            lp += cut * ((r.nextFloat() * 2 - 1) - lp)
            lp * env(t, 0.08f, 0.07f) * 0.5f
        }
    }

    fun crumple(): FloatArray {
        val r = Random(11)
        return FloatArray((RATE * 0.45f).toInt()) { i ->
            val t = i / RATE.toFloat()
            val burst = if (r.nextFloat() < 0.04f) 1f else 0.25f
            (r.nextFloat() * 2 - 1) * burst * env(t, 0.01f, 0.12f) * 0.35f
        }
    }

    fun basso(): FloatArray = FloatArray((RATE * 0.5f).toInt()) { i ->
        val t = i / RATE.toFloat()
        (sin(2 * PI * 196 * t).toFloat() * 0.6f + sin(2 * PI * 98 * t).toFloat() * 0.3f) * env(t, 0.005f, 0.15f) * 0.5f
    }

    data class Track(val title: String, val artist: String, val album: String, val seed: Int, val bpm: Int, val root: Int, val minor: Boolean, val seconds: Int)

    /** Original procedural compositions. */
    val tracks = listOf(
        Track("Fog Over the Bay", "Procedural Ensemble", "Golden Hour Sessions", 1, 84, 57, true, 48),
        Track("Cable Car Morning", "Procedural Ensemble", "Golden Hour Sessions", 2, 112, 60, false, 44),
        Track("Liquid Light", "Glass Choir", "Refractions", 3, 96, 62, false, 46),
        Track("Night Shift", "Glass Choir", "Refractions", 4, 72, 55, true, 50),
        Track("Spaces", "Mission Synth", "Desktop Suite", 5, 104, 64, false, 42),
    )

    private fun midiHz(n: Int) = 440.0 * 2.0.pow((n - 69) / 12.0)

    /** Renders a short generative piece: pad chords, a bass line, a melody and soft percussion. */
    fun render(track: Track): FloatArray {
        val rnd = Random(track.seed)
        val total = RATE * track.seconds
        val out = FloatArray(total)
        val beat = 60.0 / track.bpm
        val scale = if (track.minor) intArrayOf(0, 2, 3, 5, 7, 8, 10) else intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val progression = if (track.minor) intArrayOf(0, 5, 2, 6) else intArrayOf(0, 4, 5, 3)
        val bars = (track.seconds / (beat * 4)).toInt() + 1
        for (bar in 0 until bars) {
            val degree = progression[bar % progression.size]
            val chord = intArrayOf(degree, degree + 2, degree + 4).map { d -> track.root + scale[d % 7] + 12 * (d / 7) }
            val barStart = bar * beat * 4
            // pad
            for (n in chord) addTone(out, barStart, beat * 4, midiHz(n), 0.07f, attack = 0.4f, release = 0.8f, harmonics = 3)
            // bass
            for (b in 0 until 4) addTone(out, barStart + b * beat, beat * 0.9, midiHz(chord[0] - 24), 0.16f, attack = 0.01f, release = 0.15f, harmonics = 2)
            // melody
            var t = 0.0
            while (t < 4) {
                val dur = listOf(0.5, 0.5, 1.0, 0.25, 0.75)[rnd.nextInt(5)]
                if (rnd.nextFloat() > 0.18f) {
                    val d = degree + rnd.nextInt(0, 7)
                    val note = track.root + 12 + scale[d % 7] + 12 * (d / 7)
                    addTone(out, barStart + t * beat, dur * beat, midiHz(note), 0.10f, attack = 0.01f, release = 0.3f, harmonics = 4, bell = true)
                }
                t += dur
            }
            // percussion (hat + soft kick)
            for (b in 0 until 8) addNoise(out, barStart + b * beat / 2, 0.04, 0.035f, rnd)
            for (b in 0 until 4 step 2) addKick(out, barStart + b * beat)
        }
        // fade in/out and normalize
        val fade = RATE * 2
        for (i in 0 until total) {
            val g = minOf(1f, i / fade.toFloat(), (total - i) / fade.toFloat())
            out[i] *= g
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.01f)
        for (i in out.indices) out[i] = out[i] / peak * 0.85f
        return out
    }

    private fun addTone(buf: FloatArray, start: Double, dur: Double, hz: Double, amp: Float, attack: Float, release: Float, harmonics: Int, bell: Boolean = false) {
        val s0 = (start * RATE).toInt()
        val len = ((dur + release) * RATE).toInt()
        for (i in 0 until len) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i / RATE.toFloat()
            val e = when {
                t < attack -> t / attack
                t < dur -> if (bell) exp(-(t - attack) / 0.35f) else 1f
                else -> (if (bell) exp(-(dur.toFloat() - attack) / 0.35f) else 1f) * exp(-(t - dur.toFloat()) / (release / 3f))
            }
            var v = 0.0
            for (h in 1..harmonics) v += sin(2 * PI * hz * h * t) / (h * h)
            buf[idx] += (v * amp * e).toFloat()
        }
    }

    private fun addNoise(buf: FloatArray, start: Double, dur: Double, amp: Float, rnd: Random) {
        val s0 = (start * RATE).toInt()
        val len = (dur * RATE).toInt()
        var prev = 0f
        for (i in 0 until len) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val n = rnd.nextFloat() * 2 - 1
            val hp = n - prev
            prev = n
            buf[idx] += hp * amp * exp(-i / (len * 0.25f))
        }
    }

    private fun addKick(buf: FloatArray, start: Double) {
        val s0 = (start * RATE).toInt()
        val len = (0.25 * RATE).toInt()
        for (i in 0 until len) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i / RATE.toFloat()
            val f = 50 + 90 * exp(-t / 0.03f)
            buf[idx] += sin(2 * PI * f * t).toFloat() * exp(-t / 0.08f) * 0.22f
        }
    }
}
