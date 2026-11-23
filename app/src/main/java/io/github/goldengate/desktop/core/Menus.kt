package io.github.goldengate.desktop.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import io.github.goldengate.desktop.ui.Glyph

/** A keyboard shortcut. "cmd" matches either the Meta (⌘) or Ctrl key on Android keyboards. */
@Immutable
data class Shortcut(
    val key: Key,
    val cmd: Boolean = true,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val keyLabel: String? = null,
) {
    val label: String
        get() = buildString {
            if (ctrl) append("⌃")
            if (alt) append("⌥")
            if (shift) append("⇧")
            if (cmd) append("⌘")
            append(keyLabel ?: keyName(key))
        }

    companion object {
        fun keyName(key: Key): String = when (key) {
            Key.Spacebar -> "Space"
            Key.Tab -> "⇥"
            Key.Enter -> "↩"
            Key.Escape -> "⎋"
            Key.Backspace -> "⌫"
            Key.Delete -> "⌦"
            Key.DirectionLeft -> "←"
            Key.DirectionRight -> "→"
            Key.DirectionUp -> "↑"
            Key.DirectionDown -> "↓"
            Key.Comma -> ","
            Key.Period -> "."
            Key.Slash -> "/"
            Key.Equals -> "="
            Key.Minus -> "-"
            Key.F3 -> "F3"
            else -> {
                val s = key.toString() // e.g. "Key: N"
                s.substringAfter("Key: ", s).uppercase()
            }
        }
    }
}

sealed class MenuEntry {
    data class Item(
        val label: String,
        val shortcut: Shortcut? = null,
        val enabled: Boolean = true,
        val checked: Boolean = false,
        val glyph: Glyph? = null,
        val destructive: Boolean = false,
        val submenu: List<MenuEntry>? = null,
        val action: () -> Unit = {},
    ) : MenuEntry()

    data object Separator : MenuEntry()
    data class Header(val text: String) : MenuEntry()
}

@Immutable
data class MenuDef(val title: String, val entries: List<MenuEntry>, val bold: Boolean = false, val glyph: Glyph? = null)

fun MenuDef.allItems(): Sequence<MenuEntry.Item> = entries.asSequence().flatMap { e ->
    when (e) {
        is MenuEntry.Item -> sequenceOf(e) + (e.submenu?.asSequence()?.filterIsInstance<MenuEntry.Item>() ?: emptySequence())
        else -> emptySequence()
    }
}

/** Builder helpers for concise menu definitions. */
class MenuBuilder {
    val entries = mutableListOf<MenuEntry>()
    fun item(
        label: String, shortcut: Shortcut? = null, enabled: Boolean = true, checked: Boolean = false,
        glyph: Glyph? = null, destructive: Boolean = false, action: () -> Unit,
    ) {
        entries += MenuEntry.Item(label, shortcut, enabled, checked, glyph, destructive, null, action)
    }
    fun submenu(label: String, block: MenuBuilder.() -> Unit) {
        entries += MenuEntry.Item(label, submenu = MenuBuilder().apply(block).entries.toList())
    }
    fun separator() { if (entries.isNotEmpty() && entries.last() !is MenuEntry.Separator) entries += MenuEntry.Separator }
    fun header(text: String) { entries += MenuEntry.Header(text) }
}

fun menu(title: String, bold: Boolean = false, block: MenuBuilder.() -> Unit) = MenuDef(title, MenuBuilder().apply(block).entries.toList(), bold)
fun entries(block: MenuBuilder.() -> Unit): List<MenuEntry> = MenuBuilder().apply(block).entries.toList()
