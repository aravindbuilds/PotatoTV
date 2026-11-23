package app.potatotv.data.sources

import app.potatotv.data.model.Playlist

/**
 * The FMHYPlay source list, baked into the APK.
 *
 * Format (mirrors the upstream file):
 *   label|url        a playlist (M3U / M3U8)
 *   label|url|18     a playlist shown only after 18+ is enabled in Settings
 *   @deeplinks|url   a table of title-page addresses (movie / tv templates per site)
 *
 * These are third-party lists. PotatoTV does not host or index their content.
 */
object BakedSources {

    const val DEEPLINKS_URL =
        "https://raw.githubusercontent.com/fmhyplay/sources/main/deeplinks.json"

    /** Richer title/episode/anime templates from AnantPlay. */
    const val ANANT_DEEPLINKS_URL =
        "https://raw.githubusercontent.com/AnantPlay/sources/refs/heads/main/deeplinks.json"

    data class Entry(
        val label: String,
        val url: String,
        val adult: Boolean = false
    )

    val playlists: List<Entry> = listOf(
        Entry(
            label = "Verified",
            url = "https://raw.githubusercontent.com/fmhyplay/sources/main/lists/verified.m3u",
            adult = true
        ),
        Entry(
            label = "adultiptv.net",
            url = "https://raw.githubusercontent.com/fmhyplay/sources/main/lists/adultiptv.m3u",
            adult = true
        ),
        Entry(
            label = "Izanami",
            url = "https://raw.githubusercontent.com/fmhyplay/sources/main/lists/izanami.m3u",
            adult = true
        ),
        Entry(
            label = "AdihashBCC",
            url = "https://raw.githubusercontent.com/fmhyplay/sources/main/lists/adihashbcc.m3u",
            adult = true
        ),
        Entry(
            label = "6j9",
            url = "https://raw.githubusercontent.com/fmhyplay/sources/main/lists/6j9.m3u",
            adult = true
        )
    )

    /** Stable ids so a re-seed never duplicates a playlist the user already has. */
    fun toPlaylists(): List<Playlist> = playlists.map { entry ->
        Playlist(
            id = "fmhy-" + entry.url.substringAfterLast('/').substringBefore('.').lowercase(),
            name = entry.label,
            kind = Playlist.M3U_URL,
            url = entry.url,
            adult = entry.adult
        )
    }
}
