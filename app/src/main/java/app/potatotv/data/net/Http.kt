package app.potatotv.data.net

import app.potatotv.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object Http {

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val TV_UA =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val PLAYER_UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var proxy: Proxy? = null

    @Volatile
    private var client: OkHttpClient = buildClient(null)

    val http: OkHttpClient get() = client

    val probe: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun configureProxy(settings: AppSettings) {
        // WARP takes precedence over the regular proxy when enabled
        if (settings.warpEnabled && settings.warpHost.isNotEmpty() && settings.warpPort in 1..65535) {
            val type = if (settings.warpType.equals("socks5", true) || settings.warpType.equals("socks", true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            val next = Proxy(type, InetSocketAddress(settings.warpHost.trim(), settings.warpPort))
            if (next != proxy) {
                proxy = next
                client = buildWarpClient(next)
            }
            return
        }

        val host = settings.proxyHost.trim()
        val port = settings.proxyPort
        val next = if (settings.proxyEnabled && host.isNotEmpty() && port in 1..65535) {
            val type = if (settings.proxyType.equals("socks", true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(host, port))
        } else {
            null
        }
        if (next != proxy) {
            proxy = next
            client = buildClient(next)
        }
    }

    /** Build an OkHttp client configured for WARP-like proxy routing.
     *  WARP routes traffic through Cloudflare's network without requiring a VPN permission.
     *  When proxy is null, uses direct connection.
     */
    private fun buildWarpClient(proxy: Proxy?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .proxy(proxy)
        .build()

    fun currentProxy(): Proxy? = proxy

    private fun buildClient(proxy: Proxy?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .apply { if (proxy != null) proxy(proxy) }
        .build()

    fun request(url: String, headers: Map<String, String> = emptyMap(), userAgent: String = DESKTOP_UA): Request {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        headers.forEach { (k, v) -> if (v.isNotBlank()) builder.header(k, v) }
        return builder.build()
    }

    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = DESKTOP_UA
    ): String = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers, userAgent)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string() ?: error("Empty response for $url")
        }
    }

    suspend fun getTextOrNull(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = DESKTOP_UA
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(request(url, headers, userAgent)).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = DESKTOP_UA
    ): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers, userAgent)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.bytes() ?: error("Empty response for $url")
        }
    }

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = DESKTOP_UA
    ): String = withContext(Dispatchers.IO) {
        val request = request(url, headers, userAgent).newBuilder()
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string() ?: ""
        }
    }

    /** Peek at a candidate stream URL to classify it as HLS, DASH or progressive. */
    suspend fun sniffStreamType(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = DESKTOP_UA
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = request(url, headers, userAgent).newBuilder()
                .header("Range", "bytes=0-2048")
                .build()
            probe.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@use null
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                val body = response.body?.string().orEmpty()
                when {
                    url.contains(".m3u8", true) || contentType.contains("mpegurl") ||
                        body.trimStart().startsWith("#EXTM3U") -> "hls"
                    url.contains(".mpd", true) || contentType.contains("dash+xml") ||
                        body.trimStart().startsWith("<?xml") && body.contains("<MPD") -> "dash"
                    contentType.startsWith("video/") -> "progressive"
                    else -> null
                }
            }
        }.getOrNull()
    }
}
