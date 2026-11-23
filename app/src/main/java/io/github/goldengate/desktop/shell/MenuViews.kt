package io.github.goldengate.desktop.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.audio.UiSound
import io.github.goldengate.desktop.core.MenuEntry
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import kotlin.math.roundToInt

/**
 * A dropdown/context menu panel positioned in root pixels. It flips/clamps to stay on screen,
 * opens with a short scale+fade from its anchor, and supports nested submenus.
 */
@Composable
fun MenuPanel(
    entries: List<MenuEntry>,
    anchor: Offset,
    rootSize: IntSize,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    level: Int = 0,
) {
    val desk = LocalDesktop.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val appear = remember { Animatable(if (desk.settings.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
    var openSub by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val margin = with(density) { 6.dp.toPx() }
    val x = when {
        size.width == 0 -> anchor.x
        anchor.x + size.width > rootSize.width - margin -> (if (level > 0) anchor.x - size.width - with(density) { 236.dp.toPx() } else rootSize.width - size.width - margin).coerceAtLeast(margin)
        else -> anchor.x
    }
    val y = when {
        size.height == 0 -> anchor.y
        anchor.y + size.height > rootSize.height - margin -> (rootSize.height - size.height - margin).coerceAtLeast(margin)
        else -> anchor.y
    }
    Box(
        modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { size = it }
            .graphicsLayer {
                alpha = appear.value
                val s = 0.96f + 0.04f * appear.value
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .testTag("menu-panel-$level"),
    ) {
        GlassSurface(
            Modifier.widthIn(min = 230.dp, max = 340.dp).width(IntrinsicSize.Max),
            cornerRadius = Tokens.menuRadius, material = GlassMaterial.Menu, elevation = 18.dp,
        ) {
            Column(Modifier.heightIn(max = with(density) { (rootSize.height * 0.85f).toDp() }).verticalScroll(rememberScrollState()).padding(5.dp)) {
                entries.forEachIndexed { i, e ->
                    when (e) {
                        is MenuEntry.Separator -> Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp).height(0.5.dp).background(Desk.colors.separator))
                        is MenuEntry.Header -> DeskText(e.text, Desk.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Desk.colors.secondaryText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        is MenuEntry.Item -> MenuRow(e, highlightedSub = openSub?.first == i, onHoverSub = { pos -> openSub = if (e.submenu != null) i to pos else null }) {
                            if (e.submenu != null) return@MenuRow
                            desk.play(UiSound.Click)
                            onDismiss()
                            e.action()
                        }
                    }
                }
            }
        }
    }
    openSub?.let { (i, pos) ->
        val sub = (entries.getOrNull(i) as? MenuEntry.Item)?.submenu
        if (sub != null) MenuPanel(sub, Offset(pos.x, pos.y - with(density) { 5.dp.toPx() }), rootSize, onDismiss, level = level + 1)
    }
}

@Composable
private fun MenuRow(item: MenuEntry.Item, highlightedSub: Boolean, onHoverSub: (Offset) -> Unit, onClick: () -> Unit) {
    val c = Desk.colors
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var rowPos by remember { mutableStateOf(Offset.Zero) }
    var rowW by remember { mutableStateOf(0) }
    val hl = (hovered || highlightedSub) && item.enabled
    LaunchedEffect(hovered) { if (hovered) onHoverSub(Offset(rowPos.x + rowW, rowPos.y)) }
    val fg = when {
        !item.enabled -> c.tertiaryText
        hl -> Color.White
        item.destructive -> Color(0xFFFF453A)
        else -> c.text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowPos = it.positionInRoot(); rowW = it.size.width }
            .clip(RoundedCornerShape(Tokens.menuItemRadius))
            .background(if (hl) c.accent else Color.Transparent)
            .hoverable(src)
            .clickable(enabled = item.enabled, interactionSource = src, indication = null) {
                if (item.submenu != null) onHoverSub(Offset(rowPos.x + rowW, rowPos.y)) else onClick()
            }
            .heightIn(min = 24.dp)
            .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
            .testTag("menu-item-${item.label}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (item.checked) GlyphIcon(Glyph.Check, fg, 11.dp, weight = 2.4f)
        }
        Spacer(Modifier.width(4.dp))
        if (item.glyph != null) { GlyphIcon(item.glyph, fg, 14.dp); Spacer(Modifier.width(7.dp)) }
        DeskText(item.label, Desk.type.menu.copy(fontWeight = FontWeight.Normal), color = fg, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(18.dp))
        if (item.shortcut != null) DeskText(item.shortcut.label, Desk.type.menu.copy(fontWeight = FontWeight.Normal), color = if (hl) Color.White.copy(alpha = 0.85f) else c.tertiaryText, maxLines = 1)
        if (item.submenu != null) GlyphIcon(Glyph.ChevronRight, fg, 10.dp, weight = 2.2f)
    }
}

@Suppress("unused")
private val keep = Modifier.size(0.dp)
