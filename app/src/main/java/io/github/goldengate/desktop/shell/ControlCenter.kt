package io.github.goldengate.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.Appearance
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.NetKind
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.SystemPanel
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassMaterial
import io.github.goldengate.desktop.glass.GlassSurface
import io.github.goldengate.desktop.glass.Tokens
import io.github.goldengate.desktop.ui.DeskSlider
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.desktopGestures

/**
 * Control Center. Android does not let ordinary apps toggle Wi‑Fi (API 29+) or Bluetooth
 * (API 33+) directly, so those tiles show the live state and open the matching system panel.
 * Brightness applies to this app's window; volume changes the real media stream volume.
 */
@Composable
fun ControlCenterPanel(modifier: Modifier = Modifier) {
    val desk = LocalDesktop.current
    val s = desk.settings
    val st = desk.status
    val c = Desk.colors
    var brightness by remember { mutableFloatStateOf(desk.bridge?.appBrightness() ?: 0.7f) }
    LaunchedEffect(Unit) { st.refreshVolume(); st.refreshBluetooth() }

    Column(
        modifier.width(330.dp).padding(top = Tokens.menuBarHeight + 6.dp, end = 10.dp)
            .verticalScroll(rememberScrollState())
            .testTag("control-center"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Connectivity module.
            Module(Modifier.weight(1f)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleRow(
                        Glyph.Wifi, "Wi‑Fi",
                        when (st.network) { NetKind.Wifi -> "Connected"; NetKind.None -> "Off / No network"; else -> "Not on Wi‑Fi" },
                        on = st.network == NetKind.Wifi, tag = "cc-wifi",
                    ) { desk.dismissAll(); desk.bridge?.openSystemPanel(SystemPanel.Wifi) }
                    ToggleRow(
                        Glyph.Bluetooth, "Bluetooth",
                        when (st.bluetoothOn) { true -> "On"; false -> "Off"; null -> if (st.hasBluetoothPermission) "Unavailable" else "Tap to allow status" },
                        on = st.bluetoothOn == true, tag = "cc-bluetooth",
                    ) {
                        if (!st.hasBluetoothPermission) desk.bridge?.requestBluetoothPermission { st.refreshBluetooth() }
                        else { desk.dismissAll(); desk.bridge?.openSystemPanel(SystemPanel.Bluetooth) }
                    }
                    ToggleRow(Glyph.Globe, "Network", "Internet panel", on = st.network != NetKind.None, tag = "cc-network") {
                        desk.dismissAll(); desk.bridge?.openSystemPanel(SystemPanel.Internet)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Module(Modifier.fillMaxWidth().height(62.dp).desktopGestures(key = "cc-focus", onTap = {
                    desk.updateSettings { it.copy(doNotDisturb = !it.doNotDisturb) }
                }).testTag("cc-focus")) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Circle(Glyph.Moon, s.doNotDisturb, Color(0xFF5E5CE6))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            DeskText("Focus", Desk.type.bodyStrong, color = c.text)
                            DeskText(if (s.doNotDisturb) "Do Not Disturb" else "Off", Desk.type.small, color = c.secondaryText)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallTile(Glyph.Sun, "Dark", s.appearance == Appearance.Dark || (s.appearance == Appearance.Auto && c.dark), "cc-dark", Modifier.weight(1f)) {
                        desk.updateSettings { it.copy(appearance = if (c.dark) Appearance.Light else Appearance.Dark) }
                    }
                    SmallTile(Glyph.Eye, "Night Shift", s.nightShift, "cc-nightshift", Modifier.weight(1f)) {
                        desk.updateSettings { it.copy(nightShift = !it.nightShift) }
                    }
                }
            }
        }
        // Display.
        Module(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                DeskText("Display", Desk.type.bodyStrong, color = c.text)
                Spacer(Modifier.height(8.dp))
                DeskSlider(brightness, { v ->
                    brightness = v
                    desk.bridge?.setAppBrightness(v)
                }, Modifier.fillMaxWidth().testTag("cc-brightness"), glyph = Glyph.Sun, height = 26.dp,
                    onChangeFinished = { desk.showHud(Glyph.Sun, brightness, "Brightness") })
                DeskText("Applies to this app's screen while it's open.", Desk.type.small, color = c.tertiaryText, modifier = Modifier.padding(top = 6.dp))
            }
        }
        // Sound.
        Module(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeskText("Sound", Desk.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    DeskText("Output…", Desk.type.caption, color = c.accent, modifier = Modifier.desktopGestures(key = "cc-out", onTap = {
                        desk.dismissAll(); desk.bridge?.openSystemPanel(SystemPanel.Volume)
                    }).padding(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                DeskSlider(st.volume, { v -> st.changeVolume(v) }, Modifier.fillMaxWidth().testTag("cc-volume"),
                    glyph = if (st.volume <= 0.01f) Glyph.SpeakerMute else Glyph.Speaker, height = 26.dp,
                    onChangeFinished = { desk.showHud(Glyph.Speaker, st.volume, "Volume") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTile(Glyph.Mirror, "Cast Screen", false, "cc-cast", Modifier.weight(1f)) { desk.dismissAll(); desk.bridge?.openSystemPanel(SystemPanel.Cast) }
            SmallTile(Glyph.Lock, "Lock Screen", false, "cc-lock", Modifier.weight(1f)) { desk.overlay = Overlay.Locked }
            SmallTile(Glyph.Gear, "Settings", false, "cc-settings", Modifier.weight(1f)) { desk.launch(AppIds.SETTINGS, "controlcenter") }
        }
        NowPlayingWidget(desk, compact = true)
    }
}

@Composable
private fun Module(modifier: Modifier, content: @Composable () -> Unit) {
    GlassSurface(modifier, cornerRadius = Tokens.controlModuleRadius, material = GlassMaterial.Regular) { content() }
}

@Composable
private fun Circle(glyph: Glyph, on: Boolean, onColor: Color = Desk.colors.accent) {
    val c = Desk.colors
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(if (on) onColor else c.controlFillPressed),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (on) Color.White else c.text, 15.dp) }
}

@Composable
private fun ToggleRow(glyph: Glyph, title: String, subtitle: String, on: Boolean, tag: String, onTap: () -> Unit) {
    val c = Desk.colors
    Row(Modifier.fillMaxWidth().desktopGestures(key = tag, onTap = onTap).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Circle(glyph, on)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            DeskText(title, Desk.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
            DeskText(subtitle, Desk.type.small, color = c.secondaryText, maxLines = 1)
        }
    }
}

@Composable
private fun SmallTile(glyph: Glyph, label: String, on: Boolean, tag: String, modifier: Modifier, onTap: () -> Unit) {
    val c = Desk.colors
    Module(modifier.height(74.dp).desktopGestures(key = tag, onTap = onTap).testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Circle(glyph, on)
            Spacer(Modifier.height(5.dp))
            DeskText(label, Desk.type.small, color = c.text, maxLines = 1)
        }
    }
}

@Suppress("unused")
private fun keepRef(d: DesktopController) = d
