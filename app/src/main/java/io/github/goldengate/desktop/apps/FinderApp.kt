package io.github.goldengate.desktop.apps

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.DialogSpec
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FileKind
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.fs.FsEntry
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.shell.connectFolder
import io.github.goldengate.desktop.shell.fileMenu
import io.github.goldengate.desktop.shell.pasteInto
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.EmptyState
import io.github.goldengate.desktop.ui.FileIcon
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.HSeparator
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SidebarHeader
import io.github.goldengate.desktop.ui.SidebarItem
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.ToolbarGroup
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private const val TRASH_KEY = "trash"
private const val RECENTS_KEY = "recents"

enum class FinderSort(val label: String) { Name("Name"), Kind("Kind"), Date("Date Modified"), Size("Size") }

@Composable
fun FinderApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val fs = desk.fs
    val arg = rememberWindowArgument(win)
    val history = remember { mutableStateListOf<String>() }
    var index by remember { mutableStateOf(-1) }
    var listMode by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(FinderSort.Name) }
    var showHidden by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val selection = remember { mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var quickLook by remember { mutableStateOf<FsEntry?>(null) }

    fun navigate(key: String) {
        while (history.size > index + 1) history.removeAt(history.lastIndex)
        history.add(key); index = history.lastIndex
        selection.clear(); search = ""
    }
    LaunchedEffect(arg) { navigate(arg ?: ("file:" + fs.home.absolutePath)) }
    val current = history.getOrNull(index) ?: ("file:" + fs.home.absolutePath)
    val currentRef: FsRef? = when (current) { TRASH_KEY, RECENTS_KEY -> if (current == TRASH_KEY) FsRef.Local(fs.trash) else null; else -> desk.refFromKey(current) }
    val title = when (current) {
        TRASH_KEY -> "Trash"; RECENTS_KEY -> "Recents"
        else -> currentRef?.let { r -> if (r is FsRef.Local && r.file == fs.home) desk.settings.userName.ifBlank { "Home" } else r.name } ?: "Finder"
    }
    LaunchedEffect(title) { win.title = title }

    LaunchedEffect(current, desk.fsVersion, showHidden, search) {
        loadError = null
        entries = withContext(Dispatchers.IO) {
            try {
                when {
                    search.isNotBlank() -> fs.search(search, 200)
                    current == RECENTS_KEY -> desk.recentItems.mapNotNull { k -> (desk.refFromKey(k) as? FsRef.Local)?.file?.takeIf { it.exists() }?.let { fs.localEntry(it) } }
                    currentRef != null -> fs.list(currentRef, showHidden)
                    else -> emptyList()
                }
            } catch (t: Throwable) { loadError = t.message ?: "This folder can't be opened."; emptyList() }
        }
    }
    val sorted = remember(entries, sort) {
        val base = when (sort) {
            FinderSort.Name -> entries.sortedBy { it.name.lowercase() }
            FinderSort.Kind -> entries.sortedWith(compareBy({ it.kind.label }, { it.name.lowercase() }))
            FinderSort.Date -> entries.sortedByDescending { it.modified }
            FinderSort.Size -> entries.sortedByDescending { it.size }
        }
        base.sortedBy { if (it.isDirectory) 0 else 1 }
    }
    val selectedEntries = sorted.filter { it.ref.key in selection }
    val inTrash = current == TRASH_KEY

    fun open(e: FsEntry) {
        if (e.isDirectory && !inTrash) navigate(e.ref.key) else if (!inTrash) desk.openEntry(e)
    }
    fun newFolder() {
        val parent = currentRef ?: return
        if (inTrash) return
        try { fs.createFolder(parent, "untitled folder") } catch (t: Throwable) { desk.error(t.message ?: "Couldn't create folder.") }
        desk.refreshFs()
    }
    fun trashSelected() {
        if (inTrash) {
            desk.dialog = DialogSpec("Delete ${selectedEntries.size} item(s) immediately?", "You can't undo this action.", confirm = "Delete", destructive = true, glyph = Glyph.Trash) {
                selectedEntries.forEach { (it.ref as? FsRef.Local)?.file?.deleteRecursively() }
                desk.refreshFs()
            }
        } else desk.trash(selectedEntries.map { it.ref })
        selection.clear()
    }

    WindowMenus(win, listOf(
        menu("File") {
            item("New Finder Window", Shortcut(Key.N)) { desk.launch(io.github.goldengate.desktop.core.AppIds.FINDER, newWindow = true) }
            item("New Folder", Shortcut(Key.N, shift = true), enabled = !inTrash && currentRef != null) { newFolder() }
            item("New Text Document", enabled = !inTrash && currentRef != null) {
                currentRef?.let { try { fs.createFile(it, "Untitled.txt") } catch (t: Throwable) { desk.error(t.message ?: "Couldn't create file.") } }
                desk.refreshFs()
            }
            separator()
            item("Open", Shortcut(Key.O), enabled = selectedEntries.isNotEmpty()) { selectedEntries.forEach { open(it) } }
            item("Get Info", Shortcut(Key.I), enabled = selectedEntries.size == 1) { showInfo(desk, selectedEntries.first(), currentRef) }
            item("Quick Look", Shortcut(Key.Y), enabled = selectedEntries.size == 1) { quickLook = selectedEntries.first() }
            item("Rename…", enabled = selectedEntries.size == 1 && !inTrash) { renameEntry(desk, selectedEntries.first()) }
            separator()
            item(if (inTrash) "Delete Immediately…" else "Move to Trash", Shortcut(Key.Backspace), enabled = selectedEntries.isNotEmpty()) { trashSelected() }
            if (inTrash) item("Empty Trash…", Shortcut(Key.Backspace, shift = true), enabled = sorted.isNotEmpty()) { desk.confirmEmptyTrash() }
            separator()
            item("Connect Folder…") { connectFolder(desk) }
        },
        menu("Edit") {
            item("Copy", Shortcut(Key.C), enabled = selectedEntries.isNotEmpty()) { desk.fileClipboard = selectedEntries.map { it.ref } to false }
            item("Cut", Shortcut(Key.X), enabled = selectedEntries.isNotEmpty() && !inTrash) { desk.fileClipboard = selectedEntries.map { it.ref } to true }
            item("Paste", Shortcut(Key.V), enabled = desk.fileClipboard != null && currentRef != null && !inTrash) { currentRef?.let { pasteInto(desk, it) } }
            item("Duplicate", Shortcut(Key.D), enabled = selectedEntries.isNotEmpty() && !inTrash) {
                selectedEntries.forEach { e -> currentRef?.let { try { fs.copy(e.ref, it) } catch (t: Throwable) { desk.error(t.message ?: "Couldn't duplicate.") } } }
                desk.refreshFs()
            }
            separator()
            item("Select All", Shortcut(Key.A)) { selection.clear(); selection.addAll(sorted.map { it.ref.key }) }
        },
        menu("View") {
            item("as Icons", Shortcut(Key.One), checked = !listMode) { listMode = false }
            item("as List", Shortcut(Key.Two), checked = listMode) { listMode = true }
            separator()
            submenu("Sort By") { FinderSort.entries.forEach { s -> item(s.label, checked = sort == s) { sort = s } } }
            item(if (showHidden) "Hide Hidden Files" else "Show Hidden Files", Shortcut(Key.Period, shift = true)) { showHidden = !showHidden }
        },
        menu("Go") {
            item("Back", Shortcut(Key.LeftBracket, keyLabel = "["), enabled = index > 0) { index--; selection.clear() }
            item("Forward", Shortcut(Key.RightBracket, keyLabel = "]"), enabled = index < history.lastIndex) { index++; selection.clear() }
            item("Enclosing Folder", Shortcut(Key.DirectionUp), enabled = currentRef != null && fs.parentOf(currentRef) != null) { currentRef?.let { fs.parentOf(it) }?.let { navigate(it.key) } }
            separator()
            item("Recents") { navigate(RECENTS_KEY) }
            item("Documents", Shortcut(Key.O, shift = true)) { navigate("file:" + fs.documents.absolutePath) }
            item("Desktop", Shortcut(Key.D, shift = true)) { navigate("file:" + fs.desktop.absolutePath) }
            item("Downloads", Shortcut(Key.L, alt = true)) { navigate("file:" + fs.downloads.absolutePath) }
            item("Home", Shortcut(Key.H, shift = true)) { navigate("file:" + fs.home.absolutePath) }
        },
    ))

    WindowScaffold(
        title = title,
        subtitle = if (loadError == null) "${sorted.size} item${if (sorted.size == 1) "" else "s"}" else null,
        sidebar = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SidebarHeader("Favorites")
                val favs = listOf(
                    Triple("Recents", Glyph.Clock, RECENTS_KEY),
                    Triple("Desktop", Glyph.Desktop, "file:" + fs.desktop.absolutePath),
                    Triple("Documents", Glyph.Doc, "file:" + fs.documents.absolutePath),
                    Triple("Downloads", Glyph.Download, "file:" + fs.downloads.absolutePath),
                    Triple("Pictures", Glyph.Image, "file:" + fs.pictures.absolutePath),
                    Triple("Music", Glyph.Music, "file:" + fs.music.absolutePath),
                    Triple(desk.settings.userName.ifBlank { "Home" }, Glyph.House, "file:" + fs.home.absolutePath),
                )
                favs.forEach { (label, g, key) ->
                    SidebarItem(label, g, c.accent, current == key, { navigate(key) }, Modifier.testTag("finder-sidebar-$label"), onSecondary = { p ->
                        desk.showContextMenu(p, entries { item("Open in New Window") { desk.launch(io.github.goldengate.desktop.core.AppIds.FINDER, key, true) } })
                    })
                }
                SidebarHeader("Locations")
                val grants = remember(desk.fsVersion) { runCatching { fs.grantedTrees() }.getOrDefault(emptyList()) }
                grants.forEach { uri ->
                    val root = remember(uri) { runCatching { fs.treeRoot(uri) }.getOrNull() }
                    if (root != null) SidebarItem(root.name, Glyph.Folder, Color(0xFF8E8E93), current == root.key, { navigate(root.key) }, onSecondary = { p ->
                        desk.showContextMenu(p, entries {
                            item("Open in New Window") { desk.launch(io.github.goldengate.desktop.core.AppIds.FINDER, root.key, true) }
                            item("Disconnect (Revoke Access)", destructive = true) { fs.revokeGrant(uri); desk.refreshFs(); if (current == root.key) navigate("file:" + fs.home.absolutePath) }
                        })
                    })
                }
                SidebarItem("Connect Folder…", Glyph.Plus, c.secondaryText, false, { connectFolder(desk) }, Modifier.testTag("finder-connect"))
                SidebarItem("Trash", Glyph.Trash, Color(0xFF8E8E93), inTrash, { navigate(TRASH_KEY) }, badge = if (desk.trashCount > 0) desk.trashCount.toString() else null)
            }
        },
        toolbarLeading = {
            ToolbarGroup {
                ToolbarButton(Glyph.ChevronLeft, { index--; selection.clear() }, enabled = index > 0, description = "Back")
                ToolbarButton(Glyph.ChevronRight, { index++; selection.clear() }, enabled = index < history.lastIndex, description = "Forward")
            }
        },
        toolbar = {
            ToolbarGroup {
                ToolbarButton(Glyph.Grid, { listMode = false }, active = !listMode, description = "Icons")
                ToolbarButton(Glyph.ListView, { listMode = true }, active = listMode, description = "List")
            }
            Spacer(Modifier.width(6.dp))
            ToolbarGroup {
                var anchorHolder by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                ToolbarButton(Glyph.Sort, {
                    desk.showContextMenu(anchorHolder, entries { header("Sort By"); FinderSort.entries.forEach { s -> item(s.label, checked = sort == s) { sort = s } } })
                }, Modifier.onGloballyPositioned { anchorHolder = it.positionInRoot() + androidx.compose.ui.geometry.Offset(0f, it.size.height.toFloat()) }, description = "Sort")
                ToolbarButton(Glyph.Plus, { newFolder() }, enabled = !inTrash && currentRef != null, description = "New Folder")
                if (inTrash) ToolbarButton(Glyph.Trash, { desk.confirmEmptyTrash() }, enabled = sorted.isNotEmpty(), description = "Empty Trash")
            }
            Spacer(Modifier.width(6.dp))
            DeskTextField(search, { search = it }, Modifier.width(150.dp).testTag("finder-search"), placeholder = "Search", glyph = Glyph.Search, rounded = 14.dp)
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .desktopGestures(key = "finder-bg-$current", onTap = { selection.clear() }, onSecondary = { p ->
                        desk.showContextMenu(p, entries {
                            if (!inTrash && currentRef != null) {
                                item("New Folder") { newFolder() }
                                item("New Text Document") { try { fs.createFile(currentRef, "Untitled.txt") } catch (_: Throwable) {}; desk.refreshFs() }
                                if (desk.fileClipboard != null) item("Paste Item") { pasteInto(desk, currentRef) }
                                separator()
                            }
                            if (inTrash) { item("Empty Trash…", enabled = sorted.isNotEmpty()) { desk.confirmEmptyTrash() }; separator() }
                            item("View as Icons", checked = !listMode) { listMode = false }
                            item("View as List", checked = listMode) { listMode = true }
                            submenu("Sort By") { FinderSort.entries.forEach { s -> item(s.label, checked = sort == s) { sort = s } } }
                        })
                    }),
            ) {
                when {
                    loadError != null -> EmptyState(Glyph.Lock, "Can't open this folder", loadError ?: "", Modifier.align(Alignment.Center))
                    sorted.isEmpty() -> EmptyState(if (inTrash) Glyph.Trash else Glyph.Folder, if (search.isNotBlank()) "No results" else if (inTrash) "Trash is empty" else "This folder is empty",
                        if (search.isNotBlank()) "Nothing matches “$search”." else "", Modifier.align(Alignment.Center))
                    listMode -> FinderList(sorted, selection, inTrash, desk, currentRef, ::open)
                    else -> FinderGrid(sorted, selection, inTrash, desk, currentRef, ::open)
                }
            }
            HSeparator()
            Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskText(
                    (currentRef?.let { fs.displayPath(it) } ?: title),
                    Desk.type.small, color = c.secondaryText, maxLines = 1, modifier = Modifier.weight(1f),
                )
                DeskText(
                    if (selection.isNotEmpty()) "${selection.size} of ${sorted.size} selected" else "${sorted.size} items, ${FileSystem.formatSize(fs.freeSpace())} available",
                    Desk.type.small, color = c.secondaryText, maxLines = 1,
                )
            }
        }
        quickLook?.let { e -> QuickLookSheet(e, desk) { quickLook = null } }
    }
}

