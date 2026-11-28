package io.github.goldengate.desktop.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.Appearance
import io.github.goldengate.desktop.core.DockPosition
import io.github.goldengate.desktop.core.LocalAccount
import io.github.goldengate.desktop.glass.Accent
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.ui.DeskSlider
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.desktopGestures

/**
 * First-run Setup Assistant. Everything it changes applies to the live desktop immediately, and
 * it uses the same Liquid Glass surfaces as the Dock, menus and Control Center.
 */
@Composable
fun SetupAssistant(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val s = desk.settings
    val c = Desk.colors
    var step by remember { mutableIntStateOf(desk.setupStartStep.coerceIn(0, setupTitles.lastIndex)) }
    var name by remember { mutableStateOf(if (s.userName == "Guest") "" else s.userName) }
    var computer by remember { mutableStateOf(s.computerName) }
    var pin by remember { mutableStateOf(s.lockPin) }
    var email by remember { mutableStateOf(s.accountEmail) }
    var password by remember { mutableStateOf("") }
    var avatar by remember { mutableIntStateOf(s.accountAvatar) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var isDefaultHome by remember { mutableStateOf(desk.bridge?.isDefaultLauncher() == true) }
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "setupAppear",
    )
    LaunchedEffect(Unit) { shown = true }

    fun validateAccount(): Boolean {
        val e = email.trim()
        when {
            e.isEmpty() && password.isEmpty() -> { accountError = null; return true }
            e.isEmpty() -> { accountError = "Enter the email address for your account, or leave both fields empty to skip."; return false }
            !LocalAccount.isValidEmail(e) -> { accountError = "That doesn't look like an email address — try name@example.com."; return false }
            password.isEmpty() && !(s.accountCreated && e.equals(s.accountEmail, true)) -> {
                accountError = "Choose a password with at least ${LocalAccount.MIN_PASSWORD_LENGTH} characters."; return false
            }
            password.isNotEmpty() && password.length < LocalAccount.MIN_PASSWORD_LENGTH -> {
                accountError = "Use at least ${LocalAccount.MIN_PASSWORD_LENGTH} characters for your password."; return false
            }
        }
        accountError = null
        return true
    }

    fun saveIdentity() {
        val trimmedEmail = email.trim().lowercase()
        val sameEmail = s.accountCreated && trimmedEmail == s.accountEmail
        val salt = if (sameEmail && s.accountSalt.isNotBlank()) s.accountSalt else LocalAccount.newSalt()
        val hash = when {
            trimmedEmail.isEmpty() -> s.accountHash
            password.isNotEmpty() -> LocalAccount.hash(password, salt)
            sameEmail -> s.accountHash
            else -> s.accountHash
        }
        desk.updateSettings {
            it.copy(
                userName = name.trim().ifBlank { "Guest" }.take(24),
                computerName = computer.trim().ifBlank { "Android Desktop" }.take(32),
                lockPin = pin.filter { ch -> ch.isDigit() }.take(8),
                accountName = if (trimmedEmail.isEmpty()) it.accountName
                else name.trim().ifBlank { trimmedEmail.substringBefore('@') }.take(48),
                accountEmail = if (trimmedEmail.isEmpty()) it.accountEmail else trimmedEmail,
                accountSalt = if (trimmedEmail.isEmpty()) it.accountSalt else salt,
                accountHash = hash,
                accountAvatar = avatar,
            )
        }
    }

    fun finish() {
        if (!validateAccount()) { step = 2; return }
        saveIdentity()
        desk.updateSettings { it.copy(setupComplete = true) }
        desk.showSetupAssistant = false
        desk.setupStartStep = 0
        desk.play(io.github.goldengate.desktop.audio.UiSound.Open)
        desk.notify(
            AppIds.HELP, "You're all set",
            "Your desktop is ready. Open the App Store to download and install real Android apps, or drag the Dock's inner edge to resize it.",
        )
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .desktopGestures(key = "setup-scrim", onTap = {})
            .testTag("setup-assistant"),
        contentAlignment = Alignment.Center,
    ) {
        // Soft colour wash behind the glass so the refraction has something to bend.
        Box(
            Modifier.size(460.dp).align(Alignment.TopStart).padding(top = 40.dp)
                .background(Brush.radialGradient(listOf(Color(0x55FF9A3C), Color(0x00FF9A3C))), CircleShape),
        )
        Box(
            Modifier.size(420.dp).align(Alignment.BottomEnd).padding(bottom = 30.dp)
                .background(Brush.radialGradient(listOf(Color(0x554A7BFF), Color(0x004A7BFF))), CircleShape),
        )
        GlassSurface(
            Modifier.widthIn(max = 620.dp).fillMaxWidth(0.94f)
                .heightIn(max = minOf(660.dp, maxHeight * 0.94f))
                .graphicsLayer {
                    alpha = appear
                    scaleX = 0.94f + 0.06f * appear
                    scaleY = 0.94f + 0.06f * appear
                }
                .padding(vertical = 12.dp).testTag("setup-card"),
            cornerRadius = 26.dp, material = GlassMaterial.Menu, elevation = 30.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF2A65A), Color(0xFFD9567A), Color(0xFF3A2C7A)))),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.Emblem, Color.White, 32.dp, weight = 1.9f) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        DeskText("Golden Gate Desktop", Desk.type.headline.copy(fontSize = 22.sp))
                        DeskText(setupTitles[step], Desk.type.caption, color = c.secondaryText)
                    }
                    DeskText("${step + 1} / ${setupTitles.size}", Desk.type.small, color = c.tertiaryText)
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val direction = if (targetState >= initialState) 1 else -1
                            (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 6 * direction }) togetherWith
                                (fadeOut(tween(110)) + slideOutHorizontally(tween(240)) { -it / 6 * direction })
                        },
                        label = "setup-step",
                    ) { current ->
                        Column(Modifier.fillMaxWidth()) {
                            when (current) {
                                0 -> WelcomeStep()
                                1 -> AppearanceStep(desk)
                                2 -> AccountStep(
                                    name = name, onName = { name = it.take(48) },
                                    email = email, onEmail = { email = it.trim().take(120); accountError = null },
                                    password = password, onPassword = { password = it.take(64); accountError = null },
                                    avatar = avatar, onAvatar = { avatar = it },
                                    computer = computer, onComputer = { computer = it.take(32) },
                                    signedInEmail = if (s.accountCreated) s.accountEmail else "",
                                    error = accountError,
                                )
                                else -> LockAndHomeStep(
                                    pin = pin,
                                    onPinChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                                    isDefaultHome = isDefaultHome,
                                    askDefaultHome = {
                                        isDefaultHome = desk.bridge?.isDefaultLauncher() == true
                                        desk.bridge?.requestDefaultLauncher { granted ->
                                            isDefaultHome = granted || desk.bridge?.isDefaultLauncher() == true
                                            if (!granted) desk.bridge?.toastFallback("Allow Golden Gate Desktop as your Home app in Android Settings")
                                        }
                                    },
                                    openHomeSettings = { desk.bridge?.openHomeSettings() },
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeskText("Step ${step + 1}", Desk.type.small, color = c.tertiaryText)
                    Spacer(Modifier.weight(1f))
                    if (step > 0) PushButton("Back", { step-- })
                    Spacer(Modifier.width(6.dp))
                    if (step < setupTitles.lastIndex) PushButton(
                        "Continue",
                        { if (step != 2 || validateAccount()) step++ },
                        primary = true,
                    ) else PushButton("Finish", { finish() }, primary = true)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(setupTitles.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(width = if (i == step) 18.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (i == step) c.accent else c.tertiaryText.copy(alpha = 0.35f)),
                        )
                    }
                }
                DeskText(
                    "Skip for now",
                    Desk.type.small,
                    color = c.tertiaryText,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        .desktopGestures(key = "setup-skip", onTap = { finish() })
                        .testTag("setup-skip"),
                )
            }
        }
    }
}

