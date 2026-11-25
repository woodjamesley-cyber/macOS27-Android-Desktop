package io.github.goldengate.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppLibrary
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.LibraryApk
import io.github.goldengate.desktop.core.StoreApp
import io.github.goldengate.desktop.core.StoreCatalog
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FileSystem
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.EmptyState
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.PushButton
import io.github.goldengate.desktop.ui.SegmentedControl
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The App Store.
 *
 * Android doesn't allow one app to silently install another, so the store works the way the
 * platform allows and always really does something:
 *  - apps already on the device open straight into the app ("Open");
 *  - apps that aren't installed open their real Google Play listing ("Get");
 *  - any APK you pick is copied into the desktop's private App Library (kept after installing),
 *    listed under Library, and handed to Android's own package installer ("Install").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppStoreApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val context = desk.app
    val scope = rememberCoroutineScope()
    val c = Desk.colors
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var installed by remember { mutableStateOf(emptySet<String>()) }
    var library by remember { mutableStateOf(runCatching { AppLibrary.list(context) }.getOrDefault(emptyList())) }
    var busy by remember { mutableStateOf(false) }

    // Android's package list changes outside this app, so poll while the window is open.
    LaunchedEffect(Unit) {
        while (true) {
            installed = StoreCatalog.all.filter { desk.bridge?.isPackageInstalled(it.packageName) == true }.map { it.packageName }.toSet()
            library = runCatching { AppLibrary.list(context) }.getOrDefault(emptyList())
            delay(2000)
        }
    }

    fun open(app: StoreApp) {
        if (desk.bridge?.launchPackage(app.packageName) != true) {
            desk.error("“${app.name}” couldn't be opened. It may have been uninstalled.")
        }
    }

    fun get(app: StoreApp) {
        desk.bridge?.openStoreListing(app.packageName)
        desk.notify(AppIds.STORE, "Opening Google Play", "Android installs ${app.name} itself — tap Install on the Play page.")
    }

    fun importApk() {
        if (busy) return
        desk.bridge?.pickApk { uri ->
            if (uri == null) return@pickApk
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { AppLibrary.importFrom(context, uri) } }
                busy = false
                result.onSuccess { apk ->
                    library = runCatching { AppLibrary.list(context) }.getOrDefault(library)
                    desk.notify(AppIds.STORE, "Saved to App Library", "“${apk.label}” is kept inside Golden Gate Desktop.")
                    if (desk.bridge?.installApk(apk.file) != true) {
                        desk.notify(AppIds.STORE, "Allow installs first", "Android needs permission to install apps from Golden Gate Desktop. Tap Install again once it's allowed.")
                    }
                }.onFailure { desk.error(it.message ?: "Couldn't import that APK.") }
            }
        }
    }

    WindowMenus(win, listOf(
        menu("File") {
            item("Install APK…") { importApk() }
            item("Refresh") { installed = StoreCatalog.all.filter { desk.bridge?.isPackageInstalled(it.packageName) == true }.map { it.packageName }.toSet() }
        },
    ))

    WindowScaffold(
        title = "App Store",
        toolbar = {
            SegmentedControl(listOf("Discover", "Library"), tab, { tab = it }, Modifier.testTag("store-tabs"))
            Spacer(Modifier.width(8.dp))
            ToolbarButton(Glyph.Refresh, {
                installed = StoreCatalog.all.filter { desk.bridge?.isPackageInstalled(it.packageName) == true }.map { it.packageName }.toSet()
                library = runCatching { AppLibrary.list(context) }.getOrDefault(library)
            }, description = "Refresh")
        },
    ) {
        Column(Modifier.fillMaxSize().background(c.contentBackground).verticalScroll(rememberScrollState()).padding(18.dp).testTag("app-store")) {
            if (tab == 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2B5CF6), Color(0xFF7B2FF7), Color(0xFFE14FA0))))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        DeskText("Real Android apps", Desk.type.headline.copy(fontSize = 22.sp), color = Color.White)
                        DeskText(
                            "Open what's already installed, get the rest from Google Play, or install an APK you own. APKs stay in your App Library.",
                            Desk.type.caption, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PushButton(if (busy) "Importing…" else "Install APK…", { importApk() }, primary = true, enabled = !busy, glyph = Glyph.Download)
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoreCatalog.categories.forEach { cat ->
                        val sel = cat == category
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (sel) c.accent else c.controlFill)
                                .desktopGestures(key = "cat-$cat", onTap = { category = cat })
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                        ) {
                            DeskText(cat, Desk.type.caption.copy(fontWeight = FontWeight.Medium), color = if (sel) Color.White else c.text)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(12.dp))
                DeskTextField(
                    query, { query = it }, Modifier.fillMaxWidth(0.6f).testTag("store-search"),
                    placeholder = "Search the App Store", glyph = Glyph.Search, rounded = 9.dp,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StoreCatalog.filter(query, category).forEach { app ->
                        StoreCard(
                            app = app,
                            installed = app.packageName in installed,
                            onOpen = { open(app) },
                            onGet = { get(app) },
                        )
                    }
                }
                if (StoreCatalog.filter(query, category).isEmpty()) {
                    EmptyState(Glyph.Search, "No apps found", "Try another name or category.")
                }
                Spacer(Modifier.height(14.dp))
                DeskText(
                    "Free apps are installed by Google Play; Android doesn't let other apps install them silently. APKs you install yourself are copied into this app's private storage first.",
                    Desk.type.small, color = c.tertiaryText, modifier = Modifier.widthIn(max = 560.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        DeskText("App Library", Desk.type.headline.copy(fontSize = 20.sp))
                        DeskText(
                            "APKs you imported live in Golden Gate Desktop's private storage and survive uninstalling the app they belong to.",
                            Desk.type.caption, color = c.secondaryText,
                        )
                    }
                    PushButton(if (busy) "Importing…" else "Install APK…", { importApk() }, primary = true, enabled = !busy, glyph = Glyph.Plus)
                }
                Spacer(Modifier.height(12.dp))
                DeskText("${library.size} saved · ${FileSystem.formatSize(AppLibrary.totalSize(context))}", Desk.type.small, color = c.tertiaryText)
                Spacer(Modifier.height(10.dp))
                if (library.isEmpty()) {
                    EmptyState(
                        Glyph.Download, "No APKs yet",
                        "Tap Install APK… and pick an .apk file from your device. The store keeps a copy here and opens Android's installer.",
                    )
                } else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(0.5.dp, c.separator, RoundedCornerShape(10.dp))) {
                        library.forEachIndexed { index, apk ->
                            LibraryRow(
                                apk = apk,
                                installed = apk.packageName?.let { pkg -> installed.contains(pkg) || desk.bridge?.isPackageInstalled(pkg) == true } == true,
                                divider = index < library.lastIndex,
                                onInstall = { desk.bridge?.installApk(apk.file) ?: desk.error("This build can't reach Android's installer.") },
                                onOpen = { apk.packageName?.let { pkg -> if (desk.bridge?.launchPackage(pkg) != true) desk.error("Install it first, then Open.") } },
                                onDelete = {
                                    if (AppLibrary.delete(apk)) {
                                        library = runCatching { AppLibrary.list(context) }.getOrDefault(emptyList())
                                        desk.play(io.github.goldengate.desktop.audio.UiSound.Trash)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreCard(app: StoreApp, installed: Boolean, onOpen: () -> Unit, onGet: () -> Unit) {
    val c = Desk.colors
    Column(
        Modifier.width(232.dp).height(154.dp).clip(RoundedCornerShape(14.dp))
            .background(if (c.dark) Color(0x14FFFFFF) else Color(0xB3FFFFFF))
            .border(0.5.dp, c.separator, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("store-app-${app.packageName}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoreMonogram(app, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                DeskText(app.name, Desk.type.bodyStrong, maxLines = 1)
                DeskText(app.category, Desk.type.small, color = c.secondaryText, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskText(app.tagline, Desk.type.caption, color = c.secondaryText, maxLines = 2, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (installed) {
                GlyphIcon(Glyph.Check, c.accent, 12.dp)
                Spacer(Modifier.width(4.dp))
                DeskText("Installed", Desk.type.small, color = c.accent)
            } else {
                DeskText("Free · Google Play", Desk.type.small, color = c.tertiaryText)
            }
            Spacer(Modifier.weight(1f))
            if (installed) PushButton("Open", onOpen, primary = true)
            else PushButton("Get", onGet, primary = true)
        }
    }
}

@Composable
private fun StoreMonogram(app: StoreApp, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(app.colorStart), Color(app.colorEnd)))),
        contentAlignment = Alignment.Center,
    ) {
        DeskText(
            app.monogram,
            Desk.type.bodyStrong.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold),
            color = Color.White, maxLines = 1,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
            ),
        )
    }
}

@Composable
private fun LibraryRow(
    apk: LibraryApk,
    installed: Boolean,
    divider: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Desk.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c.controlFill), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Download, c.text, 17.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                DeskText(apk.label, Desk.type.bodyStrong, maxLines = 1)
                DeskText(
                    listOfNotNull(apk.packageName ?: "Unknown package", apk.versionName, FileSystem.formatSize(apk.sizeBytes))
                        .joinToString(" · "),
                    Desk.type.small, color = c.secondaryText, maxLines = 1,
                )
            }
            if (installed) PushButton("Open", onOpen, primary = false)
            else PushButton("Install", onInstall, primary = true)
            Spacer(Modifier.width(6.dp))
            PushButton("Remove", onDelete, destructive = true)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator).padding(start = 12.dp))
    }
}
