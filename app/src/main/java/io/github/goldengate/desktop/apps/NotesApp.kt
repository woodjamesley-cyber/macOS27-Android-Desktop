package io.github.goldengate.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.EmptyState
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SidebarHeader
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import io.github.goldengate.desktop.ui.selectionBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotesApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val fs = desk.fs
    val c = Desk.colors
    val arg = rememberWindowArgument(win)
    val notesDir = remember { File(fs.documents, "Notes").apply { mkdirs() } }
    var current by remember { mutableStateOf<FsRef?>(null) }
    var text by remember { mutableStateOf("") }
    var loadedText by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(15f) }
    var error by remember { mutableStateOf<String?>(null) }

    val notes = remember(desk.fsVersion, search) {
        (notesDir.listFiles() ?: emptyArray()).filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in setOf("txt", "md") }
            .filter { search.isBlank() || it.name.contains(search, true) || runCatching { it.readText().contains(search, true) }.getOrDefault(false) }
            .sortedByDescending { it.lastModified() }
    }

    suspend fun load(ref: FsRef) {
        current = ref
        val t = withContext(Dispatchers.IO) { runCatching { fs.readText(ref) } }
        t.onSuccess { text = it; loadedText = it; error = null }.onFailure { error = it.message ?: "Couldn't open this file."; text = ""; loadedText = "" }
        win.title = ref.name.substringBeforeLast('.')
    }
    fun save() {
        val ref = current ?: return
        if (text == loadedText) return
        runCatching { fs.writeText(ref, text) }.onSuccess { loadedText = text; win.documentEdited = false; desk.refreshFs() }.onFailure { desk.error("Couldn't save: ${it.message}") }
    }
    fun newNote() {
        save()
        val f = fs.uniqueFile(notesDir, "New Note.txt")
        f.writeText("")
        current = FsRef.Local(f); text = ""; loadedText = ""; win.title = f.nameWithoutExtension
        desk.refreshFs()
    }

    LaunchedEffect(arg) {
        val ref = arg?.let { desk.refFromKey(it) }
        if (ref != null) load(ref) else if (current == null) notes.firstOrNull()?.let { load(FsRef.Local(it)) }
        if (current == null) win.title = "Notes"
    }
    // Autosave after a short pause, and rename local notes after their first line.
    LaunchedEffect(text) {
        win.documentEdited = text != loadedText
        if (text == loadedText) return@LaunchedEffect
        delay(800)
        save()
        val ref = current
        if (ref is FsRef.Local && ref.file.parentFile == notesDir) {
            val first = text.lineSequence().firstOrNull()?.trim()?.take(40)?.replace(Regex("[/\\\\:*?\"<>|]"), "")?.trim().orEmpty()
            if (first.isNotEmpty() && first != ref.file.nameWithoutExtension) {
                val target = fs.uniqueFile(notesDir, "$first.${ref.file.extension}")
                if (ref.file.renameTo(target)) { current = FsRef.Local(target); win.title = target.nameWithoutExtension; desk.refreshFs() }
            }
        }
    }

    WindowMenus(win, listOf(
        menu("File") {
            item("New Note", Shortcut(Key.N)) { newNote() }
            item("Save", Shortcut(Key.S), enabled = current != null) { save() }
            separator()
            item("Show in Finder", enabled = current is FsRef.Local) { (current as? FsRef.Local)?.file?.parentFile?.let { desk.launch(AppIds.FINDER, "file:" + it.absolutePath, newWindow = true) } }
            item("Move to Trash", enabled = current != null) { current?.let { desk.trash(listOf(it)); current = null; text = ""; loadedText = "" } }
        },
        menu("Format") {
            item("Bigger", Shortcut(Key.Equals, keyLabel = "+")) { fontSize = (fontSize + 1).coerceAtMost(30f) }
            item("Smaller", Shortcut(Key.Minus)) { fontSize = (fontSize - 1).coerceAtLeast(10f) }
            separator()
            item("Insert Checklist Item", Shortcut(Key.L, shift = true)) { text += (if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + "☐ " }
            item("Insert Date") { text += SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(System.currentTimeMillis()) }
        },
    ))

    var pendingLoad by remember { mutableStateOf<FsRef?>(null) }
    LaunchedEffect(pendingLoad) { pendingLoad?.let { save(); load(it); pendingLoad = null } }

    WindowScaffold(
        title = current?.name?.substringBeforeLast('.') ?: "Notes",
        sidebarWidth = 230.dp,
        sidebar = {
            DeskTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), placeholder = "Search", glyph = Glyph.Search, rounded = 8.dp)
            SidebarHeader("Notes")
            Column(Modifier.verticalScroll(rememberScrollState())) {
                notes.forEach { f ->
                    val sel = (current as? FsRef.Local)?.file == f
                    val preview = remember(f, f.lastModified()) { runCatching { f.readText().lineSequence().drop(1).firstOrNull { it.isNotBlank() } ?: "No additional text" }.getOrDefault("") }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).selectionBackground(sel, if (sel) c.accent.copy(alpha = 0.25f) else c.selection, 7.dp)
                            .desktopGestures(key = f.absolutePath, onTap = { pendingLoad = FsRef.Local(f) }, onSecondary = { at ->
                                desk.showContextMenu(at, entries {
                                    item("Open in New Window") { desk.launch(AppIds.NOTES, "file:" + f.absolutePath, newWindow = true) }
                                    item("Move to Trash", destructive = true) { desk.trash(listOf(FsRef.Local(f))); if (sel) { current = null; text = ""; loadedText = "" } }
                                })
                            }).padding(horizontal = 10.dp, vertical = 6.dp).testTag("note-${f.nameWithoutExtension}"),
                    ) {
                        DeskText(f.nameWithoutExtension, Desk.type.bodyStrong, maxLines = 1)
                        Row {
                            DeskText(SimpleDateFormat("d/M/yy", Locale.getDefault()).format(f.lastModified()) + "  ", Desk.type.small, color = c.secondaryText)
                            DeskText(preview, Desk.type.small, color = c.secondaryText, maxLines = 1)
                        }
                    }
                }
            }
        },
        toolbar = {
            ToolbarButton(Glyph.Pencil, { newNote() }, Modifier.testTag("notes-new"), description = "New Note")
            ToolbarButton(Glyph.Check, { text += (if (text.isEmpty() || text.endsWith("\n")) "" else "\n") + "☐ " }, description = "Checklist")
            ToolbarButton(Glyph.Trash, { current?.let { desk.trash(listOf(it)); current = null; text = ""; loadedText = "" } }, enabled = current != null, description = "Delete")
        },
    ) {
        val ref = current
        when {
            error != null -> EmptyState(Glyph.Doc, "Can't open note", error!!)
            ref == null -> EmptyState(Glyph.Note, "No note selected", "Create a note with the pencil button or ⌘N.")
            else -> Box(Modifier.fillMaxSize().background(c.contentBackground)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp)) {
                    DeskText(SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.getDefault()).format((ref as? FsRef.Local)?.file?.lastModified() ?: System.currentTimeMillis()),
                        Desk.type.small, color = c.tertiaryText, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    BasicTextField(
                        text, { text = it.replace("\n- [ ] ", "\n☐ ") },
                        Modifier.fillMaxWidth().testTag("notes-editor"),
                        textStyle = Desk.type.body.copy(color = c.text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.45f).sp),
                        cursorBrush = SolidColor(c.accent),
                    )
                }
                DeskText("${text.split(Regex("\\s+")).count { it.isNotEmpty() }} words", Desk.type.small, color = c.tertiaryText,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(10.dp))
            }
        }
    }
}
