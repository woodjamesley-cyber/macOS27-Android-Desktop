package io.github.goldengate.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.MenuDef
import io.github.goldengate.desktop.core.NetKind
import io.github.goldengate.desktop.core.OpenMenu
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.SystemPanel
import io.github.goldengate.desktop.core.entries
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The menu bar. Like macOS 26/27 it is fully transparent by default (items float over the
 * wallpaper) with an optional glass background. Text color adapts to wallpaper brightness.
 */
@Composable
fun MenuBar(onDarkWallpaper: Boolean, modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val s = desk.settings
    val fg = if (onDarkWallpaper || s.menuBarBackground && Desk.colors.dark) Color.White else Color(0xE6000000)
    val menus = menuBarMenus(desk)
    val sys = systemMenu(desk)
    val all = listOf(sys) + menus
    val openIndex = desk.openMenu?.fromMenuBar ?: -1

    Box(modifier.fillMaxWidth().height(Tokens.menuBarHeight).testTag("menubar")) {
        if (s.menuBarBackground) GlassSurface(Modifier.fillMaxWidth().fillMaxHeight(), cornerRadius = 0.dp, material = GlassMaterial.Bar, drawRim = false)
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
            val compact = maxWidth < 620.dp
            Row(Modifier.fillMaxHeight().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val visible = if (compact) all.take(2) else all
                visible.forEachIndexed { i, m -> MenuTitle(m, i, openIndex == i, fg, desk, all) }
                if (compact && all.size > 2) {
                    // Overflow: collapse remaining menus into a chevron menu on narrow screens.
                    MenuTitle(MenuDef("•••", entries {
                        all.drop(2).forEach { m -> submenu(m.title) { m.entries.forEach { entries.add(it) } } }
                    }), 99, openIndex == 99, fg, desk, all)
                }
                Spacer(Modifier.weight(1f))
                StatusItems(fg, desk, compact)
            }
        }
    }
}

private val textShadow = Shadow(Color(0x40000000), Offset(0f, 0.5f), 2f)

@Composable
private fun MenuTitle(m: MenuDef, index: Int, open: Boolean, fg: Color, desk: DesktopController, all: List<MenuDef>) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var pos by remember { mutableStateOf(Offset.Zero) }
    var h by remember { mutableStateOf(0) }
    // macOS: while one menu is open, hovering another title switches to it.
    LaunchedEffect(hovered) {
        if (hovered && desk.openMenu?.fromMenuBar != null && desk.openMenu!!.fromMenuBar >= 0 && desk.openMenu!!.fromMenuBar != index) {
            desk.openMenu = OpenMenu("mb$index", Offset(pos.x, pos.y + h + 2f), m.entries, index)
        }
    }
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { pos = it.positionInRoot(); h = it.size.height }
            .clip(RoundedCornerShape(6.dp))
            .background(if (open) fg.copy(alpha = 0.18f) else Color.Transparent)
            .hoverable(src)
            .desktopGestures(key = "mb$index-${m.title}", onTap = {
                desk.showMenu(OpenMenu("mb$index", Offset(pos.x, pos.y + h + 2f), m.entries, index))
            })
            .padding(horizontal = if (m.glyph != null) 10.dp else 8.dp)
            .testTag("menubar-${if (m.glyph != null) "system" else m.title}"),
        contentAlignment = Alignment.Center,
    ) {
        if (m.glyph != null) GlyphIcon(m.glyph, fg, 15.dp, weight = 1.9f)
        else DeskText(m.title, Desk.type.menu.copy(fontWeight = if (m.bold) FontWeight.Bold else FontWeight.Medium, shadow = textShadow), color = fg, maxLines = 1)
    }
}

