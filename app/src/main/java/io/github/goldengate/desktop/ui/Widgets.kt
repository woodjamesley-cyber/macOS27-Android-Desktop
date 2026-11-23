package io.github.goldengate.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.glass.Desk
import kotlin.math.roundToInt

val LocalDesktop = staticCompositionLocalOf<DesktopController> { error("No DesktopController") }

/**
 * Unified desktop gestures: tap, double tap, and "secondary click" from either a mouse right
 * button or a touch long-press. Positions passed to [onSecondary] are in root pixels.
 */
fun Modifier.desktopGestures(
    key: Any? = Unit,
    onPress: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onSecondary: ((Offset) -> Unit)? = null,
): Modifier = composed {
    val holder = remember { arrayOf(Offset.Zero) }
    val desk = LocalDesktop.current
    val press = androidx.compose.runtime.rememberUpdatedState(onPress)
    val tap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val dbl = androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
    val sec = androidx.compose.runtime.rememberUpdatedState(onSecondary)
    val hasTap = onTap != null
    val hasDbl = onDoubleTap != null
    val hasSec = onSecondary != null
    this
        .onGloballyPositioned { holder[0] = it.positionInRoot() }
        .pointerInput(key, hasSec) {
            awaitEachGesture {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.type == PointerEventType.Press) {
                    val ch = ev.changes.firstOrNull()
                    desk.input.lastPointerWasMouse = ch?.type == PointerType.Mouse
                    val s = sec.value
                    if (ev.buttons.isSecondaryPressed && s != null && ch != null) {
                        ch.consume()
                        s(holder[0] + ch.position)
                    }
                }
            }
        }
        .pointerInput(key, hasTap, hasDbl, hasSec) {
            detectTapGestures(
                onPress = { press.value?.invoke() },
                onTap = if (hasTap) { _ -> tap.value?.invoke() } else null,
                onDoubleTap = if (hasDbl) { _ -> dbl.value?.invoke() } else null,
                onLongPress = if (hasSec) { pos -> if (desk.settings.longPressSecondaryClick) sec.value?.invoke(holder[0] + pos) } else null,
            )
        }
}

@Composable
fun DeskText(
    text: String, style: TextStyle = Desk.type.body, color: Color = Color.Unspecified, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(text, modifier, style = if (color != Color.Unspecified) style.copy(color = color) else style, maxLines = maxLines, overflow = overflow)
}

@Composable
fun PushButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    enabled: Boolean = true, destructive: Boolean = false, glyph: Glyph? = null,
) {
    val c = Desk.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "btn")
    val bg = when {
        destructive && primary -> Color(0xFFFF3B30)
        primary -> c.accent
        else -> if (c.dark) Color(0x33FFFFFF) else Color(0xFFFFFFFF)
    }
    val fg = if (primary) Color.White else c.text
    Row(
        modifier
            .scale(scale)
            .shadow(if (primary || c.dark) 0.dp else 1.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(if (primary) Brush.verticalGradient(listOf(bg.copy(alpha = 0.92f), bg)) else SolidColor(bg))
            .then(if (!primary) Modifier.border(0.5.dp, c.separator, RoundedCornerShape(7.dp)) else Modifier)
            .clickable(src, null, enabled = enabled, onClick = onClick)
            .heightIn(min = 26.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) { GlyphIcon(glyph, fg, 13.dp); Spacer(Modifier.width(5.dp)) }
        DeskText(text, Desk.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = if (enabled) fg else fg.copy(alpha = 0.4f), maxLines = 1)
    }
}

/** Glass capsule that groups toolbar buttons (macOS 27 "uniform toolbars"). */
@Composable
fun ToolbarGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = Desk.colors
    Row(
        modifier
            .height(32.dp)
            .shadow(if (c.dark) 0.dp else 1.5.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.dark) Color(0x2EFFFFFF) else Color(0xE6FFFFFF))
            .border(0.5.dp, if (c.dark) Color(0x33FFFFFF) else Color(0x14000000), RoundedCornerShape(16.dp))
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ToolbarButton(glyph: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, description: String? = null) {
    val c = Desk.colors
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        when { active -> c.accent.copy(alpha = 0.18f); pressed -> c.controlFillPressed; hovered -> c.controlFill; else -> Color.Transparent }, label = "tb",
    )
    Box(
        modifier
            .size(width = 34.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .hoverable(src)
            .clickable(src, null, enabled = enabled, onClick = onClick)
            .semanticsLabel(description ?: glyph.name),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (!enabled) c.tertiaryText else if (active) c.accent else c.text.copy(alpha = 0.85f), 16.dp)
    }
}

fun Modifier.semanticsLabel(label: String): Modifier = this.semantics { contentDescription = label }

@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Desk.colors
    Row(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.controlFill)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .then(if (sel) Modifier.shadow(1.dp, RoundedCornerShape(6.dp)) else Modifier)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (sel) (if (c.dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { DeskText(o, Desk.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1) }
        }
    }
}

@Composable
fun DeskSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Desk.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "sw")
    val track by animateColorAsState(if (checked) c.accent else c.controlFillPressed, label = "swc")
    Box(
        modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset((t * 16.dp.toPx()).roundToInt(), 0) }
                .size(18.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF2F2F5)))),
        )
    }
}

