package app.potatotv.data.cinemeta

import app.potatotv.data.model.CatalogResponse
import app.potatotv.data.model.MetaDetail
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.model.MetaResponse
import app.potatotv.data.net.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Cinemeta is the free, key-less metadata addon used by Stremio/FMHYPlay.
 * It provides posters, backdrops, logos, ratings, cast and episode lists.
 */
class CinemetaRepository {

    private val base = "https://v3-cinemeta.strem.io"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun catalog(
        type: String,
        catalog: String,
        extra: String? = null
    ): List<MetaPreview> = runCatching {
        val suffix = if (extra.isNullOrBlank()) "" else "/$extra"
        val body = Http.getText("$base/catalog/$type/$catalog$suffix.json")
        json.decodeFromString(CatalogResponse.serializer(), body).metas
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    suspend fun meta(type: String, id: String): MetaDetail? = runCatching {
        val body = Http.getText("$base/meta/$type/$id.json")
        json.decodeFromString(MetaResponse.serializer(), body).meta
    }.getOrNull()

    suspend fun search(query: String): List<MetaPreview> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8").replace("+", "%20")
        val movies = catalog("movie", "top", "search=$encoded")
        val series = catalog("series", "top", "search=$encoded")
        return (movies + series).distinctBy { it.id }
    }

    suspend fun homeRows(onRow: suspend (app.potatotv.data.model.MediaRow) -> Unit = {}): List<app.potatotv.data.model.MediaRow> {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val specs = listOf(
            Triple("Trending Now", "movie/top", "trending-movies"),
            Triple("Popular TV Shows", "series/top", "popular-series"),
            Triple("New Movies", "movie/year", "new-movies-$year"),
            Triple("Top Rated Movies", "movie/imdbRating", "top-movies"),
            Triple("New Series", "series/year", "new-series-$year"),
            Triple("Top Rated Series", "series/imdbRating", "top-series"),
            Triple("Action", "movie/top|genre=Action", "genre-action"),
            Triple("Comedy", "movie/top|genre=Comedy", "genre-comedy"),
            Triple("Animation", "movie/top|genre=Animation", "genre-animation"),
            Triple("Documentary", "series/top|genre=Documentary", "genre-documentary")
        )
        return coroutineScope {
            specs.map { (title, spec, key) ->
                async {
                    val parts = spec.split("|")
                    val type = parts[0].substringBefore("/")
                    val catalog = parts[0].substringAfter("/")
                    val extra = parts.getOrNull(1)?.let { splitExtra(it) }
                    val row = app.potatotv.data.model.MediaRow(title, catalog(type, catalog, extra), key)
                    if (row.items.isNotEmpty()) onRow(row)
                    row
                }
            }.awaitAll().filter { it.items.isNotEmpty() }
        }
    }

    private fun splitExtra(extra: String): String {
        val idx = extra.indexOf('=')
        if (idx <= 0) return extra
        return extra.substring(0, idx + 1) + URLEncoder.encode(extra.substring(idx + 1), "UTF-8").replace("+", "%20")
    }
}
