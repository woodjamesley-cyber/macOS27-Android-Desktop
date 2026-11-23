package io.github.goldengate.desktop.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class UiSound { Click, Open, Close, Notification, Trash, Error, Pop }

/** Restrained interface sounds played through SoundPool (generated at first launch). */
class UiSounds(private val context: Context, scope: CoroutineScope) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<UiSound, Int>()
    private var lastPlay = HashMap<UiSound, Long>()

    var enabled = true
    var volume = 0.55f

    init {
        scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "ui_sounds").apply { mkdirs() }
            val gens = mapOf(
                UiSound.Click to Synth::click, UiSound.Open to Synth::whoosh, UiSound.Close to Synth::pop,
                UiSound.Notification to Synth::chime, UiSound.Trash to Synth::crumple, UiSound.Error to Synth::basso,
                UiSound.Pop to Synth::pop,
            )
            for ((s, gen) in gens) {
                val f = File(dir, "v2_${s.name}.wav")
                if (!f.exists()) Synth.writeWav(f, gen())
                val id = pool.load(f.absolutePath, 1)
                synchronized(ids) { ids[s] = id }
            }
        }
    }

    fun play(sound: UiSound, gain: Float = 1f) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - (lastPlay[sound] ?: 0L) < 60) return // avoid machine-gun repeats
        lastPlay[sound] = now
        val id = synchronized(ids) { ids[sound] } ?: return
        val v = (volume * gain * when (sound) { UiSound.Click -> 0.35f; UiSound.Open -> 0.5f; else -> 0.7f }).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() = pool.release()
}
