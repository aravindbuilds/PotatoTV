package app.potatotv.data.net

import app.potatotv.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .proxy(proxy)
        .build()

    fun currentProxy(): Proxy? = proxy

    private fun buildClient(proxy: Proxy?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
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

    /** Bounded, conditional resource fetch used by metadata and EPG loaders. */
    data class Resource(
        val body: String?,
        val notModified: Boolean,
        val etag: String?,
        val lastModified: String?,
        val code: Int
    )

    suspend fun getResource(
        url: String,
        maxBytes: Int = 40 * 1024 * 1024,
        ifNoneMatch: String? = null,
        ifModifiedSince: String? = null,
        userAgent: String = DESKTOP_UA
    ): Resource = withContext(Dispatchers.IO) {
        val builder = request(url, userAgent = userAgent).newBuilder()
        ifNoneMatch?.takeIf { it.isNotBlank() }?.let { builder.header("If-None-Match", it) }
        ifModifiedSince?.takeIf { it.isNotBlank() }?.let { builder.header("If-Modified-Since", it) }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 304) {
                return@use Resource(null, true, response.header("ETag"), response.header("Last-Modified"), 304)
            }
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val stream = response.body?.byteStream() ?: error("Empty response for $url")
            val buffer = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxBytes) error("Response too large (> ${maxBytes / 1024 / 1024} MB) for $url")
                buffer.write(chunk, 0, read)
            }
            val charset = response.body?.contentType()?.charset() ?: Charsets.UTF_8
            Resource(
                body = String(buffer.toByteArray(), charset),
                notModified = false,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                code = response.code
            )
        }
    }

    data class BinaryResource(
        val bytes: ByteArray?,
        val notModified: Boolean,
        val etag: String?,
        val lastModified: String?,
        val code: Int
    )

    /**
     * Bounded binary fetch. Setting acceptEncoding to "identity" prevents
     * OkHttp's transparent gzip decoding so callers can stream-decompress a
     * .gz EPG file with a GZIPInputStream instead of buffering the whole guide.
     */
    suspend fun getBinaryResource(
        url: String,
        maxBytes: Int = 32 * 1024 * 1024,
        ifNoneMatch: String? = null,
        ifModifiedSince: String? = null,
        acceptEncoding: String? = null,
        userAgent: String = DESKTOP_UA
    ): BinaryResource = withContext(Dispatchers.IO) {
        val builder = request(url, userAgent = userAgent).newBuilder()
        ifNoneMatch?.takeIf { it.isNotBlank() }?.let { builder.header("If-None-Match", it) }
        ifModifiedSince?.takeIf { it.isNotBlank() }?.let { builder.header("If-Modified-Since", it) }
        acceptEncoding?.let { builder.header("Accept-Encoding", it) }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 304) {
                return@use BinaryResource(null, true, response.header("ETag"), response.header("Last-Modified"), 304)
            }
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val stream = response.body?.byteStream() ?: error("Empty response for $url")
            val buffer = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxBytes) error("Response too large for EPG source")
                buffer.write(chunk, 0, read)
            }
            BinaryResource(
                bytes = buffer.toByteArray(),
                notModified = false,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                code = response.code
            )
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
