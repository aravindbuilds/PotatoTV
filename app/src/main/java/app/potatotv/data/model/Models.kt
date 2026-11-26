package app.potatotv.data.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

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
    val rating: Int = 0,
    /** FMHY wiki order — lower means it appeared earlier in Streaming.md. */
    val order: Int = Int.MAX_VALUE,
    /** Sites flagged 🌟/⭐ in the public FMHY wiki. */
    val starred: Boolean = false,
    /** Comes from the primary title-template directory used for playback. */
    val primaryTemplate: Boolean = false
)

/** FMHY grades out of 18, shown to users as a friendlier 0–10 score. */
val Site.ratingOutOf10: Int
    get() = if (rating <= 0) 0 else ((rating / 18f) * 10f).roundToInt().coerceIn(1, 10)

/** True for sites that can deep-link a title from the metadata ids. */
val Site.canPlayTitle: Boolean
    get() = movieTemplate.isNotBlank() || tvTemplate.isNotBlank() ||
        episodeTemplate.isNotBlank() || animeTemplate.isNotBlank()

/**
 * Ranking: user favourites and frequently opened sites first, then FMHY stars
 * and 10/10 sites, with the exact Streaming.md order breaking remaining ties.
 */
fun Site.popularityScore(favoriteIds: Set<String>): Int {
    val favorite = id in favoriteIds
    val usage = minOf(usageCount, 50) * 10_000
    val star = if (starred) 5_000 else 0
    val grade = ratingOutOf10 * 1_000
    val wiki = maxOf(0, 10_000 - order)
    return (if (favorite) 1_000_000 else 0) + usage + star + grade + wiki
}

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
    val playlistName: String? = null,
    /** Human-readable languages from provider feeds or playlist attributes. */
    val languages: List<String> = emptyList(),
    val description: String? = null,
    /** Id of the canonical intelligence record, when metadata has been built. */
    val canonicalId: String? = null,
    /** Locally recorded popularity and recency. Never supplied by a provider. */
    val viewCount: Int = 0,
    val lastViewedAt: Long = 0L
) {
    fun allSources(): List<String> = (listOf(url) + urls).filter { it.isNotBlank() }.distinct()
}

@Serializable
data class ChannelView(val count: Int = 0, val lastViewedAt: Long = 0L)

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
    /** Master switch for the detected-stream popup. Can be turned off forever. */
    val showStreamPrompts: Boolean = true,
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
