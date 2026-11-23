package io.github.goldengate.desktop.apps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.goldengate.desktop.core.AppIds
import io.github.goldengate.desktop.core.DeskWindow
import io.github.goldengate.desktop.core.Shortcut
import io.github.goldengate.desktop.core.menu
import io.github.goldengate.desktop.fs.FsRef
import io.github.goldengate.desktop.glass.Desk
import io.github.goldengate.desktop.shell.WindowScaffold
import io.github.goldengate.desktop.ui.DeskText
import io.github.goldengate.desktop.ui.DeskTextField
import io.github.goldengate.desktop.ui.Glyph
import io.github.goldengate.desktop.ui.GlyphIcon
import io.github.goldengate.desktop.ui.LocalDesktop
import io.github.goldengate.desktop.ui.ToolbarButton
import io.github.goldengate.desktop.ui.desktopGestures
import java.net.URLEncoder

/** Turns what the user typed into a URL: adds https:// or falls back to a web search. */
fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return ""
    if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") || t.startsWith("about:") || t.startsWith("data:")) return t
    val looksLikeHost = !t.contains(' ') && (t.contains('.') || t.startsWith("localhost")) && !t.endsWith('.')
    return if (looksLikeHost) "https://$t" else "https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8")
}

class BrowserTab(val id: Int) {
    var title by mutableStateOf("Start Page")
    var url by mutableStateOf("")
    var progress by mutableFloatStateOf(1f)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var webView: WebView? = null
    var pending: String? = null
}

private val favourites = listOf(
    Triple("Wikipedia", "https://en.wikipedia.org", 0xFF636366),
    Triple("DuckDuckGo", "https://duckduckgo.com", 0xFFDE5833),
    Triple("GitHub", "https://github.com", 0xFF24292E),
    Triple("Android Developers", "https://developer.android.com", 0xFF3DDC84),
    Triple("OpenStreetMap", "https://www.openstreetmap.org", 0xFF7EBC6F),
    Triple("Hacker News", "https://news.ycombinator.com", 0xFFFF6600),
    Triple("Project Gutenberg", "https://www.gutenberg.org", 0xFF8B4513),
    Triple("Weather", "https://wttr.in", 0xFF0A84FF),
)

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(ctx: Context, tab: BrowserTab, onNewTab: (String) -> Unit, onDownload: (String) -> Unit): WebView = WebView(ctx).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.setSupportMultipleWindows(false)
    settings.allowFileAccess = false
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url
            return if (u.scheme == "http" || u.scheme == "https" || u.scheme == "file" || u.scheme == "about" || u.scheme == "data") false
            else { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; true }
        }
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { tab.url = url ?: ""; tab.progress = 0.05f }
        override fun onPageFinished(view: WebView, url: String?) {
            tab.url = url ?: tab.url; tab.progress = 1f; tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.url
        }
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward() }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress / 100f }
        override fun onReceivedTitle(view: WebView, title: String?) { if (!title.isNullOrBlank()) tab.title = title }
    }
    setDownloadListener { url, _, _, _, _ -> onDownload(url) }
}

