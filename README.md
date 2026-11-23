# Golden Gate Desktop — a macOS 27–inspired desktop for Android

[![Android CI](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/actions/workflows/android.yml/badge.svg)](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/actions/workflows/android.yml)

An interactive, desktop-style environment for Android phones, tablets and foldables, inspired by the
Liquid Glass design of macOS 27 "Golden Gate". Built with Kotlin and Jetpack Compose, with
GPU-accelerated glass (live blur, vibrancy and an AGSL refraction shader).

> **Unofficial project.** Not affiliated with, endorsed by, or connected to Apple Inc. macOS and
> Liquid Glass are trademarks of Apple Inc. This app contains no Apple code, icons, wallpapers,
> fonts, or sounds — everything is original and drawn or synthesized in code.

**[Download the latest APK](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/releases/latest/download/GoldenGateDesktop.apk)** · [All releases](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/releases) · [CI builds](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/actions)

## Screenshots

Captured automatically by the CI screenshot tour on API 34 emulators (Pixel C tablet and Pixel 6 phone, software GPU). Glass effects may look slightly different on devices with a hardware GPU.

<table>
<tr><td width="50%"><img src="docs/screenshots/tablet-01-desktop-banners.jpg" alt="Desktop, glass menu bar, Dock and notification banners"><br><sub>Desktop, glass menu bar, Dock and notification banners</sub></td><td width="50%"><img src="docs/screenshots/tablet-02-finder-notes-snapped.jpg" alt="Finder and Notes snapped side by side"><br><sub>Finder and Notes snapped side by side</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-03-control-center.jpg" alt="Control Center"><br><sub>Control Center</sub></td><td width="50%"><img src="docs/screenshots/tablet-04-notification-center.jpg" alt="Notification Center with widgets"><br><sub>Notification Center with widgets</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-05-system-menu.jpg" alt="System menu"><br><sub>System menu</sub></td><td width="50%"><img src="docs/screenshots/tablet-06-mission-control.jpg" alt="Mission Control with Spaces"><br><sub>Mission Control with Spaces</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-07-terminal-calculator.jpg" alt="Terminal running sandboxed commands"><br><sub>Terminal running sandboxed commands</sub></td><td width="50%"><img src="docs/screenshots/tablet-09-settings-dark.jpg" alt="System Settings in Dark appearance"><br><sub>System Settings in Dark appearance</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-10-photos.jpg" alt="Photos"><br><sub>Photos</sub></td><td width="50%"><img src="docs/screenshots/tablet-11-music.jpg" alt="Music"><br><sub>Music</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-12-calendar.jpg" alt="Calendar"><br><sub>Calendar</sub></td><td width="50%"><img src="docs/screenshots/tablet-14b-browser-page.jpg" alt="Browser rendering a page with System WebView"><br><sub>Browser rendering a page with System WebView</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-16-launcher.jpg" alt="App launcher"><br><sub>App launcher</sub></td><td width="50%"><img src="docs/screenshots/tablet-17-spotlight-calc.jpg" alt="Spotlight with inline calculation"><br><sub>Spotlight with inline calculation</sub></td></tr>
<tr><td width="50%"><img src="docs/screenshots/tablet-18-light-tinted-glass.jpg" alt="Light appearance with tinted glass"><br><sub>Light appearance with tinted glass</sub></td></tr>
</table>

<table><tr><td><img src="docs/screenshots/phone-01-desktop-banners.jpg" width="220" alt="Phone: desktop"><br><sub>Phone: desktop</sub></td><td><img src="docs/screenshots/phone-06-mission-control.jpg" width="220" alt="Phone: Mission Control"><br><sub>Phone: Mission Control</sub></td><td><img src="docs/screenshots/phone-16-launcher.jpg" width="220" alt="Phone: launcher"><br><sub>Phone: launcher</sub></td></tr></table>


## Install

