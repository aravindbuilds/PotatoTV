package app.potatotv.data.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
data class ChannelFilter(
    val query: String = "",
    val category: String? = null,
    val country: String? = null,
    val language: String? = null,
    val epgOnly: Boolean = false,
    val unmatchedOnly: Boolean = false
)

/**
 * Local, provider-independent intelligence layer. It deliberately exposes
 * repository methods instead of a network API: the Android client is the only
 * consumer, and no private playlist URL ever leaves the device.
 */
class TvIntelligenceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mutex = Mutex()
    private val directory: File get() = File(context.filesDir, "tvintel")

    val provider = IptvOrgProvider(context)
    val epg = EpgRepository(context)

    @Volatile
    private var catalogue = ProviderCatalogue("iptv-org")

    @Volatile
    private var matcher = ChannelMatcher(emptyList())

    @Volatile
    private var entries: List<PlaylistEntry> = emptyList()

    @Volatile
    private var channels: List<CanonicalChannel> = emptyList()

    @Volatile
    private var reviews: List<MatchReview> = emptyList()

    @Volatile
    private var overrides = ChannelOverrides()

    suspend fun initialize() {
        withContext(Dispatchers.IO) { directory.mkdirs() }
        epg.restore()
        restore()
        refreshCatalogue()
    }

    private fun restore() {
        val entriesFile = File(directory, "entries.json")
        if (entriesFile.exists()) {
            entries = runCatching {
                json.decodeFromString(ListSerializer(PlaylistEntry.serializer()), entriesFile.readText())
            }.getOrDefault(emptyList())
        }
        val overridesFile = File(directory, "overrides.json")
        if (overridesFile.exists()) {
            overrides = runCatching {
                json.decodeFromString(ChannelOverrides.serializer(), overridesFile.readText())
            }.getOrDefault(ChannelOverrides())
        }
        val reviewsFile = File(directory, "reviews.json")
        if (reviewsFile.exists()) {
            reviews = runCatching {
                json.decodeFromString(ListSerializer(MatchReview.serializer()), reviewsFile.readText())
            }.getOrDefault(emptyList())
        }
        val channelsFile = File(directory, "channels.json")
        if (channelsFile.exists()) {
            channels = runCatching {
                json.decodeFromString(ListSerializer(CanonicalChannel.serializer()), channelsFile.readText())
            }.getOrDefault(emptyList())
        }
    }

    private fun persist(name: String, value: String) {
        val file = File(directory, name)
        val tmp = File(directory, "$name.tmp")
        tmp.writeText(value)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    suspend fun refreshCatalogue(force: Boolean = false) = mutex.withLock {
        catalogue = runCatching { provider.load(force) }
            .onFailure { /* Cached provider data from previous runs stays usable. */ }
            .getOrElse { catalogue }
        matcher = ChannelMatcher(catalogue.channels)
        rebuildLocked()
    }

    suspend fun refreshEpg(force: Boolean = false) {
        epg.load(force)
        rebuildLockedInLock()
    }

    private suspend fun rebuildLockedInLock() = mutex.withLock { rebuildLocked() }

    /** Convert every stored playlist entry into canonical channels again. */
    private fun rebuildLocked() {
        val byId = catalogue.channels.associateBy { it.id }
        val grouped = LinkedHashMap<String, MutableList<PlaylistEntry>>()
        val reviewList = mutableListOf<MatchReview>()

        entries.forEach { entry ->
            val best = matcher.bestAutomatic(entry, overrides)
            val manualId = listOf(entry.id, entry.originalName, entry.tvgId.orEmpty())
                .firstNotNullOfOrNull { overrides.mappings[it] }
            val providerId = when {
                manualId != null -> manualId
                best != null && best.confidence == MatchConfidence.HIGH -> best.channel.id
                else -> null
            }
            val separated = providerId != null && "${providerId}|${entry.tvgId.orEmpty()}" in overrides.separated
            val key = if (providerId != null && !separated) "prov:$providerId" else localKey(entry)
            grouped.getOrPut(key) { mutableListOf() } += entry

            if (best != null && best.confidence != MatchConfidence.HIGH) {
                val alternatives = matcher.match(entry, overrides).take(4).map { candidate ->
                    ReviewCandidate(
                        channelId = candidate.channel.id,
                        name = candidate.channel.name,
                        score = candidate.score,
                        confidence = candidate.confidence.name,
                        reasons = candidate.reasons
                    )
                }
                reviewList += MatchReview(
                    key = entry.id,
                    entryName = entry.originalName,
                    tvgId = entry.tvgId,
                    candidates = alternatives
                )
            }
        }

        val result = grouped.map { (key, group) ->
            val first = group.first()
            val matchedId = key.removePrefix("prov:")
            val template = byId[matchedId]
            val matched = template != null && key.startsWith("prov:")
            val epgIds = linkedSetOf<String>()
            if (matched) epgIds += matchedId
            group.forEach { entry ->
                entry.tvgId?.let { epgIds += it }
                epgIds += epg.epgIdsForName(entry.originalName)
            }
            val streams = group.map { entry ->
                StreamSource(
                    url = entry.streamUrl,
                    playlistId = entry.playlistId,
                    playlistName = entry.playlistName,
                    entryId = entry.id,
                    feedId = null,
                    title = entry.originalName,
                    qualityClaim = entry.qualityClaim ?: entry.attributes["tvg-quality"],
                    referrer = entry.headers["Referer"],
                    userAgent = entry.headers["User-Agent"],
                    importedAt = entry.importedAt
                )
            }.distinctBy { it.url }

            val claims = buildSet {
                group.forEach { entry ->
                    addAll(ChannelIdentity.qualityClaims(entry.originalName))
                    entry.qualityClaim?.let { add(it.lowercase(Locale.ROOT)) }
                }
                if (matched) template!!.qualityClaims.forEach { add(it) }
            }.toList()

            val provenance = buildList {
                if (matched) addAll(template!!.provenance)
                group.forEach { entry ->
                    add(
                        FieldProvenance(
                            field = "stream",
                            source = entry.playlistName,
                            sourceId = entry.id,
                            retrievedAt = entry.importedAt,
                            verified = false,
                            confidence = 1f
                        )
                    )
                }
            }

            CanonicalChannel(
                id = if (matched) "ch:$matchedId" else key,
                name = template?.name ?: group.bestName(),
                aliases = (template?.aliases.orEmpty() + group.map { it.originalName }).distinct(),
                categories = (template?.categories.orEmpty() + group.mapNotNull { it.groupTitle }).distinct(),
                country = template?.country ?: first.country,
                countryName = template?.country?.let { catalogue.countries[it] },
                languages = (template?.languages.orEmpty() + listOfNotNull(first.language)).distinct(),
                region = template?.region,
                timezone = template?.timezone,
                network = template?.network,
                owners = template?.owners.orEmpty(),
                logo = template?.logo ?: first.logoUrl,
                alternativeLogos = (template?.alternativeLogos.orEmpty() +
                    group.mapNotNull { it.logoUrl }).distinct(),
                description = template?.description,
                website = template?.website,
                qualityClaims = claims,
                feeds = template?.feeds.orEmpty(),
                streams = streams,
                epgIds = epgIds.toList(),
                provenance = provenance,
                originalNames = group.map { it.originalName }.distinct(),
                playlistIds = group.map { it.playlistId }.distinct(),
                matchSources = (template?.matchSources.orEmpty() + group.map { it.playlistName }).distinct(),
                status = if (matched) "matched" else if (reviewList.any { it.key in group.map { e -> e.id } }) "review" else "unmatched",
                matchConfidence = if (matched) 1f else 0f
            )
        }
        channels = result.sortedBy { it.name.lowercase(Locale.ROOT) }
        reviews = reviewList
        persist("channels.json", json.encodeToString(ListSerializer(CanonicalChannel.serializer()), channels))
        persist("reviews.json", json.encodeToString(ListSerializer(MatchReview.serializer()), reviews))
    }

    private fun List<PlaylistEntry>.bestName(): String =
        maxByOrNull { it.originalName.length }?.originalName ?: "Unknown channel"

    private fun localKey(entry: PlaylistEntry): String {
        val idPart = entry.tvgId?.takeIf { it.isNotBlank() }?.let { "id:${it.lowercase(Locale.ROOT)}" }
            ?: "name:${entry.normalizedName}"
        return "local:$idPart|${entry.country?.lowercase(Locale.ROOT).orEmpty()}"
    }

    /** Parse and ingest a playlist's raw text. Returns entry count and warnings. */
    suspend fun importM3u(
        raw: String,
        playlistId: String,
        playlistName: String
    ): ParsedPlaylist = withContext(Dispatchers.Default) {
        val parsed = M3uPlaylistParser.parse(raw, playlistId, playlistName)
        mutex.withLock {
            val existing = entries.associateBy { it.id }.toMutableMap()
            parsed.entries.forEach { existing[it.id] = it }
            entries = existing.values.toList()
            persist("entries.json", json.encodeToString(ListSerializer(PlaylistEntry.serializer()), entries))
            rebuildLocked()
        }
        parsed
    }

    suspend fun importEntries(newEntries: List<PlaylistEntry>) = mutex.withLock {
        val existing = entries.associateBy { it.id }.toMutableMap()
        newEntries.forEach { existing[it.id] = it }
        entries = existing.values.toList()
        persist("entries.json", json.encodeToString(ListSerializer(PlaylistEntry.serializer()), entries))
        rebuildLocked()
    }

    suspend fun removePlaylist(playlistId: String) = mutex.withLock {
        entries = entries.filterNot { it.playlistId == playlistId }
        persist("entries.json", json.encodeToString(ListSerializer(PlaylistEntry.serializer()), entries))
        rebuildLocked()
    }

    fun allChannels(): List<CanonicalChannel> = channels

    fun filterChannels(filter: ChannelFilter): List<CanonicalChannel> {
        val query = filter.query.trim().lowercase(Locale.ROOT)
        return channels.filter { channel ->
            (query.isBlank() || listOfNotNull(
                channel.name, channel.network, channel.description, channel.countryName
            ).plus(channel.aliases).plus(channel.categories).plus(channel.originalNames)
                .any { it.contains(query, true) }) &&
                (filter.category == null || channel.categories.any { it.equals(filter.category, true) }) &&
                (filter.country == null || channel.country.equals(filter.country, true)) &&
                (filter.language == null || channel.languages.any { it.contains(filter.language!!, true) }) &&
                (!filter.epgOnly || channel.epgIds.isNotEmpty()) &&
                (!filter.unmatchedOnly || !channel.isMatched)
        }
    }

    fun channel(id: String): CanonicalChannel? = channels.firstOrNull { it.id == id }

    fun streams(id: String): List<StreamSource> = channel(id)?.streams.orEmpty()

    /** Health is provider-specific state; a temporary failure never deletes a stream. */
    suspend fun recordStreamHealthBatch(results: Map<String, Pair<Boolean, Long>>) = mutex.withLock {
        if (results.isEmpty()) return@withLock
        val checkedAt = System.currentTimeMillis()
        channels = channels.map { channel ->
            if (channel.streams.none { it.url in results }) channel else channel.copy(
                streams = channel.streams.map { stream ->
                    val result = results[stream.url] ?: return@map stream
                    stream.copy(
                        health = StreamHealth(
                            available = result.first,
                            checkedAt = checkedAt,
                            latencyMs = result.second,
                            errorCategory = if (result.first) null else "probe_failed"
                        )
                    )
                }
            )
        }
        persist("channels.json", json.encodeToString(ListSerializer(CanonicalChannel.serializer()), channels))
    }

    fun nowNext(ids: List<String>): Map<String, NowNext> {
        val epgIds = ids.mapNotNull { id -> channel(id)?.epgIds?.firstOrNull() }
        val byEpg = epg.nowNext(epgIds)
        return ids.associateWith { id ->
            val epgId = channel(id)?.epgIds?.firstOrNull()
            byEpg[epgId] ?: NowNext(id)
        }
    }

    suspend fun setManualMapping(entryKey: String, channelId: String) = mutex.withLock {
        overrides = overrides.copy(mappings = overrides.mappings + (entryKey to channelId))
        persist("overrides.json", json.encodeToString(ChannelOverrides.serializer(), overrides))
        rebuildLocked()
    }

    suspend fun clearManualMapping(entryKey: String) = mutex.withLock {
        overrides = overrides.copy(mappings = overrides.mappings - entryKey)
        persist("overrides.json", json.encodeToString(ChannelOverrides.serializer(), overrides))
        rebuildLocked()
    }

    suspend fun separate(entryKey: String, providerId: String) = mutex.withLock {
        overrides = overrides.copy(separated = overrides.separated + "$providerId|$entryKey")
        persist("overrides.json", json.encodeToString(ChannelOverrides.serializer(), overrides))
        rebuildLocked()
    }

    fun reviews(): List<MatchReview> = reviews

    fun stats(): IntelligenceStats {
        val withEpg = channels.count { it.epgIds.isNotEmpty() }
        return IntelligenceStats(
            playlists = entries.map { it.playlistId }.distinct().size,
            entries = entries.size,
            channels = channels.size,
            streams = channels.sumOf { it.streams.size },
            matched = channels.count { it.isMatched },
            unmatched = channels.count { !it.isMatched },
            withLogo = channels.count { !it.logo.isNullOrBlank() },
            withDescription = channels.count { !it.description.isNullOrBlank() },
            withEpg = withEpg,
            withoutEpg = channels.size - withEpg,
            reviewCandidates = reviews.size,
            epgSources = epg.allSources().count { it.enabled },
            staleEpgSources = epg.sourceStatuses().count { it.stale }
        )
    }
}