private val setupTitles = listOf("Welcome", "Make it yours", "Your account", "Lock screen & Home")

@Composable
private fun WelcomeStep() {
    val c = Desk.colors
    DeskText("A desktop for Android", Desk.type.headline)
    DeskText(
        "Golden Gate Desktop turns your phone or tablet into a desktop-style workspace with Liquid Glass, " +
            "windows, multiple desktops, a Dock and real Android apps.",
        Desk.type.body, color = c.secondaryText, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
    )
    listOf(
        Glyph.Dock to "A glass Dock on any edge — drag its inner edge to resize it.",
        Glyph.Grid to "Windows, Spaces and Mission Control, with mouse, keyboard and controller support.",
        Glyph.Download to "The App Store opens real apps and keeps every APK you install in your library.",
        Glyph.Lock to "A Liquid Glass lock screen with an optional passcode.",
    ).forEach { (glyph, text) ->
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.controlFill), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, c.accent, 17.dp)
            }
            Spacer(Modifier.width(10.dp))
            DeskText(text, Desk.type.body, color = c.text, modifier = Modifier.widthIn(max = 460.dp))
        }
    }
}

@Composable
private fun AppearanceStep(desk: io.github.goldengate.desktop.core.DesktopController) {
    val s = desk.settings
    val c = Desk.colors
    DeskText("Appearance", Desk.type.title)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Appearance.Light to "Light", Appearance.Dark to "Dark", Appearance.Auto to "Auto").forEach { (a, label) ->
            Column(
                Modifier.desktopGestures(key = "sa-ap-$a", onTap = { desk.updateSettings { it.copy(appearance = a) } }).testTag("setup-appearance-$label"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 78.dp, height = 50.dp).clip(RoundedCornerShape(8.dp))
                        .background(when (a) { Appearance.Light -> Color(0xFFE9E9EE); Appearance.Dark -> Color(0xFF2C2C2E); Appearance.Auto -> Color(0xFF8E8E93) })
                        .border(if (s.appearance == a) 2.5.dp else 0.5.dp, if (s.appearance == a) c.accent else c.separator, RoundedCornerShape(8.dp)),
                ) {
                    Box(Modifier.padding(9.dp).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(5.dp)).background(if (a == Appearance.Dark) Color(0xFF1C1C1E) else Color.White))
                }
                DeskText(label, Desk.type.caption, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    DeskText("Accent color", Desk.type.bodyStrong)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Accent.entries.forEachIndexed { i, a ->
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (c.dark) a.dark else a.light)
                    .border(if (s.accent == i) 2.5.dp else 0.dp, if (s.accent == i) c.text.copy(alpha = 0.7f) else Color.Transparent, CircleShape)
                    .desktopGestures(key = "sa-accent-$i", onTap = { desk.updateSettings { it.copy(accent = i) } }),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    DeskText("Liquid Glass", Desk.type.bodyStrong)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        DeskText("Clear", Desk.type.small, color = c.secondaryText)
        Spacer(Modifier.width(8.dp))
        DeskSlider(s.glassTint, { v -> desk.updateSettings { it.copy(glassTint = v) } }, Modifier.width(180.dp), height = 18.dp)
        Spacer(Modifier.width(8.dp))
        DeskText("Tinted", Desk.type.small, color = c.secondaryText)
    }
    Spacer(Modifier.height(16.dp))
    DeskText("Dock", Desk.type.bodyStrong)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SegmentedControl(listOf("Left", "Bottom", "Right"), when (s.dockPosition) { DockPosition.Left -> 0; DockPosition.Bottom -> 1; DockPosition.Right -> 2 }, { i ->
            desk.updateSettings { it.copy(dockPosition = listOf(DockPosition.Left, DockPosition.Bottom, DockPosition.Right)[i]) }
        })
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        DeskText("Size", Desk.type.small, color = c.secondaryText)
        Spacer(Modifier.width(8.dp))
        DeskSlider(
            ((s.dockIconSize - 32f) / 48f).coerceIn(0f, 1f),
            { v -> desk.updateSettings { it.copy(dockIconSize = 32f + v * 48f) } },
            Modifier.width(180.dp), height = 18.dp,
        )
    }
    DeskText(
        "Tip: after setup, drag the Dock's inner edge in any position to resize it, or use System Settings ▸ Desktop & Dock.",
        Desk.type.small, color = c.tertiaryText, modifier = Modifier.padding(top = 8.dp).widthIn(max = 480.dp),
    )
}

