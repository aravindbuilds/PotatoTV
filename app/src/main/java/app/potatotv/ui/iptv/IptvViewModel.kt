package app.potatotv.ui.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.potatotv.ServiceLocator
import app.potatotv.data.iptv.IptvData
import app.potatotv.data.iptv.M3uParser
import app.potatotv.data.model.Channel
import app.potatotv.data.model.Playlist
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

enum class IptvTab { Live, Playlists, Starred, Working }

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
    var workingOnly by mutableStateOf(false)

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
        }
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
        }
    }

    fun closePlaylist() {
        activePlaylist = null
        playlistChannels = emptyList()
        playlistError = null
    }

    fun addPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            ServiceLocator.prefs.addPlaylist(playlist)
        }
    }

    fun removePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            ServiceLocator.prefs.removePlaylist(playlist.id)
            if (activePlaylist?.id == playlist.id) closePlaylist()
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch { ServiceLocator.prefs.toggleStarChannel(id) }
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
            val okIds = results.filter { it.second }.map { it.first }.toSet()
            val checkedIds = subset.map { it.id }.toSet()
            ServiceLocator.prefs.setWorkingChannels((current - checkedIds) + okIds)
            checkMessage = "Working: ${okIds.size} of ${subset.size}"
            checking = false
        }
    }

    private suspend fun probe(channel: Channel): Boolean {
        val headers = buildMap {
            channel.referrer?.let { put("Referer", it) }
            channel.userAgent?.let { put("User-Agent", it) }
            put("Range", "bytes=0-2048")
        }
        val ua = channel.userAgent ?: Http.DESKTOP_UA
        return withContext(Dispatchers.IO) {
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
    }

    private suspend fun loadChannels(playlist: Playlist): List<Channel> {
        return when (playlist.kind) {
            Playlist.M3U_URL -> {
                val raw = Http.getText(playlist.url, userAgent = Http.DESKTOP_UA)
                M3uParser.parse(raw, playlist.id, playlist.name)
            }
            Playlist.M3U_TEXT -> {
                val file = File(File(ServiceLocator.appContext.filesDir, "playlists"), playlist.fileName)
                if (!file.exists()) error("The pasted playlist file is missing.")
                M3uParser.parse(file.readText(), playlist.id, playlist.name)
            }
            Playlist.XTREAM -> ServiceLocator.xtream.load(
                playlist.url, playlist.username, playlist.password, playlist.name
            )
            Playlist.STREAM -> listOf(
                Channel(
                    id = playlist.id,
                    name = playlist.name,
                    url = playlist.url,
                    playlistId = playlist.id,
                    playlistName = playlist.name
                )
            )
            else -> emptyList()
        }
    }
}
