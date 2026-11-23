# Known limitations (honest list)

Android sandboxes ordinary apps, so some macOS behaviours can only be approximated. Where the
platform blocks direct control, the app uses the closest legitimate alternative.

## Platform restrictions

| Feature | What happens | Why |
|---|---|---|
| Wi‑Fi toggle | Opens Android's Wi‑Fi / Internet panel; status is read live | Apps can't toggle Wi‑Fi on Android 10+ |
| Bluetooth toggle | Opens Bluetooth settings; on/off state shown when permission is granted | Apps can't toggle Bluetooth on Android 13+ |
| Brightness slider | Changes brightness of **this app's window** only | Changing system brightness needs `WRITE_SETTINGS`, which we deliberately don't request |
| Volume slider | Controls the real media volume | — |
| Night Shift | A warm color overlay drawn over the desktop | No public API for the system night-light |
| Focus / Do Not Disturb | Silences this app's banners only (history still kept) | System DND needs notification-policy access |
| Lock / Sleep / Restart / Shut Down | In-app lock screen, dimmed "sleep" screen, restart = reload desktop, shut down = quit app | Apps can't power-manage the device |
| Files | Private sandbox "home" folder plus any folders you grant through the Storage Access Framework | Scoped storage; we never request broad storage permission |
| Browser | Android `WebView` with tabs, back/forward, zoom and a start page; downloads are handed to Android | Not Safari/WebKit; no extensions |
| Trackpad gestures | Three/four-finger gestures are not available; use the Dock, hot keys, or controller buttons | Android doesn't deliver them to apps |
| Display | Runs full-screen inside one Android activity; it does not replace the Android launcher | — |

## Rendering

* Live blur requires Android 12+ (API 31). Refraction/dispersion requires Android 13+ (API 33).
  Android 8–11 uses a pre-blurred wallpaper sample ("Efficient" quality).
* Glass inside a window samples the **wallpaper** layer, not other windows behind it, to avoid
  recursive rendering. Shell glass (menu bar, Dock, menus, notifications, Control Center) samples
  the full desktop including windows.
* The genie minimize effect is an approximation (scale + squeeze), not a mesh warp.
* Performance depends on GPU; Settings → Performance lets you drop to Balanced or Efficient.

## Apps — what's intentionally limited

* **Music** plays original, procedurally generated tracks plus audio files you import; there is no
  streaming service.
* **Calendar** stores events locally; it does not sync with Google Calendar or system calendars.
* **Clock** timers and stopwatches run while the app is alive; there are no system alarms.
* **Terminal** is a built-in, sandboxed shell with 24 commands (`help pwd whoami uname date clear
  exit history echo ps calc cd ls cat head wc mkdir touch rm mv cp find open notify`), quoting,
  `>`/`>>` redirection, history and Tab completion. It is not a Linux
  shell and cannot run Android binaries or leave the app's home folder.
* **Photos** shows images in the app's Pictures folder, images you import with the photo picker,
  and images in granted SAF folders.
* **System Information** reports what Android exposes in six sections: Hardware, Graphics (live
  OpenGL renderer query), Memory, Storage, Software and Input Devices.

## Testing coverage

* 30 JVM unit tests cover the window manager, notification center, calculator engine and state
  machine, terminal shell (including sandbox escapes), and URL normalization.
* Instrumented Compose/UiAutomator tests run on an API 34 emulator in CI (tablet profile: full
  suite; phone profile: screenshot tour) and include a smoke test of the minified release APK.
* The CI emulators use a software GPU (SwiftShader), so the refraction shader's exact look on
  real GPUs is verified only by the rendering code path, not by pixel comparison.
* Robolectric versions of the UI tests exist for fast local debugging (`./gradlew testDebugUnitTest -Probo`)
  but are not part of CI, because Robolectric's environment makes some of them flaky.
* Not tested automatically: physical game controllers, physical mice/keyboards, foldable hinge
  postures, Android 8–11 fallback rendering on real hardware.