@Composable
private fun AccountStep(
    name: String, onName: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    avatar: Int, onAvatar: (Int) -> Unit,
    computer: String, onComputer: (String) -> Unit,
    signedInEmail: String,
    error: String?,
) {
    val c = Desk.colors
    val gradient = LocalAccount.gradient(avatar)
    DeskText(if (signedInEmail.isBlank()) "Create your account" else "Your account", Desk.type.title)
    DeskText(
        if (signedInEmail.isBlank())
            "Your account personalises the lock screen and menu bar. It's created on this device only — " +
                "the password is salted and hashed, and nothing is uploaded anywhere."
        else "Signed in as $signedInEmail. Change any detail below to update your account.",
        Desk.type.caption, color = c.secondaryText,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).widthIn(max = 480.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(62.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(gradient.first), Color(gradient.second))))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DeskText(
                LocalAccount.initials(name, email),
                Desk.type.title.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            DeskText("Avatar colour", Desk.type.caption, color = c.secondaryText)
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LocalAccount.avatarGradients.forEachIndexed { i, pair ->
                    val selected = avatar == i
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(pair.first), Color(pair.second))))
                            .border(
                                if (selected) 2.dp else 0.dp,
                                if (selected) c.text.copy(alpha = 0.8f) else Color.Transparent,
                                CircleShape,
                            )
                            .desktopGestures(key = "sa-avatar-$i", onTap = { onAvatar(i) })
                            .testTag("setup-avatar-$i"),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.widthIn(max = 220.dp)) {
            DeskText("Full name", Desk.type.caption, color = c.secondaryText)
            DeskTextField(name, onName, Modifier.fillMaxWidth().testTag("setup-name"), placeholder = "Alex Rivera")
        }
        Column(Modifier.widthIn(max = 220.dp)) {
            DeskText("Computer name", Desk.type.caption, color = c.secondaryText)
            DeskTextField(computer, onComputer, Modifier.fillMaxWidth().testTag("setup-computer"), placeholder = "Android Desktop")
        }
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.widthIn(max = 300.dp)) {
        DeskText("Email", Desk.type.caption, color = c.secondaryText)
        DeskTextField(email, onEmail, Modifier.fillMaxWidth().testTag("setup-email"), placeholder = "you@example.com")
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.widthIn(max = 300.dp)) {
        DeskText("Password", Desk.type.caption, color = c.secondaryText)
        DeskTextField(
            password, onPassword, Modifier.fillMaxWidth().testTag("setup-password"),
            placeholder = if (signedInEmail.isBlank()) "At least ${LocalAccount.MIN_PASSWORD_LENGTH} characters"
            else "Leave empty to keep the current password",
            password = true,
        )
    }
    if (error != null) {
        DeskText(
            error, Desk.type.small, color = Color(0xFFFF6961),
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 480.dp).testTag("setup-account-error"),
        )
    }
    DeskText(
        "Leave the email and password empty to use the desktop as a guest. You can create an account later in System Settings.",
        Desk.type.small, color = c.tertiaryText, modifier = Modifier.padding(top = 10.dp).widthIn(max = 500.dp),
    )
}

