package app.potatotv.ui.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.potatotv.ServiceLocator
import app.potatotv.data.iptv.IptvData
import app.potatotv.data.model.Channel
import app.potatotv.data.model.ChannelView
import app.potatotv.data.model.Playlist
import app.potatotv.data.net.Http
import app.potatotv.data.tv.CanonicalChannel
import app.potatotv.data.tv.IntelligenceStats
import app.potatotv.data.tv.NowNext
import app.potatotv.data.tv.PlaylistEntry
import app.potatotv.data.tv.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

enum class IptvTab { Live, Guide, Playlists, Starred, Working }
enum class SortMode { Popularity, Recent, Alphabetical }

class IptvViewModel : ViewModel() {

    var data by mutableStateOf<IptvData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var tab by mutableStateOf(IptvTab.Live)
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var country by mutableStateOf<String?>(null)
    var language by mutableStateOf<String?>(null)
    var sort by mutableStateOf(SortMode.Popularity)
    var workingOnly by mutableStateOf(false)
    var tick by mutableLongStateOf(System.currentTimeMillis())
        private set

    var viewCounts by mutableStateOf<Map<String, ChannelView>>(emptyMap())
        private set
    var nowNext by mutableStateOf<Map<String, NowNext>>(emptyMap())
        private set
    var stats by mutableStateOf<IntelligenceStats?>(null)
        private set
    var intelligenceReady by mutableStateOf(false)
        private set
    var guideRefreshing by mutableStateOf(false)
        private set
    var selectedProgramme by mutableStateOf<Programme?>(null)
    var selectedChannel by mutableStateOf<CanonicalChannel?>(null)

    var activePlaylist by mutableStateOf<Playlist?>(null)
        private set
    var playlistChannels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var playlistLoading by mutableStateOf(false)
        private set
    var playlistError by mutableStateOf<String?>(null)
        private set

    var checking by mutableStateOf(false)
        private set
    var checkDone by mutableIntStateOf(0)
        private set
    var checkTotal by mutableIntStateOf(0)
        private set
    var checkProgress by mutableFloatStateOf(0f)
        private set
    var checkMessage by mutableStateOf<String?>(null)