@Composable
private fun StatusItems(fg: Color, desk: DesktopController, compact: Boolean) {
    val s = desk.settings
    val st = desk.status
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.showSeconds) {
        while (true) { now = System.currentTimeMillis(); delay(if (s.showSeconds) 1000 else 15_000) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (desk.music.playing && !compact) StatusButton("music", fg, { desk.launch(io.github.goldengate.desktop.core.AppIds.MUSIC) }) { GlyphIcon(Glyph.Music, fg, 14.dp) }
        if (s.doNotDisturb) StatusButton("focus", fg, { desk.toggleOverlay(Overlay.ControlCenter) }) { GlyphIcon(Glyph.Moon, fg, 14.dp) }
        if (desk.input.gamepad && !compact) StatusButton("gamepad", fg, {}) { GlyphIcon(Glyph.Gamepad, fg, 15.dp) }
        StatusButton("battery", fg, { pos ->
            desk.showContextMenu(pos, entries {
                header("Battery")
                item("${st.batteryLevel}%" + if (st.charging) " — Charging" else " — On battery", enabled = false) {}
                separator()
                item("Battery Settings…") { desk.launch(io.github.goldengate.desktop.core.AppIds.SETTINGS, "battery") }
            })
        }) {
            if (s.showBatteryPercent && !compact) DeskText("${st.batteryLevel}%", Desk.type.menu.copy(shadow = textShadow), color = fg, maxLines = 1, modifier = Modifier.padding(end = 4.dp))
            BatteryGlyph(st.batteryLevel, st.charging, fg)
        }
        StatusButton("wifi", fg, { pos ->
            desk.showContextMenu(pos, entries {
                header("Network")
                item(
                    when (st.network) {
                        NetKind.Wifi -> "Connected via Wi‑Fi"; NetKind.Cellular -> "Connected via mobile data"
                        NetKind.Ethernet -> "Connected via Ethernet"; NetKind.Other -> "Connected"; NetKind.None -> "Not connected"
                    }, enabled = false,
                ) {}
                separator()
                item("Wi‑Fi Settings…") { desk.bridge?.openSystemPanel(SystemPanel.Wifi) }
                item("Internet Panel…") { desk.bridge?.openSystemPanel(SystemPanel.Internet) }
            })
        }) { GlyphIcon(if (st.network == NetKind.None) Glyph.WifiOff else Glyph.Wifi, fg, 16.dp) }
        if (!compact) StatusButton("search", fg, { desk.toggleOverlay(Overlay.Spotlight) }) { GlyphIcon(Glyph.Search, fg, 14.dp, weight = 2f) }
        StatusButton("controlcenter", fg, { desk.toggleOverlay(Overlay.ControlCenter) }, active = desk.overlay == Overlay.ControlCenter) { GlyphIcon(Glyph.ControlCenter, fg, 15.dp) }
        StatusButton("clock", fg, { desk.toggleOverlay(Overlay.NotificationCenter) }, active = desk.overlay == Overlay.NotificationCenter) {
            val pattern = buildString {
                if (!compact) append("EEE MMM d  ")
                append(if (s.clock24h) "HH:mm" else "h:mm")
                if (s.showSeconds) append(":ss")
                if (!s.clock24h) append(" a")
            }
            DeskText(SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)), Desk.type.menu.copy(shadow = textShadow), color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun StatusButton(tag: String, fg: Color, onClick: (Offset) -> Unit, active: Boolean = false, content: @Composable () -> Unit) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var h by remember { mutableStateOf(0) }
    Row(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { pos = it.positionInRoot(); h = it.size.height }
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) fg.copy(alpha = 0.18f) else Color.Transparent)
            .desktopGestures(key = tag, onTap = { onClick(Offset(pos.x, pos.y + h + 2f)) })
            .padding(horizontal = 7.dp)
            .testTag("status-$tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun BatteryGlyph(level: Int, charging: Boolean, color: Color) {
    Box(Modifier.size(width = 25.dp, height = 12.dp).drawBehind {
        val bodyW = size.width - 2.5.dp.toPx()
        drawRoundRect(color.copy(alpha = 0.45f), size = Size(bodyW, size.height), cornerRadius = CornerRadius(3.5.dp.toPx()), style = Stroke(1.dp.toPx()))
        val inset = 2.dp.toPx()
        val fillW = (bodyW - inset * 2) * (level.coerceIn(0, 100) / 100f)
        val fillColor = if (level <= 20 && !charging) Color(0xFFFF3B30) else if (charging) Color(0xFF34C759) else color
        drawRoundRect(fillColor, topLeft = Offset(inset, inset), size = Size(fillW, size.height - inset * 2), cornerRadius = CornerRadius(1.8.dp.toPx()))
        drawRoundRect(color.copy(alpha = 0.45f), topLeft = Offset(bodyW + 0.8.dp.toPx(), size.height * 0.33f), size = Size(1.5.dp.toPx(), size.height * 0.34f), cornerRadius = CornerRadius(1f))
    }, contentAlignment = Alignment.Center) {
        if (charging) GlyphIcon(Glyph.Bolt, Color.White, 10.dp)
    }
}

@Suppress("unused")
private val spacer = Modifier.width(0.dp)
