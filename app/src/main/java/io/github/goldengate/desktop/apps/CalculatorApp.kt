package io.github.goldengate.desktop.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.CalcEngine
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.LocalDesktop

/** Pure calculator state machine; the UI feeds keys into it. */
class CalculatorState {
    var expr by mutableStateOf("")
    var result by mutableStateOf("0")
    var justEvaluated by mutableStateOf(false)
    var degrees by mutableStateOf(true)
    val tape = mutableStateListOf<String>()

    private val ops = setOf('+', '−', '×', '÷', '^')

    fun preview(): String? = if (expr.isEmpty()) null else CalcEngine.tryEvaluate(expr, degrees)?.let { CalcEngine.format(it) }

    fun input(k: String) {
        when (k) {
            "AC" -> { expr = ""; result = "0"; justEvaluated = false }
            "⌫" -> { expr = expr.dropLast(1); justEvaluated = false }
            "=" -> evaluate()
            "±" -> {
                // negate the last number
                val m = Regex("(-?)([0-9.]+)$").find(expr)
                expr = if (m != null) expr.substring(0, m.range.first) + (if (m.groupValues[1] == "-") "" else "-") + m.groupValues[2]
                else if (expr.isEmpty() && result != "0") "-$result" else expr
            }
            "%" -> append("%")
            else -> {
                if (justEvaluated) {
                    expr = if (k.length == 1 && k[0] in ops) result.replace(",", "") else ""
                    justEvaluated = false
                }
                if (k.length == 1 && k[0] in ops && expr.isNotEmpty() && expr.last() in ops) expr = expr.dropLast(1)
                append(k)
            }
        }
    }

    private fun append(s: String) { if (expr.length < 120) expr += s }

    fun evaluate() {
        if (expr.isEmpty()) return
        val v = CalcEngine.tryEvaluate(expr, degrees)
        if (v == null || v.isNaN()) { result = "Error"; return }
        result = CalcEngine.format(v)
        tape.add(0, "$expr = $result")
        justEvaluated = true
    }
}

private data class Key2(val label: String, val input: String = label, val kind: Int = 0, val wide: Boolean = false) // 0 digit, 1 fn, 2 op

