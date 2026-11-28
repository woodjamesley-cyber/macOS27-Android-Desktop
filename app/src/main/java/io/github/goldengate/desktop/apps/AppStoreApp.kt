package io.github.goldengate.desktop.apps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.AppLibrary
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Fdroid
import io.github.goldengate.desktop.core.FdroidApp
import io.github.goldengate.desktop.core.GoogleApp
import io.github.goldengate.desktop.core.LibraryApk
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
import io.github.goldengate.desktop.ui.rememberRemoteIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The App Store.
 *
 * - **Discover** downloads signed APKs straight from f-droid.org over HTTPS, keeps them in the
 *   desktop's private App Library, hands them to Android's installer and opens the app once it is
 *   installed. Cards use the real app icons served by F-Droid.
 * - **Google Play** covers Google's own apps: they live behind Google Play, which can't legally be
 *   bundled or installed by another app, so the store opens the Play listing — or installs Aurora
 *   Store, a real open-source Play client, when Play isn't on the device.
 * - **Library** lists every APK the desktop has downloaded or imported, even after uninstalling
 *   the app it belongs to.
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
    var progress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun libraryList(): List<LibraryApk> = runCatching { AppLibrary.list(context) }.getOrDefault(emptyList())

    fun refreshInstalled() {
        val packages = Fdroid.catalog.map { it.packageName } + Fdroid.googleApps.map { it.packageName }
        installed = packages.filter { desk.bridge?.isPackageInstalled(it) == true }.toSet()
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshInstalled()
            library = libraryList()
            delay(2000)
        }
    }

    fun open(packageName: String, name: String) {
        if (desk.bridge?.launchPackage(packageName) != true) {
            desk.error("“$name” couldn't be opened. It may still be installing or may have been uninstalled.")
        }
    }

    fun install(app: FdroidApp) {
        if (busyPackage != null) return
        busyPackage = app.packageName
        status = null
        progress = progress + (app.packageName to 0f)
        scope.launch {
            var versionName: String? = null
            val outcome = runCatching {
                val version = Fdroid.latestVersion(app.packageName)
                    ?: error("F-Droid isn't listing ${app.name} right now.")
                versionName = version.versionName
                Fdroid.download(context, app, version) { p ->
                    progress = progress + (app.packageName to p)
                }
            }
            outcome.onSuccess { file ->
                library = libraryList()
                if (desk.bridge?.installApk(file, app.packageName) == true) {
                    status = "Installing ${app.name} ${versionName ?: ""} — confirm in Android's installer. " +
                        "The app opens automatically when it's done."
                } else {
                    status = "Allow “Install unknown apps” for Golden Gate Desktop, then tap Install again."
                }
            }.onFailure {
                desk.error(it.message ?: "Couldn't download ${app.name}.")
            }
            progress = progress - app.packageName
            busyPackage = null
        }
    }

    fun installAurora() {
        val aurora = Fdroid.byPackage(Fdroid.AURORA)
        if (aurora == null) desk.error("Aurora Store isn't in the catalog.")
        else install(aurora)
    }

    fun importApk() {
        desk.bridge?.pickApk { uri ->
            if (uri == null) return@pickApk
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { AppLibrary.importFrom(context, uri) } }
                result.onSuccess { apk ->
                    library = libraryList()
                    desk.notify(AppIds.STORE, "Saved to App Library", "“${apk.label}” is kept inside Golden Gate Desktop.")
                    if (desk.bridge?.installApk(apk.file, apk.packageName) != true) {
                        desk.notify(AppIds.STORE, "Allow installs first", "Android needs permission to install apps from Golden Gate Desktop. Tap Install again once it's allowed.")
                    }
                }.onFailure { desk.error(it.message ?: "Couldn't import that APK.") }
            }
        }
    }

    fun googleAction(app: GoogleApp) {
        when {
            desk.bridge?.isPackageInstalled(app.packageName) == true -> open(app.packageName, app.name)
            desk.bridge?.isPackageInstalled("com.android.vending") == true -> {
                desk.bridge?.openStoreListing(app.packageName)
                desk.notify(AppIds.STORE, "Opening Google Play", "Android installs ${app.name} on the Play page — tap Install there.")
            }
            else -> desk.bridge?.openStoreListing(app.packageName)
        }
    }

    WindowMenus(win, listOf(
        menu("File") {
            item("Install APK…") { importApk() }
            item("Refresh") { refreshInstalled(); library = libraryList() }
        },
    ))

    WindowScaffold(
        title = "App Store",
        toolbar = {
            SegmentedControl(listOf("Discover", "Google Play", "Library"), tab, { tab = it }, Modifier.testTag("store-tabs"))
            Spacer(Modifier.width(8.dp))
            ToolbarButton(Glyph.Refresh, {
                refreshInstalled()
                library = libraryList()
            }, description = "Refresh")
        },
    ) {
        Column(
            Modifier.fillMaxSize().background(c.contentBackground)
                .verticalScroll(rememberScrollState()).padding(18.dp).testTag("app-store"),
        ) {
            when (tab) {
                0 -> DiscoverPane(
                    desk = desk,
                    query = query,
                    onQuery = { query = it },
                    category = category,
                    onCategory = { category = it },
                    installed = installed,
                    progress = progress,
                    busyPackage = busyPackage,
                    status = status,
                    onOpen = { open(it.packageName, it.name) },
                    onInstall = { install(it) },
                    onImport = { importApk() },
                )
                1 -> GooglePane(
                    desk = desk,
                    installed = installed,
                    onOpen = { open(it.packageName, it.name) },
                    onAction = { googleAction(it) },
                    onInstallAurora = { installAurora() },
                    auroraInstalling = busyPackage == Fdroid.AURORA,
                )
                else -> LibraryPane(
                    desk = desk,
                    library = library,
                    installed = installed,
                    onInstall = { apk ->
                        if (desk.bridge?.installApk(apk.file, apk.packageName) != true) {
                            desk.notify(AppIds.STORE, "Allow installs first", "Android needs permission to install apps from Golden Gate Desktop.")
                        }
                    },
                    onOpen = { apk ->
                        apk.packageName?.let { pkg -> open(pkg, apk.label) } ?: desk.error("This APK doesn't declare a package name.")
                    },
                    onDelete = { apk ->
                        if (AppLibrary.delete(apk)) {
                            library = libraryList()
                            desk.play(io.github.goldengate.desktop.audio.UiSound.Trash)
                        }
                    },
                    onImport = { importApk() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverPane(
    desk: io.github.goldengate.desktop.core.DesktopController,
    query: String,
    onQuery: (String) -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    installed: Set<String>,
    progress: Map<String, Float>,
    busyPackage: String?,
    status: String?,
    onOpen: (FdroidApp) -> Unit,
    onInstall: (FdroidApp) -> Unit,
    onImport: () -> Unit,
) {
    val c = Desk.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B5CF6), Color(0xFF7B2FF7), Color(0xFFE14FA0))))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DeskText("Install real Android apps", Desk.type.headline.copy(fontSize = 22.sp), color = Color.White)
            DeskText(
                "Downloads come straight from f-droid.org, are kept in your App Library, and open automatically " +
                    "once Android finishes installing them.",
                Desk.type.caption, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        PushButton("Install APK…", onImport, primary = true, glyph = Glyph.Download)
    }

    if (status != null) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(c.accent.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Info, c.accent, 14.dp)
            Spacer(Modifier.width(8.dp))
            DeskText(status, Desk.type.caption, color = c.text, modifier = Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Fdroid.categories.forEach { cat ->
            val selected = cat == category
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (selected) c.accent else c.controlFill)
                    .desktopGestures(key = "cat-$cat", onTap = { onCategory(cat) })
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                DeskText(cat, Desk.type.caption.copy(fontWeight = FontWeight.Medium), color = if (selected) Color.White else c.text)
            }
        }
        Spacer(Modifier.width(4.dp))
    }
    Spacer(Modifier.height(12.dp))
    DeskTextField(
        query, onQuery, Modifier.fillMaxWidth(0.6f).testTag("store-search"),
        placeholder = "Search F-Droid", glyph = Glyph.Search, rounded = 9.dp,
    )
    Spacer(Modifier.height(12.dp))

    val apps = Fdroid.filter(query, category)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        apps.forEach { app ->
            StoreCard(
                app = app,
                installed = app.packageName in installed,
                download = progress[app.packageName],
                onOpen = { onOpen(app) },
                onInstall = { onInstall(app) },
            )
        }
    }
    if (apps.isEmpty()) EmptyState(Glyph.Search, "No apps found", "Try another name or category.")
    Spacer(Modifier.height(14.dp))
    DeskText(
        "APKs are downloaded over HTTPS from f-droid.org and verified by reading their package name before " +
            "Android's installer is opened. Android always asks you to confirm an install; nothing is installed silently. " +
            "Downloads stay in the App Library so you can reinstall later.",
        Desk.type.small, color = c.tertiaryText, modifier = Modifier.widthIn(max = 620.dp),
    )
}

