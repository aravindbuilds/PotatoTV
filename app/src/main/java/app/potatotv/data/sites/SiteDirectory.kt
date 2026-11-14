package app.potatotv.data.sites

import app.potatotv.data.model.DeeplistEntry
import app.potatotv.data.model.Site
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * One tolerant parser for every directory shape PotatoTV accepts:
 *
 *   { "sites": [ { "name": "...", "url": "https://...", "searchTemplate": "..." } ] }
 *   { "deeplinks": [ { "external": "...", "nativeId": "...", "searchTemplate": "..." } ] }
 *   { "site.tld": { "movie": "/movie/{tmdb}", "tv": "/tv/{tmdb}" } }   <- FMHYPlay
 *   a bare JSON array of either entry type
 */
object SiteDirectory {

    @Serializable
    private data class DirectorySite(
        val id: String? = null,
        val name: String = "",
        val url: String = "",
        val searchTemplate: String = "",
        val logo: String? = null
    )

    @Serializable
    private data class DirectoryFile(
        val sites: List<DirectorySite> = emptyList(),
        val deeplinks: List<DeeplistEntry> = emptyList()
    )

    data class Parsed(
        val sites: List<Site> = emptyList(),
        val deeplinks: List<DeeplistEntry> = emptyList(),
        /** FMHYPlay title-page sites, ready to show in the Sites grid. */
        val titleSites: List<Site> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(raw: String): Parsed {
        if (raw.isBlank()) return Parsed()
        return runCatching {
            val element = json.parseToJsonElement(raw)
            when (element) {
                is JsonArray -> {
                    val sites = decodeSites(element)
                    if (sites.isNotEmpty()) {
                        Parsed(sites = sites)
                    } else {
                        val deeplinks = decodeDeeplinks(element)
                        Parsed(
                            deeplinks = deeplinks,
                            titleSites = deeplinks.mapNotNull { deeplinkToSite(it) }
                        )
                    }
                }
                is JsonObject -> {
                    val hasDirectoryKeys =
                        element.containsKey("sites") || element.containsKey("deeplinks")
                    if (hasDirectoryKeys) {
                        val file = runCatching {
                            json.decodeFromJsonElement(DirectoryFile.serializer(), element)
                        }.getOrDefault(DirectoryFile())
                        Parsed(
                            sites = file.sites.mapNotNull { it.toSite() },
                            deeplinks = file.deeplinks.filter { it.nativeId.isNotBlank() },
                            titleSites = file.deeplinks
                                .filter { it.nativeId.isNotBlank() }
                                .mapNotNull { deeplinkToSite(it) }
                        )
                    } else {
                        Parsed(titleSites = parseDeeplinkMap(element))
                    }
                }
                else -> Parsed()
            }
        }.getOrDefault(Parsed())
    }

    private fun parseDeeplinkMap(obj: JsonObject): List<Site> = obj.mapNotNull { (host, value) ->
        if (host.isBlank() || host.startsWith("@") || host.startsWith("#")) return@mapNotNull null
        val entry = value as? JsonObject ?: return@mapNotNull null
        val movie = (entry["movie"] as? JsonPrimitive)?.stringValue().orEmpty()
        val tv = (entry["tv"] as? JsonPrimitive)?.stringValue().orEmpty()
        if (movie.isBlank() && tv.isBlank()) return@mapNotNull null
        val root = "https://$host"
        Site(
            id = "fmhy-" + host.hashCode(),
            name = host,
            url = root,
            searchTemplate = if (movie.isNotBlank()) root + movie else root + tv,
            movieTemplate = if (movie.isNotBlank()) root + movie else "",
            tvTemplate = if (tv.isNotBlank()) root + tv else ""
        )
    }

    private fun JsonPrimitive.stringValue(): String? = contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun decodeSites(array: JsonArray): List<Site> = runCatching {
        json.decodeFromJsonElement(ListSerializer(DirectorySite.serializer()), array)
            .mapNotNull { it.toSite() }
    }.getOrDefault(emptyList())

    private fun decodeDeeplinks(array: JsonArray): List<DeeplistEntry> = runCatching {
        json.decodeFromJsonElement(ListSerializer(DeeplistEntry.serializer()), array)
            .filter { it.nativeId.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun DirectorySite.toSite(): Site? {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isBlank() || !cleanUrl.startsWith("http")) return null
        return Site(
            id = id?.takeIf { it.isNotBlank() } ?: ("dir-" + (cleanUrl + cleanName).hashCode()),
            name = cleanName,
            url = cleanUrl,
            searchTemplate = searchTemplate.trim(),
            logo = logo,
            category = inferCategory(cleanName, cleanUrl)
        )
    }

    /** Turn an array-style deeplink entry into a browsable Site. */
    fun deeplinkToSite(entry: DeeplistEntry): Site? {
        if (entry.nativeId.isBlank()) return null
        val name = entry.external
            .substringAfter("://", entry.external)
            .substringBefore('?')
            .trimEnd('/')
            .substringAfterLast('/')
            .ifBlank { entry.nativeId }
        return Site(
            id = entry.nativeId,
            name = name.replaceFirstChar { it.uppercase() },
            url = "https://fmhy.net",
            searchTemplate = entry.searchTemplate,
            category = "general"
        )
    }

    /** Guess a site's category from its name / URL for popularity grouping. */
    private fun inferCategory(name: String, url: String): String {
        val text = (name + " " + url).lowercase()
        return when {
            text.contains("live") || text.contains("tv") || text.contains("channel") ||
                text.contains("iptv") || text.contains("twitch") -> "livetv"
            text.contains("sports") || text.contains("sport") ||
                text.contains("espn") || text.contains("fifa") || text.contains("nba") ||
                text.contains("ufc") || text.contains("mlb") || text.contains("nfl") ||
                text.contains("cricket") || text.contains("racing") -> "sports"
            text.contains("documentary") || text.contains("docu") ||
                text.contains("history") || text.contains("natgeo") ||
                text.contains("discovery") || text.contains("bbc") -> "documentary"
            text.contains("anime") || text.contains("crunchyroll") ||
                text.contains("funimation") || text.contains("hidive") -> "series"
            text.contains("movie") || text.contains("film") || text.contains("cinema") ||
                text.contains("netflix") || text.contains("hulu") ||
                text.contains("disney") || text.contains("tubi") ||
                text.contains("prime") || text.contains("vimeo") ||
                text.contains("peacock") || text.contains("paramount") -> "movie"
            else -> "general"
        }
    }
}