    init {
        load()
        viewModelScope.launch {
            ServiceLocator.prefs.channelViews.collect { viewCounts = it }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                tick = System.currentTimeMillis()
                updateDerived()
            }
        }
    }

    private suspend fun updateDerived() {
        runCatching {
            nowNext = ServiceLocator.tvIntel.nowNext(
                ServiceLocator.tvIntel.allChannels().map { it.id }
            )
            stats = ServiceLocator.tvIntel.stats()
            intelligenceReady = ServiceLocator.tvIntel.allChannels().isNotEmpty()
        }
    }

    fun load(force: Boolean = false) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            if (force) refreshing = true
            error = null
            runCatching { ServiceLocator.iptv.load(force) }
                .onSuccess { data = it }
                .onFailure { error = it.message ?: "Could not download the IPTV catalogue." }
            loading = false
            refreshing = false
            updateDerived()
        }
    }

    fun refreshGuide() {
        if (guideRefreshing) return
        viewModelScope.launch {
            guideRefreshing = true
            ServiceLocator.tvIntel.refreshEpg(force = true)
            updateDerived()
            guideRefreshing = false
        }
    }

    fun guideChannels(): List<CanonicalChannel> =
        ServiceLocator.tvIntel.allChannels().filter { it.epgIds.isNotEmpty() }

    fun schedule(epgId: String, from: Long, to: Long): List<Programme> =
        ServiceLocator.tvIntel.epg.schedule(epgId, from, to)

    fun searchProgrammes(query: String): List<Programme> {
        val now = System.currentTimeMillis()
        return ServiceLocator.tvIntel.epg.search(query, now - 2 * 60 * 60 * 1000L, now + 48 * 60 * 60 * 1000L)
    }

    fun selectPlaylist(playlist: Playlist) {
        if (playlistLoading) return
        viewModelScope.launch {
            activePlaylist = playlist
            playlistLoading = true
            playlistError = null
            playlistChannels = emptyList()
            runCatching { loadChannels(playlist) }
                .onSuccess { playlistChannels = it }
                .onFailure { playlistError = it.message ?: "Could not load that playlist." }
            playlistLoading = false
            updateDerived()
        }
    }

    fun closePlaylist() {
        activePlaylist = null
        playlistChannels = emptyList()
        playlistError = null
    }

    fun addPlaylist(playlist: Playlist) {
        viewModelScope.launch { ServiceLocator.prefs.addPlaylist(playlist) }
    }

    fun removePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            ServiceLocator.prefs.removePlaylist(playlist.id)
            ServiceLocator.tvIntel.removePlaylist(playlist.id)
            if (activePlaylist?.id == playlist.id) closePlaylist()
            updateDerived()
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch { ServiceLocator.prefs.toggleStarChannel(id) }
    }

    fun recordPlay(channel: Channel) {
        viewModelScope.launch {
            ServiceLocator.prefs.recordChannelView(channel.canonicalId ?: channel.id)
            updateDerived()
        }
    }

    fun checkWorking(channels: List<Channel>) {
        if (checking || channels.isEmpty()) return
        viewModelScope.launch {
            checking = true
            checkDone = 0
            checkTotal = minOf(channels.size, 300)
            checkProgress = 0f
            checkMessage = "Testing ${checkTotal} streams…"
            val subset = channels.take(300)
            val semaphore = Semaphore(8)
            val done = AtomicInteger(0)
            val results = coroutineScope {
                subset.map { channel ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val ok = probe(channel)
                            val finished = done.incrementAndGet()
                            checkDone = finished
                            checkProgress = finished.toFloat() / checkTotal.coerceAtLeast(1)
                            channel.id to ok
                        }
                    }
                }.awaitAll()
            }
            val current = ServiceLocator.prefs.workingChannels.first()
            val okIds = results.filter { it.second.first }.map { it.first }.toSet()
            val checkedIds = subset.map { it.id }.toSet()
            val byId = subset.associateBy { it.id }
            val health = results.mapNotNull { (id, result) ->
                byId[id]?.url?.let { it to result }
            }.toMap()
            launch { ServiceLocator.tvIntel.recordStreamHealthBatch(health) }
            ServiceLocator.prefs.setWorkingChannels((current - checkedIds) + okIds)
            checkMessage = "Working: ${okIds.size} of ${subset.size}"
            checking = false
        }
    }

    private suspend fun probe(channel: Channel): Pair<Boolean, Long> {
        val started = System.currentTimeMillis()
        val headers = buildMap {
            channel.referrer?.let { put("Referer", it) }
            channel.userAgent?.let { put("User-Agent", it) }
            put("Range", "bytes=0-2048")
        }
        val ua = channel.userAgent ?: Http.DESKTOP_UA
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Http.request(channel.url, headers, ua)
                Http.probe.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 206 || response.code == 302
                }
            }.getOrElse {
                runCatching {
                    val request = Http.request(channel.url, headers, ua).newBuilder().head().build()
                    Http.probe.newCall(request).execute().use { it.isSuccessful || it.code in 200..399 }
                }.getOrDefault(false)
            }
        }
        return result to (System.currentTimeMillis() - started)
    }

    private suspend fun loadChannels(playlist: Playlist): List<Channel> = when (playlist.kind) {
        Playlist.M3U_URL -> {
            val raw = Http.getText(playlist.url, userAgent = Http.DESKTOP_UA)
            ServiceLocator.tvIntel.importM3u(raw, playlist.id, playlist.name).entries.map { it.toChannel() }
        }
        Playlist.M3U_TEXT -> {
            val file = File(File(ServiceLocator.appContext.filesDir, "playlists"), playlist.fileName)
            if (!file.exists()) error("The pasted playlist file is missing.")
            ServiceLocator.tvIntel.importM3u(file.readText(), playlist.id, playlist.name).entries.map { it.toChannel() }
        }
        Playlist.XTREAM -> {
            val loaded = ServiceLocator.xtream.load(playlist.url, playlist.username, playlist.password, playlist.name)
            val imported = loaded.map { it.toEntry(playlist) }
            ServiceLocator.tvIntel.importEntries(imported)
            imported.map { it.toChannel() }
        }
        Playlist.STREAM -> {
            val entry = PlaylistEntry(
                id = "entry-" + playlist.id.hashCode().toString() + "-" + playlist.url.hashCode().toString(),
                playlistId = playlist.id,
                playlistName = playlist.name,
                originalName = playlist.name,
                normalizedName = playlist.name.lowercase(),
                streamUrl = playlist.url,
                importedAt = System.currentTimeMillis()
            )
            ServiceLocator.tvIntel.importEntries(listOf(entry))
            listOf(entry.toChannel())
        }
        else -> emptyList()
    }

    private fun PlaylistEntry.toChannel(): Channel {
        val canonical = ServiceLocator.tvIntel.allChannels()
            .firstOrNull { channel -> channel.streams.any { it.entryId == id } }
        return Channel(
            id = canonical?.id ?: id,
            name = originalName,
            url = streamUrl,
            logo = logoUrl,
            group = groupTitle,
            categories = listOfNotNull(groupTitle),
            country = country,
            languages = listOfNotNull(language),
            quality = qualityClaim,
            referrer = headers["Referer"],
            userAgent = headers["User-Agent"],
            playlistId = playlistId,
            playlistName = playlistName,
            canonicalId = canonical?.id
        )
    }

    private fun Channel.toEntry(playlist: Playlist) = PlaylistEntry(
        id = "entry-" + playlist.id.hashCode().toString() + "-" + url.hashCode().toString(),
        playlistId = playlist.id,
        playlistName = playlist.name,
        originalName = name,
        normalizedName = name.lowercase(),
        streamUrl = url,
        logoUrl = logo,
        groupTitle = group,
        qualityClaim = quality,
        headers = buildMap {
            referrer?.let { put("Referer", it) }
            userAgent?.let { put("User-Agent", it) }
        }
    )
}
