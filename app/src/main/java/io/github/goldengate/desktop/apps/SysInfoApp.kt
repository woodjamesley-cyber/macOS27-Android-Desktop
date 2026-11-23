package io.github.goldengate.desktop.apps

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.goldengate.desktop.BuildConfig
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.glass.GlassShaders
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.FormRow
import io.github.goldengate.desktop.ui.FormSection
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.SidebarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class GpuInfo(val renderer: String, val vendor: String, val version: String)

/** Queries the GPU through a throwaway 1×1 pbuffer EGL context. */
fun queryGpu(): GpuInfo = try {
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val ver = IntArray(2)
    EGL14.eglInitialize(display, ver, 0, ver, 1)
    val attribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
    val configs = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
    EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
    val ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
    val surf = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
    EGL14.eglMakeCurrent(display, surf, surf, ctx)
    val info = GpuInfo(GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown", GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown", GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown")
    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    EGL14.eglDestroySurface(display, surf); EGL14.eglDestroyContext(display, ctx); EGL14.eglTerminate(display)
    info
} catch (t: Throwable) { GpuInfo("Unavailable", "Unavailable", t.message ?: "") }

private fun uptime(ms: Long): String { val m = ms / 60_000; return "${m / 1440} days, ${(m / 60) % 24} hours, ${m % 60} minutes" }

@Composable
fun SysInfoApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var section by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { win.title = "System Information"; while (true) { tick++; delay(2000) } }
    val gpu by produceState(GpuInfo("Querying…", "", ""), Unit) { value = withContext(Dispatchers.Default) { queryGpu() } }
    val am = remember { ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val mem = remember(tick) { ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) } }
    val rt = Runtime.getRuntime()
    val dm = remember { DisplayMetrics().also { @Suppress("DEPRECATION") (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it) } }
    @Suppress("DEPRECATION") val refresh = remember { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate }

    val sections = listOf("Hardware", "Graphics", "Memory", "Storage", "Software", "Input Devices")
    val rows: List<Pair<String, String>> = when (section) {
        0 -> listOf(
            "Model" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", "Device" to Build.DEVICE, "Board" to Build.BOARD,
            "Hardware" to Build.HARDWARE, "SoC" to (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "Not reported"),
            "CPU cores" to "${rt.availableProcessors()}", "ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
            "Battery" to "${desk.status.batteryLevel}%${if (desk.status.charging) " (charging)" else ""}",
        )
        1 -> listOf(
            "Renderer" to gpu.renderer, "Vendor" to gpu.vendor, "OpenGL ES" to gpu.version,
            "Display" to "${dm.widthPixels} × ${dm.heightPixels} px", "Density" to "${dm.densityDpi} dpi (${dm.density}×)", "Refresh rate" to "${refresh.toInt()} Hz",
            "Liquid Glass path" to when { GlassShaders.supportsRefraction -> "AGSL refraction + RenderEffect blur (Full)"; GlassShaders.supportsBlur -> "RenderEffect blur (Balanced)"; else -> "Pre-blurred wallpaper (Efficient)" },
        )
        2 -> listOf(
            "Total RAM" to FileSystem.formatSize(mem.totalMem), "Available" to FileSystem.formatSize(mem.availMem), "Low memory" to if (mem.lowMemory) "Yes" else "No",
            "App heap used" to FileSystem.formatSize(rt.totalMemory() - rt.freeMemory()), "App heap max" to FileSystem.formatSize(rt.maxMemory()),
            "Memory class" to "${am.memoryClass} MB",
        )
        3 -> listOf(
            "Device storage" to FileSystem.formatSize(desk.fs.totalSpace()), "Free" to FileSystem.formatSize(desk.fs.freeSpace()),
            "Home folder" to "Private app storage", "Connected folders" to "${runCatching { desk.fs.grantedTrees().size }.getOrDefault(0)}",
        )
        4 -> listOf(
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Security patch" to (Build.VERSION.SECURITY_PATCH ?: "—"),
            "Build" to Build.DISPLAY, "Kernel" to (System.getProperty("os.version") ?: "—"), "Java VM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}",
            "Uptime" to uptime(SystemClock.elapsedRealtime()), "Golden Gate Desktop" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Open windows" to "${desk.wm.windows.size}",
        )
        else -> listOf(
            "Keyboard" to if (desk.input.keyboard) "Connected" else "Not connected", "Mouse" to if (desk.input.mouse) "Connected" else "Not connected",
            "Touchpad" to if (desk.input.touchpad) "Connected" else "Not connected", "Game controller" to if (desk.input.gamepad) "Connected" else "Not connected",
            "Touchscreen" to if (desk.input.touchscreen) "Yes" else "No",
        ) + desk.input.deviceNames.mapIndexed { i, n -> "Device ${i + 1}" to n }
    }
    WindowMenus(win, listOf(menu("Edit") {
        item("Copy Report", Shortcut(Key.C, shift = true)) { clipboard.setText(AnnotatedString(rows.joinToString("\n") { "${it.first}: ${it.second}" })) }
    }))

    WindowScaffold(
        title = "System Information", subtitle = sections[section],
        sidebar = {
            sections.forEachIndexed { i, s ->
                SidebarItem(s, listOf(Glyph.Cpu, Glyph.Display, Glyph.Bolt, Glyph.Folder, Glyph.Info, Glyph.Keyboard)[i], Color(0xFF0A84FF), section == i, { section = i }, Modifier.testTag("sysinfo-$s"))
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            if (section == 2) {
                val used = 1f - mem.availMem / mem.totalMem.toFloat()
                DeskText("Memory pressure", Desk.type.bodyStrong)
                Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(Desk.colors.controlFillPressed)) {
                    Box(Modifier.fillMaxWidth(used).height(14.dp).background(if (used > 0.85f) Color(0xFFFF453A) else if (used > 0.65f) Color(0xFFFFD60A) else Color(0xFF32D74B)))
                }
            }
            FormSection(sections[section]) {
                rows.forEachIndexed { i, (k, v) -> FormRow(k, detail = null, divider = i < rows.lastIndex) { DeskText(v, Desk.type.body, color = Desk.colors.secondaryText, maxLines = 2) } }
            }
            Spacer(Modifier.height(4.dp))
            Row { Spacer(Modifier.width(2.dp)); DeskText("Values come from Android APIs on this device and refresh every 2 seconds.", Desk.type.small, color = Desk.colors.tertiaryText) }
        }
    }
}
