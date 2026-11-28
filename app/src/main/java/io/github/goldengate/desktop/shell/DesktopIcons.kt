package io.github.goldengate.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.DialogSpec
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.fs.FsEntry
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.ui.AppIcon
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.FileIcon
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CELL_W = 96f
private const val CELL_H = 96f

/**
 * Desktop background interaction + icons for files in ~/Desktop. Supports selection (tap,
 * rubber band), double-tap to open, drag to move (positions persist), drop on a folder to move
 * the file into it, drop on the Dock's Trash to delete, and context menus.
 */
@Composable
fun DesktopSurface(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val density = LocalDensity.current
    val version = desk.fsVersion
    val entries = remember(version) {
        runCatching { desk.fs.list(FsRef.Local(desk.fs.desktop)) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }
    var band by remember { mutableStateOf<Rect?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val area = desk.wm.area
        val rows = max(1, ((area.h - 10f) / CELL_H).toInt())
        // Default slots: column-major from the top-right corner, like Finder.
        fun slot(i: Int): Offset {
            val col = i / rows; val row = i % rows
            return Offset(maxWidth.value - 12f - CELL_W * (col + 1), area.y + 8f + row * CELL_H)
        }
        val appShortcuts = desk.settings.desktopApps.filter { AppRegistry.get(it) != null }
        val positions = LinkedHashMap<String, Offset>()
        entries.forEachIndexed { i, e -> positions[e.ref.key] = desk.iconPositions[e.ref.key] ?: slot(i) }
        appShortcuts.forEachIndexed { i, id ->
            val key = deskAppKey(id)
            positions[key] = desk.iconPositions[key] ?: slot(entries.size + i)
        }

        // Background: clear selection, rubber band selection, context menu.
        Box(
            Modifier
                .fillMaxSize()
                .desktopGestures(
                    key = "desktop-bg",
                    onTap = { desk.desktopSelection.clear(); desk.closeMenus(); desk.wm.focus(null) },
                    onSecondary = { p -> desk.showContextMenu(p, desktopMenu(desk)) },
                )
                .then(if (desk.settings.showDesktopIcons) Modifier.pointerBand(density.density, positions, { band = it }) { r ->
                    desk.desktopSelection.clear()
                    entries.forEach { e ->
                        val p = positions[e.ref.key]!!
                        if (r.overlaps(Rect(p.x, p.y, p.x + CELL_W, p.y + CELL_H))) desk.desktopSelection.add(e.ref.key)
                    }
                } else Modifier)
                .testTag("desktop"),
        )
        if (desk.settings.showDesktopIcons) {
            appShortcuts.forEach { id -> DesktopAppIcon(desk, id, positions[deskAppKey(id)]!!) }
            entries.forEach { e -> DesktopIcon(desk, e, positions[e.ref.key]!!, entries, positions) }
        }
        band?.let { r ->
            Box(
                Modifier
                    .offset { IntOffset((r.left * density.density).roundToInt(), (r.top * density.density).roundToInt()) }
                    .size(r.width.dp, r.height.dp)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.55f)),
            )
        }
    }
}

private fun Modifier.pointerBand(d: Float, key: Any, onBand: (Rect?) -> Unit, onSelect: (Rect) -> Unit): Modifier =
    this.pointerInput(key) {
        var start = Offset.Zero
        var cur = Offset.Zero
        detectDragGestures(
            onDragStart = { start = it; cur = it },
            onDragEnd = { onBand(null) },
            onDragCancel = { onBand(null) },
        ) { ch, amt ->
            ch.consume(); cur += amt
            val r = Rect(min(start.x, cur.x) / d, min(start.y, cur.y) / d, max(start.x, cur.x) / d, max(start.y, cur.y) / d)
            onBand(r); onSelect(r)
        }
    }

private val labelShadow = Shadow(Color(0x99000000), Offset(0f, 1f), 3f)

