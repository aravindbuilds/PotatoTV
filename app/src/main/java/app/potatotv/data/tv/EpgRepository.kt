package app.potatotv.data.tv

import android.content.Context
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

/** XMLTV schedule ingestion, normalisation, indexing and Now/Next queries. */
class EpgRepository(private val context: Context) {

    companion object {
        val DEFAULT_SOURCES = listOf(
            EpgSource(
                id = "iptv-epg-in",
                name = "India · IPTV-EPG.org",
                url = "https://iptv-epg.org/files/epg-in.xml.gz",
                country = "IN",
                refreshHours = 6,
                note = "Public XMLTV, channel ids follow the iptv-org catalogue."
            ),
            EpgSource(
                id = "epgshare-in1",
                name = "India · epgshare01 IN1 (optional)",
                url = "https://epgshare01.online/epgshare01/epg_ripper_IN1.xml.gz",
                country = "IN",
                refreshHours = 12,
                enabled = false,
                note = "Public XMLTV; channel ids differ from iptv-org, enable if your playlist uses them."
            )
        )
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mutex = Mutex()
    private val dir: File get() = File(context.filesDir, "tvintel/epg")

    @Volatile
    private var sources: List<EpgSource> = DEFAULT_SOURCES

    @Volatile
    private var snapshots: Map<String, EpgSnapshot> = emptyMap()

    @Volatile
    private var statuses: Map<String, SourceStatus> = emptyMap()

    @Volatile
    private var indexes: Map<String, Map<String, List<Programme>>> = emptyMap()

    private var cacheKey: String? = null
    private var cacheAt = 0L
    private var cachedNowNext: Map<String, NowNext> = emptyMap()

    fun setSources(customSources: List<EpgSource>) {
        sources = (DEFAULT_SOURCES.filter { default -> customSources.none { it.id == default.id } } + customSources)
            .map { source -> customSources.firstOrNull { it.id == source.id } ?: source }
    }

    fun allSources(): List<EpgSource> = sources

    suspend fun restore() = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext
        val loaded = mutableMapOf<String, EpgSnapshot>()
        dir.listFiles { file -> file.name.startsWith("snapshot-") && file.name.endsWith(".json") }
            .orEmpty().forEach { file ->
                runCatching {
                    json.decodeFromString(EpgSnapshot.serializer(), file.readText())
                }.onSuccess { loaded[it.sourceId] = it }
            }
        val statusFile = File(dir, "status.json")
        if (statusFile.exists()) {
            runCatching {
                json.decodeFromString(
                    MapSerializer(String.serializer(), SourceStatus.serializer()),
                    statusFile.readText()
                )
            }.onSuccess { statuses = it }
        }
        snapshots = loaded
        rebuildIndexes()
    }

    suspend fun load(force: Boolean = false): List<EpgSnapshot> = mutex.withLock {
        withContext(Dispatchers.IO) { dir.mkdirs() }
        for (source in sources.filter { it.enabled }) {
            val status = statuses[source.id] ?: SourceStatus(source.id)
            val stale = status.lastSuccess == 0L ||
                System.currentTimeMillis() - status.lastSuccess > source.refreshHours * 60 * 60 * 1000L
            if (!force && !stale && snapshots.containsKey(source.id)) continue
            val attempt = status.copy(lastAttempt = System.currentTimeMillis())
            try {
                val check = SafeUrls.checkFetchable(source.url)
                if (!check.ok) error(check.reason ?: "Unsafe EPG URL")
                val response = Http.getBinaryResource(
                    url = source.url,
                    maxBytes = 12 * 1024 * 1024,
                    ifNoneMatch = status.etag,
                    ifModifiedSince = status.lastModified,
                    acceptEncoding = "identity",
                    userAgent = "PotatoTV/1.0 (+tv-intelligence)"
                )
                if (response.notModified) {
                    statuses = statuses + (source.id to attempt.copy(lastSuccess = System.currentTimeMillis()))
                    continue
                }
                val bytes = response.bytes ?: error("Empty EPG response")
                val stream = if (source.url.endsWith(".gz", true)) {
                    GZIPInputStream(ByteArrayInputStream(bytes), 64 * 1024)
                } else ByteArrayInputStream(bytes)
                val parsed = withContext(Dispatchers.Default) {
                    EpgXmlParser.parse(
                        InputStreamReader(stream, Charsets.UTF_8),
                        source.id,
                        maxProgrammes = 120_000
                    )
                }
                val snapshot = EpgSnapshot(
                    sourceId = source.id,
                    fetchedAt = System.currentTimeMillis(),
                    channels = parsed.channels,
                    programmes = parsed.programmes,
                    quality = parsed.quality.copy(fetchedAt = System.currentTimeMillis())
                )
                val file = File(dir, "snapshot-${source.id}.json")
                val tmp = File(dir, "snapshot-${source.id}.tmp")
                tmp.writeText(json.encodeToString(EpgSnapshot.serializer(), snapshot))
                if (file.exists()) file.delete()
                tmp.renameTo(file)
                snapshots = snapshots + (source.id to snapshot)
                statuses = statuses + (source.id to SourceStatus(
                    id = source.id,
                    lastSuccess = System.currentTimeMillis(),
                    lastAttempt = attempt.lastAttempt,
                    failureCount = 0,
                    etag = response.etag,
                    lastModified = response.lastModified
                ))
            } catch (t: Throwable) {
                statuses = statuses + (source.id to status.copy(
                    lastAttempt = attempt.lastAttempt,
                    failureCount = status.failureCount + 1,
                    lastError = t.message?.let { SafeUrls.redact(it).take(200) }
                ))
            }
        }
        writeStatuses()
        rebuildIndexes()
        snapshots.values.toList()
    }

