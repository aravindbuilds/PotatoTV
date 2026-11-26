package app.potatotv.data.tv

import android.content.Context
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

interface MetadataProvider {
    val id: String
    suspend fun load(force: Boolean = false): ProviderCatalogue
}

@Serializable
private data class OrgChannel(
    val id: String = "",
    val name: String = "",
    val alt_names: List<String> = emptyList(),
    val network: String? = null,
    val owners: List<String> = emptyList(),
    val country: String? = null,
    val categories: List<String> = emptyList(),
    val is_nsfw: Boolean = false,
    val website: String? = null,
    val closed: String? = null
)

@Serializable
private data class OrgFeed(
    val channel: String? = null,
    val id: String? = null,
    val name: String? = null,
    val alt_names: List<String> = emptyList(),
    val is_main: Boolean = false,
    val broadcast_area: List<String> = emptyList(),
    val timezones: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val format: String? = null
)

@Serializable
private data class OrgLogo(
    val channel: String? = null,
    val feed: String? = null,
    val in_use: Boolean = false,
    val url: String = ""
)

@Serializable
private data class OrgCategory(val id: String = "", val name: String = "")
@Serializable
private data class OrgLanguage(val code: String = "", val name: String = "")
@Serializable
private data class OrgTimezone(val id: String = "", val utc_offset: String = "")
@Serializable
private data class OrgCountry(val code: String = "", val name: String = "", val flag: String = "")

/**
 * Adapter for the public iptv-org API datasets. Only channel identity and
 * descriptive metadata are consumed; stream URLs stay with their playlists.
 */
class IptvOrgProvider(private val context: Context) : MetadataProvider {

    override val id = "iptv-org"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val cacheDir: File get() = File(context.filesDir, "tvintel/providers/iptv-org")