@Composable
private fun GooglePane(
    desk: io.github.goldengate.desktop.core.DesktopController,
    installed: Set<String>,
    onOpen: (GoogleApp) -> Unit,
    onAction: (GoogleApp) -> Unit,
    onInstallAurora: () -> Unit,
    auroraInstalling: Boolean,
) {
    val c = Desk.colors
    val playInstalled = "com.android.vending" in installed
    val auroraInstalled = Fdroid.AURORA in installed
    Column(Modifier.fillMaxWidth()) {
        DeskText("Google apps", Desk.type.headline.copy(fontSize = 20.sp))
        DeskText(
            "Google Play is Google's own store, so Android doesn't allow another app to download or install it, " +
                "and its apps are distributed through Play. Here's how to get them on this desktop:",
            Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).widthIn(max = 620.dp),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1A73E8).copy(alpha = 0.18f), Color(0xFF34A853).copy(alpha = 0.12f))))
                .border(0.5.dp, c.separator, RoundedCornerShape(12.dp)).padding(14.dp),
        ) {
            DeskText(
                when {
                    playInstalled -> "Google Play is installed on this device."
                    auroraInstalled -> "Aurora Store is installed — sign in anonymously to download Google Play apps."
                    else -> "Aurora Store is a free, open-source client for Google Play apps."
                },
                Desk.type.bodyStrong,
            )
            DeskText(
                when {
                    playInstalled -> "Tap any app below to open its Play listing, or open the Play Store itself."
                    auroraInstalled -> "Open Aurora, search for the Google app you want and tap Install. Aurora downloads the same Play packages."
                    else -> "Install Aurora Store from F-Droid (a real download, about 8 MB), open it, choose an anonymous session, " +
                        "then install Chrome, Gmail, Maps, YouTube and the rest from Google Play."
                },
                Desk.type.caption, color = c.secondaryText, modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    auroraInstalled -> PushButton("Open Aurora Store", { desk.bridge?.launchPackage(Fdroid.AURORA) }, primary = true, glyph = Glyph.Download)
                    !playInstalled -> PushButton(
                        if (auroraInstalling) "Downloading Aurora…" else "Install Aurora Store",
                        onInstallAurora, primary = true, enabled = !auroraInstalling, glyph = Glyph.Download,
                    )
                    else -> PushButton("Open Google Play", { desk.bridge?.launchPackage("com.android.vending") }, primary = true)
                }
                PushButton("Open F-Droid Page", { desk.bridge?.openStoreListing(Fdroid.AURORA) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(0.5.dp, c.separator, RoundedCornerShape(10.dp))) {
            Fdroid.googleApps.forEachIndexed { index, app ->
                val isInstalled = app.packageName in installed
                val icon = rememberInstalledIcon(desk, app.packageName, isInstalled)
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon, app.name, 0xFF4285F4, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            DeskText(app.name, Desk.type.bodyStrong, maxLines = 1)
                            DeskText(app.tagline, Desk.type.small, color = c.secondaryText, maxLines = 1)
                        }
                        if (isInstalled) {
                            GlyphIcon(Glyph.Check, c.accent, 12.dp)
                            Spacer(Modifier.width(4.dp))
                            DeskText("Installed", Desk.type.small, color = c.accent)
                            Spacer(Modifier.width(8.dp))
                            PushButton("Open", { onOpen(app) }, primary = true)
                        } else {
                            PushButton("Get", { onAction(app) }, primary = false)
                        }
                    }
                    if (index < Fdroid.googleApps.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        DeskText(
            "Why not bundled? Google Play and Google's apps are proprietary and licensed through Google Play; " +
                "an app store can't legally redistribute them. Aurora Store talks to Play on your behalf with your own account.",
            Desk.type.small, color = c.tertiaryText, modifier = Modifier.widthIn(max = 620.dp),
        )
    }
}

@Composable
private fun LibraryPane(
    desk: io.github.goldengate.desktop.core.DesktopController,
    library: List<LibraryApk>,
    installed: Set<String>,
    onInstall: (LibraryApk) -> Unit,
    onOpen: (LibraryApk) -> Unit,
    onDelete: (LibraryApk) -> Unit,
    onImport: () -> Unit,
) {
    val c = Desk.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DeskText("App Library", Desk.type.headline.copy(fontSize = 20.sp))
            DeskText(
                "Every APK the store downloaded or you imported is kept in Golden Gate Desktop's private storage.",
                Desk.type.caption, color = c.secondaryText,
            )
        }
        PushButton("Install APK…", onImport, primary = true, glyph = Glyph.Plus)
    }
    Spacer(Modifier.height(12.dp))
    DeskText(
        "${library.size} saved · ${FileSystem.formatSize(AppLibrary.totalSize(desk.app))}",
        Desk.type.small, color = c.tertiaryText,
    )
    Spacer(Modifier.height(10.dp))
    if (library.isEmpty()) {
        EmptyState(
            Glyph.Download, "No APKs yet",
            "Install something from Discover, or tap Install APK… and pick an .apk file. The store keeps a copy here and opens Android's installer.",
        )
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(0.5.dp, c.separator, RoundedCornerShape(10.dp))) {
            library.forEachIndexed { index, apk ->
                LibraryRow(
                    apk = apk,
                    installed = apk.packageName?.let { pkg -> pkg in installed || desk.bridge?.isPackageInstalled(pkg) == true } == true,
                    divider = index < library.lastIndex,
                    onInstall = { onInstall(apk) },
                    onOpen = { onOpen(apk) },
                    onDelete = { onDelete(apk) },
                )
            }
        }
    }
}