@Composable
private fun FinderGrid(items: List<FsEntry>, selection: MutableList<String>, inTrash: Boolean, desk: DesktopController, parent: FsRef?, open: (FsEntry) -> Unit) {
    val c = Desk.colors
    LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(8.dp)) {
        items(items, key = { it.ref.key }) { e ->
            val sel = e.ref.key in selection
            Column(
                Modifier.padding(4.dp)
                    .entryGestures(e, selection, inTrash, desk, parent, open)
                    .testTag("finder-item-${e.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) c.controlFillPressed else Color.Transparent).padding(4.dp)) { FileIcon(e, 56.dp) }
                DeskText(
                    e.name, Desk.type.caption.copy(textAlign = TextAlign.Center), color = if (sel) Color.White else c.text, maxLines = 2,
                    modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(4.dp)).background(if (sel) c.accent else Color.Transparent).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun FinderList(items: List<FsEntry>, selection: MutableList<String>, inTrash: Boolean, desk: DesktopController, parent: FsRef?, open: (FsEntry) -> Unit) {
    val c = Desk.colors
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            DeskText("Name", Desk.type.small, color = c.secondaryText, modifier = Modifier.weight(1f))
            DeskText("Date Modified", Desk.type.small, color = c.secondaryText, modifier = Modifier.width(150.dp))
            DeskText("Size", Desk.type.small, color = c.secondaryText, modifier = Modifier.width(70.dp))
            DeskText("Kind", Desk.type.small, color = c.secondaryText, modifier = Modifier.width(110.dp))
        }
        HSeparator()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { _, e -> e.ref.key }) { i, e ->
                val sel = e.ref.key in selection
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(5.dp))
                        .background(when { sel -> c.accent; i % 2 == 1 -> c.controlFill.copy(alpha = 0.5f); else -> Color.Transparent })
                        .entryGestures(e, selection, inTrash, desk, parent, open)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("finder-row-${e.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (sel) Color.White else c.text
                    val fg2 = if (sel) Color.White.copy(alpha = 0.85f) else c.secondaryText
                    FileIcon(e, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    DeskText(e.name, Desk.type.body, color = fg, maxLines = 1, modifier = Modifier.weight(1f))
                    DeskText(if (e.modified > 0) df.format(Date(e.modified)) else "—", Desk.type.caption, color = fg2, maxLines = 1, modifier = Modifier.width(150.dp))
                    DeskText(if (e.isDirectory) "—" else FileSystem.formatSize(e.size), Desk.type.caption, color = fg2, maxLines = 1, modifier = Modifier.width(70.dp))
                    DeskText(e.kind.label, Desk.type.caption, color = fg2, maxLines = 1, modifier = Modifier.width(110.dp))
                }
            }
        }
    }
}

