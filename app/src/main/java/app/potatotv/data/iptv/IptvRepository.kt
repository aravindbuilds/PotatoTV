package app.potatotv.data.iptv

import android.content.Context
import app.potatotv.data.model.Channel
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class IptvCategory(val id: String, val name: String)
data class IptvCountry(val code: String, val name: String, val flag: String)

data class IptvData(
    val channels: List<Channel>,
    val categories: List<IptvCategory>,
    val countries: List<IptvCountry>
)

@Serializable
private data class ChannelDto(
    val id: String = "",
    val name: String = "",
    val alt_names: List<String> = emptyList(),
    val country: String? = null,
    val categories: List<String> = emptyList(),
    val logo: String? = null,
    val is_nsfw: Boolean = false
)

@Serializable
private data class StreamDto(
    val channel: String? = null,
    val title: String? = null,
    val url: String = "",
    val quality: String? = null,
    val referrer: String? = null,
    val user_agent: String? = null
)

@Serializable
private data class FeedDto(
    val channel: String? = null,
    val languages: List<String> = emptyList(),
    val timezones: List<String> = emptyList(),
    val format: String? = null
)

@Serializable
private data class CategoryDto(val id: String = "", val name: String = "")
@Serializable
private data class CountryDto(val code: String = "", val name: String = "", val flag: String = "")
@Serializable
private data class LanguageDto(val code: String = "", val name: String = "")

/**
 * Loads the public iptv-org catalogue (31k channels / 18k streams) and keeps a
 * 24 hour disk cache. Only channels that actually have a stream are exposed.
 */
class IptvRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val mutex = Mutex()

    @Volatile
    private var memory: IptvData? = null

    suspend fun load(force: Boolean = false): IptvData = mutex.withLock {
        if (!force) memory?.let { return it }
        val data = withContext(Dispatchers.Default) { build(force) }
        memory = data
        data
    }

    private suspend fun build(force: Boolean): IptvData {
        val dir = File(context.filesDir, "iptv").apply { mkdirs() }
        val channelsFile = File(dir, "channels.json")
        val streamsFile = File(dir, "streams.json")
        val categoriesFile = File(dir, "categories.json")
        val countriesFile = File(dir, "countries.json")
        val feedsFile = File(dir, "feeds.json")
        val languagesFile = File(dir, "languages.json")

        val fresh = !force && channelsFile.exists() &&
            System.currentTimeMillis() - channelsFile.lastModified() < 24 * 60 * 60 * 1000L

        val payloads: Payloads = if (fresh) {
            readCache(channelsFile, streamsFile, categoriesFile, countriesFile, feedsFile, languagesFile)
        } else {
            try {
                val fetched = coroutineScope {
                    val c = async { download("https://iptv-org.github.io/api/channels.json") }
                    val s = async { download("https://iptv-org.github.io/api/streams.json") }
                    val cat = async { download("https://iptv-org.github.io/api/categories.json") }
                    val co = async { download("https://iptv-org.github.io/api/countries.json") }
                    val f = async { runCatching { download("https://iptv-org.github.io/api/feeds.json") }.getOrDefault("[]") }
                    val lang = async { runCatching { download("https://iptv-org.github.io/api/languages.json") }.getOrDefault("[]") }
                    Payloads(c.await(), s.await(), cat.await(), co.await(), f.await(), lang.await())
                }
                channelsFile.writeText(fetched.channels)
                streamsFile.writeText(fetched.streams)
                categoriesFile.writeText(fetched.categories)
                countriesFile.writeText(fetched.countries)
                feedsFile.writeText(fetched.feeds)
                languagesFile.writeText(fetched.languages)
                fetched
            } catch (t: Throwable) {
                if (!channelsFile.exists()) throw t
                readCache(channelsFile, streamsFile, categoriesFile, countriesFile, feedsFile, languagesFile)
            }
        }

        val channels = json.decodeFromString(ListSerializerHolder.channels, payloads.channels)
        val streams = json.decodeFromString(ListSerializerHolder.streams, payloads.streams)
        val categories = json.decodeFromString(ListSerializerHolder.categories, payloads.categories)
        val countries = json.decodeFromString(ListSerializerHolder.countries, payloads.countries)
        val feeds = json.decodeFromString(ListSerializerHolder.feeds, payloads.feeds)
        val languages = json.decodeFromString(ListSerializerHolder.languages, payloads.languages)

        return join(channels, streams, categories, countries, feeds, languages)
    }

    private fun readCache(
        channels: File,
        streams: File,
        categories: File,
        countries: File,
        feeds: File,
        languages: File
    ) = Payloads(
        channels = channels.readText(),
        streams = streams.readText(),
        categories = categories.readText(),
        countries = countries.readText(),
        feeds = if (feeds.exists()) feeds.readText() else "[]",
        languages = if (languages.exists()) languages.readText() else "[]"
    )

    private data class Payloads(
        val channels: String,
        val streams: String,
        val categories: String,
        val countries: String,
        val feeds: String = "[]",
        val languages: String = "[]"
    )

    private suspend fun download(url: String): String {
        val body = Http.getText(url, userAgent = "PotatoTV/1.0 (+https://github.com)")
        if (body.length < 32) error("Unexpected empty payload from $url")
        return body
    }

    private fun join(
        channels: List<ChannelDto>,
        streams: List<StreamDto>,
        categories: List<CategoryDto>,
        countries: List<CountryDto>,
        feeds: List<FeedDto>,
        languages: List<LanguageDto>
    ): IptvData {
        val categoryNames = categories.associate { it.id to it.name }
        val countryNames = countries.associate { it.code to it }
        val languageNames = languages.associate { it.code to it.name }
        val feedsByChannel = feeds.filter { !it.channel.isNullOrBlank() }.groupBy { it.channel!! }
        val streamsByChannel = streams.asSequence()
            .filter { !it.channel.isNullOrBlank() && it.url.isNotBlank() }
            .groupBy { it.channel!! }

        val result = ArrayList<Channel>(channels.size)
        for (c in channels) {
            if (c.is_nsfw) continue
            val list = streamsByChannel[c.id] ?: continue
            val ordered = list.sortedByDescending { qualityScore(it.quality) }
            val first = ordered.first()
            val categoryIds = c.categories
            val channelLanguages = feedsByChannel[c.id].orEmpty()
                .asSequence().flatMap { it.languages.asSequence() }
                .map { languageNames[it] ?: it }
                .distinct().take(4).toList()
            result += Channel(
                id = c.id,
                name = c.name,
                url = first.url,
                urls = ordered.map { it.url }.distinct(),
                logo = c.logo?.takeIf { it.isNotBlank() },
                group = categoryIds.firstOrNull()?.let { categoryNames[it] },
                categories = categoryIds,
                country = c.country,
                countryName = c.country?.let { countryNames[it]?.name },
                languages = channelLanguages,
                quality = first.quality,
                referrer = first.referrer,
                userAgent = first.user_agent
            )
        }
        result.sortBy { it.name.lowercase() }

        val usedCategories = result.asSequence().flatMap { it.categories.asSequence() }.toSet()
        return IptvData(
            channels = result,
            categories = categories
                .filter { it.id in usedCategories }
                .map { IptvCategory(it.id, it.name) }
                .sortedBy { it.name },
            countries = countries
                .map { IptvCountry(it.code, it.name, it.flag) }
                .sortedBy { it.name }
        )
    }

    private fun qualityScore(quality: String?): Int {
        val q = quality?.lowercase().orEmpty()
        return when {
            q.contains("2160") || q.contains("4k") -> 4
            q.contains("1080") -> 3
            q.contains("720") -> 2
            q.contains("576") || q.contains("480") -> 1
            else -> 0
        }
    }

    private object ListSerializerHolder {
        val channels = kotlinx.serialization.builtins.ListSerializer(ChannelDto.serializer())
        val streams = kotlinx.serialization.builtins.ListSerializer(StreamDto.serializer())
        val categories = kotlinx.serialization.builtins.ListSerializer(CategoryDto.serializer())
        val countries = kotlinx.serialization.builtins.ListSerializer(CountryDto.serializer())
        val feeds = kotlinx.serialization.builtins.ListSerializer(FeedDto.serializer())
        val languages = kotlinx.serialization.builtins.ListSerializer(LanguageDto.serializer())
    }
}
