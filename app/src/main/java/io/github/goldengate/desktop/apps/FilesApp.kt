package io.github.goldengate.desktop.apps

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FileKind
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.shell.connectFolder
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.FileIcon
import io.github.goldengate.desktop.ui.FormRow
import io.github.goldengate.desktop.ui.FormSection
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Usage(val label: String, val bytes: Long, val color: Color)

/** Import a single document chosen with the system picker into Downloads, then open it. */
fun importDocument(desk: DesktopController, uri: Uri) {
    runCatching {
        var name = "Document"
        desk.app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
        val out = desk.fs.uniqueFile(desk.fs.downloads, name)
        desk.app.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        desk.refreshFs()
        desk.openEntry(desk.fs.localEntry(out))
    }.onFailure { desk.error("Couldn't open the document: ${it.message}") }
}

@Composable
fun FilesApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val fs = desk.fs
    val c = Desk.colors
    LaunchedEffect(Unit) { win.title = "Files" }
    val usage by produceState(emptyList<Usage>(), desk.fsVersion) {
        value = withContext(Dispatchers.IO) {
            val by = HashMap<FileKind, Long>()
            fs.home.walkTopDown().filter { it.isFile }.forEach { f -> val k = FileKind.of(f.name, false, FileSystem.mimeOf(f.name)); by[k] = (by[k] ?: 0L) + f.length() }
            listOf(
                Usage("Images", by[FileKind.Image] ?: 0, Color(0xFFFF9F0A)),
                Usage("Audio", by[FileKind.Audio] ?: 0, Color(0xFFFF375F)),
                Usage("Documents", (by[FileKind.Text] ?: 0) + (by[FileKind.Markdown] ?: 0) + (by[FileKind.Csv] ?: 0) + (by[FileKind.Pdf] ?: 0), Color(0xFF0A84FF)),
                Usage("Other", by.filterKeys { it !in setOf(FileKind.Image, FileKind.Audio, FileKind.Text, FileKind.Markdown, FileKind.Csv, FileKind.Pdf) }.values.sum(), Color(0xFF8E8E93)),
            )
        }
    }
    val grants = remember(desk.fsVersion) { runCatching { fs.grantedTrees() }.getOrDefault(emptyList()) }
    fun openDoc() = desk.bridge?.pickDocument(arrayOf("*/*")) { uri -> if (uri != null) importDocument(desk, uri) }

    WindowMenus(win, listOf(menu("File") {
        item("Open Document…", Shortcut(Key.O)) { openDoc() }
        item("Connect Folder…", Shortcut(Key.K)) { connectFolder(desk) }
        item("Open Finder", Shortcut(Key.F, shift = true)) { desk.launch(AppIds.FINDER) }
    }))

    WindowScaffold("Files") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).testTag("files-app")) {
            val total = usage.sumOf { it.bytes }
            FormSection("Golden Gate Desktop Storage", footer = "Your home folder lives in app-private storage. Device free space: ${FileSystem.formatSize(fs.freeSpace())} of ${FileSystem.formatSize(fs.totalSpace())}.") {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    DeskText("${FileSystem.formatSize(total)} used", Desk.type.headline)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp)).background(c.controlFillPressed)) {
                        usage.filter { it.bytes > 0 }.forEach { u -> Box(Modifier.weight((u.bytes.toFloat() / total.coerceAtLeast(1)).coerceAtLeast(0.01f)).fillMaxHeight().background(u.color)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        usage.forEach { u ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(u.color)); Spacer(Modifier.width(4.dp))
                                DeskText("${u.label} ${FileSystem.formatSize(u.bytes)}", Desk.type.small, color = c.secondaryText)
                            }
                        }
                    }
                }
            }
            FormSection("Locations") {
                listOf("Desktop" to fs.desktop, "Documents" to fs.documents, "Downloads" to fs.downloads, "Pictures" to fs.pictures, "Music" to fs.music).forEach { (n, f) ->
                    FormRow(n, detail = "${f.listFiles()?.count { !it.name.startsWith(".") } ?: 0} items") {
                        PushButton("Open", { desk.launch(AppIds.FINDER, "file:" + f.absolutePath, newWindow = true) })
                    }
                }
                FormRow("Trash", detail = "${desk.trashCount} items", divider = false) { PushButton("Open", { desk.launch(AppIds.FINDER, "trash", newWindow = true) }) }
            }
            FormSection("Connected Device Folders", footer = "Android's Storage Access Framework lets you grant access to one folder at a time. Golden Gate Desktop never reads other files on your device.") {
                if (grants.isEmpty()) FormRow("No folders connected yet")
                grants.forEach { u ->
                    val root = remember(u) { runCatching { fs.treeRoot(u) }.getOrNull() }
                    FormRow(root?.name ?: (u.lastPathSegment ?: "Folder")) {
                        GlyphIcon(Glyph.Folder, Color(0xFF0A84FF), 16.dp); Spacer(Modifier.width(8.dp))
                        PushButton("Open", { root?.let { desk.launch(AppIds.FINDER, it.key, newWindow = true) } })
                    }
                }
                FormRow("Connect a Folder…", divider = false) { PushButton("Connect…", { connectFolder(desk) }, Modifier.testTag("files-connect"), primary = true) }
            }
            FormSection("Open a Single Document", footer = "The document is copied into Downloads and opened in the matching app (Notes, Photos, Music or Browser).") {
                FormRow("Choose a document from your device", divider = false) { PushButton("Open…", { openDoc() }) }
            }
            if (desk.recentItems.isNotEmpty()) {
                FormSection("Recent Items") {
                    desk.recentItems.take(8).forEachIndexed { i, key ->
                        val ref = desk.refFromKey(key)
                        FormRow(ref?.name ?: key, divider = i < desk.recentItems.take(8).lastIndex) {
                            if (ref is io.github.goldengate.desktop.fs.FsRef.Local && ref.file.exists()) {
                                FileIcon(fs.localEntry(ref.file), 20.dp); Spacer(Modifier.width(8.dp))
                                PushButton("Open", { desk.openEntry(fs.localEntry(ref.file)) })
                            }
                        }
                    }
                }
            }
        }
    }
}