private fun Modifier.entryGestures(
    e: FsEntry, selection: MutableList<String>, inTrash: Boolean, desk: DesktopController, parent: FsRef?, open: (FsEntry) -> Unit,
): Modifier = this.desktopGestures(
    key = e.ref.key,
    onTap = {
        // Ctrl/⌘-click style toggling when a keyboard modifier is held isn't observable here; a tap selects one item.
        selection.clear(); selection.add(e.ref.key)
    },
    onDoubleTap = { open(e) },
    onSecondary = { p ->
        if (e.ref.key !in selection) { selection.clear(); selection.add(e.ref.key) }
        desk.showContextMenu(p, if (inTrash) entries {
            item("Put Back") { (e.ref as? FsRef.Local)?.file?.let { f -> try { desk.fs.putBack(f) } catch (t: Throwable) { desk.error(t.message ?: "Couldn't put back.") } }; desk.refreshFs() }
            item("Delete Immediately…", destructive = true) {
                desk.dialog = DialogSpec("Delete “${e.name}” immediately?", "You can't undo this action.", confirm = "Delete", destructive = true, glyph = Glyph.Trash) {
                    (e.ref as? FsRef.Local)?.file?.deleteRecursively(); desk.refreshFs()
                }
            }
        } else fileMenu(desk, e, parent ?: e.ref))
    },
)