@Composable
private fun StoreCard(
    app: FdroidApp,
    installed: Boolean,
    download: Float?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
) {
    val c = Desk.colors
    val icon = rememberRemoteIcon(Fdroid.iconUrl(app.packageName))
    Column(
        Modifier.width(236.dp).height(174.dp).clip(RoundedCornerShape(14.dp))
            .background(if (c.dark) Color(0x14FFFFFF) else Color(0xB3FFFFFF))
            .border(0.5.dp, c.separator, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("store-app-${app.packageName}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, app.name, app.color, 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                DeskText(app.name, Desk.type.bodyStrong, maxLines = 1)
                DeskText(app.category, Desk.type.small, color = c.secondaryText, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskText(app.tagline, Desk.type.caption, color = c.secondaryText, maxLines = 2, modifier = Modifier.weight(1f))
        if (download != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.controlFill)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(download.coerceIn(0f, 1f)).background(c.accent))
            }
            Spacer(Modifier.height(4.dp))
            DeskText(
                if (download >= 1f) "Downloaded — opening installer…" else "Downloading… ${(download * 100).toInt()}%",
                Desk.type.small, color = c.secondaryText,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (installed) {
                    GlyphIcon(Glyph.Check, c.accent, 12.dp)
                    Spacer(Modifier.width(4.dp))
                    DeskText("Installed", Desk.type.small, color = c.accent)
                } else {
                    DeskText("Free · F-Droid", Desk.type.small, color = c.tertiaryText)
                }
                Spacer(Modifier.weight(1f))
                if (installed) PushButton("Open", onOpen, primary = true)
                else PushButton("Install", onInstall, primary = true, glyph = Glyph.Download)
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageBitmap?, label: String, color: Long, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(color), Color(color).copy(alpha = 0.72f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = "$label icon", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            DeskText(
                label.filter { it.isLetterOrDigit() }.take(2).uppercase(),
                Desk.type.bodyStrong.copy(fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold),
                color = Color.White, maxLines = 1,
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))))
    }
}

/** The launcher icon Android itself shows for an installed app, or null. */
@Composable
private fun rememberInstalledIcon(
    desk: io.github.goldengate.desktop.core.DesktopController,
    packageName: String,
    installed: Boolean,
): ImageBitmap? {
    var bitmap by remember(packageName, installed) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(packageName, installed) {
        if (installed && bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                (desk.bridge?.appIcon(packageName) as Bitmap?)?.asImageBitmap()
            }
        }
    }
    return bitmap
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
