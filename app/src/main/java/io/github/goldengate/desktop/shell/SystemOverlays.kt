package io.github.goldengate.desktop.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.audio.UiSound
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.CalcEngine
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.AppIcon
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.FileIcon
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Spotlight

private data class SpotResult(val title: String, val subtitle: String, val icon: @Composable () -> Unit, val run: () -> Unit)

/** Settings panes searchable from Spotlight (id → title). */
val settingsPanes = listOf(
    "appearance" to "Appearance", "wallpaper" to "Wallpaper", "dock" to "Desktop & Dock", "display" to "Displays",
    "sound" to "Sound", "notifications" to "Notifications", "focus" to "Focus", "controlcenter" to "Control Center",
    "privacy" to "Privacy & Security", "accessibility" to "Accessibility", "keyboard" to "Keyboard", "mouse" to "Mouse & Trackpad",
    "controllers" to "Game Controllers", "battery" to "Battery", "general" to "General", "about" to "About",
)

@Composable
fun SpotlightPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    val results = remember(query, desk.fsVersion) { spotlightResults(desk, query) }
    LaunchedEffect(results.size) { selected = 0 }

    GlassSurface(modifier.widthIn(max = 640.dp).fillMaxWidth(0.92f).testTag("spotlight"), cornerRadius = 22.dp, material = GlassMaterial.Menu, elevation = 30.dp) {
        Column {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Search, c.secondaryText, 22.dp, weight = 2f)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = Desk.type.headline.copy(fontWeight = FontWeight.Normal, color = c.text, fontSize = 22.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.getOrNull(selected)?.let { desk.overlay = Overlay.None; it.run() } }),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("spotlight-field").onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(results.lastIndex.coerceAtLeast(0)); true }
                            Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                            Key.Enter, Key.NumPadEnter -> { results.getOrNull(selected)?.let { desk.overlay = Overlay.None; it.run() }; true }
                            Key.Escape -> { desk.overlay = Overlay.None; true }
                            else -> false
                        }
                    },
                    decorationBox = { inner ->
                        Box { if (query.isEmpty()) DeskText("Spotlight Search", Desk.type.headline.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp), color = c.tertiaryText); inner() }
                    },
                )
            }
            if (results.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(6.dp)) {
                    results.forEachIndexed { i, r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (i == selected) c.accent else Color.Transparent)
                                .desktopGestures(key = "sr-$query-$i", onTap = { desk.overlay = Overlay.None; r.run() })
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("spotlight-result-$i"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { r.icon() }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                DeskText(r.title, Desk.type.body, color = if (i == selected) Color.White else c.text, maxLines = 1)
                                DeskText(r.subtitle, Desk.type.small, color = if (i == selected) Color.White.copy(alpha = 0.8f) else c.secondaryText, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun spotlightResults(desk: DesktopController, q: String): List<SpotResult> {
    val t = q.trim()
    if (t.isEmpty()) return emptyList()
    val out = ArrayList<SpotResult>()
    val lower = t.lowercase()
    if (CalcEngine.looksLikeMath(t)) {
        CalcEngine.tryEvaluate(t)?.let { v ->
            val s = CalcEngine.format(v)
            out += SpotResult("= $s", "Calculator · tap to copy into Calculator", { AppIcon(AppIds.CALCULATOR, 28.dp) }) { desk.launch(AppIds.CALCULATOR, "expr:$t") }
        }
    }
    AppRegistry.apps.filter { a -> a.name.lowercase().contains(lower) || a.keywords.any { it.startsWith(lower) } }
        .sortedBy { if (it.name.lowercase().startsWith(lower)) 0 else 1 }
        .forEach { a -> out += SpotResult(a.name, "Application", { AppIcon(a.id, 28.dp) }) { desk.launch(a.id) } }
    settingsPanes.filter { it.second.lowercase().contains(lower) }.forEach { (id, title) ->
        out += SpotResult(title, "System Settings", { AppIcon(AppIds.SETTINGS, 28.dp) }) { desk.launch(AppIds.SETTINGS, id) }
    }
    if (t.length >= 2) desk.fs.search(t, 12).forEach { e ->
        out += SpotResult(e.name, desk.fs.displayPath(desk.fs.parentOf(e.ref) ?: e.ref), { FileIcon(e, 28.dp) }) { desk.openEntry(e) }
    }
    out += SpotResult("Search the web for “$t”", "Browser", { AppIcon(AppIds.BROWSER, 28.dp) }) {
        desk.launch(AppIds.BROWSER, "url:https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8"), newWindow = true)
    }
    return out
}

// ---------------------------------------------------------------- Apps launcher

@Composable
fun LauncherPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    var query by remember { mutableStateOf("") }
    val apps = AppRegistry.apps.filter { query.isBlank() || it.name.contains(query, true) || it.keywords.any { k -> k.contains(query, true) } }
    val appear = remember { Animatable(if (desk.settings.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
    BoxWithConstraints(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = appear.value; val s = 1.06f - 0.06f * appear.value; scaleX = s; scaleY = s }
            .desktopGestures(key = "launcher-bg", onTap = { desk.overlay = Overlay.None })
            .testTag("launcher"),
    ) {
        val cols = ((maxWidth.value - 80f) / 130f).toInt().coerceIn(3, 7)
        Column(Modifier.fillMaxSize().padding(top = Tokens.menuBarHeight + 24.dp, start = 40.dp, end = 40.dp, bottom = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DeskTextField(query, { query = it }, Modifier.width(280.dp).testTag("launcher-search"), placeholder = "Search", glyph = Glyph.Search, rounded = 10.dp)
            Spacer(Modifier.height(28.dp))
            LazyVerticalGrid(GridCells.Fixed(cols), Modifier.widthIn(max = (cols * 130).dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                items(apps, key = { it.id }) { a ->
                    Column(
                        Modifier.desktopGestures(
                            key = "la-${a.id}",
                            onTap = { desk.launch(a.id) },
                            onSecondary = { p ->
                                desk.showContextMenu(p, io.github.goldengate.desktop.core.entries {
                                    item("Open") { desk.launch(a.id) }
                                    val pinned = a.id in desk.settings.dockApps
                                    item(if (pinned) "Remove from Dock" else "Add to Dock") {
                                        desk.updateSettings { s -> s.copy(dockApps = if (pinned) s.dockApps - a.id else s.dockApps + a.id) }
                                    }
                                })
                            },
                        ).testTag("launcher-app-${a.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(a.id, 76.dp)
                        Spacer(Modifier.height(6.dp))
                        DeskText(a.name, Desk.type.body.copy(textAlign = TextAlign.Center, shadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), blurRadius = 4f)), color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ⌘Tab switcher

@Composable
fun AppSwitcherPanel(index: Int, modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val apps = switcherApps(desk)
    if (apps.isEmpty()) return
    val sel = index.mod(apps.size)
    GlassSurface(modifier.testTag("app-switcher"), cornerRadius = 26.dp, material = GlassMaterial.Menu, elevation = 30.dp) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.forEachIndexed { i, id ->
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (i == sel) Desk.colors.controlFillPressed else Color.Transparent)
                            .desktopGestures(key = "sw-$id", onTap = { desk.overlay = Overlay.None; activateApp(desk, id) }).padding(8.dp),
                    ) { AppIcon(id, 72.dp) }
                }
            }
            DeskText(AppRegistry.name(apps[sel]), Desk.type.bodyStrong, color = Desk.colors.text, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Running apps in most-recently-used order (the window list is ordered back-to-front). */
fun switcherApps(desk: DesktopController): List<String> = desk.wm.windows.reversed().map { it.appId }.distinct()

fun activateApp(desk: DesktopController, appId: String) {
    val wins = desk.wm.windows.filter { it.appId == appId }
    val target = wins.lastOrNull { it.space == desk.wm.currentSpace } ?: wins.lastOrNull() ?: return
    if (target.space != desk.wm.currentSpace) desk.wm.switchSpace(target.space)
    desk.wm.focus(target.id)
}

// ---------------------------------------------------------------- Force Quit

@Composable
fun ForceQuitPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    var sel by remember { mutableStateOf<String?>(null) }
    val running = desk.runningApps()
    GlassSurface(modifier.width(360.dp).testTag("force-quit"), cornerRadius = 16.dp, material = GlassMaterial.Menu, elevation = 30.dp) {
        Column(Modifier.padding(16.dp)) {
            DeskText("Force Quit Applications", Desk.type.title, color = c.text)
            DeskText("If an app doesn't respond, select its name and click Force Quit.", Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(8.dp)).background(c.contentBackground).border(0.5.dp, c.separator, RoundedCornerShape(8.dp))) {
                if (running.isEmpty()) DeskText("No apps are running.", Desk.type.body, color = c.secondaryText, modifier = Modifier.padding(12.dp))
                running.forEach { id ->
                    Row(
                        Modifier.fillMaxWidth().background(if (sel == id) c.accent else Color.Transparent)
                            .desktopGestures(key = "fq-$id", onTap = { sel = id }).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(id, 20.dp); Spacer(Modifier.width(8.dp))
                        DeskText(AppRegistry.name(id), Desk.type.body, color = if (sel == id) Color.White else c.text)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                PushButton("Cancel", { desk.overlay = Overlay.None })
                PushButton("Force Quit", {
                    sel?.let { id -> desk.wm.windows.filter { it.appId == id }.map { it.id }.forEach { desk.wm.close(it) }; if (id == AppIds.MUSIC) desk.music.pause() }
                    sel = null
                }, primary = true, enabled = sel != null)
            }
        }
    }
}

// ---------------------------------------------------------------- Mission Control (Spaces bar)

@Composable
fun MissionControlBar(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val wm = desk.wm
    Row(modifier.padding(top = Tokens.menuBarHeight + 10.dp).testTag("spaces-bar"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until wm.spaceCount) {
            val current = i == wm.currentSpace
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF2A65A), Color(0xFF3A2C7A))))
                        .border(if (current) 2.5.dp else 0.5.dp, if (current) Color.White else Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .desktopGestures(key = "space-$i", onTap = { wm.switchSpace(i) }, onSecondary = { p ->
                            desk.showContextMenu(p, io.github.goldengate.desktop.core.entries {
                                item("Remove Desktop", enabled = wm.spaceCount > 1) { wm.removeSpace(i); desk.updateSettings { it.copy(spaces = wm.spaceCount) } }
                            })
                        })
                        .testTag("space-$i"),
                ) {
                    // Miniature windows.
                    wm.windowsOn(i).filter { !it.minimized }.forEach { w ->
                        val sx = 120f / wm.area.w.coerceAtLeast(1f)
                        Box(
                            Modifier.padding(start = (w.x * sx).dp.coerceAtLeast(0.dp), top = ((w.y - wm.area.y) * sx).dp.coerceAtLeast(0.dp))
                                .size((w.w * sx).dp, (w.h * sx).dp).clip(RoundedCornerShape(2.dp))
                                .background(if (Desk.colors.dark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
                        )
                    }
                }
                DeskText("Desktop ${i + 1}", Desk.type.caption, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (wm.spaceCount < 6) Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                .desktopGestures(key = "space-add", onTap = { wm.addSpace(); desk.updateSettings { it.copy(spaces = wm.spaceCount) } })
                .testTag("space-add"),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.Plus, Color.White, 20.dp, weight = 2.2f) }
    }
}

// ---------------------------------------------------------------- Dialog sheet

@Composable
fun DialogPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val d = desk.dialog ?: return
    val c = Desk.colors
    var text by remember(d) { mutableStateOf(d.input ?: "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(d) { if (d.input != null) { delay(60); runCatching { focus.requestFocus() } } }
    GlassSurface(modifier.width(300.dp).testTag("dialog"), cornerRadius = 18.dp, material = GlassMaterial.Menu, elevation = 30.dp) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            d.glyph?.let { Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.controlFill), contentAlignment = Alignment.Center) { GlyphIcon(it, c.text, 28.dp) } }
            Spacer(Modifier.height(10.dp))
            DeskText(d.title, Desk.type.bodyStrong.copy(textAlign = TextAlign.Center), color = c.text)
            if (d.message.isNotBlank()) DeskText(d.message, Desk.type.caption.copy(textAlign = TextAlign.Center), color = c.secondaryText, modifier = Modifier.padding(top = 6.dp))
            if (d.input != null) {
                Spacer(Modifier.height(10.dp))
                DeskTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("dialog-input"), onSubmit = {
                    desk.dialog = null; d.onConfirm(text)
                })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.cancel != null) PushButton(d.cancel, { desk.dialog = null }, Modifier.weight(1f).testTag("dialog-cancel"))
                PushButton(d.confirm, { desk.dialog = null; d.onConfirm(text) }, Modifier.weight(1f).testTag("dialog-confirm"), primary = true, destructive = d.destructive)
            }
        }
    }
}

// ---------------------------------------------------------------- HUD (brightness/volume)

@Composable
fun HudPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val h = desk.hud ?: return
    val c = Desk.colors
    GlassSurface(modifier.width(220.dp).testTag("hud"), cornerRadius = 18.dp, material = GlassMaterial.Regular, elevation = 18.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(h.glyph, c.text, 18.dp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c.controlFillPressed)) {
                Box(Modifier.fillMaxWidth(h.value.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(c.text))
            }
        }
    }
}

// ---------------------------------------------------------------- Lock & sleep

@Composable
fun LockScreen(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).desktopGestures(key = "lock", onTap = {}).testTag("lock-screen")) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DeskText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)), Desk.type.title.copy(fontSize = 20.sp), color = Color.White.copy(alpha = 0.9f))
            DeskText(SimpleDateFormat(if (desk.settings.clock24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)),
                Desk.type.largeTitle.copy(fontSize = 96.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp), color = Color.White.copy(alpha = 0.92f))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Person, Color.White, 34.dp)
            }
            DeskText(desk.settings.userName, Desk.type.bodyStrong, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            GlassSurface(Modifier.desktopGestures(key = "unlock", onTap = { desk.overlay = Overlay.None; desk.play(UiSound.Open) }).testTag("unlock"), cornerRadius = 16.dp, material = GlassMaterial.Clear) {
                DeskText("Click or tap to unlock", Desk.type.body, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun SleepScreen(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    Box(modifier.fillMaxSize().background(Color.Black).desktopGestures(key = "sleep", onTap = { desk.overlay = Overlay.Locked }).testTag("sleep-screen"))
}