    private fun writeStatuses() {
        val file = File(dir, "status.json")
        if (!dir.exists()) return
        runCatching {
            file.writeText(json.encodeToString(MapSerializer(String.serializer(), SourceStatus.serializer()), statuses))
        }
    }

    private fun rebuildIndexes() {
        indexes = snapshots.mapValues { (_, snapshot) ->
            snapshot.programmes.groupBy { it.channelId }.mapValues { entry ->
                entry.value.sortedBy { it.startUtc }
            }
        }
    }

    fun allProgrammes(): List<Programme> = snapshots.values.flatMap { it.programmes }

    /** EPG ids whose display name matches a channel name exactly. */
    fun epgIdsForName(name: String): List<String> {
        val target = ChannelIdentity.normalize(name)
        return snapshots.values.asSequence()
            .flatMap { it.channels.asSequence() }
            .filter { channel -> channel.names.any { ChannelIdentity.normalize(it) == target } }
            .map { it.id }
            .distinct()
            .toList()
    }

    fun schedule(epgId: String, from: Long, to: Long, limit: Int = 200): List<Programme> {
        val result = ArrayList<Programme>()
        indexes.values.forEach { byChannel ->
            val list = byChannel[epgId].orEmpty()
            val start = lowerBound(list, from)
            for (i in start until list.size) {
                if (list[i].startUtc > to) break
                result += list[i]
                if (result.size >= limit) return result
            }
        }
        return result.sortedBy { it.startUtc }
    }

    fun nowNext(
        epgIds: List<String>,
        at: Long = System.currentTimeMillis(),
        cacheTtlMs: Long = 30_000L
    ): Map<String, NowNext> {
        val key = epgIds.distinct().sorted().joinToString(",")
        val bucket = at / 60_000L
        val composite = "$key|$bucket"
        if (composite == cacheKey && System.currentTimeMillis() - cacheAt < cacheTtlMs) return cachedNowNext
        val result = epgIds.associateWith { id -> computeNowNext(id, at) }
        cacheKey = composite
        cacheAt = System.currentTimeMillis()
        cachedNowNext = result
        return result
    }

    private fun computeNowNext(epgId: String, at: Long): NowNext =
        EpgQueries.nowNext(epgId, indexes.values.map { it[epgId].orEmpty() }, at)

    private fun lowerBound(list: List<Programme>, time: Long): Int {
        var low = 0
        var high = list.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (list[mid].startUtc < time) low = mid + 1 else high = mid
        }
        return low
    }

    fun search(query: String, from: Long, to: Long, limit: Int = 80): List<Programme> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("""\s+""")).filter { it.length > 1 }
        if (terms.isEmpty()) return emptyList()
        return allProgrammes().asSequence()
            .filter { it.startUtc <= to && it.endUtc >= from }
            .filter { programme ->
                val haystack = (listOf(programme.title, programme.subtitle, programme.description) +
                    programme.categories + programme.credits.values.flatten())
                    .joinToString(" ").lowercase(Locale.ROOT)
                terms.all { it in haystack }
            }
            .sortedBy { it.startUtc }
            .take(limit)
            .toList()
    }

    fun sourceStatuses(): List<SourceStatus> = sources.map { source ->
        statuses[source.id] ?: SourceStatus(source.id).copy(
            stale = snapshots[source.id] == null
        )
    }.map { status ->
        val source = sources.first { it.id == status.id }
        status.copy(stale = status.lastSuccess == 0L ||
            System.currentTimeMillis() - status.lastSuccess > source.refreshHours * 60 * 60 * 1000L)
    }

    fun qualityReports(): List<EpgQualityReport> = snapshots.values.map { it.quality }
}
