package io.github.goldengate.desktop

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.key
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.goldengate.desktop.core.DesktopController
import io.github.goldengate.desktop.core.Overlay
import io.github.goldengate.desktop.core.PlatformBridge
import io.github.goldengate.desktop.core.SystemPanel
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.shell.DesktopRoot
import io.github.goldengate.desktop.shell.activateApp
import io.github.goldengate.desktop.shell.hiddenShortcuts
import io.github.goldengate.desktop.shell.menuBarMenus
import io.github.goldengate.desktop.shell.switcherApps
import io.github.goldengate.desktop.shell.systemMenu
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/** Holds the desktop so windows, notifications and timers survive rotation and resizing. */
class DesktopViewModel(app: Application) : AndroidViewModel(app) {
    val desk = DesktopController(app, viewModelScope)
    var firstStart = true
    override fun onCleared() { desk.saveSession(); desk.release() }
}

class MainActivity : ComponentActivity(), PlatformBridge {
    private val vm: DesktopViewModel by viewModels()
    /** Exposed (internal) so instrumented tests can stage scenes for screenshots. */
    internal val desk get() = vm.desk

    private var folderCallback: ((Uri?) -> Unit)? = null
    private var imageCallback: ((Uri?) -> Unit)? = null
    private var documentCallback: ((Uri?) -> Unit)? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var apkCallback: ((Uri?) -> Unit)? = null
    private var homeCallback: ((Boolean) -> Unit)? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folderCallback?.invoke(it); folderCallback = null }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageCallback?.invoke(it); imageCallback = null }
    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { documentCallback?.invoke(it); documentCallback = null }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionCallback?.invoke(it); permissionCallback = null }
    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { apkCallback?.invoke(it); apkCallback = null }
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homeCallback?.invoke(isDefaultLauncher())
        homeCallback = null
    }

    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = scanInputDevices()
        override fun onInputDeviceRemoved(deviceId: Int) = scanInputDevices()
        override fun onInputDeviceChanged(deviceId: Int) = scanInputDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        desk.bridge = this
        if (vm.firstStart) {
            vm.firstStart = false
            desk.restoreSession()
            if (desk.settings.demoNotifications && desk.settings.setupComplete) desk.postDemoNotifications()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { desk.dismissAll() }
        })
        setContent { DesktopRoot(desk) }
        scanInputDevices()
        inputManager.registerInputDeviceListener(deviceListener, null)
    }

    override fun onDestroy() {
        inputManager.unregisterInputDeviceListener(deviceListener)
        if (desk.bridge === this) desk.bridge = null
        super.onDestroy()
    }

    override fun onStart() { super.onStart(); desk.onStart() }
    override fun onStop() { desk.onStop(); super.onStop() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scanInputDevices()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------- input device detection ----------------
    private fun scanInputDevices() {
        var keyboard = resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY && resources.configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        var mouse = false; var touchpad = false; var gamepad = false; var touch = false
        val names = ArrayList<String>()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val s = d.sources
            fun has(src: Int) = (s and src) == src
            val virtual = d.isVirtual
            if (has(InputDevice.SOURCE_TOUCHSCREEN)) touch = true
            if (virtual) continue
            var kind: String? = null
            if (has(InputDevice.SOURCE_GAMEPAD) || has(InputDevice.SOURCE_JOYSTICK)) { gamepad = true; kind = "Game controller" }
            if (has(InputDevice.SOURCE_TOUCHPAD) || (Build.VERSION.SDK_INT >= 26 && has(InputDevice.SOURCE_MOUSE_RELATIVE))) { touchpad = true; kind = kind ?: "Touchpad" }
            if (has(InputDevice.SOURCE_MOUSE)) { mouse = true; kind = kind ?: "Mouse" }
            if (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && Build.VERSION.SDK_INT >= 29 && d.isExternal) { keyboard = true; kind = kind ?: "Keyboard" }
            else if (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && Build.VERSION.SDK_INT < 29) { keyboard = true; kind = kind ?: "Keyboard" }
            if (kind != null) names += "${d.name} — $kind"
        }
        val i = desk.input
        i.keyboard = keyboard; i.mouse = mouse; i.touchpad = touchpad; i.gamepad = gamepad; i.touchscreen = touch
        i.deviceNames.clear(); i.deviceNames.addAll(names.distinct())
        if (!gamepad) i.controllerCursor = null
    }

    // ---------------- keyboard ----------------
    private var switcherIndex = -1

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleGamepadKey(event)) return true
        val meta = event.isMetaPressed
        val ctrl = event.isCtrlPressed
        if (event.action == KeyEvent.ACTION_UP) {
            if (switcherIndex >= 0 && (event.keyCode in listOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT) || (!meta && !ctrl))) {
                val apps = switcherApps(desk)
                apps.getOrNull(switcherIndex)?.let { activateApp(desk, it) }
                switcherIndex = -1
                if (desk.overlay is Overlay.AppSwitcher) desk.overlay = Overlay.None
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        // A real key press means a hardware keyboard is in use.
        if (!desk.input.keyboard && event.device?.isVirtual == false && event.isPrintingKey) desk.input.keyboard = true

        // ⌘Tab / Ctrl+Tab app switcher
        if (event.keyCode == KeyEvent.KEYCODE_TAB && (meta || ctrl)) {
            val apps = switcherApps(desk)
            if (apps.isNotEmpty()) {
                switcherIndex = if (switcherIndex < 0) (if (apps.size > 1) 1 else 0) else (switcherIndex + if (event.isShiftPressed) -1 else 1).mod(apps.size)
                desk.overlay = Overlay.AppSwitcher(switcherIndex)
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && !meta && !ctrl && !event.isAltPressed) {
            if (desk.openMenu != null || (desk.overlay != Overlay.None && desk.overlay !is Overlay.Locked) || desk.dialog != null) {
                if (desk.dialog != null) desk.dialog = null else desk.dismissAll()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        val fnKey = event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12
        if ((meta || ctrl || fnKey) && event.repeatCount == 0) {
            val key = androidx.compose.ui.input.key.KeyEvent(event).key
            val menus = listOf(systemMenu(desk)) + menuBarMenus(desk) + hiddenShortcuts(desk)
            if (desk.handleShortcut(key, meta, ctrl, event.isShiftPressed, event.isAltPressed, menus)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------- game controller ----------------
    private var stickX = 0f; private var stickY = 0f; private var scrollY = 0f
    private var stickJob: Job? = null

    private fun isGamepad(e: KeyEvent) = (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || KeyEvent.isGamepadButton(e.keyCode)

    private fun ensureCursor(): Offset {
        val c = desk.input.controllerCursor ?: Offset(window.decorView.width / 2f, window.decorView.height / 2f)
        desk.input.controllerCursor = c
        desk.input.gamepad = true
        return c
    }

    private fun handleGamepadKey(e: KeyEvent): Boolean {
        val dpad = e.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT) && (e.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD && (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if (!isGamepad(e) && !dpad) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        val c = ensureCursor()
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> injectPointer(c, if (down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP, secondary = false)
            KeyEvent.KEYCODE_BUTTON_X -> if (down) { injectPointer(c, MotionEvent.ACTION_DOWN, secondary = true); injectPointer(c, MotionEvent.ACTION_UP, secondary = true) }
            KeyEvent.KEYCODE_BUTTON_B -> if (down) { if (desk.dialog != null) desk.dialog = null else desk.dismissAll() }
            KeyEvent.KEYCODE_BUTTON_Y -> if (down) desk.toggleOverlay(Overlay.MissionControl)
            KeyEvent.KEYCODE_BUTTON_L1 -> if (down) desk.wm.switchSpace(desk.wm.currentSpace - 1)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (down) desk.wm.switchSpace(desk.wm.currentSpace + 1)
            KeyEvent.KEYCODE_BUTTON_START -> if (down) desk.toggleOverlay(Overlay.Spotlight)
            KeyEvent.KEYCODE_BUTTON_SELECT -> if (down) desk.toggleOverlay(Overlay.Launcher)
            KeyEvent.KEYCODE_DPAD_UP -> if (down) moveCursor(0f, -24f)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) moveCursor(0f, 24f)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) moveCursor(-24f, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) moveCursor(24f, 0f)
            else -> return false
        }
        return true
    }

    private var pointerDown = false
    private var downTime = 0L

    private fun moveCursor(dx: Float, dy: Float) {
        val c = ensureCursor()
        val w = window.decorView.width.toFloat(); val h = window.decorView.height.toFloat()
        val n = Offset((c.x + dx).coerceIn(0f, w - 1), (c.y + dy).coerceIn(0f, h - 1))
        desk.input.controllerCursor = n
        if (pointerDown) injectPointer(n, MotionEvent.ACTION_MOVE, secondary = false)
    }

    private fun injectPointer(at: Offset, action: Int, secondary: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) { downTime = now; if (!secondary) pointerDown = true }
        if (action == MotionEvent.ACTION_UP && !secondary) pointerDown = false
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = if (secondary) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        val buttons = if (secondary && action != MotionEvent.ACTION_UP) MotionEvent.BUTTON_SECONDARY else 0
        val source = if (secondary) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN
        val ev = MotionEvent.obtain(downTime, now, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0, source, 0)
        window.decorView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun injectScroll(at: Offset, v: Float) {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; setAxisValue(MotionEvent.AXIS_VSCROLL, v) })
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        window.decorView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if ((ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && ev.action == MotionEvent.ACTION_MOVE) {
            fun axis(a: Int): Float { val v = ev.getAxisValue(a); val r = ev.device?.getMotionRange(a, ev.source)?.flat ?: 0.1f; return if (abs(v) > maxOf(r, 0.12f)) v else 0f }
            stickX = axis(MotionEvent.AXIS_X).let { if (it == 0f) axis(MotionEvent.AXIS_HAT_X) else it }
            stickY = axis(MotionEvent.AXIS_Y).let { if (it == 0f) axis(MotionEvent.AXIS_HAT_Y) else it }
            scrollY = axis(MotionEvent.AXIS_RZ)
            ensureCursor()
            if (stickJob?.isActive != true && (stickX != 0f || stickY != 0f || scrollY != 0f)) {
                stickJob = lifecycleScope.launch {
                    while (isActive && (stickX != 0f || stickY != 0f || scrollY != 0f)) {
                        val speed = 14f * desk.settings.pointerSpeed * resources.displayMetrics.density / 2f
                        if (stickX != 0f || stickY != 0f) moveCursor(stickX * abs(stickX) * speed * 2f, stickY * abs(stickY) * speed * 2f)
                        if (scrollY != 0f) desk.input.controllerCursor?.let { injectScroll(it, -scrollY) }
                        delay(16)
                    }
                }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    // ---------------- PlatformBridge ----------------
    override fun pickFolder(onResult: (Uri?) -> Unit) {
        folderCallback = onResult
        try { folderPicker.launch(null) } catch (e: ActivityNotFoundException) { folderCallback = null; onResult(null); toastFallback("No folder picker is available on this device.") }
    }

    override fun pickImage(onResult: (Uri?) -> Unit) {
        imageCallback = onResult
        try { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (e: ActivityNotFoundException) { imageCallback = null; onResult(null); toastFallback("No photo picker is available.") }
    }

    override fun pickDocument(mimeTypes: Array<String>, onResult: (Uri?) -> Unit) {
        documentCallback = onResult
        try { documentPicker.launch(mimeTypes) } catch (e: ActivityNotFoundException) { documentCallback = null; onResult(null); toastFallback("No document picker is available.") }
    }

    override fun openSystemPanel(panel: SystemPanel) {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val intent = when (panel) {
            SystemPanel.Wifi -> Intent(if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
            SystemPanel.Internet -> Intent(if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
            SystemPanel.Volume -> Intent(if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)
            SystemPanel.Nfc -> Intent(if (q) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS)
            SystemPanel.Bluetooth -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SystemPanel.Display -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SystemPanel.Cast -> Intent(Settings.ACTION_CAST_SETTINGS)
            SystemPanel.AppDetails -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        try { startActivity(intent) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { toastFallback("Android Settings couldn't be opened.") }
        }
    }

    override fun openExternally(ref: FsRef) {
        try {
            val (uri, mime) = when (ref) {
                is FsRef.Local -> FileProvider.getUriForFile(this, "$packageName.files", ref.file) to FileSystem.mimeOf(ref.file.name)
                is FsRef.Saf -> ref.uri to (contentResolver.getType(ref.uri) ?: FileSystem.mimeOf(ref.name))
            }
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(view, "Open “${ref.name}” with"))
        } catch (e: Exception) {
            desk.error("No app on this device can open “${ref.name}”.")
        }
    }

    override fun setAppBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.02f, 1f) }
    }

    override fun appBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.6f }
    }

    override fun requestBluetoothPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { onResult(true); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { onResult(true); return }
        permissionCallback = onResult
        permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun quitApp() { desk.saveSession(); finishAndRemoveTask() }

    override fun toastFallback(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    // ---------------- App Store ----------------
    override fun pickApk(onResult: (Uri?) -> Unit) {
        apkCallback = onResult
        try {
            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        } catch (e: ActivityNotFoundException) {
            apkCallback = null
            onResult(null)
            toastFallback("No file picker is available on this device.")
        }
    }

    override fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (t: Throwable) {
        false
    }

    override fun launchPackage(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            toastFallback("Android couldn't open that app.")
            false
        }
    }

    override fun openStoreListing(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(market)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (t: Throwable) {
                toastFallback("No app store is available on this device.")
            }
        } catch (t: Throwable) {
            toastFallback("No app store is available on this device.")
        }
    }

    override fun installApk(file: File): Boolean {
        if (!file.isFile) {
            toastFallback("That APK is no longer in the App Library.")
            return false
        }
        if (!packageManager.canRequestPackageInstalls()) {
            toastFallback("Allow “Install unknown apps” for Golden Gate Desktop, then tap Install again.")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (t: Throwable) {
                openSystemPanel(SystemPanel.AppDetails)
            }
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (t: Throwable) {
            toastFallback("Android couldn't start the installer: ${t.message ?: "unknown error"}")
            false
        }
    }

    override fun openAppSettings(packageName: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (t: Throwable) {
            toastFallback("Android Settings couldn't be opened.")
        }
    }

    // ---------------- Default Home app (launcher) ----------------
    override fun isDefaultLauncher(): Boolean = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.packageName == packageName
    } catch (t: Throwable) {
        false
    }

    override fun requestDefaultLauncher(onResult: (Boolean) -> Unit) {
        if (isDefaultLauncher()) {
            onResult(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    onResult(true)
                    return
                }
                homeCallback = onResult
                try {
                    homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (t: Throwable) {
                    homeCallback = null
                }
            }
        }
        // Older devices (and OEMs without the role API) use the Home app setting page.
        openHomeSettings()
        onResult(false)
    }

    override fun openHomeSettings() {
        val candidates = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (intent in candidates) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (t: Throwable) {
                // Try the next page.
            }
        }
        toastFallback("Android Settings couldn't be opened.")
    }
}