@Composable
private fun LockAndHomeStep(
    pin: String,
    onPinChange: (String) -> Unit,
    isDefaultHome: Boolean,
    askDefaultHome: () -> Unit,
    openHomeSettings: () -> Unit,
) {
    val c = Desk.colors
    DeskText("Lock screen", Desk.type.title)
    DeskText("Optionally protect the lock screen with a 4–8 digit passcode. Leave it blank to unlock with a tap.", Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    Column(Modifier.widthIn(max = 220.dp)) {
        DeskTextField(pin, onPinChange, Modifier.fillMaxWidth().testTag("setup-pin"), placeholder = "No passcode", glyph = Glyph.Lock)
    }
    Spacer(Modifier.height(18.dp))
    DeskText("Home screen", Desk.type.title)
    DeskText(
        if (isDefaultHome) "Golden Gate Desktop is currently your default Home app."
        else "You can use Golden Gate Desktop instead of One UI Home as your Home app (default launcher). Android will ask you to confirm.",
        Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).widthIn(max = 480.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PushButton(if (isDefaultHome) "Change Home App…" else "Set as Default Home App", askDefaultHome, primary = !isDefaultHome, glyph = Glyph.House)
        PushButton("Android Home Settings…", openHomeSettings)
    }
    DeskText(
        "To go back to One UI Home later, open Android Settings ▸ Apps ▸ Default apps ▸ Home app.",
        Desk.type.small, color = c.tertiaryText, modifier = Modifier.padding(top = 8.dp).widthIn(max = 480.dp),
    )
}