/** Glass-style slider with an optional leading glyph rendered inside the track (Control Center style). */
@Composable
fun DeskSlider(
    value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null,
    height: Dp = 22.dp, onChangeFinished: (() -> Unit)? = null, fill: Color? = null, steps: Int = 0,
) {
    val c = Desk.colors
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(1f) }
    fun quantize(v: Float) = if (steps > 0) (Math.round(v * steps) / steps.toFloat()) else v
    val onChangeState = androidx.compose.runtime.rememberUpdatedState(onChange)
    val onFinishedState = androidx.compose.runtime.rememberUpdatedState(onChangeFinished)
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(c.controlFillPressed)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { onChangeState.value(quantize((it.x / widthPx).coerceIn(0f, 1f))); onFinishedState.value?.invoke() }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onFinishedState.value?.invoke() }) { change, _ ->
                    change.consume()
                    onChangeState.value(quantize((change.position.x / widthPx).coerceIn(0f, 1f)))
                }
            },
    ) {
        val v = value.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(density) { (widthPx * v).toDp() }.coerceAtLeast(height))
                .clip(RoundedCornerShape(height / 2))
                .background(fill ?: if (glyph != null) Color.White.copy(alpha = if (c.dark) 0.92f else 1f) else c.accent),
        )
        if (glyph != null) {
            GlyphIcon(glyph, if (v > 0.12f || !c.dark) Color(0xFF6E6E73) else Color.White, (height.value * 0.62f).dp, Modifier.align(Alignment.CenterStart).padding(start = (height.value * 0.22f).dp))
        }
    }
}

@Composable
fun DeskTextField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    glyph: Glyph? = null, singleLine: Boolean = true, onSubmit: (() -> Unit)? = null, style: TextStyle = Desk.type.body,
    rounded: Dp = 7.dp,
) {
    val c = Desk.colors
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = style.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Go else ImeAction.Default),
        keyboardActions = KeyboardActions(onGo = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(rounded))
                    .background(if (c.dark) Color(0x24FFFFFF) else Color(0xFFFFFFFF))
                    .border(0.5.dp, c.separator, RoundedCornerShape(rounded))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (glyph != null) { GlyphIcon(glyph, c.secondaryText, 13.dp); Spacer(Modifier.width(6.dp)) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) DeskText(placeholder, style, color = c.tertiaryText, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
fun SidebarHeader(text: String) {
    DeskText(text, Desk.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Desk.colors.tertiaryText,
        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp))
}

/** Sidebar row. macOS 27 brought back colored sidebar icons. */
@Composable
fun SidebarItem(
    label: String, glyph: Glyph, tint: Color, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, badge: String? = null, onSecondary: ((Offset) -> Unit)? = null, filledIcon: Boolean = false,
) {
    val c = Desk.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) c.text.copy(alpha = if (c.dark) 0.14f else 0.09f) else Color.Transparent)
            .desktopGestures(label, onTap = onClick, onSecondary = onSecondary)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filledIcon) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.85f), tint))), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, Color.White, 13.dp, weight = 2f)
            }
        } else GlyphIcon(glyph, tint, 16.dp)
        Spacer(Modifier.width(8.dp))
        DeskText(label, Desk.type.body, maxLines = 1, modifier = Modifier.weight(1f))
        if (badge != null) DeskText(badge, Desk.type.caption, color = c.secondaryText)
    }
}

@Composable
fun HSeparator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Desk.colors.separator))
}

@Composable
fun VSeparator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(0.5.dp).background(Desk.colors.separator))
}

/** Grouped settings-style card ("inset grouped" form). */
@Composable
fun FormSection(title: String? = null, modifier: Modifier = Modifier, footer: String? = null, content: @Composable () -> Unit) {
    val c = Desk.colors
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (title != null) DeskText(title, Desk.type.bodyStrong, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (c.dark) Color(0x14FFFFFF) else Color(0xB3FFFFFF))
                .border(0.5.dp, c.separator, RoundedCornerShape(10.dp)),
        ) { content() }
        if (footer != null) DeskText(footer, Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(start = 4.dp, top = 5.dp, end = 4.dp))
    }
}

@Composable
fun FormRow(label: String, modifier: Modifier = Modifier, detail: String? = null, divider: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DeskText(label, Desk.type.body)
                if (detail != null) DeskText(detail, Desk.type.caption, color = Desk.colors.secondaryText)
            }
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (divider) HSeparator(Modifier.padding(start = 12.dp))
    }
}

@Composable
fun EmptyState(glyph: Glyph, title: String, message: String, modifier: Modifier = Modifier) {
    val c = Desk.colors
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphIcon(glyph, c.tertiaryText, 40.dp)
        Spacer(Modifier.height(10.dp))
        DeskText(title, Desk.type.title, color = c.secondaryText)
        Spacer(Modifier.height(4.dp))
        DeskText(message, Desk.type.caption, color = c.tertiaryText, modifier = Modifier.widthIn(max = 320.dp))
    }
}

/** Utility: subtle rounded selection background drawn behind content. */
fun Modifier.selectionBackground(selected: Boolean, color: Color, radius: Dp = 6.dp) = this.drawBehind {
    if (selected) drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx()))
}

@Composable
fun ResponsiveRow(minWidth: Dp, modifier: Modifier = Modifier, wide: @Composable () -> Unit, narrow: @Composable () -> Unit) {
    BoxWithConstraints(modifier) { if (maxWidth >= minWidth) wide() else narrow() }
}
