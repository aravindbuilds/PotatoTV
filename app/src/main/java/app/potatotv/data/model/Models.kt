package app.potatotv.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MetaPreview(
    val id: String = "",
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList()
) {
    fun posterLarge(): String? = poster?.replace("/small/", "/medium/")
    fun backdropLarge(): String? = background?.replace("/medium/", "/large/")
}

@Serializable
data class MetaVideo(
    val id: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val name: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null
)

@Serializable
data class MetaDetail(
    val id: String = "",
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val videos: List<MetaVideo> = emptyList(),
    @kotlinx.serialization.SerialName("moviedb_id")
    val moviedbId: Int? = null
) {
    fun toPreview() = MetaPreview(
        id = id, type = type, name = name, poster = poster, background = background,
        logo = logo, description = description, releaseInfo = releaseInfo,
        imdbRating = imdbRating, genres = genres
    )

    fun seasons(): List<Int> = videos.mapNotNull { it.season }.distinct().sorted()
}

@Serializable
data class CatalogResponse(val metas: List<MetaPreview> = emptyList())

@Serializable
data class MetaResponse(val meta: MetaDetail? = null)

data class MediaRow(
    val title: String,
    val items: List<MetaPreview>,
    val key: String
)

@Serializable
data class Site(
    val id: String,
    val name: String,
    val url: String,
    /** Optional template, e.g. https://site.example/search?q={q} or /title/{imdb}. */
    val searchTemplate: String = "",
    val builtIn: Boolean = false,
    val logo: String? = null,
    /** FMHY deeplink templates, filled from the source directory. */
    val movieTemplate: String = "",
    val tvTemplate: String = "",
    val episodeTemplate: String = "",
    val animeTemplate: String = "",
    /** Category for popularity ranking and grouping: movie / series / sports / documentary / livetv / general. */
    val category: String = "general",
    /** Optional favicon URL shown on the site card. */
    val favicon: String? = null,
    /** Times the site has been opened — drives popularity sorting. */
    val usageCount: Int = 0,
    /** FMHY Stream Site Grading score, 0–18. Zero means not graded. */
    val rating: Int = 0
)

/** A deeplink entry mapping an external URL to a native site/search template.
 *  Used by FMHY sources repo `deeplinks.json`: e.g. {"watch": "https://youtube.com/watch?v=..."}. */
@Serializable
data class DeeplistEntry(
    val external: String,
    val nativeId: String,
    val searchTemplate: String
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val kind: String,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val fileName: String = "",
    /** 18+ lists stay hidden until the user enables them in Settings. */
    val adult: Boolean = false
) {
    companion object {
        const val M3U_URL = "m3u_url"
        const val M3U_TEXT = "m3u_text"
        const val XTREAM = "xtream"
        const val STREAM = "stream"
    }
}

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val urls: List<String> = emptyList(),
    val logo: String? = null,
    val group: String? = null,
    val categories: List<String> = emptyList(),
    val country: String? = null,
    val countryName: String? = null,
    val quality: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null
) {
    fun allSources(): List<String> = (listOf(url) + urls).filter { it.isNotBlank() }.distinct()
}

@Serializable
data class Progress(
    val key: String,
    val title: String,
    val poster: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
    val imdbId: String? = null,
    val type: String = "movie",
    val season: Int? = null,
    val episode: Int? = null
) {
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

@Serializable
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class SubtitleTrack(
    val name: String,
    val language: String,
    val url: String,
    val cues: List<SubtitleCue> = emptyList(),
    val isExternal: Boolean = true
)

@Serializable
data class AppSettings(
    val adBlock: Boolean = true,
    val adBlockRedirectPrevent: Boolean = true,
    val adBlockSiteHiding: Boolean = false,
    val darkPages: Boolean = true,
    val desktopUserAgent: Boolean = true,
    val autoPlayStreams: Boolean = true,
    val cursorMode: Boolean = false,
    val openSubtitlesKey: String = "",
    val simklClientId: String = "",
    val simklToken: String = "",
    val updateRepo: String = "",
    val siteDirectoryUrl: String = "",
    /** "cinemeta" — Stremio/Cinemeta metadata; "fmhy-sources" — pull deeplinks + movie lists from FMHY. */
    val catalogProvider: String = "fmhy-sources",
    /** Reveals the baked-in 18+ playlists. Off by default. */
    val adultSources: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyType: String = "http",
    val warpEnabled: Boolean = false,
    val warpHost: String = "",
    val warpPort: Int = 0,
    val warpType: String = "socks5",
    val subtitleSize: Float = 18f,
    val subtitleBottom: Float = 7f,
    val subtitleDelayMs: Int = 0,
    val preferredQuality: String = "auto",
    val iptvLastChecked: Long = 0
)