fun showInfo(desk: DesktopController, e: FsEntry, parent: FsRef?) {
    desk.dialog = DialogSpec(
        e.name,
        buildString {
            append("Kind: ${e.kind.label}\n")
            if (!e.isDirectory) append("Size: ${FileSystem.formatSize(e.size)}\n")
            parent?.let { append("Where: ${desk.fs.displayPath(it)}\n") }
            if (e.modified > 0) append("Modified: ${DateFormat.getDateTimeInstance().format(Date(e.modified))}")
        },
        cancel = null, glyph = Glyph.Info,
    )
}

fun renameEntry(desk: DesktopController, e: FsEntry) {
    desk.dialog = DialogSpec("Rename “${e.name}”", input = e.name, confirm = "Rename") { name ->
        if (name.isNotBlank() && name != e.name) {
            try { desk.fs.rename(e.ref, name.trim()) } catch (t: Throwable) { desk.error(t.message ?: "Couldn't rename.") }
            desk.refreshFs()
        }
    }
}

/** Quick Look: a lightweight preview for images and text. */
@Composable
private fun QuickLookSheet(e: FsEntry, desk: DesktopController, onClose: () -> Unit) {
    val c = Desk.colors
    var text by remember(e.ref.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(e.ref.key) {
        if (e.kind == FileKind.Text || e.kind == FileKind.Markdown || e.kind == FileKind.Csv) {
            text = withContext(Dispatchers.IO) { runCatching { desk.fs.readText(e.ref, 20_000) }.getOrElse { "Preview unavailable." } }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).desktopGestures(key = "ql-bg", onTap = onClose).testTag("quicklook")) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(320.dp).clip(RoundedCornerShape(12.dp)).background(c.windowBackground),
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskText(e.name, Desk.type.bodyStrong, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
                DeskText("Open", Desk.type.body, color = c.accent, modifier = Modifier.desktopGestures(key = "ql-open", onTap = { onClose(); desk.openEntry(e) }).padding(4.dp))
            }
            HSeparator()
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                when {
                    e.kind == FileKind.Image -> FileIcon(e, 260.dp)
                    text != null -> DeskText(text!!, Desk.type.mono, color = c.text, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        FileIcon(e, 96.dp)
                        DeskText("${e.kind.label} · ${FileSystem.formatSize(e.size)}", Desk.type.caption, color = c.secondaryText)
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun unusedFile(f: File) = f
@Suppress("unused")
private val unusedSize = Modifier.size(0.dp)
