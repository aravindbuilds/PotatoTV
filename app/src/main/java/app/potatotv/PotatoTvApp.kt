package app.potatotv

import android.app.Application
import android.content.Context
import app.potatotv.data.adblock.AdBlocker
import app.potatotv.data.cinemeta.CinemetaRepository
import app.potatotv.data.iptv.IptvRepository
import app.potatotv.data.iptv.XtreamClient
import app.potatotv.data.net.Http
import app.potatotv.data.prefs.Prefs
import app.potatotv.data.simkl.SimklRepository
import app.potatotv.data.subtitles.SubtitleRepository
import app.potatotv.data.tv.TvIntelligenceRepository
import app.potatotv.data.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object ServiceLocator {
    @Volatile
    private var ready = false

    lateinit var prefs: Prefs
    lateinit var cinemeta: CinemetaRepository
    lateinit var iptv: IptvRepository
    lateinit var xtream: XtreamClient
    lateinit var subtitles: SubtitleRepository
    lateinit var simkl: SimklRepository
    lateinit var updates: UpdateChecker
    lateinit var adBlocker: AdBlocker
    lateinit var tvIntel: TvIntelligenceRepository
    lateinit var appContext: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            appContext = context.applicationContext
            prefs = Prefs(appContext)
            cinemeta = CinemetaRepository()
            iptv = IptvRepository(appContext)
            xtream = XtreamClient()
            subtitles = SubtitleRepository()
            simkl = SimklRepository()
            updates = UpdateChecker(appContext)
            adBlocker = AdBlocker(appContext).apply { load() }
            tvIntel = TvIntelligenceRepository(appContext)
            ready = true

            scope.launch {
                prefs.ensureSeeded()
                prefs.settings.collectLatest { Http.configureProxy(it) }
                // Metadata and EPG are independent of playback: failures never
                // stop channels from being playable.
                runCatching { tvIntel.initialize() }
            }
        }
    }
}

class PotatoTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
