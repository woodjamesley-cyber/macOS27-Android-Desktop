package io.github.goldengate.desktop.apps

import android.provider.OpenableColumns
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.audio.Song
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskSlider
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.EmptyState
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SidebarHeader
import io.github.goldengate.desktop.ui.SidebarItem
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import io.github.goldengate.desktop.ui.selectionBackground
import java.util.Locale

/** Original generated "album art": a soft gradient orb derived from the song's hue. */
@Composable
fun Artwork(song: Song?, size: Dp, modifier: Modifier = Modifier) {
    val hue = song?.hue ?: 220f
    val a = Color.hsv(((hue % 360) + 360) % 360, 0.65f, 0.95f)
    val b = Color.hsv(((hue + 60) % 360 + 360) % 360, 0.75f, 0.55f)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.14f)).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(this.size.width * 0.3f, this.size.height * 0.3f), radius = this.size.width * 0.6f))
            drawCircle(Color.White.copy(alpha = 0.18f), this.size.width * 0.22f, Offset(this.size.width * 0.68f, this.size.height * 0.7f))
        }
        if (song == null) GlyphIcon(Glyph.Music, Color.White, size * 0.4f)
    }
}

fun formatMs(ms: Int): String { val s = ms / 1000; return String.format(Locale.US, "%d:%02d", s / 60, s % 60) }