@Composable
fun CalculatorApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val state = remember { CalculatorState() }
    val arg = rememberWindowArgument(win)
    var scientific by remember { mutableStateOf(false) }
    var showTape by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(arg) {
        if (arg != null && arg.startsWith("expr:")) { state.expr = arg.removePrefix("expr:"); state.evaluate() }
    }
    LaunchedEffect(Unit) { win.title = "Calculator"; runCatching { focus.requestFocus() } }

    WindowMenus(win, listOf(
        menu("Edit") {
            item("Copy", Shortcut(Key.C)) { clipboard.setText(AnnotatedString(state.result)) }
            item("Paste", Shortcut(Key.V)) { clipboard.getText()?.text?.filter { it.isDigit() || it in ".+-*/()^" }?.let { state.expr += it.replace('*', '×').replace('/', '÷').replace('-', '−') } }
        },
        menu("View") {
            item("Basic", Shortcut(Key.One), checked = !scientific) { scientific = false; win.w = 260f; win.h = 420f }
            item("Scientific", Shortcut(Key.Two), checked = scientific) { scientific = true; win.w = 560f; win.h = 420f }
            separator()
            item("Show Paper Tape", Shortcut(Key.T), checked = showTape) { showTape = !showTape }
            item(if (state.degrees) "Use Radians" else "Use Degrees") { state.degrees = !state.degrees }
        },
    ))

    val basic = listOf(
        listOf(Key2(if (state.expr.isEmpty()) "AC" else "⌫", kind = 1), Key2("±", kind = 1), Key2("%", kind = 1), Key2("÷", kind = 2)),
        listOf(Key2("7"), Key2("8"), Key2("9"), Key2("×", kind = 2)),
        listOf(Key2("4"), Key2("5"), Key2("6"), Key2("−", kind = 2)),
        listOf(Key2("1"), Key2("2"), Key2("3"), Key2("+", kind = 2)),
        listOf(Key2("0", wide = true), Key2("."), Key2("=", kind = 2)),
    )
    val sci = listOf(
        listOf(Key2("("), Key2(")"), Key2("x²", "^2"), Key2("xʸ", "^")),
        listOf(Key2("sin", "sin("), Key2("cos", "cos("), Key2("tan", "tan("), Key2("√", "sqrt(")),
        listOf(Key2("ln", "ln("), Key2("log", "log("), Key2("x!", "!"), Key2("1/x", "1÷")),
        listOf(Key2("π", "π"), Key2("e", "e"), Key2("eˣ", "e^"), Key2(if (state.degrees) "Deg" else "Rad", "DEG")),
        listOf(Key2("AC"), Key2("EE", "×10^"), Key2("|x|", "abs("), Key2("Rand", "RAND")),
    )

    WindowScaffold("Calculator", showTitle = false, contentBackground = Color(0xF0202022), darkToolbar = true) {
        Row(
            Modifier.fillMaxSize().background(Color(0xF0202022)).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val ch = e.utf16CodePoint.toChar()
                when {
                    e.key == Key.Enter || e.key == Key.NumPadEnter || ch == '=' -> { state.input("="); true }
                    e.key == Key.Backspace -> { state.input("⌫"); true }
                    e.key == Key.Escape -> { state.input("AC"); true }
                    ch.isDigit() || ch == '.' || ch == '(' || ch == ')' -> { state.input(ch.toString()); true }
                    ch == '+' -> { state.input("+"); true }
                    ch == '-' -> { state.input("−"); true }
                    ch == '*' || ch == 'x' -> { state.input("×"); true }
                    ch == '/' -> { state.input("÷"); true }
                    ch == '^' -> { state.input("^"); true }
                    ch == '%' -> { state.input("%"); true }
                    else -> false
                }
            }.testTag("calculator"),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                // Display
                Column(Modifier.fillMaxWidth().weight(0.34f), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                    DeskText(state.expr.ifEmpty { " " }, Desk.type.body.copy(fontSize = 15.sp, textAlign = TextAlign.End), color = Color(0xFF9A9AA0), maxLines = 2)
                    val shown = if (state.justEvaluated || state.expr.isEmpty()) state.result else state.preview() ?: state.result
                    val size = when { shown.length > 14 -> 26.sp; shown.length > 9 -> 34.sp; else -> 46.sp }
                    DeskText(shown, Desk.type.largeTitle.copy(fontSize = size, fontWeight = FontWeight.Light, textAlign = TextAlign.End), color = if (state.justEvaluated || state.expr.isEmpty()) Color.White else Color(0xFFCFCFD4), maxLines = 1, modifier = Modifier.testTag("calc-display"))
                }
                Row(Modifier.fillMaxWidth().weight(0.66f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scientific) {
                        KeyGrid(sci, Modifier.weight(1f)) { k ->
                            when (k) {
                                "DEG" -> state.degrees = !state.degrees
                                "RAND" -> state.input(CalcEngine.format(Math.random(), 8))
                                else -> state.input(k)
                            }
                        }
                    }
                    KeyGrid(basic, Modifier.weight(1f)) { state.input(it) }
                }
            }
            if (showTape) {
                Column(Modifier.width(180.dp).fillMaxHeight().background(Color(0x22FFFFFF)).padding(8.dp).verticalScroll(rememberScrollState())) {
                    DeskText("Paper Tape", Desk.type.bodyStrong, color = Color.White)
                    state.tape.forEach { DeskText(it, Desk.type.mono, color = Color(0xFFDDDDDD), modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun KeyGrid(rows: List<List<Key2>>, modifier: Modifier, onKey: (String) -> Unit) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> CalcKey(k, onKey) }
            }
        }
    }
}

@Composable
private fun RowScope.CalcKey(k: Key2, onKey: (String) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "key")
    val bg = when (k.kind) { 2 -> Color(0xFFFF9F0A); 1 -> Color(0xFF5C5C60); else -> Color(0xFF3A3A3D) }
    Box(
        Modifier.weight(if (k.wide) 2.08f else 1f).fillMaxHeight().scale(scale).clip(if (k.wide) RoundedCornerShape(50) else CircleShape)
            .background(if (pressed) bg.copy(alpha = 0.7f) else bg)
            .clickable(src, null) { onKey(k.input) }.testTag("calc-key-${k.label}"),
        contentAlignment = if (k.wide) Alignment.CenterStart else Alignment.Center,
    ) {
        DeskText(k.label, Desk.type.title.copy(fontSize = if (k.label.length > 2) 14.sp else 22.sp), color = Color.White, modifier = if (k.wide) Modifier.padding(start = 22.dp) else Modifier)
    }
}
