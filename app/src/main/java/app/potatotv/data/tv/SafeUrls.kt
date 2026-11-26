package app.potatotv.data.tv

import java.net.InetAddress
import java.net.URI

/** Validation for untrusted playlist and EPG URLs. */
object SafeUrls {

    data class Check(val ok: Boolean, val reason: String? = null)

    fun isHttp(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    /**
     * Blocks loopback, private, link-local and metadata-style destinations in
     * URLs that the app will fetch. Stream URLs are only played, never fetched
     * by a metadata request, so other player schemes are allowed there.
     */
    fun checkFetchable(url: String): Check {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
            ?: return Check(false, "Malformed URL")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return Check(false, "Only http(s) URLs can be fetched")
        val host = uri.host ?: return Check(false, "URL has no host")
        val lower = host.lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local") ||
            lower.endsWith(".internal")
        ) {
            return Check(false, "Local destinations are not allowed")
        }
        return runCatching {
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) return@runCatching Check(false, "Host could not be resolved")
            val unsafe = addresses.any { address ->
                address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
                    address.isAnyLocalAddress || address.isMulticastAddress ||
                    address.hostAddress.orEmpty().startsWith("169.254.")
            }
            if (unsafe) Check(false, "Private network destinations are not allowed") else Check(true)
        }.getOrElse { Check(false, "Host could not be resolved") }
    }

    /** True for schemes the player can reasonably handle. */
    fun isPlayable(url: String): Boolean {
        val scheme = runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme in setOf("http", "https", "rtmp", "rtmps", "rtsp", "udp")
    }

    /** Removes user info and sensitive query values before a URL is logged. */
    fun redact(url: String): String = runCatching {
        val uri = URI(url)
        val hostPart = buildString {
            uri.userInfo?.let { append("***@") }
            uri.host?.let { append(it) }
            if (uri.port != -1) append(":").append(uri.port)
        }
        val query = uri.rawQuery?.let { raw ->
            raw.split("&").joinToString("&") { part ->
                val key = part.substringBefore("=").lowercase()
                if (key.contains("token") || key.contains("pass") || key.contains("key") ||
                    key.contains("secret") || key.contains("auth")
                ) "$key=***" else part
            }
        }
        val path = uri.rawPath.orEmpty().let { p ->
            if (p.contains("/live/", ignoreCase = true)) {
                p.replace(Regex("/live/[^/]+/[^/]+/"), "/live/***/***/")
            } else p
        }
        "${uri.scheme}://$hostPart$path" + (query?.let { "?$it" }.orEmpty())
    }.getOrElse { "<invalid url>" }
}
