package app.potatotv.data.iptv

import app.potatotv.data.model.Channel

/**
 * Tolerant M3U/M3U8 parser. Supports tvg attributes, group-title, logos,
 * per-stream VLC user-agent/referrer options and plain single-stream files.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val extInfRegex = Regex("""#EXTINF:\s*(-?\d+(?:\.\d+)?)\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String, playlistId: String, playlistName: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pending: Pending? = null
        var userAgent: String? = null
        var referrer: String? = null
        var groupOverride: String? = null

        fun flush(url: String) {
            val p = pending ?: Pending(name = url.substringAfterLast('/').ifBlank { url })
            val name = p.name.ifBlank { url.substringAfterLast('/').ifBlank { "Channel" } }
            val id = p.tvgId?.takeIf { it.isNotBlank() } ?: "$playlistId:${url.hashCode()}"
            channels += Channel(
                id = id,
                name = name,
                url = url,
                logo = p.logo?.takeIf { it.isNotBlank() },
                group = groupOverride ?: p.group,
                referrer = referrer,
                userAgent = userAgent,
                playlistId = playlistId,
                playlistName = playlistName
            )
            pending = null
            userAgent = null
            referrer = null
            groupOverride = null
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTM3U", true) -> Unit
                line.startsWith("#EXTINF", true) -> {
                    val match = extInfRegex.find(line)
                    val attrs = attrRegex.findAll(match?.groupValues?.getOrNull(2).orEmpty())
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val displayName = match?.groupValues?.getOrNull(2)
                        ?.substringAfterLast(',')?.trim().orEmpty()
                    pending = Pending(
                        name = attrs["tvg-name"].orEmpty().ifBlank { displayName },
                        tvgId = attrs["tvg-id"],
                        logo = attrs["tvg-logo"] ?: attrs["logo"],
                        group = attrs["group-title"]
                    )
                }
                line.startsWith("#EXTGRP:", true) -> {
                    groupOverride = line.substringAfter(':').trim().ifBlank { null }
                }
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val value = line.substringAfter(':').trim()
                    when {
                        value.startsWith("http-user-agent=", true) ->
                            userAgent = value.substringAfter('=').trim().ifBlank { null }
                        value.startsWith("http-referrer=", true) || value.startsWith("http-referer=", true) ->
                            referrer = value.substringAfter('=').trim().ifBlank { null }
                    }
                }
                line.startsWith("#") -> Unit
                line.startsWith("http://", true) || line.startsWith("https://", true) ||
                    line.startsWith("rtmp://", true) || line.startsWith("rtsp://", true) ||
                    line.startsWith("udp://", true) -> flush(line)
                else -> {
                    if (pending != null) flush(line)
                }
            }
        }
        return channels.distinctBy { it.url }
    }

    private data class Pending(
        val name: String,
        val tvgId: String? = null,
        val logo: String? = null,
        val group: String? = null
    )
}
