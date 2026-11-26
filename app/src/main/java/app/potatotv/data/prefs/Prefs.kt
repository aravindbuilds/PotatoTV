package app.potatotv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.ChannelView
import app.potatotv.data.model.DeeplistEntry
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.model.Playlist
import app.potatotv.data.model.Progress
import app.potatotv.data.model.Site
import app.potatotv.data.net.Http
import app.potatotv.data.sites.SiteDirectory
import app.potatotv.data.sources.BakedSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore("potato_prefs")

class Prefs(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val store: DataStore<Preferences> get() = context.prefsDataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            adBlock = p[ADBLOCK] ?: true,
            darkPages = p[DARK_PAGES] ?: true,
            desktopUserAgent = p[DESKTOP_UA] ?: true,
            autoPlayStreams = p[AUTO_PLAY] ?: true,
            showStreamPrompts = p[SHOW_STREAM_PROMPTS] ?: true,
            cursorMode = p[CURSOR_MODE] ?: false,
            openSubtitlesKey = p[SUBTITLE_KEY] ?: "",
            simklClientId = p[SIMKL_CLIENT] ?: "",
            simklToken = p[SIMKL_TOKEN] ?: "",
            updateRepo = p[UPDATE_REPO] ?: app.potatotv.BuildConfig.DEFAULT_UPDATE_REPO,
            siteDirectoryUrl = p[SITE_DIRECTORY] ?: BakedSources.DEEPLINKS_URL,
            catalogProvider = p[CATALOG_PROVIDER] ?: "fmhy-sources",
            adultSources = p[ADULT_SOURCES] ?: false,
            proxyEnabled = p[PROXY_ENABLED] ?: false,
            proxyHost = p[PROXY_HOST] ?: "",
            proxyPort = p[PROXY_PORT] ?: 0,
            proxyType = p[PROXY_TYPE] ?: "http",
            warpEnabled = p[WARP_ENABLED] ?: false,
            warpHost = p[WARP_HOST] ?: "",
            warpPort = p[WARP_PORT] ?: 0,
            warpType = p[WARP_TYPE] ?: "socks5",
            subtitleSize = p[SUBTITLE_SIZE] ?: 18f,
            subtitleBottom = p[SUBTITLE_BOTTOM] ?: 7f,
            subtitleDelayMs = p[SUBTITLE_DELAY] ?: 0,
            preferredQuality = p[PREFERRED_QUALITY] ?: "auto",
            iptvLastChecked = p[IPTV_CHECKED] ?: 0L
        )
    }.distinctUntilChanged()

    val favoriteIds: Flow<Set<String>> = store.data.map { it[FAVORITES] ?: emptySet() }.distinctUntilChanged()

    val sites: Flow<List<Site>> = store.data.map { decodeList(it[SITES], Site.serializer()) }.distinctUntilChanged()
    val playlists: Flow<List<Playlist>> = store.data.map { decodeList(it[PLAYLISTS], Playlist.serializer()) }.distinctUntilChanged()
    val myList: Flow<List<MetaPreview>> = store.data.map { decodeList(it[MY_LIST], MetaPreview.serializer()) }.distinctUntilChanged()
    val progress: Flow<Map<String, Progress>> = store.data.map { decodeProgress(it[PROGRESS]) }.distinctUntilChanged()
    val starredChannels: Flow<Set<String>> = store.data.map { it[STARRED_CHANNELS] ?: emptySet() }.distinctUntilChanged()
    val workingChannels: Flow<Set<String>> = store.data.map { it[WORKING_CHANNELS] ?: emptySet() }.distinctUntilChanged()
    /** Locally observed channel popularity. No provider contributes to this. */
    val channelViews: Flow<Map<String, ChannelView>> = store.data.map { decodeViews(it[CHANNEL_VIEWS]) }.distinctUntilChanged()

    suspend fun ensureSeeded() {
        store.edit { p ->
            if (p[SEEDED] != true) {
                p[SEEDED] = true
                if (p[SITES].isNullOrBlank()) {
                    p[SITES] = json.encodeToString(ListSerializer(Site.serializer()), defaultSites())
                }
                if (p[MY_LIST].isNullOrBlank()) p[MY_LIST] = "[]"
                if (p[PLAYLISTS].isNullOrBlank()) p[PLAYLISTS] = "[]"
            }

            // Bake the FMHYPlay source list into existing installs too, once.
            if ((p[SOURCES_VERSION] ?: 0) < 1) {
                val existing = decodeList(p[PLAYLISTS], Playlist.serializer())
                val ids = existing.map { it.id }.toSet()
                val baked = BakedSources.toPlaylists().filterNot { it.id in ids }
                if (baked.isNotEmpty()) {
                    p[PLAYLISTS] = json.encodeToString(
                        ListSerializer(Playlist.serializer()),
                        existing + baked
                    )
                }
                if (p[SITE_DIRECTORY].isNullOrBlank()) {
                    p[SITE_DIRECTORY] = BakedSources.DEEPLINKS_URL
                }
                p[SOURCES_VERSION] = 1
            }

            // 18+ playlists are no longer shipped or offered.
            if ((p[SOURCES_VERSION] ?: 0) < 2) {
                val existing = decodeList(p[PLAYLISTS], Playlist.serializer())
                val cleaned = existing.filterNot { it.adult || it.id.startsWith("fmhy-") }
                if (cleaned.size != existing.size) {
                    p[PLAYLISTS] = json.encodeToString(
                        ListSerializer(Playlist.serializer()),
                        cleaned
                    )
                }
                p[ADULT_SOURCES] = false
                p[SOURCES_VERSION] = 2
            }

            // Seed the title-template sites from the bundled backup so playback
            // works before the first live refresh.
            if ((p[SOURCES_VERSION] ?: 0) < 3) {
                val raw = runCatching {
                    context.assets.open("title_templates.json").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                val primary = SiteDirectory.parse(raw).titleSites
                    .map { it.copy(primaryTemplate = true) }
                val primaryIds = primary.map { it.id }.toSet()
                val current = decodeList(p[SITES], Site.serializer())
                val next = current.filterNot { it.id in primaryIds } + primary
                p[SITES] = json.encodeToString(ListSerializer(Site.serializer()), next)
                p[SOURCES_VERSION] = 3
            }
        }
    }

    suspend fun toggleFavorite(id: String) {
        store.edit { p ->
            val current = p[FAVORITES] ?: emptySet()
            p[FAVORITES] = if (id in current) current - id else current + id
        }
    }

    suspend fun saveSites(list: List<Site>) {
        store.edit { it[SITES] = json.encodeToString(ListSerializer(Site.serializer()), list) }
    }

    suspend fun addSite(site: Site) {
        store.edit { p ->
            val current = decodeList(p[SITES], Site.serializer())
            p[SITES] = json.encodeToString(
                ListSerializer(Site.serializer()),
                listOf(site) + current.filterNot { it.id == site.id }
            )
        }
    }

    /** Replace all sites with a freshly fetched set (from local defaults or remote JSON). */
    suspend fun replaceAllSites(sites: List<Site>) {
        store.edit { p ->
            p[SITES] = json.encodeToString(ListSerializer(Site.serializer()), sites)
        }
    }

    /** Fetch deeplinks from the FMHY sources repo and merge into built-in sites. */
    suspend fun loadDeeplinks(remoteUrl: String): List<DeeplistEntry> {
        if (remoteUrl.isBlank()) return emptyList()
        return runCatching {
            SiteDirectory.parse(Http.getText(remoteUrl)).deeplinks
        }.getOrDefault(emptyList())
    }

    /** Fetch a remote directory and return the browsable sites it declares. */
    suspend fun loadDirectorySites(remoteUrl: String): List<Site> {
        if (remoteUrl.isBlank()) return emptyList()
        return runCatching {
            SiteDirectory.parse(Http.getText(remoteUrl)).sites
        }.getOrDefault(emptyList())
    }

    suspend fun removeSite(id: String) {
        store.edit { p ->
            val current = decodeList(p[SITES], Site.serializer())
            p[SITES] = json.encodeToString(ListSerializer(Site.serializer()), current.filterNot { it.id == id })
        }
    }

    /** Bump a site's usage count so popular sites sort first. */
    suspend fun incrementSiteUsage(id: String) {
        store.edit { p ->
            val current = decodeList(p[SITES], Site.serializer())
            val next = current.map { site ->
                if (site.id == id) site.copy(usageCount = site.usageCount + 1) else site
            }
            p[SITES] = json.encodeToString(ListSerializer(Site.serializer()), next)
        }
    }

    suspend fun toggleMyList(meta: MetaPreview) {
        store.edit { p ->
            val current = decodeList(p[MY_LIST], MetaPreview.serializer())
            val next = if (current.any { it.id == meta.id }) {
                current.filterNot { it.id == meta.id }
            } else {
                listOf(meta) + current
            }
            p[MY_LIST] = json.encodeToString(ListSerializer(MetaPreview.serializer()), next)
        }
    }

    suspend fun saveProgress(progress: Progress) {
        store.edit { p ->
            val current = decodeProgress(p[PROGRESS]).toMutableMap().apply { put(progress.key, progress) }
            val next = current.entries.sortedByDescending { it.value.updatedAt }.take(60).associate { it.key to it.value }
            p[PROGRESS] = json.encodeToString(MapSerializer(String.serializer(), Progress.serializer()), next)
        }
    }

    suspend fun removeProgress(key: String) {
        store.edit { p ->
            val next = decodeProgress(p[PROGRESS]) - key
            p[PROGRESS] = json.encodeToString(MapSerializer(String.serializer(), Progress.serializer()), next)
        }
    }

    suspend fun addPlaylist(playlist: Playlist) {
        store.edit { p ->
            val current = decodeList(p[PLAYLISTS], Playlist.serializer())
            p[PLAYLISTS] = json.encodeToString(
                ListSerializer(Playlist.serializer()),
                listOf(playlist) + current.filterNot { it.id == playlist.id }
            )
        }
    }

    suspend fun removePlaylist(id: String) {
        store.edit { p ->
            val current = decodeList(p[PLAYLISTS], Playlist.serializer())
            p[PLAYLISTS] = json.encodeToString(ListSerializer(Playlist.serializer()), current.filterNot { it.id == id })
        }
    }

    suspend fun toggleStarChannel(id: String) {
        store.edit { p ->
            val current = p[STARRED_CHANNELS] ?: emptySet()
            p[STARRED_CHANNELS] = if (id in current) current - id else current + id
        }
    }

    suspend fun setWorkingChannels(ids: Set<String>) {
        store.edit { it[WORKING_CHANNELS] = ids }
    }

    suspend fun recordChannelView(id: String) {
        store.edit { p ->
            val current = decodeViews(p[CHANNEL_VIEWS]).toMutableMap()
            val existing = current[id] ?: ChannelView()
            current[id] = ChannelView(existing.count + 1, System.currentTimeMillis())
            // Keep storage bounded to the 300 most recently opened channels.
            p[CHANNEL_VIEWS] = json.encodeToString(
                MapSerializer(String.serializer(), ChannelView.serializer()),
                current.entries.sortedByDescending { it.value.lastViewedAt }.take(300).associate { it.key to it.value }
            )
        }
    }

    private fun decodeViews(raw: String?): Map<String, ChannelView> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), ChannelView.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun setSetting(key: String, value: Any) {
        store.edit { p ->
            when (key) {
                "adblock" -> p[ADBLOCK] = value as Boolean
                "adblockRedirectPrevent" -> p[ADBLOCK_REDIRECT_PREVENT] = value as Boolean
                "adBlockSiteHiding" -> p[ADBLOCK_SITE_HIDING] = value as Boolean
                "darkPages" -> p[DARK_PAGES] = value as Boolean
                "desktopUserAgent" -> p[DESKTOP_UA] = value as Boolean
                "autoPlayStreams" -> p[AUTO_PLAY] = value as Boolean
                "showStreamPrompts" -> p[SHOW_STREAM_PROMPTS] = value as Boolean
                "cursorMode" -> p[CURSOR_MODE] = value as Boolean
                "openSubtitlesKey" -> p[SUBTITLE_KEY] = value as String
                "simklClientId" -> p[SIMKL_CLIENT] = value as String
                "simklToken" -> p[SIMKL_TOKEN] = value as String
                "updateRepo" -> p[UPDATE_REPO] = value as String
                "siteDirectoryUrl" -> p[SITE_DIRECTORY] = value as String
                "catalogProvider" -> p[CATALOG_PROVIDER] = value as String
                "adultSources" -> p[ADULT_SOURCES] = value as Boolean
                "proxyEnabled" -> p[PROXY_ENABLED] = value as Boolean
                "proxyHost" -> p[PROXY_HOST] = value as String
                "proxyPort" -> p[PROXY_PORT] = value as Int
                "proxyType" -> p[PROXY_TYPE] = value as String
                "subtitleSize" -> p[SUBTITLE_SIZE] = value as Float
                "subtitleBottom" -> p[SUBTITLE_BOTTOM] = value as Float
                "subtitleDelayMs" -> p[SUBTITLE_DELAY] = value as Int
                "preferredQuality" -> p[PREFERRED_QUALITY] = value as String
                "iptvLastChecked" -> p[IPTV_CHECKED] = value as Long
                "warpEnabled" -> p[WARP_ENABLED] = value as Boolean
                "warpHost" -> p[WARP_HOST] = value as String
                "warpPort" -> p[WARP_PORT] = value as Int
                "warpType" -> p[WARP_TYPE] = value as String
            }
        }
    }

    private fun <T> decodeList(raw: String?, serializer: KSerializer<T>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(serializer), raw)
        }.getOrDefault(emptyList())
    }

    private fun decodeProgress(raw: String?): Map<String, Progress> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), Progress.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    private fun defaultSites(): List<Site> = listOf(
        Site(
            id = "youtube",
            name = "YouTube",
            url = "https://www.youtube.com",
            searchTemplate = "https://www.youtube.com/results?search_query={q}",
            builtIn = true,
            category = "general",
            favicon = "https://www.youtube.com/favicon.ico"
        ),
        Site(
            id = "tubi",
            name = "Tubi",
            url = "https://tubitv.com",
            searchTemplate = "https://tubitv.com/search/{q}",
            builtIn = true,
            category = "movie",
            favicon = "https://tubitv.com/favicon.ico"
        ),
        Site(
            id = "pluto",
            name = "Pluto TV",
            url = "https://pluto.tv",
            searchTemplate = "https://pluto.tv/en/search?q={q}",
            builtIn = true,
            category = "livetv",
            favicon = "https://pluto.tv/favicon.ico"
        ),
        Site(
            id = "plex",
            name = "Plex",
            url = "https://watch.plex.tv",
            searchTemplate = "https://watch.plex.tv/search?query={q}",
            builtIn = true,
            category = "movie",
            favicon = "https://watch.plex.tv/favicon.ico"
        ),
        Site(
            id = "archive",
            name = "Internet Archive",
            url = "https://archive.org/details/movies",
            searchTemplate = "https://archive.org/search?query={q}",
            builtIn = true,
            category = "documentary",
            favicon = "https://archive.org/favicon.ico"
        ),
        Site(
            id = "crunchyroll",
            name = "Crunchyroll",
            url = "https://www.crunchyroll.com",
            searchTemplate = "https://www.crunchyroll.com/search?q={q}",
            builtIn = true,
            category = "series",
            favicon = "https://www.crunchyroll.com/favicon.ico"
        ),
        Site(
            id = "iplayer",
            name = "BBC iPlayer",
            url = "https://www.bbc.co.uk/iplayer",
            searchTemplate = "https://www.bbc.co.uk/iplayer/search?q={q}",
            builtIn = true,
            category = "series",
            favicon = "https://www.bbc.co.uk/iplayer/favicon.ico"
        ),
        Site(
            id = "fmhy",
            name = "FMHY",
            url = "https://fmhy.net",
            builtIn = true,
            category = "general",
            favicon = "https://fmhy.net/favicon.ico"
        ),
        // Movie and series deeplinks from the FMHY sources repo
        Site(id = "cinejoy-pk", name = "CineJoy", url = "https://cinejoy.pk", searchTemplate = "https://cinejoy.pk/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "movy-sx", name = "Movy", url = "https://movy.sx", searchTemplate = "https://movy.sx/search/{q}", builtIn = true, category = "movie"),
        Site(id = "7movies-ac", name = "7Movies", url = "https://7movies.ac", searchTemplate = "https://7movies.ac/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "arrowtv-net", name = "Arrow TV", url = "https://arrowtv.net", searchTemplate = "https://arrowtv.net/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "chillflix-lol", name = "ChillFlix", url = "https://chillflix.lol", searchTemplate = "https://chillflix.lol/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "popwatch-to", name = "PopWatch", url = "https://popwatch.to", searchTemplate = "https://popwatch.to/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "kaitotv-com", name = "Kaito TV", url = "https://kaitotv.com", searchTemplate = "https://kaitotv.com/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "cinebytv-com", name = "CineBy TV", url = "https://cinebytv.com", searchTemplate = "https://cinebytv.com/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "streamgoblin-cc", name = "StreamGoBlin", url = "https://streamgoblin.cc", searchTemplate = "https://streamgoblin.cc/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "cno-to", name = "CNO", url = "https://cno.to", searchTemplate = "https://cno.to/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "consumit-online", name = "ConsumIt", url = "https://consumit.online", searchTemplate = "https://consumit.online/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "meowly-qzz-io", name = "Meowly", url = "https://meowly.qzz.io", searchTemplate = "https://meowly.qzz.io/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "nexa-mov", name = "Nexa MOV", url = "https://nexa.mov", searchTemplate = "https://nexa.mov/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "cinecat-eu", name = "CineCat", url = "https://cinecat.eu", searchTemplate = "https://cinecat.eu/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "aether-st", name = "Aether", url = "https://aether.st", searchTemplate = "https://aether.st/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "basementx-lol", name = "BasementX", url = "https://basementx.lol", searchTemplate = "https://basementx.lol/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "cinefork-net", name = "CineForK", url = "https://cinefork.net", searchTemplate = "https://cinefork.net/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "streamwatch-online", name = "StreamWatch", url = "https://streamwatch.online", searchTemplate = "https://streamwatch.online/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "peestream-in", name = "PeeStream", url = "https://peestream.in", searchTemplate = "https://peestream.in/search?q={q}", builtIn = true, category = "movie"),
        Site(id = "fmfau-com", name = "FMFAU", url = "https://fmfau.com", searchTemplate = "https://fmfau.com/search?q={q}", builtIn = true, category = "movie"),
        // General placeholder for any user sites that don't fit a specific category
        Site(id = "custom", name = "Custom Sites", url = "", searchTemplate = "", builtIn = false, category = "general")
    )

    private companion object {
        val ADBLOCK = booleanPreferencesKey("adblock")
        val ADBLOCK_REDIRECT_PREVENT = booleanPreferencesKey("adblock_redirect_prevent")
        val ADBLOCK_SITE_HIDING = booleanPreferencesKey("adBlockSiteHiding")
        val DARK_PAGES = booleanPreferencesKey("dark_pages")
        val DESKTOP_UA = booleanPreferencesKey("desktop_ua")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val SHOW_STREAM_PROMPTS = booleanPreferencesKey("show_stream_prompts")
        val CURSOR_MODE = booleanPreferencesKey("cursor_mode")
        val SUBTITLE_KEY = stringPreferencesKey("subtitle_key")
        val SIMKL_CLIENT = stringPreferencesKey("simkl_client")
        val SIMKL_TOKEN = stringPreferencesKey("simkl_token")
        val UPDATE_REPO = stringPreferencesKey("update_repo")
        val CATALOG_PROVIDER = stringPreferencesKey("catalog_provider")
        val ADULT_SOURCES = booleanPreferencesKey("adult_sources")
        val SOURCES_VERSION = intPreferencesKey("sources_version")
        val SITE_DIRECTORY = stringPreferencesKey("site_directory")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val WARP_ENABLED = booleanPreferencesKey("warp_enabled")
        val WARP_HOST = stringPreferencesKey("warp_host")
        val WARP_PORT = intPreferencesKey("warp_port")
        val WARP_TYPE = stringPreferencesKey("warp_type")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        val SUBTITLE_BOTTOM = floatPreferencesKey("subtitle_bottom")
        val SUBTITLE_DELAY = intPreferencesKey("subtitle_delay")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val IPTV_CHECKED = longPreferencesKey("iptv_checked")
        val SEEDED = booleanPreferencesKey("seeded")

        val FAVORITES = stringSetPreferencesKey("favorites")
        val SITES = stringPreferencesKey("sites")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val MY_LIST = stringPreferencesKey("my_list")
        val PROGRESS = stringPreferencesKey("progress")
        val STARRED_CHANNELS = stringSetPreferencesKey("starred_channels")
        val WORKING_CHANNELS = stringSetPreferencesKey("working_channels")
        val CHANNEL_VIEWS = stringPreferencesKey("channel_views")
    }
}
