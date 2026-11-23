# Architecture

Single-module Android app written in **Kotlin + Jetpack Compose** (no XML layouts).
Package root: `io.github.goldengate.desktop`.

```
MainActivity ──► DesktopViewModel ──► DesktopController (all desktop state, survives rotation)
     │                                   ├── WindowManager      windows, z-order, snapping, spaces
     │                                   ├── NotificationCenter banners + history + grouping
     │                                   ├── SettingsStore      SharedPreferences-backed settings
     │                                   ├── SystemStatus       battery / network / volume / BT
     │                                   ├── FileSystem         sandboxed home + SAF folders
     │                                   ├── CalendarStore, MusicPlayer, UiSounds …
     │                                   └── PlatformBridge ◄── implemented by MainActivity
     │                                        (pickers, system panels, permissions, intents)
     └── setContent { DesktopRoot(desk) }
              ├── wallpaper layer  ─┐ recorded into GraphicsLayers ("backdrops")
              ├── desktop icons     │ that glass surfaces sample with RenderEffect
              ├── WindowsLayer ─────┘ (each window: WindowChrome + app content)
              ├── MenuBar, Dock
              └── overlays: menus, Control Center, Notification Center, Spotlight,
                  Launchpad-style launcher, Mission Control, app switcher, dialogs, HUDs
```

| Package | Responsibility |
|---|---|
| `glass/` | Theme tokens, `GlassSurface` (backdrop sampling + blur + vibrancy + refraction shader + highlights), AGSL shaders, procedural wallpapers |
| `core/` | Pure state/logic: window manager, menus, settings, notifications, shell interpreter, calculator engine, app registry, desktop controller |
| `fs/` | File abstraction over the app's private "home" folder and Storage Access Framework trees |
| `audio/` | Runtime-synthesized UI sounds and the generative music player |
| `ui/` | Shared widgets (buttons, sliders, text fields, gesture handling), vector glyphs, app & file icons |
| `shell/` | Desktop chrome: menu bar, Dock, windows, Mission Control, Control Center, notifications, overlays |
| `apps/` | The 13 bundled apps, one file each, mapped by `AppHost` |

## Rendering pipeline for Liquid Glass

1. The wallpaper, desktop icons and windows are drawn into GPU `GraphicsLayer`s (RenderNodes).
2. Each glass surface redraws the relevant layer, offset to its own on-screen position and
   clipped to its shape — i.e. it shows "what is behind it".
3. A `RenderEffect` chain is applied: Gaussian blur → saturation matrix (vibrancy) → on
   Android 13+ an AGSL `RuntimeShader` that refracts the edge and adds chromatic dispersion.
4. A tint fill (controlled by the clear↔tinted slider), a specular top highlight, a darker rim
   and a soft shadow are painted on top.
5. Fallbacks: Android 12 gets blur without refraction; Android 8–11 uses a heavily downscaled,
   pre-blurred wallpaper bitmap plus a stronger tint ("Efficient" quality). *Reduce
   transparency* makes surfaces opaque.

## Input

`MainActivity` inspects `InputDevice`s and every incoming event's source so the UI adapts without a
mode switch: mouse → hover states, right-click context menus, scroll wheel; keyboard → ⌘/Ctrl
shortcuts and Tab switcher; touch → long-press = secondary click, larger hit targets; gamepad → an
on-screen pointer driven by the left stick / D-pad with A = click, X = secondary click, B = back,
Y = Mission Control, L1/R1 = switch spaces.

## Dependencies

| Library | Version | License |
|---|---|---|
| AndroidX Core KTX | 1.15.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 |
| AndroidX Lifecycle (runtime, compose, viewmodel) | 2.8.7 | Apache-2.0 |
| Jetpack Compose (BOM) | 2024.12.01 | Apache-2.0 |
| AndroidX DocumentFile | 1.0.1 | Apache-2.0 |
| Kotlin Coroutines | 1.9.0 | Apache-2.0 |
| Inter typeface (bundled) | 4.x | SIL OFL 1.1 (`third_party_licenses/`) |

Tests: JUnit 4, AndroidX Test (runner, rules, ext-junit), UiAutomator, Compose UI Test.
Build: Gradle 8.11.1, Android Gradle Plugin, JDK 17, compileSdk/targetSdk 35, minSdk 26.
