package io.github.goldengate.desktop.apps

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FileKind
import io.github.goldengate.desktop.fs.FsEntry
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.Wallpaper
import io.github.goldengate.desktop.glass.WallpaperPainter
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.EmptyState
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.SidebarItem
import io.github.goldengate.desktop.ui.Thumbnails
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PhotosApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val fs = desk.fs
    val c = Desk.colors
    val arg = rememberWindowArgument(win)
    var album by remember { mutableIntStateOf(0) } // 0 library, 1 favourites, 2 recents
    var viewing by remember { mutableStateOf<FsEntry?>(null) }
    var zoomStep by remember { mutableIntStateOf(1) }
    var slideshow by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }
    val prefs = remember { desk.app.getSharedPreferences("photos", android.content.Context.MODE_PRIVATE) }
    var favourites by remember { mutableStateOf(prefs.getStringSet("fav", emptySet())!!.toSet()) }
    fun toggleFav(e: FsEntry) { favourites = if (e.ref.key in favourites) favourites - e.ref.key else favourites + e.ref.key; prefs.edit().putStringSet("fav", favourites).apply() }

    // Seed a few original sample photos (rendered from the procedural wallpapers) the first time.
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("seeded", false)) {
            seeding = true
            // Rendered in parallel and published one by one so the grid fills in progressively.
            val dir = File(fs.pictures, "Samples").apply { mkdirs() }
            coroutineScope {
                Wallpaper.entries.forEachIndexed { i, w ->
                    for (dark in listOf(false, true)) launch(Dispatchers.Default) {
                        val bmp = WallpaperPainter.paint(w, dark, 960, 640, seed = i * 13 + if (dark) 5 else 1)
                        val name = "${w.label}${if (dark) " Night" else ""}.jpg"
                        val tmp = File(dir, ".$name.part")
                        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                        tmp.renameTo(File(dir, name))
                        bmp.recycle()
                        withContext(Dispatchers.Main) { desk.refreshFs() }
                    }
                }
            }
            prefs.edit().putBoolean("seeded", true).apply()
            seeding = false
            desk.refreshFs()
        }
    }

    val all by produceState(emptyList<FsEntry>(), desk.fsVersion, seeding) {
        value = withContext(Dispatchers.IO) {
            fs.pictures.walkTopDown().maxDepth(4).filter { it.isFile && !it.name.startsWith(".") }.map { fs.localEntry(it) }
                .filter { it.kind == FileKind.Image }.sortedByDescending { it.modified }.toList()
        }
    }
    val shown = when (album) {
        1 -> all.filter { it.ref.key in favourites }
        2 -> all.filter { System.currentTimeMillis() - it.modified < 30L * 86_400_000L }
        else -> all
    }

    LaunchedEffect(arg) {
        val ref = arg?.let { desk.refFromKey(it) } ?: return@LaunchedEffect
        val e = when (ref) {
            is FsRef.Local -> fs.localEntry(ref.file)
            is FsRef.Saf -> FsEntry(ref, ref.name, false, 0, 0, "image/*")
        }
        viewing = e
    }
    LaunchedEffect(viewing) { win.title = viewing?.name ?: "Photos"; zoomStep = 1 }
    LaunchedEffect(slideshow, viewing) {
        if (slideshow && viewing != null) {
            delay(3500)
            val list = shown.ifEmpty { listOfNotNull(viewing) }
            val i = list.indexOfFirst { it.ref == viewing?.ref }
            viewing = list[(i + 1).mod(list.size)]
        }
    }
    fun step(d: Int) {
        val v = viewing ?: return
        val list = shown.ifEmpty { return }
        val i = list.indexOfFirst { it.ref == v.ref }
        if (i >= 0) viewing = list[(i + d).mod(list.size)]
    }
    fun import() {
        desk.bridge?.pickImage { uri ->
            if (uri != null) runCatching {
                val name = "Imported ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(System.currentTimeMillis())}.jpg"
                val out = fs.uniqueFile(fs.pictures, name)
                desk.app.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                desk.refreshFs()
            }.onFailure { desk.error("Couldn't import: ${it.message}") }
        }
    }

    WindowMenus(win, listOf(
        menu("File") {
            item("Import…", Shortcut(Key.I, shift = true)) { import() }
            item("Show in Finder", enabled = viewing?.ref is FsRef.Local) { (viewing?.ref as? FsRef.Local)?.file?.parentFile?.let { desk.launch(AppIds.FINDER, "file:" + it.absolutePath, newWindow = true) } }
            item("Delete Photo", Shortcut(Key.Backspace), enabled = viewing != null) { viewing?.let { desk.trash(listOf(it.ref)); viewing = null } }
        },
        menu("View") {
            item("Zoom In", Shortcut(Key.Equals, keyLabel = "+"), enabled = viewing != null) { zoomStep = (zoomStep + 1).coerceAtMost(4) }
            item("Zoom Out", Shortcut(Key.Minus), enabled = viewing != null) { zoomStep = (zoomStep - 1).coerceAtLeast(1) }
            separator()
            item("Next Photo", Shortcut(Key.DirectionRight), enabled = viewing != null) { step(1) }
            item("Previous Photo", Shortcut(Key.DirectionLeft), enabled = viewing != null) { step(-1) }
            item(if (slideshow) "Stop Slideshow" else "Play Slideshow", enabled = shown.isNotEmpty()) { if (viewing == null) viewing = shown.firstOrNull(); slideshow = !slideshow }
            item("Back to Library", enabled = viewing != null) { viewing = null; slideshow = false }
        },
    ))

    WindowScaffold(
        title = viewing?.name ?: "Photos",
        subtitle = if (viewing == null) "${shown.size} items" else null,
        sidebar = {
            SidebarItem("Library", Glyph.Image, Color(0xFF0A84FF), album == 0, { album = 0; viewing = null })
            SidebarItem("Favourites", Glyph.StarFill, Color(0xFFFF9F0A), album == 1, { album = 1; viewing = null })
            SidebarItem("Recents", Glyph.Clock, Color(0xFF0A84FF), album == 2, { album = 2; viewing = null })
        },
        toolbar = {
            if (viewing != null) {
                ToolbarButton(Glyph.ChevronLeft, { viewing = null; slideshow = false }, description = "Back")
                ToolbarButton(if (viewing?.ref?.key in favourites) Glyph.StarFill else Glyph.Star, { viewing?.let { toggleFav(it) } }, description = "Favourite")
                ToolbarButton(Glyph.Minus, { zoomStep = (zoomStep - 1).coerceAtLeast(1) }, description = "Zoom out")
                ToolbarButton(Glyph.Plus, { zoomStep = (zoomStep + 1).coerceAtMost(4) }, description = "Zoom in")
                ToolbarButton(if (slideshow) Glyph.Pause else Glyph.Play, { slideshow = !slideshow }, description = "Slideshow")
                ToolbarButton(Glyph.Trash, { viewing?.let { desk.trash(listOf(it.ref)); viewing = null } }, description = "Delete")
            } else {
                SegmentedControl(listOf("Years", "Months", "All Photos"), 2, {})
                Spacer(Modifier.width(8.dp))
                ToolbarButton(Glyph.Download, { import() }, Modifier.testTag("photos-import"), description = "Import")
                ToolbarButton(Glyph.Play, { viewing = shown.firstOrNull(); slideshow = viewing != null }, enabled = shown.isNotEmpty(), description = "Slideshow")
            }
        },
    ) {
        val v = viewing
        if (v != null) {
            PhotoViewer(v, zoomStep, onZoom = { zoomStep = it }, onSwipe = { step(it) })
        } else if (shown.isEmpty()) {
            EmptyState(Glyph.Image, if (seeding) "Preparing sample photos…" else "No Photos", if (album == 1) "Tap the star while viewing a photo to add it here." else "Import photos with the download button. Only the photos you choose are shared with this app.")
        } else {
            LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.fillMaxSize().testTag("photos-grid"), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.ref.key }) { e ->
                    val ctx = LocalContext.current
                    val bmp by produceState(Thumbnails.cached(e.ref.key + "@320"), e.ref.key) { if (value == null) value = Thumbnails.load(ctx, e.ref, 320) }
                    Box(Modifier.aspectRatio(1f).background(c.controlFill).desktopGestures(key = e.ref.key, onTap = { viewing = e }, onSecondary = { at ->
                        desk.showContextMenu(at, entries {
                            item("Open") { viewing = e }
                            item(if (e.ref.key in favourites) "Unfavourite" else "Favourite") { toggleFav(e) }
                            item("Set as Wallpaper") { (e.ref as? FsRef.Local)?.let { r -> desk.updateSettings { it.copy(customWallpaper = r.file.absolutePath) } } }
                            separator()
                            item("Delete", destructive = true) { desk.trash(listOf(e.ref)) }
                        })
                    }).testTag("photo-${e.name}")) {
                        bmp?.let { Image(it, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        if (e.ref.key in favourites) Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) { io.github.goldengate.desktop.ui.GlyphIcon(Glyph.StarFill, Color.White, 14.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewer(e: FsEntry, zoomStep: Int, onZoom: (Int) -> Unit, onSwipe: (Int) -> Unit) {
    val ctx = LocalContext.current
    val c = Desk.colors
    val img by produceState<ImageBitmap?>(null, e.ref.key) { value = Thumbnails.load(ctx, e.ref, 2048) }
    var scale by remember(e.ref.key) { mutableFloatStateOf(1f) }
    var pan by remember(e.ref.key) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(zoomStep) { scale = listOf(1f, 1f, 2f, 3f, 5f)[zoomStep]; if (zoomStep == 1) pan = Offset.Zero }
    Box(Modifier.fillMaxSize().background(if (c.dark) Color.Black else Color(0xFFF2F2F7)).testTag("photo-viewer")
        .pointerInput(e.ref.key) {
            detectTransformGestures { _, p, z, _ ->
                scale = (scale * z).coerceIn(1f, 6f)
                pan = if (scale <= 1.01f) Offset.Zero else pan + p
            }
        }
        .pointerInput(e.ref.key) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.type == PointerEventType.Scroll) {
                        val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        scale = (scale * if (dy < 0) 1.1f else 0.9f).coerceIn(1f, 6f)
                        if (scale <= 1.01f) pan = Offset.Zero
                    }
                }
            }
        }
        .desktopGestures(key = "pv" + e.ref.key, onDoubleTap = { onZoom(if (scale > 1.05f) 1 else 3) }),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(img, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "photo") { bmp ->
            if (bmp != null) Image(bmp, e.name, Modifier.fillMaxSize().padding(8.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y }, contentScale = ContentScale.Fit)
            else DeskText("Loading…", Desk.type.body, color = c.secondaryText)
        }
        Box(Modifier.align(Alignment.CenterStart).padding(8.dp)) { ToolbarButton(Glyph.ChevronLeft, { onSwipe(-1) }, description = "Previous") }
        Box(Modifier.align(Alignment.CenterEnd).padding(8.dp)) { ToolbarButton(Glyph.ChevronRight, { onSwipe(1) }, description = "Next") }
        Column(Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            DeskText("${img?.width ?: 0} × ${img?.height ?: 0} · ${(scale * 100).toInt()}%", Desk.type.small, color = Color.White)
        }
    }
}