@Composable
private fun DesktopIcon(desk: DesktopController, e: FsEntry, pos: Offset, all: List<FsEntry>, positions: Map<String, Offset>) {
    val density = LocalDensity.current
    val selected = e.ref.key in desk.desktopSelection
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val iconSize = desk.settings.desktopIconSize.dp
    Column(
        Modifier
            .offset { IntOffset(((pos.x + drag.x) * density.density).roundToInt(), ((pos.y + drag.y) * density.density).roundToInt()) }
            .width(CELL_W.dp)
            .graphicsLayer { if (dragging) { alpha = 0.8f } }
            .iconDrag(
                key = positions,
                onStart = {
                    dragging = true
                    if (e.ref.key !in desk.desktopSelection) { desk.desktopSelection.clear(); desk.desktopSelection.add(e.ref.key) }
                },
                onDrag = { d -> drag += Offset(d.x / density.density, d.y / density.density) },
                onEnd = {
                    dragging = false
                    val drop = Offset(pos.x + drag.x + CELL_W / 2f, pos.y + drag.y + 30f)
                    val trash = desk.dockIconCenters[AppIds.TRASH]
                    val target = all.firstOrNull { o ->
                        o.isDirectory && o.ref.key != e.ref.key && positions[o.ref.key]?.let { p ->
                            drop.x in p.x..(p.x + CELL_W) && drop.y in p.y..(p.y + CELL_H)
                        } == true
                    }
                    when {
                        trash != null && (Offset(drop.x * density.density, drop.y * density.density) - trash).getDistance() < 60f * density.density -> {
                            desk.trash(desk.desktopSelection.mapNotNull { desk.refFromKey(it) }.ifEmpty { listOf(e.ref) })
                            desk.desktopSelection.clear()
                        }
                        target != null -> {
                            try { desk.fs.move(e.ref, target.ref) } catch (t: Throwable) { desk.error(t.message ?: "Couldn't move item.") }
                            desk.refreshFs()
                        }
                        abs(drag.x) + abs(drag.y) > 2f -> {
                            // Snap to an invisible grid like "Snap to Grid" in Finder.
                            val nx = ((pos.x + drag.x) / 8f).roundToInt() * 8f
                            val ny = ((pos.y + drag.y) / 8f).roundToInt() * 8f
                            desk.iconPositions[e.ref.key] = Offset(nx, ny.coerceAtLeast(desk.wm.area.y))
                            desk.saveIconPositions()
                        }
                    }
                    drag = Offset.Zero
                },
            )
            .desktopGestures(
                key = e.ref.key,
                onTap = { desk.desktopSelection.clear(); desk.desktopSelection.add(e.ref.key); desk.wm.focus(null) },
                onDoubleTap = { desk.openEntry(e) },
                onSecondary = { p ->
                    if (e.ref.key !in desk.desktopSelection) { desk.desktopSelection.clear(); desk.desktopSelection.add(e.ref.key) }
                    desk.showContextMenu(p, fileMenu(desk, e, FsRef.Local(desk.fs.desktop)))
                },
            )
            .padding(top = 4.dp)
            .testTag("desktop-icon-${e.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) Color.Black.copy(alpha = 0.25f) else Color.Transparent).padding(3.dp),
        ) { FileIcon(e, iconSize) }
        DeskText(
            e.name, Desk.type.caption.copy(textAlign = TextAlign.Center, shadow = if (selected) null else labelShadow),
            color = Color.White, maxLines = 2,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(4.dp))
                .background(if (selected) Desk.colors.accent else Color.Transparent).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private fun deskAppKey(appId: String) = "desktop-app:$appId"

/** An app shortcut on the wallpaper (the App Store by default): double-click to open, drag to move. */
@Composable
private fun DesktopAppIcon(desk: DesktopController, appId: String, pos: Offset) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val iconSize = desk.settings.desktopIconSize.dp
    Column(
        Modifier
            .offset { IntOffset(((pos.x + drag.x) * density.density).roundToInt(), ((pos.y + drag.y) * density.density).roundToInt()) }
            .width(CELL_W.dp)
            .graphicsLayer { if (dragging) alpha = 0.8f }
            .iconDrag(
                key = pos,
                onStart = { dragging = true },
                onDrag = { d -> drag += Offset(d.x / density.density, d.y / density.density) },
                onEnd = {
                    dragging = false
                    if (abs(drag.x) + abs(drag.y) > 2f) {
                        val nx = ((pos.x + drag.x) / 8f).roundToInt() * 8f
                        val ny = ((pos.y + drag.y) / 8f).roundToInt() * 8f
                        desk.iconPositions[deskAppKey(appId)] = Offset(nx, ny.coerceAtLeast(desk.wm.area.y))
                        desk.saveIconPositions()
                    }
                    drag = Offset.Zero
                },
            )
            .desktopGestures(
                key = deskAppKey(appId),
                onTap = { desk.wm.focus(null) },
                onDoubleTap = { desk.launch(appId) },
                onSecondary = { p ->
                    desk.showContextMenu(p, entries {
                        item("Open") { desk.launch(appId) }
                        item("Remove from Desktop", glyph = Glyph.Trash) {
                            desk.updateSettings { s -> s.copy(desktopApps = s.desktopApps - appId) }
                        }
                    })
                },
            )
            .padding(top = 4.dp)
            .testTag("desktop-app-$appId"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(appId, iconSize)
        DeskText(
            AppRegistry.name(appId), Desk.type.caption.copy(textAlign = TextAlign.Center, shadow = labelShadow),
            color = Color.White, maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun Modifier.iconDrag(key: Any, onStart: () -> Unit, onDrag: (Offset) -> Unit, onEnd: () -> Unit): Modifier =
    this.pointerInput(key) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() },
        ) { ch, amt -> ch.consume(); onDrag(amt) }
    }

/** Context menu for a file or folder (shared by Desktop and Finder). */
fun fileMenu(desk: DesktopController, e: FsEntry, parent: FsRef) = entries {
    item("Open") { desk.openEntry(e) }
    if (!e.isDirectory) item("Open Externally…") { desk.bridge?.openExternally(e.ref) }
    separator()
    item("Move to Trash", glyph = Glyph.Trash) {
        val refs = desk.desktopSelection.mapNotNull { desk.refFromKey(it) }.filter { it.key == e.ref.key || desk.desktopSelection.contains(it.key) }
        desk.trash(refs.ifEmpty { listOf(e.ref) })
        desk.desktopSelection.clear()
    }
    separator()
    item("Get Info", glyph = Glyph.Info) {
        desk.dialog = DialogSpec(
            e.name,
            buildString {
                append("Kind: ${e.kind.label}\n")
                if (!e.isDirectory) append("Size: ${io.github.goldengate.desktop.fs.FileSystem.formatSize(e.size)}\n")
                append("Where: ${desk.fs.displayPath(parent)}\n")
                if (e.modified > 0) append("Modified: ${java.text.DateFormat.getDateTimeInstance().format(e.modified)}")
            },
            cancel = null, glyph = Glyph.Info,
        )
    }
    item("Rename…", glyph = Glyph.Pencil) {
        desk.dialog = DialogSpec("Rename “${e.name}”", input = e.name, confirm = "Rename") { name ->
            if (name.isNotBlank() && name != e.name) {
                try {
                    val newRef = desk.fs.rename(e.ref, name.trim())
                    desk.iconPositions.remove(e.ref.key)?.let { desk.iconPositions[newRef.key] = it }
                } catch (t: Throwable) { desk.error(t.message ?: "Couldn't rename.") }
                desk.refreshFs()
            }
        }
    }
    item("Duplicate", glyph = Glyph.Copy) {
        try { desk.fs.copy(e.ref, parent) } catch (t: Throwable) { desk.error(t.message ?: "Couldn't duplicate.") }
        desk.refreshFs()
    }
    separator()
    item("Copy", glyph = Glyph.Copy) { desk.fileClipboard = listOf(e.ref) to false }
    item("Cut", glyph = Glyph.Scissors) { desk.fileClipboard = listOf(e.ref) to true }
    if (e.isDirectory) item("Paste Into Folder", enabled = desk.fileClipboard != null) { pasteInto(desk, e.ref) }
}

fun desktopMenu(desk: DesktopController) = entries {
    item("New Folder", glyph = Glyph.Folder) { desk.fs.createFolder(FsRef.Local(desk.fs.desktop), "untitled folder"); desk.refreshFs() }
    item("New Text Document", glyph = Glyph.Doc) { desk.fs.createFile(FsRef.Local(desk.fs.desktop), "Untitled.txt", ""); desk.refreshFs() }
    if (desk.fileClipboard != null) item("Paste Item") { pasteInto(desk, FsRef.Local(desk.fs.desktop)) }
    separator()
    item("Get Info", glyph = Glyph.Info) { desk.launch(AppIds.SYSINFO) }
    item("Change Wallpaper…", glyph = Glyph.Image) { desk.launch(AppIds.SETTINGS, "wallpaper") }
    item("Edit Widgets…", enabled = false) {}
    separator()
    item("Clean Up") { desk.iconPositions.clear(); desk.saveIconPositions() }
    submenu("Sort By") {
        item("Name", checked = true) { desk.iconPositions.clear(); desk.saveIconPositions() }
    }
    item(if (desk.settings.showDesktopIcons) "Hide Desktop Icons" else "Show Desktop Icons") { desk.updateSettings { it.copy(showDesktopIcons = !it.showDesktopIcons) } }
    separator()
    item("Mission Control") { desk.toggleOverlay(io.github.goldengate.desktop.core.Overlay.MissionControl) }
}
