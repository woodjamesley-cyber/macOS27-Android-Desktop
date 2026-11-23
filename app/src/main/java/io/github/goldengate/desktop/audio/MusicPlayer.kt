package io.github.goldengate.desktop.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Song(val id: String, val title: String, val artist: String, val album: String, val source: Uri, val generated: Boolean, val hue: Float)

/** System-wide media playback shared by the Music app, Control Center and the menu bar. */
class MusicPlayer(private val context: Context, private val scope: CoroutineScope) {
    val library = mutableStateListOf<Song>()
    var current by mutableStateOf<Song?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var volume by mutableFloatStateOf(0.8f)
    var preparing by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)

    private var player: MediaPlayer? = null

    fun loadLibrary(musicDir: File) {
        scope.launch(Dispatchers.IO) {
            val songs = ArrayList<Song>()
            Synth.tracks.forEachIndexed { i, t ->
                val f = File(musicDir, "${t.title}.wav")
                if (!f.exists()) {
                    preparingLibrary = true
                    Synth.writeWav(f, Synth.render(t))
                }
                songs += Song("gen$i", t.title, t.artist, t.album, Uri.fromFile(f), true, (i * 67f) % 360f)
            }
            musicDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "ogg", "flac", "aac") }?.forEach { f ->
                songs += Song("file:" + f.name, f.nameWithoutExtension, "Imported", "My Music", Uri.fromFile(f), false, (f.name.hashCode() % 360).toFloat())
            }
            withContext(Dispatchers.Main) {
                library.clear(); library.addAll(songs)
                preparingLibrary = false
            }
        }
    }

    var preparingLibrary by mutableStateOf(false)

    fun addExternal(uri: Uri, name: String) {
        val song = Song("uri:$uri", name.substringBeforeLast('.'), "Imported", "Opened Files", uri, false, (name.hashCode() % 360).toFloat())
        if (library.none { it.id == song.id }) library.add(song)
        play(song)
    }

    fun play(song: Song) {
        lastError = null
        player?.release()
        player = null
        current = song
        preparing = true
        playing = false
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(context, song.source)
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                preparing = false
                duration = it.duration
                it.start()
                playing = true
            }
            mp.setOnCompletionListener { if (repeat) { it.seekTo(0); it.start() } else next() }
            mp.setOnErrorListener { _, what, _ -> lastError = "Playback error ($what)"; playing = false; preparing = false; true }
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            preparing = false
            lastError = t.message ?: "Can't play this file"
            mp.release()
        }
        startTicker()
    }

    private var ticking = false
    private fun startTicker() {
        if (ticking) return
        ticking = true
        scope.launch {
            while (isActive) {
                player?.let { p -> try { if (p.isPlaying) position = p.currentPosition } catch (_: Throwable) {} }
                delay(250)
            }
        }
    }

    fun toggle() {
        val p = player
        if (p == null) { (current ?: library.firstOrNull())?.let { play(it) }; return }
        if (preparing) return
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }

    fun pause() { player?.let { if (it.isPlaying) it.pause() }; playing = false }

    fun next() {
        if (library.isEmpty()) return
        val idx = library.indexOf(current)
        val n = if (shuffle) library.indices.random() else (idx + 1) % library.size
        play(library[n])
    }

    fun previous() {
        if (library.isEmpty()) return
        if (position > 3000) { seekTo(0); return }
        val idx = library.indexOf(current)
        play(library[if (idx <= 0) library.lastIndex else idx - 1])
    }

    fun seekTo(ms: Int) { player?.seekTo(ms); position = ms }

    fun setVol(v: Float) { volume = v.coerceIn(0f, 1f); player?.setVolume(volume, volume) }

    fun release() { player?.release(); player = null }
}
