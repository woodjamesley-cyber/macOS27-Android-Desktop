package io.github.goldengate.desktop.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.audio.UiSound
import io.github.goldengate.desktop.core.LocalAccount
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Liquid Glass lock screen: the desktop is blurred behind a full-screen glass pane, the clock
 * floats above it, and unlocking uses a glass keypad when a passcode is set.
 */
@Composable
fun LockScreen(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val s = desk.settings
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val requiresPin = s.lockPin.isNotEmpty()
    // An account exists but no passcode yet (e.g. the account was created before launch-locking).
    val needsPasscode = s.accountCreated && !requiresPin
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val avatar = LocalAccount.gradient(s.accountAvatar)
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.78f, stiffness = 360f),
        label = "lockAppear",
    )
    LaunchedEffect(Unit) { shown = true }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(requiresPin) { if (requiresPin) runCatching { focus.requestFocus() } }
    LaunchedEffect(wrong) {
        if (wrong) {
            repeat(3) {
                shake.animateTo(11f, tween(45))
                shake.animateTo(-11f, tween(45))
            }
            shake.animateTo(0f, tween(40))
            delay(600)
            wrong = false
        }
    }

    fun unlock() {
        desk.play(UiSound.Open)
        wrong = false
        entry = ""
        setupError = null
        desk.overlay = Overlay.None
        desk.bridge?.onUnlocked()
    }

    fun append(digit: Char) {
        if (entry.length >= s.lockPin.length) return
        wrong = false
        val next = entry + digit
        entry = next
        if (next.length == s.lockPin.length) {
            if (next == s.lockPin) unlock()
            else {
                desk.play(UiSound.Error)
                wrong = true
                entry = ""
            }
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = when (e.key) {
                    Key.Zero -> '0'
                    Key.One -> '1'
                    Key.Two -> '2'
                    Key.Three -> '3'
                    Key.Four -> '4'
                    Key.Five -> '5'
                    Key.Six -> '6'
                    Key.Seven -> '7'
                    Key.Eight -> '8'
                    Key.Nine -> '9'
                    else -> null
                }
                when {
                    !requiresPin && !needsPasscode && (e.key == Key.Enter || e.key == Key.Spacebar || e.key == Key.Escape) -> { unlock(); true }
                    digit != null && requiresPin -> { append(digit); true }
                    e.key == Key.Backspace && requiresPin -> { entry = entry.dropLast(1); true }
                    e.key == Key.Enter && requiresPin -> {
                        if (entry == s.lockPin) unlock() else { wrong = true; entry = "" }
                        true
                    }
                    else -> false
                }
            }
            .testTag("lock-screen"),
    ) {
        // Blurred desktop behind full-screen glass.
        GlassSurface(Modifier.fillMaxSize(), cornerRadius = 0.dp, material = GlassMaterial.Regular, drawRim = false) {}
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x44000000), Color(0x22000000), Color(0x66000000)))))

        val timeSize = (maxWidth.value * 0.19f).coerceIn(48f, 104f).sp
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = if (maxHeight < 500.dp) 30.dp else 64.dp)
                .graphicsLayer { alpha = appear; translationY = (1f - appear) * -26f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeskText(
                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)),
                Desk.type.title.copy(fontSize = 18.sp, letterSpacing = 0.sp),
                color = Color.White.copy(alpha = 0.92f),
            )
            DeskText(
                SimpleDateFormat(if (s.clock24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)),
                Desk.type.largeTitle.copy(fontSize = timeSize, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp),
                color = Color.White.copy(alpha = 0.95f),
            )
            if (requiresPin) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(s.lockPin.length.coerceIn(4, 8)) { i ->
                        Box(
                            Modifier.size(9.dp).clip(CircleShape)
                                .background(if (i < entry.length) Color.White else Color.White.copy(alpha = 0.32f)),
                        )
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = if (maxHeight < 500.dp) 16.dp else 40.dp)
                .graphicsLayer { alpha = appear; translationY = (1f - appear) * 30f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(avatar.first), Color(avatar.second))))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DeskText(
                    LocalAccount.initials(s.accountName.ifBlank { s.userName }, s.accountEmail),
                    Desk.type.title.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
            DeskText(
                s.accountName.ifBlank { s.userName }, Desk.type.bodyStrong, color = Color.White,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (s.accountCreated) {
                DeskText(
                    s.accountEmail, Desk.type.small, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            if (requiresPin) {
                DeskText(
                    if (wrong) "Wrong passcode — try again" else "Enter your passcode",
                    Desk.type.caption,
                    color = if (wrong) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                )
                GlassSurface(
                    Modifier.widthIn(max = 290.dp)
                        .graphicsLayer { translationX = shake.value }
                        .testTag("lock-keypad"),
                    cornerRadius = 20.dp, material = GlassMaterial.Menu, elevation = 18.dp,
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        listOf("123", "456", "789").forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                                row.forEach { d -> KeypadKey(d.toString()) { append(d) } }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                            KeypadKey("", enabled = false) {}
                            KeypadKey("0") { append('0') }
                            KeypadKey("", glyph = Glyph.Close) { entry = entry.dropLast(1) }
                        }
                    }
                }
            } else if (needsPasscode) {
                Spacer(Modifier.height(8.dp))
                GlassSurface(
                    Modifier.widthIn(max = 300.dp).testTag("lock-passcode-setup"),
                    cornerRadius = 20.dp, material = GlassMaterial.Menu, elevation = 18.dp,
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DeskText("Protect your desktop", Desk.type.bodyStrong, color = Color.White)
                        DeskText(
                            "Choose a 4–8 digit passcode for ${s.accountName.ifBlank { s.userName }}.",
                            Desk.type.small, color = Color.White.copy(alpha = 0.7f),
                        )
                        DeskTextField(
                            newPin, { newPin = it.filter(Char::isDigit).take(8) },
                            Modifier.fillMaxWidth().testTag("passcode-new"),
                            placeholder = "Passcode", glyph = Glyph.Lock, password = true,
                        )
                        DeskTextField(
                            confirmPin, { confirmPin = it.filter(Char::isDigit).take(8) },
                            Modifier.fillMaxWidth().testTag("passcode-confirm"),
                            placeholder = "Confirm passcode", glyph = Glyph.Lock, password = true,
                        )
                        setupError?.let {
                            DeskText(it, Desk.type.small, color = Color(0xFFFFB4AB))
                        }
                        PushButton("Set Passcode", {
                            when {
                                newPin.length !in 4..8 -> setupError = "Use 4–8 digits."
                                newPin != confirmPin -> setupError = "The passcodes don't match."
                                else -> {
                                    desk.updateSettings { it.copy(lockPin = newPin) }
                                    unlock()
                                }
                            }
                        }, primary = true)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                GlassSurface(
                    Modifier
                        .desktopGestures(key = "unlock", onTap = { unlock() })
                        .testTag("unlock"),
                    cornerRadius = 18.dp, material = GlassMaterial.Clear,
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Lock, Color.White, 14.dp)
                        Spacer(Modifier.size(7.dp))
                        DeskText("Tap to unlock", Desk.type.body, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DeskText(
                "Golden Gate Desktop · ${s.computerName}",
                Desk.type.small, color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun KeypadKey(label: String, glyph: Glyph? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 78.dp, height = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.05f))
            .then(if (enabled) Modifier.desktopGestures(key = "key-$label", onTap = onClick) else Modifier)
            .testTag("lock-key-${label.ifEmpty { glyph?.name ?: "blank" }}"),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) GlyphIcon(glyph, Color.White.copy(alpha = 0.9f), 16.dp, weight = 2f)
        else if (label.isNotEmpty()) DeskText(label, Desk.type.title, color = Color.White)
    }
}
