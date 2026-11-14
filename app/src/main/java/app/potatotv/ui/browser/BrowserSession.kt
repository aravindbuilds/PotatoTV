package app.potatotv.ui.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import app.potatotv.data.net.Http
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.ref.WeakReference

data class MenuEntry(val id: String, val label: String, val kind: String = "server")

data class BrowserMenus(
    val servers: List<MenuEntry> = emptyList(),
    val episodes: List<MenuEntry> = emptyList(),
    val url: String = ""
) {
    val isEmpty: Boolean get() = servers.isEmpty() && episodes.isEmpty()
}

data class PageInfo(val title: String = "", val url: String = "")

data class SniffedStream(
    val url: String,
    val kind: String,
    val source: String,
    val foundAt: Long = System.currentTimeMillis()
)

data class BrowserStats(
    val blocked: Long = 0,
    val streams: Int = 0,
    val progress: Float = 0f,
    val canGoBack: Boolean = false
)

sealed interface BrowserEvent {
    data class Stream(val stream: SniffedStream) : BrowserEvent
    data class Cursor(val on: Boolean) : BrowserEvent
}

/**
 * Lives outside the screen so a player Activity on top can keep talking to the
 * page underneath: pressing "Servers" in ExoPlayer clicks the site's own button,
 * and the stream the site then starts is handed back to the same player.
 */
object BrowserSession {

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    private val _menus = MutableStateFlow(BrowserMenus())
    val menus = _menus.asStateFlow()

    private val _page = MutableStateFlow(PageInfo())
    val page = _page.asStateFlow()

    private val _stats = MutableStateFlow(BrowserStats())
    val stats = _stats.asStateFlow()

    private val frameMenus = mutableMapOf<String, BrowserMenus>()
    private var lastMenuUrl: String = ""

    fun attach(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun detach(webView: WebView) {
        if (webViewRef?.get() === webView) {
            webViewRef = null
            frameMenus.clear()
            _menus.value = BrowserMenus()
        }
    }

    fun webView(): WebView? = webViewRef?.get()

    fun isActive(): Boolean = webView() != null

    fun updateProgress(progress: Float) {
        _stats.value = _stats.value.copy(progress = progress.coerceIn(0f, 1f))
    }

    fun updateCanGoBack(canGoBack: Boolean) {
        _stats.value = _stats.value.copy(canGoBack = canGoBack)
    }

    fun updateBlocked(count: Long) {
        _stats.value = _stats.value.copy(blocked = count)
    }

    fun newPage(url: String) {
        if (url != lastMenuUrl) {
            lastMenuUrl = url
            frameMenus.clear()
            _menus.value = BrowserMenus(url = url)
        }
        _stats.value = _stats.value.copy(canGoBack = webView()?.canGoBack() == true)
    }

    fun setPage(title: String, url: String) {
        if (url.isNotBlank()) _page.value = PageInfo(title, url)
    }

    fun setMenus(menus: BrowserMenus, frame: String, url: String) {
        if (url.isNotBlank() && url != lastMenuUrl && lastMenuUrl.isNotBlank()) {
            lastMenuUrl = url
            frameMenus.clear()
        }
        frameMenus[frame] = menus
        val servers = LinkedHashMap<String, MenuEntry>()
        val episodes = LinkedHashMap<String, MenuEntry>()
        frameMenus.values.forEach { item ->
            item.servers.forEach { servers.putIfAbsent(it.id, it) }
            item.episodes.forEach { episodes.putIfAbsent(it.id, it) }
        }
        _menus.value = BrowserMenus(servers.values.toList(), episodes.values.toList(), url)
    }

    fun updateStreamCount(count: Int) {
        _stats.value = _stats.value.copy(streams = count)
    }

    fun handleJson(raw: String) {
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "stream" -> {
                    val url = obj["url"]?.jsonPrimitive?.content ?: return
                    val kind = obj["kind"]?.jsonPrimitive?.content ?: "hls"
                    val source = obj["source"]?.jsonPrimitive?.content ?: ""
                    if (url.startsWith("http")) {
                        _stats.value = _stats.value.copy(streams = _stats.value.streams + 1)
                        _events.tryEmit(BrowserEvent.Stream(SniffedStream(url, kind, source)))
                    }
                }
                "menus" -> {
                    val frame = obj["frame"]?.jsonPrimitive?.content ?: "top"
                    val url = obj["url"]?.jsonPrimitive?.content ?: ""
                    val servers = obj["servers"]?.jsonArray?.mapNotNull {
                        val entry = it.jsonObject
                        val id = entry["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val label = entry["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        MenuEntry(id, label, "server")
                    } ?: emptyList()
                    val episodes = obj["episodes"]?.jsonArray?.mapNotNull {
                        val entry = it.jsonObject
                        val id = entry["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val label = entry["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        MenuEntry(id, label, "episode")
                    } ?: emptyList()
                    setMenus(BrowserMenus(servers, episodes, url), frame, url)
                }
                "page" -> {
                    setPage(
                        obj["title"]?.jsonPrimitive?.content.orEmpty(),
                        obj["url"]?.jsonPrimitive?.content.orEmpty()
                    )
                }
                "cursor" -> {
                    val on = obj["on"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    _events.tryEmit(BrowserEvent.Cursor(on))
                }
            }
        }
    }

    fun clickServer(id: String) = evaluate("window.__potatoClickServer('${escape(id)}');")

    fun clickEpisode(id: String) = evaluate("window.__potatoClickEpisode('${escape(id)}');")

    fun refreshMenus() = evaluate("window.__potatoCollect && window.__potatoCollect();")

    fun setCursor(on: Boolean) = evaluate("window.__potatoSetCursor && window.__potatoSetCursor($on);")

    fun evaluate(script: String) {
        val view = webView() ?: return
        view.post { runCatching { view.evaluateJavascript(script, null) } }
    }

    fun headersFor(url: String, source: String, userAgent: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (source.isNotBlank()) {
            map["Referer"] = source
            runCatching {
                val uri = java.net.URI(source)
                map["Origin"] = "${uri.scheme}://${uri.host}"
            }
        }
        val cookie = runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
            ?: runCatching { android.webkit.CookieManager.getInstance().getCookie(source) }.getOrNull()
        if (!cookie.isNullOrBlank()) map["Cookie"] = cookie
        if (userAgent.isNotBlank()) map["User-Agent"] = userAgent else map["User-Agent"] = Http.PLAYER_UA
        return map
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")
}

class WebBridge {
    @JavascriptInterface
    fun onEvent(json: String) {
        BrowserSession.handleJson(json)
    }

    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("PotatoWeb", message)
    }
}