@Composable
fun MusicApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val m = desk.music
    val c = Desk.colors
    var section by remember { mutableIntStateOf(0) } // 0 songs, 1 imported
    var search by remember { mutableStateOf("") }
    LaunchedEffect(m.current) { win.title = m.current?.let { "${it.title} — ${it.artist}" } ?: "Music" }

    fun importSong() {
        desk.bridge?.pickDocument(arrayOf("audio/*")) { uri ->
            if (uri != null) {
                var name = "Imported song"
                runCatching { desk.app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur -> if (cur.moveToFirst()) name = cur.getString(0) ?: name } }
                m.addExternal(uri, name)
                section = 1
            }
        }
    }

    WindowMenus(win, listOf(
        menu("File") { item("Add to Library…", Shortcut(Key.O)) { importSong() } },
        menu("Controls") {
            item(if (m.playing) "Pause" else "Play", Shortcut(Key.P)) { if (m.current == null) m.library.firstOrNull()?.let { m.play(it) } else m.toggle() }
            item("Next", Shortcut(Key.DirectionRight)) { m.next() }
            item("Previous", Shortcut(Key.DirectionLeft)) { m.previous() }
            separator()
            item("Increase Volume", Shortcut(Key.DirectionUp)) { m.setVol(m.volume + 0.1f) }
            item("Decrease Volume", Shortcut(Key.DirectionDown)) { m.setVol(m.volume - 0.1f) }
            separator()
            item("Shuffle", checked = m.shuffle) { m.shuffle = !m.shuffle }
            item("Repeat", checked = m.repeat) { m.repeat = !m.repeat }
        },
    ))

    val songs = m.library.filter { (section == 0 || !it.generated) && (search.isBlank() || it.title.contains(search, true) || it.artist.contains(search, true)) }

    WindowScaffold(
        title = "Music",
        sidebarWidth = 200.dp,
        sidebar = {
            DeskTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), placeholder = "Search", glyph = Glyph.Search, rounded = 8.dp)
            SidebarHeader("Library")
            SidebarItem("Songs", Glyph.Music, Color(0xFFFF375F), section == 0, { section = 0 })
            SidebarItem("Imported", Glyph.Download, Color(0xFFFF375F), section == 1, { section = 1 })
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(10.dp)) { NowPlayingCompact(desk) }
        },
        toolbar = {
            ToolbarButton(Glyph.Shuffle, { m.shuffle = !m.shuffle }, active = m.shuffle, description = "Shuffle")
            ToolbarButton(Glyph.Prev, { m.previous() }, description = "Previous")
            ToolbarButton(if (m.playing) Glyph.Pause else Glyph.Play, { if (m.current == null) m.library.firstOrNull()?.let { m.play(it) } else m.toggle() }, Modifier.testTag("music-play"), description = "Play/Pause")
            ToolbarButton(Glyph.Next, { m.next() }, description = "Next")
            ToolbarButton(Glyph.Repeat, { m.repeat = !m.repeat }, active = m.repeat, description = "Repeat")
            Spacer(Modifier.width(8.dp))
            ToolbarButton(Glyph.Plus, { importSong() }, description = "Add to Library")
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Now playing header
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val pulse by animateFloatAsState(if (m.playing) 1f else 0.94f, label = "art")
                Artwork(m.current, 96.dp, Modifier.scale(pulse))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    DeskText(m.current?.title ?: "Not Playing", Desk.type.headline, maxLines = 1)
                    DeskText(m.current?.let { "${it.artist} — ${it.album}" } ?: "Choose a song to start listening", Desk.type.body, color = c.secondaryText, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    val frac = if (m.duration > 0) m.position / m.duration.toFloat() else 0f
                    DeskSlider(frac, { v -> if (m.duration > 0) m.seekTo((v * m.duration).toInt()) }, Modifier.fillMaxWidth().testTag("music-seek"), height = 8.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        DeskText(formatMs(m.position), Desk.type.small, color = c.secondaryText)
                        Spacer(Modifier.weight(1f))
                        DeskText("-" + formatMs((m.duration - m.position).coerceAtLeast(0)), Desk.type.small, color = c.secondaryText)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        GlyphIcon(Glyph.SpeakerMute, c.secondaryText, 12.dp)
                        DeskSlider(m.volume, { m.setVol(it) }, Modifier.width(140.dp).padding(horizontal = 6.dp), height = 6.dp)
                        GlyphIcon(Glyph.Speaker, c.secondaryText, 12.dp)
                    }
                    m.lastError?.let { DeskText(it, Desk.type.small, color = Color(0xFFFF453A)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            if (m.preparingLibrary && songs.isEmpty()) EmptyState(Glyph.Music, "Preparing library…", "Synthesizing original demo tracks.")
            else if (songs.isEmpty()) EmptyState(Glyph.Music, "No songs", if (section == 1) "Add audio files from your device with the + button." else "No songs match your search.")
            else LazyColumn(Modifier.fillMaxSize().testTag("music-list")) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    val cur = m.current?.id == s.id
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectionBackground(cur, c.accent.copy(alpha = 0.18f), 6.dp)
                            .desktopGestures(key = s.id, onTap = { m.play(s) }, onSecondary = { at ->
                                desk.showContextMenu(at, entries {
                                    item("Play") { m.play(s) }
                                    if (!s.generated) item("Remove from Library", destructive = true) { if (cur) m.pause(); m.library.remove(s) }
                                })
                            })
                            .padding(horizontal = 8.dp, vertical = 6.dp).testTag("song-${s.title}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(26.dp)) {
                            if (cur && m.playing) GlyphIcon(Glyph.Speaker, c.accent, 13.dp) else DeskText("${i + 1}", Desk.type.small, color = c.secondaryText)
                        }
                        Artwork(s, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            DeskText(s.title, Desk.type.body.copy(color = if (cur) c.accent else c.text), maxLines = 1)
                            DeskText(s.artist, Desk.type.small, color = c.secondaryText, maxLines = 1)
                        }
                        DeskText(s.album, Desk.type.small, color = c.secondaryText, maxLines = 1, modifier = Modifier.width(140.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCompact(desk: io.github.goldengate.desktop.core.DesktopController) {
    val m = desk.music
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 12.dp, material = GlassMaterial.Regular) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Artwork(m.current, 30.dp)
            DeskText(m.current?.title ?: "Not Playing", Desk.type.small, maxLines = 1, modifier = Modifier.weight(1f))
            ToolbarButton(if (m.playing) Glyph.Pause else Glyph.Play, { if (m.current == null) m.library.firstOrNull()?.let { m.play(it) } else m.toggle() }, description = "Play/Pause")
        }
    }
}
