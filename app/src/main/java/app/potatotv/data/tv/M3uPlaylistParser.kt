package app.potatotv.data.tv

import java.util.Locale

/**
 * Tolerant M3U/M3U8 parser. Every stream becomes a [PlaylistEntry] that keeps
 * the original provider metadata; it is never treated as a channel identity.
 */
object M3uPlaylistParser {

    private val attrRegex = Regex("""([A-Za-z0-9_:-]+)\s*=\s*"([^"]*)"""")
    private val extInfRegex = Regex(
        """^\s*#EXTINF\s*:\s*(-?\d+(?:\.\d+)?)\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private data class Pending(
        val attrs: Map<String, String>,
        val displayName: String
    )

    fun parse(
        text: String,
        playlistId: String,
        playlistName: String,
        importedAt: Long = System.currentTimeMillis()
    ): ParsedPlaylist {
        val entries = mutableListOf<PlaylistEntry>()
        val warnings = mutableListOf<String>()
        var pending: Pending? = null
        var userAgent: String? = null
        var referrer: String? = null
        var groupOverride: String? = null
        var epgUrl: String? = null
        val seenUrls = HashSet<String>()

        fun flush(line: String) {
            val p = pending
            val rawName = when {
                p == null -> line.substringAfterLast('/').ifBlank { line }
                p.attrs["tvg-name"].isNullOrBlank() -> p.displayName
                else -> p.attrs.getValue("tvg-name")
            }.trim()
            val name = rawName.ifBlank { "Channel ${entries.size + 1}" }
            val attrs = p?.attrs.orEmpty()
            val declaredId = attrs["tvg-id"]?.trim()?.takeIf { it.isNotBlank() }
            val url = line.trim()
            if (!SafeUrls.isPlayable(url)) {
                warnings += "Skipped non-playable entry: ${SafeUrls.redact(url)}"
                pending = null
                userAgent = null
                referrer = null
                groupOverride = null
                return
            }
            if (!seenUrls.add(url)) {
                warnings += "Duplicate stream URL skipped: ${SafeUrls.redact(url)}"
                pending = null
                userAgent = null
                referrer = null
                groupOverride = null
                return
            }
            val headers = buildMap {
                referrer?.let { put("Referer", it) }
                userAgent?.let { put("User-Agent", it) }
            }
            val id = "entry-" + integerToId(playlistId.hashCode()) + "-" + integerToId(url.hashCode())
            entries += PlaylistEntry(
                id = id,
                playlistId = playlistId,
                playlistName = playlistName,
                originalName = name,
                normalizedName = ChannelIdentity.normalize(name),
                streamUrl = url,
                tvgId = declaredId,
                logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    ?: attrs["logo"]?.takeIf { it.isNotBlank() },
                groupTitle = groupOverride?.takeIf { it.isNotBlank() }
                    ?: attrs["group-title"]?.takeIf { it.isNotBlank() },
                language = attrs["tvg-language"]?.takeIf { it.isNotBlank() }
                    ?: attrs["language"]?.takeIf { it.isNotBlank() },
                country = attrs["tvg-country"]?.takeIf { it.isNotBlank() }
                    ?: attrs["country"]?.takeIf { it.isNotBlank() },
                channelNumber = attrs["tvg-chno"]?.takeIf { it.isNotBlank() },
                qualityClaim = attrs["tvg-quality"]?.takeIf { it.isNotBlank() },
                epgUrl = attrs["tvg-url"]?.takeIf { it.isNotBlank() },
                attributes = attrs.mapKeys { it.key.lowercase(Locale.ROOT) },
                headers = headers,
                importedAt = importedAt
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
                line.startsWith("#EXTM3U", true) -> {
                    val attrs = attrRegex.findAll(line).associate {
                        it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2]
                    }
                    epgUrl = attrs["x-tvg-url"]?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTINF", true) -> {
                    val match = extInfRegex.find(line)
                    val rest = match?.groupValues?.getOrNull(2).orEmpty()
                    val attrs = attrRegex.findAll(rest).associate {
                        it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2]
                    }
                    val display = rest.substringAfterLast(',').trim()
                    pending = Pending(attrs, display)
                }
                line.startsWith("#EXTGRP:", true) -> {
                    groupOverride = line.substringAfter(':').trim().ifBlank { null }
                }
                line.startsWith("#EXTVLCOPT:", true) || line.startsWith("#KODIPROP:", true) -> {
                    val value = line.substringAfter(':').trim()
                    when {
                        value.startsWith("http-user-agent=", true) ->
                            userAgent = value.substringAfter('=').trim().ifBlank { null }
                        value.startsWith("http-referrer=", true) || value.startsWith("http-referer=", true) ->
                            referrer = value.substringAfter('=').trim().ifBlank { null }
                    }
                }
                line.startsWith("#") -> Unit
                else -> flush(line)
            }
        }
        if (pending != null) warnings += "Trailing EXTINF without a stream URL was ignored"
        if (entries.isEmpty() && text.isNotBlank() && !text.contains("#EXTM3U", true)) {
            warnings += "This does not look like an M3U playlist"
        }
        return ParsedPlaylist(entries, epgUrl, warnings)
    }

    private fun integerToId(value: Int): String =
        java.lang.Integer.toUnsignedString(value, 36)
}
