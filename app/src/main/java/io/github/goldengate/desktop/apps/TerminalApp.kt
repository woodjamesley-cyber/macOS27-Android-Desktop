package io.github.goldengate.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.AndroidShell
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppRegistry
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.ShellHost
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.launch
import java.io.File

private data class TermLine(val text: String, val kind: Int) // 0 output, 1 prompt+command, 2 error

@Composable
fun TerminalApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val scope = rememberCoroutineScope()
    val shell = remember {
        AndroidShell(desk.fs.home, object : ShellHost {
            override val userName get() = desk.settings.userName
            override val hostName get() = desk.settings.computerName
            override fun open(target: File): Boolean { desk.openEntry(desk.fs.localEntry(target)); return true }
            override fun launchApp(name: String): Boolean {
                val app = AppRegistry.apps.firstOrNull { it.name.equals(name, true) || it.id.equals(name, true) } ?: return false
                desk.launch(app.id); return true
            }
            override fun notify(title: String, body: String) = desk.notify(AppIds.TERMINAL, title, body)
            override fun processes(): List<String> = listOf("launchd", "WindowServer", "Dock", "SystemUIServer") + desk.wm.windows.map { AppRegistry.name(it.appId) } + "sh"
            override fun changed() = desk.refreshFs()
        })
    }
    val lines = remember {
        mutableStateListOf(
            TermLine("Golden Gate Terminal — Android /system/bin/sh", 0),
            TermLine("Type 'help' for commands, or try: ls · getprop ro.product.model · df -h · uname -a", 0),
        )
    }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var histIdx by remember { mutableIntStateOf(-1) }
    var fontSize by remember { mutableStateOf(13f) }
    var running by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val green = Color(0xFF32D74B)
    val prompt = remember(tick) { shell.prompt() }

    LaunchedEffect(Unit) { win.title = "${desk.settings.userName.lowercase()} — sh" }
    LaunchedEffect(lines.size, running) { if (lines.isNotEmpty()) list.scrollToItem(lines.size) }

    fun submit() {
        if (running) return
        val cmd = field.text
        if (cmd.isBlank()) { field = TextFieldValue(""); return }
        lines += TermLine(prompt + cmd, 1)
        field = TextFieldValue("")
        histIdx = -1
        running = true
        scope.launch {
            val r = shell.run(cmd)
            when {
                r.clear -> lines.clear()
                r.output.isNotEmpty() -> r.output.split('\n').forEach { line ->
                    lines += TermLine(line, if (line.contains("not found") || line.contains("No such") || line.contains("denied")) 2 else 0)
                }
            }
            running = false
            tick++
            if (r.exit) desk.close(win.id)
        }
    }

    WindowMenus(win, listOf(
        menu("Shell") {
            item("New Window", Shortcut(Key.N)) { desk.launch(AppIds.TERMINAL, newWindow = true) }
            item("Clear Scrollback", Shortcut(Key.K)) { lines.clear() }
            separator()
            item("Close Window", Shortcut(Key.W)) { desk.close(win.id) }
        },
        menu("View") {
            item("Bigger", Shortcut(Key.Equals, keyLabel = "+")) { fontSize = (fontSize + 1).coerceAtMost(24f) }
            item("Smaller", Shortcut(Key.Minus)) { fontSize = (fontSize - 1).coerceAtLeast(9f) }
            item("Default Font Size", Shortcut(Key.Zero)) { fontSize = 13f }
        },
    ))

    val mono = Desk.type.mono.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.35f).sp)
    WindowScaffold(win.title, contentBackground = Color(0xF21C1C1E), darkToolbar = true) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF21C1C1E))
                .desktopGestures(key = "term", onTap = { runCatching { focus.requestFocus() } }).testTag("terminal"),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), state = list) {
                itemsIndexed(lines) { _, l ->
                    DeskText(l.text, mono, color = when (l.kind) { 1 -> Color.White; 2 -> Color(0xFFFF6961); else -> Color(0xFFE5E5EA) })
                }
                if (running) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            DeskText("running…", mono, color = Color(0xFF8E8E93))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        DeskText(prompt, mono, color = green)
                        BasicTextField(
                            field, { field = it },
                            Modifier.fillMaxWidth().focusRequester(focus).testTag("terminal-input").onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Enter, Key.NumPadEnter -> { submit(); true }
                                    Key.DirectionUp -> {
                                        if (shell.history.isNotEmpty()) {
                                            histIdx = if (histIdx < 0) shell.history.lastIndex else (histIdx - 1).coerceAtLeast(0)
                                            val t = shell.history[histIdx]; field = TextFieldValue(t, TextRange(t.length))
                                        }; true
                                    }
                                    Key.DirectionDown -> {
                                        if (histIdx >= 0) {
                                            histIdx++
                                            val t = if (histIdx > shell.history.lastIndex) { histIdx = -1; "" } else shell.history[histIdx]
                                            field = TextFieldValue(t, TextRange(t.length))
                                        }; true
                                    }
                                    Key.Tab -> { val t = shell.complete(field.text); field = TextFieldValue(t, TextRange(t.length)); true }
                                    else -> false
                                }
                            },
                            textStyle = mono.copy(color = Color.White),
                            cursorBrush = SolidColor(Color(0xFFBFBFBF)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
