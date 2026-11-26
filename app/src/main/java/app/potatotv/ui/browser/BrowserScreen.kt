package app.potatotv.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.net.Http
import app.potatotv.ui.player.PlayerActivity
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    nav: NavController,
    url: String,
    name: String,
    siteId: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by ServiceLocator.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val favorites by ServiceLocator.prefs.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val stats by BrowserSession.stats.collectAsStateWithLifecycle()
    val menus by BrowserSession.menus.collectAsStateWithLifecycle()
    val page by BrowserSession.page.collectAsStateWithLifecycle()

    val adBlockEnabled = rememberUpdatedState(settings.adBlock)
    val darkPages = rememberUpdatedState(settings.darkPages)
    val latestSettings = rememberUpdatedState(settings)

    var showMenus by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<SniffedStream?>(null) }
    var dismissedStreams by remember { mutableStateOf(setOf<String>()) }
    var cursorOn by remember { mutableStateOf(settings.cursorMode) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var appliedUa by remember { mutableStateOf<String?>(null) }
    var lastLaunchUrl by remember { mutableStateOf<String?>(null) }
    var lastLaunchAt by remember { mutableLongStateOf(0L) }

    // NEW: collapsible rail — full screen by default, MENU toggles it open
    var railCollapsed by remember { mutableStateOf(true) }
    val railWidth = if (railCollapsed) 0.dp else 64.dp

    val railFocus = remember { FocusRequester() }
    val webView = remember { WebView(context) }
    val activity = context as? Activity

    // Fullscreen video played inside the site (YouTube-style) needs a custom
    // view plus permission to rotate; the site's own player then works normally.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var previousOrientation by remember {
        mutableStateOf(activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback?) {
        if (fullscreenView != null) return
        previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreenView = view
        fullscreenCallback = callback
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val decor = activity?.findViewById<FrameLayout>(android.R.id.content)
        decor?.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun hideFullscreen() {
        fullscreenView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        activity?.requestedOrientation = previousOrientation
    }

    DisposableEffect(Unit) {
        onDispose { hideFullscreen() }
    }
    val sniffer = remember {
        context.assets.open("sniffer.js").bufferedReader().use { it.readText() }
    }
    val startUrl = url.ifBlank { "https://fmhy.net" }
    val favoriteId = siteId.ifBlank { startUrl }
    val favorite = favorites.contains(favoriteId)

    fun inject(view: WebView?) {
        if (view == null) return
        runCatching { view.evaluateJavascript(sniffer, null) }
        // The 158k-domain blocklist stays in the native interceptor; JS only needs
        // the behaviour switches so a redirect hijack can still be cancelled.
        val js = """
            ;window.__potatoAdHosts = window.__potatoAdHosts || [];
            if (${settings.adBlockRedirectPrevent} && window.__potatoDoBlockRedirects) window.__potatoDoBlockRedirects();
            if (${settings.adBlockSiteHiding} && window.__potatoDoHideAds) window.__potatoDoHideAds();
        """.trimIndent()
        runCatching { view.evaluateJavascript(js, null) }
    }

    suspend fun openPlayer(stream: SniffedStream) {
        val headers = BrowserSession.headersFor(
            stream.url,
            stream.source,
            webView.settings.userAgentString
        )
        var kind = stream.kind
        if (kind.isBlank() || kind == "auto") {
            kind = Http.sniffStreamType(stream.url, headers, webView.settings.userAgentString) ?: "hls"
        }
        val title = page.title.ifBlank { webView.title ?: name.ifBlank { "PotatoTV" } }
        runCatching {
            context.startActivity(
                PlayerActivity.intent(
                    context = context,
                    url = stream.url,
                    kind = kind,
                    title = title,
                    resumeKey = stream.url,
                    headers = headers
                )
            )
        }.onFailure {
            Toast.makeText(context, "Could not open the player: ${it.message}", Toast.LENGTH_LONG).show()
        }
        // Increment site usage for popularity tracking
        if (siteId.isNotBlank()) {
            scope.launch { ServiceLocator.prefs.incrementSiteUsage(siteId) }
        }
        detected = null
    }

    DisposableEffect(webView) {
        BrowserSession.attach(webView)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (settings.desktopUserAgent) Http.DESKTOP_UA else Http.PLAYER_UA
            appliedUa = userAgentString
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, settings.darkPages)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebBridge(), "PotatoBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val target = request?.url?.toString().orEmpty()
                if (adBlockEnabled.value && ServiceLocator.adBlocker.isBlocked(target)) {
                    BrowserSession.updateBlocked(ServiceLocator.adBlocker.blockedCount.get())
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url ?: return false
                val scheme = target.scheme?.lowercase().orEmpty()
                if (scheme != "http" && scheme != "https") return true
                if (adBlockEnabled.value && ServiceLocator.adBlocker.isBlocked(target.toString())) {
                    BrowserSession.updateBlocked(ServiceLocator.adBlocker.blockedCount.get())
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadError = null
                BrowserSession.newPage(url.orEmpty())
                BrowserSession.updateProgress(0.04f)
                if (view != null) {
                    BrowserSession.setPage(view.title ?: name, url.orEmpty())
                    inject(view)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                BrowserSession.updateProgress(1f)
                BrowserSession.updateCanGoBack(view?.canGoBack() == true)
                if (view != null) {
                    BrowserSession.setPage(view.title ?: name, url.orEmpty())
                    inject(view)
                }
                BrowserSession.refreshMenus()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadError = error?.description?.toString() ?: "Page failed to load."
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                BrowserSession.updateProgress(newProgress / 100f)
                BrowserSession.updateCanGoBack(view?.canGoBack() == true)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                BrowserSession.setPage(title.orEmpty(), view?.url.orEmpty())
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                showFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                hideFullscreen()
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.deny()
            }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_MENU ||
                    keyCode == android.view.KeyEvent.KEYCODE_INFO)
            ) {
                railCollapsed = !railCollapsed
                true
            } else {
                false
            }
        }

        val scriptHandler = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, sniffer, setOf("*"))
            }.getOrNull()
        } else null

        BrowserSession.setPage(name.ifBlank { startUrl }, startUrl)
        BrowserSession.markUserInteraction()
        webView.loadUrl(startUrl)

        onDispose {
            runCatching { scriptHandler?.remove() }
            BrowserSession.detach(webView)
            webView.removeJavascriptInterface("PotatoBridge")
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            runCatching { webView.destroy() }
        }
    }

    LaunchedEffect(settings.darkPages) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, settings.darkPages)
        }
    }

    LaunchedEffect(settings.desktopUserAgent) {
        val ua = if (settings.desktopUserAgent) Http.DESKTOP_UA else Http.PLAYER_UA
        if (appliedUa != null && appliedUa != ua) {
            webView.settings.userAgentString = ua
            webView.reload()
        }
        appliedUa = ua
    }

    LaunchedEffect(settings.cursorMode) {
        cursorOn = settings.cursorMode
        BrowserSession.setCursor(settings.cursorMode)
    }

    LaunchedEffect(dismissedStreams) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BrowserSession.events.collect { event ->
                when (event) {
                    is BrowserEvent.Stream -> {
                        // Only surface the stream. The site keeps playing its own
                        // video; the user can open the native player explicitly.
                        if (event.stream.url !in dismissedStreams) {
                            detected = event.stream
                        }
                    }
                    is BrowserEvent.Cursor -> cursorOn = event.on
                }
            }
        }
    }

    BackHandler {
        if (fullscreenView != null) {
            hideFullscreen()
        } else if (showMenus) {
            showMenus = false
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            nav.popBackStack()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(PotatoBg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU ||
                        event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_INFO)
                ) {
                    railCollapsed = !railCollapsed
                    true
                } else false
            }
    ) {
        // Collapsible rail — hidden by default for full-screen browsing
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(Color(0xFF0A0A0A))
                .padding(vertical = if (railCollapsed) 4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (railCollapsed) 4.dp else 6.dp)
        ) {
            // Ad-block status badge — always visible when rail is expanded
            if (!railCollapsed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (stats.blocked > 0) Icons.Filled.Shield else Icons.Outlined.Shield,
                        contentDescription = "Ads blocked",
                        tint = if (stats.blocked > 0) PotatoRating else PotatoTextDim,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${stats.blocked}",
                        color = if (stats.blocked > 0) PotatoRating else PotatoTextDim,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            RailButton(
                icon = Icons.Filled.ArrowBack,
                label = if (stats.canGoBack) "Back in page" else "Back",
                focusRequester = railFocus
            ) {
                if (webView.canGoBack()) webView.goBack() else nav.popBackStack()
            }
            RailButton(Icons.Filled.Refresh, "Reload") { webView.reload() }
            RailButton(Icons.Filled.Search, "Find streams") {
                BrowserSession.refreshMenus()
                Toast.makeText(context, "Scanning page for streams…", Toast.LENGTH_SHORT).show()
            }
            RailButton(
                icon = Icons.Filled.Mouse,
                label = if (cursorOn) "Cursor on" else "Cursor off",
                active = cursorOn
            ) {
                val next = !cursorOn
                cursorOn = next
                BrowserSession.setCursor(next)
                scope.launch { ServiceLocator.prefs.setSetting("cursorMode", next) }
            }
            RailButton(
                icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                label = "Favourite site",
                active = favorite
            ) {
                scope.launch {
                    ServiceLocator.prefs.toggleFavorite(favoriteId)
                    Toast.makeText(
                        context,
                        if (!favorite) "Site added to favourites" else "Site removed from favourites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            RailButton(Icons.Filled.Home, "Home") {
                nav.navigate("home") {
                    launchSingleTop = true
                    popUpTo("home") { inclusive = false }
                }
            }
            Spacer(Modifier.weight(1f))
            if (stats.blocked > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = "Ads blocked",
                        tint = PotatoRating,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "${stats.blocked}",
                        color = PotatoRating,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "MENU\nfor controls",
                color = Color(0xFF4A4A4A),
                fontSize = 8.sp,
                lineHeight = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )

            LinearProgressIndicator(
                progress = { stats.progress },
                color = PotatoAccent,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
            )

            if (loadError != null) {
                Surface(
                    color = Color(0xEE1A1A1A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text("This page did not load", color = PotatoText, style = MaterialTheme.typography.titleLarge)
                        Text(loadError ?: "", color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextRailAction("Retry") { webView.reload() }
                            TextRailAction("Back") { nav.popBackStack() }
                        }
                    }
                }
            }

            if (cursorOn && !showMenus) {
                Surface(
                    color = Color(0xDD101010),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        "Cursor: arrows move · OK clicks · BACK exits",
                        color = PotatoAccent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            detected?.let { stream ->
                Surface(
                    color = Color(0xF2141414),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PotatoAccent)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "${stream.kind.uppercase()} stream found",
                                color = PotatoText,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stream.url.substringBefore('?').takeLast(54),
                                color = PotatoTextDim,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        TextRailAction("Play") {
                            lastLaunchUrl = stream.url
                            lastLaunchAt = SystemClock.elapsedRealtime()
                            scope.launch { openPlayer(stream) }
                        }
                        IconButton(onClick = {
                            dismissedStreams = dismissedStreams + stream.url
                            detected = null
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = PotatoTextDim, modifier = Modifier.size(18.dp))
                        }
                        TextRailAction("Dismiss") {
                            dismissedStreams = dismissedStreams + stream.url
                            detected = null
                        }
                    }
                }
            }

            if (!railCollapsed && showMenus) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showMenus = false }
                )
                Surface(
                    color = Color(0xFF131313),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(380.dp)
                        .fillMaxHeight()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This page's player", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.weight(1f))
                            RailButton(Icons.Filled.Close, "Close", small = true) { showMenus = false }
                        }
                        Text(
                            "Tap a server or episode exactly as the site would. The new stream is picked up automatically.",
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(14.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (menus.servers.isNotEmpty()) {
                                item {
                                    Text("Servers", color = PotatoAccent, style = MaterialTheme.typography.labelLarge)
                                }
                                items(menus.servers, key = { "s-" + it.id }) { entry ->
                                    MenuRow(entry.label) {
                                        BrowserSession.clickServer(entry.id)
                                        showMenus = false
                                    }
                                }
                            }
                            if (menus.episodes.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Episodes", color = PotatoAccent, style = MaterialTheme.typography.labelLarge)
                                }
                                items(menus.episodes, key = { "e-" + it.id }) { entry ->
                                    MenuRow(entry.label) {
                                        BrowserSession.clickEpisode(entry.id)
                                        showMenus = false
                                    }
                                }
                            }
                            if (menus.isEmpty) {
                                item {
                                    Text(
                                        "Nothing detected yet. Start the site's own player, or use the search button in the rail, then open this panel again.",
                                        color = PotatoTextDim,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    small: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val size = if (small) 36.dp else 46.dp
    Box(
        Modifier
            .size(size)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> PotatoSurface
                    active -> PotatoAccent.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    focused -> PotatoAccent
                    active -> PotatoAccent.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused || active) PotatoAccent else PotatoTextDim,
            modifier = Modifier.size(if (small) 18.dp else 22.dp)
        )
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) PotatoSurface else Color.Transparent)
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = if (focused) PotatoAccent else PotatoText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TextRailAction(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) Color.Black else PotatoText,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) PotatoAccent else Color.White.copy(alpha = 0.12f))
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}