    private suspend fun dataset(name: String, force: Boolean): String = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val file = File(cacheDir, "$name.json")
        val fresh = !force && file.exists() &&
            System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60 * 1000L
        if (fresh) return@withContext file.readText()
        val status = File(cacheDir, "$name.status")
        val previous = runCatching { status.readText().split("\n") }.getOrElse { emptyList() }
        try {
            val result = Http.getResource(
                "https://iptv-org.github.io/api/$name.json",
                maxBytes = 32 * 1024 * 1024,
                ifNoneMatch = previous.getOrNull(0)?.ifBlank { null },
                ifModifiedSince = previous.getOrNull(1)?.ifBlank { null },
                userAgent = "PotatoTV/1.0 (+tv-intelligence)"
            )
            if (result.notModified && file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                return@withContext file.readText()
            }
            val body = result.body ?: error("Empty $name dataset")
            file.writeText(body)
            status.writeText(listOf(result.etag.orEmpty(), result.lastModified.orEmpty()).joinToString("\n"))
            body
        } catch (t: Throwable) {
            if (file.exists()) file.readText() else throw t
        }
    }

    override suspend fun load(force: Boolean): ProviderCatalogue = withContext(Dispatchers.Default) {
        val names = listOf("channels", "feeds", "logos", "categories", "languages", "countries", "timezones")
        val raw = coroutineScope {
            names.map { name -> async(Dispatchers.IO) { name to dataset(name, force) } }.awaitAll().toMap()
        }
        val rawChannels = raw.getValue("channels")
        val rawFeeds = raw.getValue("feeds")
        val rawLogos = raw.getValue("logos")
        val rawCategories = raw.getValue("categories")
        val rawLanguages = raw.getValue("languages")
        val rawCountries = raw.getValue("countries")
        val rawTimezones = raw.getValue("timezones")

        val channels = json.decodeFromString(ListSerializer(OrgChannel.serializer()), rawChannels)
        val feeds = json.decodeFromString(ListSerializer(OrgFeed.serializer()), rawFeeds)
        val logos = json.decodeFromString(ListSerializer(OrgLogo.serializer()), rawLogos)
        val categories = json.decodeFromString(ListSerializer(OrgCategory.serializer()), rawCategories)
        val languages = json.decodeFromString(ListSerializer(OrgLanguage.serializer()), rawLanguages)
        val countries = json.decodeFromString(ListSerializer(OrgCountry.serializer()), rawCountries)
        val timezones = json.decodeFromString(ListSerializer(OrgTimezone.serializer()), rawTimezones)

        val feedsByChannel = feeds.filter { !it.channel.isNullOrBlank() }.groupBy { it.channel!! }
        val logosByChannel = logos.filter { !it.channel.isNullOrBlank() }.groupBy { it.channel!! }
        val categoryNames = categories.associate { it.id to it.name }
        val languageNames = languages.associate { it.code to it.name }
        val timezoneNames = timezones.associate { it.id to it.utc_offset }
        val now = System.currentTimeMillis()

        fun provenance(field: String, sourceId: String, verified: Boolean, confidence: Float = 1f) =
            FieldProvenance(field, id, sourceId, now, verified, confidence)

        val result = channels.asSequence()
            .filter { !it.is_nsfw && it.id.isNotBlank() }
            .map { channel ->
                val channelFeeds = (feedsByChannel[channel.id].orEmpty()).map { feed ->
                    ChannelFeed(
                        id = feed.id.orEmpty(),
                        name = feed.name ?: feed.id.orEmpty(),
                        isMain = feed.is_main,
                        languages = feed.languages,
                        timezones = feed.timezones,
                        broadcastAreas = feed.broadcast_area,
                        format = feed.format,
                        logo = logosByChannel[channel.id].orEmpty()
                            .firstOrNull { it.in_use && (feed.id == null || it.feed == feed.id) }?.url
                            ?.takeIf { it.isNotBlank() }
                    )
                }
                val inUseLogos = logosByChannel[channel.id].orEmpty()
                    .filter { it.in_use && it.url.isNotBlank() }
                    .map { it.url }
                    .distinct()
                val mainLogo = inUseLogos.firstOrNull()
                val primaryLanguage = channelFeeds.firstOrNull { it.isMain }?.languages?.firstOrNull()
                    ?: channelFeeds.firstOrNull()?.languages?.firstOrNull()
                CanonicalChannel(
                    id = channel.id,
                    name = channel.name,
                    aliases = channel.alt_names,
                    categories = channel.categories.map { categoryNames[it] ?: it },
                    country = channel.country,
                    languages = listOfNotNull(primaryLanguage).distinct(),
                    region = channelFeeds.firstOrNull()?.broadcastAreas?.firstOrNull(),
                    timezone = channelFeeds.firstOrNull()?.timezones?.firstOrNull(),
                    network = channel.network,
                    owners = channel.owners,
                    logo = mainLogo,
                    alternativeLogos = inUseLogos.drop(1),
                    website = channel.website,
                    qualityClaims = channelFeeds.mapNotNull { it.format }.distinct(),
                    feeds = channelFeeds,
                    epgIds = listOf(channel.id),
                    provenance = buildList {
                        add(provenance("externalId", channel.id, true))
                        add(provenance("name", channel.id, true))
                        if (channel.country != null) add(provenance("country", channel.id, true))
                        if (mainLogo != null) add(provenance("logo", channel.id, true))
                        if (channel.network != null) add(provenance("network", channel.id, true))
                        if (channel.website != null) add(provenance("website", channel.id, true))
                        if (channelFeeds.isNotEmpty()) add(provenance("feeds", channel.id, true))
                    },
                    originalNames = listOf(channel.name) + channel.alt_names,
                    matchSources = listOf(id),
                    status = "matched",
                    matchConfidence = 1f
                )
            }
            .toList()

        ProviderCatalogue(
            providerId = id,
            channels = result,
            feeds = feedsByChannel.mapValues { entries -> entries.value.map { feed ->
                ChannelFeed(
                    id = feed.id.orEmpty(),
                    name = feed.name ?: feed.id.orEmpty(),
                    languages = feed.languages,
                    timezones = feed.timezones,
                    broadcastAreas = feed.broadcast_area,
                    format = feed.format
                )
            } },
            logos = logosByChannel.mapValues { entries -> entries.value.map { it.url } },
            categories = categoryNames,
            languages = languageNames,
            countries = countries.associate { it.code to it.name },
            timezones = timezoneNames,
            fetchedAt = now
        )
    }
}