@Composable
fun BrowserApp(win: DeskWindow) {
    val desk = LocalDesktop.current
    val c = Desk.colors
    val arg = rememberWindowArgument(win)
    val tabs = remember { mutableStateListOf(BrowserTab(0)) }
    var active by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableIntStateOf(1) }
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val tab = tabs.getOrNull(active) ?: tabs.first()

    fun load(t: BrowserTab, raw: String) {
        val url = normalizeUrl(raw)
        if (url.isEmpty()) return
        t.url = url
        val wv = t.webView
        if (wv != null) {
            wv.settings.allowFileAccess = url.startsWith("file://")
            wv.loadUrl(url)
        } else t.pending = url
    }
    fun newTab(url: String? = null) {
        val t = BrowserTab(nextId++); tabs.add(t); active = tabs.lastIndex
        if (url != null) load(t, url)
    }
    fun closeTab(i: Int) {
        if (tabs.size == 1) { desk.close(win.id); return }
        tabs.removeAt(i).webView?.destroy()
        active = active.coerceAtMost(tabs.lastIndex)
    }

    LaunchedEffect(arg) {
        when {
            arg == null -> {}
            arg.startsWith("url:") -> load(tab, arg.removePrefix("url:"))
            else -> when (val ref = desk.refFromKey(arg)) {
                is FsRef.Local -> load(tab, "file://" + ref.file.absolutePath)
                is FsRef.Saf -> load(tab, ref.uri.toString())
                null -> load(tab, arg)
            }
        }
    }
    LaunchedEffect(tab.url, active, editing) { if (!editing) address = tab.url }
    LaunchedEffect(tab.title) { win.title = if (tab.url.isEmpty()) "Start Page" else tab.title }
    DisposableEffect(Unit) { onDispose { tabs.forEach { it.webView?.destroy() } } }

    WindowMenus(win, listOf(
        menu("File") {
            item("New Tab", Shortcut(Key.T)) { newTab() }
            item("New Window", Shortcut(Key.N)) { desk.launch(AppIds.BROWSER, newWindow = true) }
            item("Close Tab", Shortcut(Key.W)) { closeTab(active) }
            separator()
            item("Open in Android Browser", enabled = tab.url.startsWith("http")) { runCatching { desk.app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        },
        menu("View") {
            item("Reload Page", Shortcut(Key.R)) { tab.webView?.reload() }
            item("Zoom In", Shortcut(Key.Equals, keyLabel = "+")) { tab.webView?.zoomIn() }
            item("Zoom Out", Shortcut(Key.Minus)) { tab.webView?.zoomOut() }
            separator()
            item("Show Start Page", Shortcut(Key.H, shift = true)) { tab.url = ""; tab.title = "Start Page" }
        },
        menu("History") {
            item("Back", Shortcut(Key.LeftBracket, keyLabel = "["), enabled = tab.canBack) { tab.webView?.goBack() }
            item("Forward", Shortcut(Key.RightBracket, keyLabel = "]"), enabled = tab.canForward) { tab.webView?.goForward() }
        },
    ))

    WindowScaffold(
        title = "",
        showTitle = false,
        toolbarLeading = {
            ToolbarButton(Glyph.ChevronLeft, { tab.webView?.goBack() }, enabled = tab.canBack, description = "Back")
            ToolbarButton(Glyph.ChevronRight, { tab.webView?.goForward() }, enabled = tab.canForward, description = "Forward")
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).widthIn(max = 520.dp)) {
                DeskTextField(
                    address, { address = it; editing = true },
                    Modifier.fillMaxWidth().testTag("browser-address"),
                    placeholder = "Search or enter website name", glyph = if (tab.url.startsWith("https")) Glyph.Lock else Glyph.Search,
                    onSubmit = { editing = false; load(tab, address) }, rounded = 10.dp,
                )
                if (tab.progress < 1f) Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp).fillMaxWidth(tab.progress).height(2.dp).background(c.accent))
            }
            Spacer(Modifier.width(8.dp))
        },
        toolbar = {
            ToolbarButton(Glyph.Refresh, { tab.webView?.reload() }, enabled = tab.url.isNotEmpty(), description = "Reload")
            ToolbarButton(Glyph.Plus, { newTab() }, Modifier.testTag("browser-new-tab"), description = "New Tab")
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.size > 1) {
                Row(Modifier.fillMaxWidth().height(30.dp).background(c.controlFill).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEachIndexed { i, t ->
                        Row(
                            Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp).clip(RoundedCornerShape(7.dp))
                                .background(if (i == active) c.contentBackground else Color.Transparent)
                                .desktopGestures(key = "tab${t.id}", onTap = { active = i }).padding(horizontal = 8.dp).testTag("browser-tab-$i"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).desktopGestures(key = "x${t.id}", onTap = { closeTab(i) })) { GlyphIcon(Glyph.Close, c.secondaryText, 10.dp, Modifier.align(Alignment.Center)) }
                            DeskText(if (t.url.isEmpty()) "Start Page" else t.title, Desk.type.small.copy(textAlign = TextAlign.Center), maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    if (i == active) {
                        androidx.compose.runtime.key(t.id) {
                            AndroidView(
                                factory = { ctx ->
                                    (t.webView ?: createWebView(ctx, t, { newTab(it) }, { url ->
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        desk.notify(AppIds.BROWSER, "Download handed to Android", "Downloads open in your device's default app.")
                                    }).also { wv ->
                                        t.webView = wv
                                        t.pending?.let { u -> wv.settings.allowFileAccess = u.startsWith("file://"); wv.loadUrl(u); t.pending = null }
                                    }).also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                                },
                                modifier = Modifier.fillMaxSize().testTag("browser-webview"),
                            )
                        }
                        if (t.url.isEmpty()) StartPage { load(t, it) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    val c = Desk.colors
    Box(Modifier.fillMaxSize().background(c.contentBackground).testTag("browser-start")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DeskText("Favourites", Desk.type.headline, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp))
            Spacer(Modifier.height(14.dp))
            FlowRow(Modifier.widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                favourites.forEach { (name, url, color) ->
                    Column(Modifier.width(76.dp).desktopGestures(key = url, onTap = { onOpen(url) }).testTag("fav-$name"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color(color)), contentAlignment = Alignment.Center) {
                            DeskText(name.take(1), Desk.type.largeTitle, color = Color.White)
                        }
                        DeskText(name, Desk.type.small.copy(textAlign = TextAlign.Center), maxLines = 2, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            DeskText("Privacy Report", Desk.type.headline, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp))
            DeskText("This browser uses Android System WebView. Pages load directly from the web; Golden Gate Desktop doesn't collect your browsing data.", Desk.type.body, color = c.secondaryText, modifier = Modifier.widthIn(max = 560.dp).padding(top = 6.dp))
        }
    }
}