1. On your Android device (Android 8.0 or newer; Android 13+ recommended for full glass), open the
   [latest APK link](https://github.com/woodjamesley-cyber/macOS27-Android-Desktop/releases/latest/download/GoldenGateDesktop.apk).
2. When Android asks, allow your browser or file manager to **Install unknown apps**.
3. Open the downloaded `GoldenGateDesktop.apk` and tap **Install**, then open **Golden Gate Desktop**.
4. Optional: verify the download against `SHA256SUMS.txt` on the release page.

With adb: `adb install GoldenGateDesktop.apk`.

## Features

**Liquid Glass material** — live background blur sampled from GPU render layers, saturation
"vibrancy", an AGSL edge-refraction shader with chromatic dispersion (Android 13+), specular
highlights, darker rims and soft shadows. A **clear ↔ tinted** slider, accent colors,
Light/Dark/Auto appearance, Reduce Transparency, Increase Contrast, and three quality levels
(Full / Balanced / Efficient) with automatic fallback on older devices.

**Desktop** — six original procedural wallpapers or your own photo; desktop files and folders
with selection, double-click to open, rename, drag to move, right-click/long-press context menus;
multiple Spaces (desktops).

**Dock** — magnification, bounce-on-launch, running indicators, separators, recent apps,
minimized windows, Trash; add/remove/rearrange apps (drag or context menu); bottom/left/right
positions, auto-hide, adjustable size.

**Menu bar** — system menu (About, Settings, Recent Items, Force Quit, Sleep, Lock, Restart, Shut
Down) and per-app File / Edit / View / Window / Help menus that execute real actions with
keyboard shortcuts; status items for Wi‑Fi, battery, Spotlight, Control Center and date/time.

**Notification Center** — glass banners with app icon, app name, timestamp and action buttons;
history grouped by app, swipe/close dismissal, clear per app or all, Do Not Disturb, per-app mute.

**Control Center** — Wi‑Fi, Bluetooth, network, Focus, Dark Mode, Night Shift, brightness, volume,
now-playing, screen cast, lock, settings shortcut (see limitations for what Android allows).

**Window management** — move, resize from any edge/corner, glass traffic lights (close, minimize
with genie-style animation, zoom), edge snapping to halves/quarters/fill, tiling menu, Mission
Control, ⌘/Ctrl+Tab app switcher, move windows between Spaces; window positions persist across
rotation and app restarts.

**Apps (13)**

| App | What it does |
|---|---|
| Finder | Browse the app's home folder and SAF-granted folders; icon/list views, sidebar, search, Quick Look, new folder/file, rename, copy/move, Trash |
| System Settings | 17 panes; every setting persists |
| Browser | WebView browser with tabs, address/search bar, start page, back/forward, zoom |
| Terminal | Sandboxed shell with 24 commands, redirection, history and Tab completion |
| Calculator | Basic + scientific modes, paper tape, keyboard input |
| Calendar | Month view, create/edit/delete events, multiple calendars |
| Clock | World clocks, stopwatch with laps, timer with notification |
| Notes | Notes stored as text files, autosave, search |
| Photos | Gallery grid, viewer, import with the Android photo picker |
| Files | Android Storage Access Framework browser and document import |
| Music | Original generative tracks + your imported audio; library, shuffle/repeat, seek, volume |
| System Information | Device hardware, GPU, memory, storage, software and input devices |
| Tips | Built-in guide and shortcut reference |

Also: Spotlight search (apps, files, settings, inline math), a full-screen app launcher, a lock
screen, volume/brightness HUDs, subtle synthesized UI sounds (toggle in Settings → Sound).

**Input that adapts automatically** — touch (long-press = right-click), mouse (hover, right-click,
scroll), physical keyboard (⌘/Ctrl shortcuts) and game controllers (on-screen pointer, buttons
mapped to click / back / Mission Control / Spaces). No manual mode switch.

**Responsive layouts** for phones, tablets and foldables; UI scale is automatic or adjustable.

## Known limitations

See [docs/LIMITATIONS.md](docs/LIMITATIONS.md) for the full, honest list. Highlights: Android
doesn't let apps toggle Wi‑Fi/Bluetooth (we open the system panels), brightness only affects this
app's window, Do Not Disturb applies to in-app notifications only, live blur needs Android 12+ and
refraction Android 13+, and the genie effect is an approximation.

## Build from source

```bash
git clone https://github.com/woodjamesley-cyber/macOS27-Android-Desktop.git
cd macOS27-Android-Desktop
./gradlew testDebugUnitTest assembleRelease   # JDK 17 + Android SDK 35 required
```

Without signing secrets, the release APK is signed with the local debug key. CI signs releases with
a key stored only in GitHub Actions secrets.

## Documentation

* [Design spec — verified vs. approximated](docs/DESIGN_SPEC.md)
* [Architecture](docs/ARCHITECTURE.md)
* [Limitations](docs/LIMITATIONS.md)

## CI / CD

Every push to `main` runs [`.github/workflows/android.yml`](.github/workflows/android.yml): JDK 17
+ Android SDK setup, unit tests, release APK build (uploaded as an artifact), instrumented UI tests
on tablet and phone emulators (screenshots uploaded as artifacts), a launch smoke test of the
minified release APK, and — only if all of that passes — a versioned GitHub Release.

## License

MIT — see [LICENSE](LICENSE). Inter font: SIL Open Font License 1.1 ([third_party_licenses](third_party_licenses)).
