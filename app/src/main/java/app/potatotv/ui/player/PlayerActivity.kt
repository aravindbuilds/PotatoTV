package app.potatotv.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.potatotv.PotatoTvApp
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.Progress
import app.potatotv.data.model.SubtitleCue
import app.potatotv.data.subtitles.SubtitleResult
import app.potatotv.ui.browser.BrowserEvent
import app.potatotv.ui.browser.BrowserSession
import app.potatotv.ui.browser.MenuEntry
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import app.potatotv.ui.theme.PotatoTvTheme
import app.potatotv.ui.theme.ResponsiveTvUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var title: String
    private lateinit var mediaKey: String
    private var imdbId: String? = null
    private var mediaType: String = "movie"
    private var season: Int? = null
    private var episode: Int? = null
    private var poster: String? = null
    private var isLive: Boolean = false
    private var scrobbled = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawableResource(android.R.color.black)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        title = intent.getStringExtra(EXTRA_TITLE) ?: "PotatoTV"
        mediaKey = intent.getStringExtra(EXTRA_RESUME_KEY) ?: url
        imdbId = intent.getStringExtra(EXTRA_IMDB)
        mediaType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        season = intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it >= 0 }
        episode = intent.getIntExtra(EXTRA_EPISODE, -1).takeIf { it >= 0 }
        poster = intent.getStringExtra(EXTRA_POSTER)
        isLive = intent.getBooleanExtra(EXTRA_LIVE, false)

        @Suppress("DEPRECATION")
        val headers = (intent.getSerializableExtra(EXTRA_HEADERS) as? HashMap<*, *>)
            ?.entries?.associate { it.key.toString() to it.value.toString() }
            ?: emptyMap()

        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setUserAgent(headers["User-Agent"] ?: app.potatotv.data.net.Http.PLAYER_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.playWhenReady = true

        val kind = intent.getStringExtra(EXTRA_KIND) ?: "auto"
        setMedia(url, kind)

        lifecycleScope.launch {
            val saved = runCatching {
                ServiceLocator.prefs.progress.first()[mediaKey]
            }.getOrNull()
            if (saved != null && saved.positionMs > 10_000) {
                val duration = player.duration
                if (duration <= 0 || saved.positionMs < duration - 30_000) {
                    player.seekTo(saved.positionMs)
                }
            }
        }

        setContent {
            ResponsiveTvUi {
                PotatoTvTheme {
                    PlayerScreen(
                        player = player,
                        title = title,
                        initialUrl = url,
                        initialKind = kind,
                        isLive = isLive,
                        mediaKey = mediaKey,
                        onBack = { finish() },
                        onSave = { position, duration -> saveProgress(position, duration) },
                        onScrobble = { scrobble() }
                    )
                }
            }
        }
    }

    private fun setMedia(url: String, kind: String) {
        val builder = MediaItem.Builder().setUri(url)
        when (kind) {
            "hls" -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            "dash" -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        player.setMediaItem(builder.build())
        player.prepare()
    }

    private fun saveProgress(position: Long, duration: Long) {
        if (position < 5_000 || duration <= 0) return
        val record = Progress(
            key = mediaKey,
            title = title,
            poster = poster,
            positionMs = position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
            imdbId = imdbId,
            type = mediaType,
            season = season,
            episode = episode
        )
        lifecycleScope.launch { runCatching { ServiceLocator.prefs.saveProgress(record) } }
    }

    private fun scrobble() {
        if (scrobbled) return
        val id = imdbId ?: return
        scrobbled = true
        lifecycleScope.launch {
            runCatching {
                val settings = ServiceLocator.prefs.settings.first()
                if (settings.simklToken.isNotBlank() && settings.simklClientId.isNotBlank()) {
                    ServiceLocator.simkl.markWatched(
                        clientId = settings.simklClientId,
                        token = settings.simklToken,
                        imdbId = id,
                        type = mediaType,
                        title = title,
                        season = season,
                        episode = episode
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_RESUME_KEY = "resume_key"
        const val EXTRA_IMDB = "imdb"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_POSTER = "poster"
        const val EXTRA_LIVE = "live"
        const val EXTRA_HEADERS = "headers"

        fun intent(
            context: Context,
            url: String,
            kind: String,
            title: String,
            resumeKey: String? = null,
            imdbId: String? = null,
            type: String = "movie",
            season: Int? = null,
            episode: Int? = null,
            poster: String? = null,
            isLive: Boolean = false,
            headers: Map<String, String> = emptyMap()
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_RESUME_KEY, resumeKey ?: url)
            putExtra(EXTRA_IMDB, imdbId)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_SEASON, season ?: -1)
            putExtra(EXTRA_EPISODE, episode ?: -1)
            putExtra(EXTRA_POSTER, poster)
            putExtra(EXTRA_LIVE, isLive)
            putExtra(EXTRA_HEADERS, HashMap(headers))
        }
    }
}

private enum class PlayerPanel { Quality, Audio, Text, Speed, Zoom, Servers, Episodes, Subtitles }

private data class TrackChoice(val label: String, val override: TrackSelectionOverride?, val group: Tracks.Group? = null)

private enum class ZoomMode(val label: String, val resizeMode: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    initialUrl: String,
    initialKind: String,
    isLive: Boolean,
    mediaKey: String,
    onBack: () -> Unit,
    onSave: (Long, Long) -> Unit,
    onScrobble: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var mediaUrl by remember { mutableStateOf(initialUrl) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var zoom by remember { mutableStateOf(ZoomMode.Fit) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf("") }

    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleName by remember { mutableStateOf("") }
    var subtitleDelay by remember { mutableStateOf(0) }
    var subtitleSize by remember { mutableFloatStateOf(18f) }
    var subtitleBottom by remember { mutableFloatStateOf(7f) }
    var subtitlesOn by remember { mutableStateOf(true) }
    var subtitleState by remember { mutableStateOf<SubtitleUiState>(SubtitleUiState.Idle) }
    var subtitleQuery by remember { mutableStateOf(title) }
    var subtitleLanguage by remember { mutableStateOf("en") }
    var subtitleUrl by remember { mutableStateOf("") }
    var subtitleResults by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val menus by BrowserSession.menus.collectAsStateWithLifecycle()
    val hasSite = BrowserSession.isActive()

    LaunchedEffect(Unit) {
        val stored = runCatching { ServiceLocator.prefs.settings.first() }.getOrNull()
        if (stored != null) {
            subtitleDelay = stored.subtitleDelayMs
            subtitleSize = stored.subtitleSize
            subtitleBottom = stored.subtitleBottom
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0)
                }
            }

            override fun onTracksChanged(trackGroups: Tracks) {
                tracks = trackGroups
            }
        }
        player.addListener(listener)
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            onSave(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0)
            val d = player.duration
            if (d > 0) duration = d
            if (!isLive && duration > 0 && position > duration * 0.92) onScrobble()
            delay(500)
        }
    }

    LaunchedEffect(player) {
        var lastSaved = 0L
        while (isActive) {
            delay(5_000)
            val now = System.currentTimeMillis()
            if (now - lastSaved >= 5_000) {
                onSave(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
                lastSaved = now
            }
        }
    }

    LaunchedEffect(Unit) {
        BrowserSession.events.collect { event ->
            if (event is BrowserEvent.Stream && event.stream.url != mediaUrl) {
                val resume = player.currentPosition
                val wasPlaying = player.isPlaying
                mediaUrl = event.stream.url
                val builder = MediaItem.Builder().setUri(event.stream.url)
                when (event.stream.kind) {
                    "hls" -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    "dash" -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                player.setMediaItem(builder.build())
                player.prepare()
                if (resume > 0) player.seekTo(resume)
                player.playWhenReady = wasPlaying
                hud = "Switched stream"
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, panel) {
        if (controlsVisible && isPlaying && panel == null) {
            delay(4_000)
            controlsVisible = false
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    fun seekBy(delta: Long) {
        val target = (player.currentPosition + delta).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        hud = if (delta > 0) "+${delta / 1000}s" else "${delta / 1000}s"
        controlsVisible = true
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        controlsVisible = true
    }

    LaunchedEffect(hud) {
        if (hud.isNotEmpty()) {
            delay(1_100)
            hud = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        togglePlay(); true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(10_000); true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-10_000); true
                    }
                    else -> {
                        if (!controlsVisible && panel == null) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                }
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { view ->
                        view.useController = false
                        view.player = player
                        view.resizeMode = zoom.resizeMode
                        view.isFocusable = false
                        view.setShutterBackgroundColor(android.graphics.Color.BLACK)
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = zoom.resizeMode
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) seekBy(-10_000) else seekBy(10_000)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var left = true
                        var base = 0f
                        var applied = 0f
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                left = offset.x < size.width / 2
                                base = if (left) {
                                    val current = activity?.window?.attributes?.screenBrightness ?: -1f
                                    if (current > 0f) current else 0.5f
                                } else {
                                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                                        am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                }
                                applied = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                applied += dragAmount / size.height
                                val value = (base - applied).coerceIn(0f, 1f)
                                if (left) {
                                    activity?.window?.let { window ->
                                        val params = window.attributes
                                        params.screenBrightness = value.coerceAtLeast(0.03f)
                                        window.attributes = params
                                    }
                                    hud = "Brightness ${(value * 100).roundToInt()}%"
                                } else {
                                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0)
                                    hud = "Volume ${(value * 100).roundToInt()}%"
                                }
                            }
                        )
                    }
            )

            if (buffering) {
                CircularProgressIndicator(
                    color = PotatoAccent,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            // External subtitle overlay (OpenSubtitles / URL / local SRT).
            if (subtitlesOn && subtitleCues.isNotEmpty()) {
                val current = position + subtitleDelay
                val text = subtitleCues
                    .filter { current >= it.startMs && current <= it.endMs }
                    .joinToString("\n") { it.text }
                if (text.isNotBlank()) {
                    val bottomPad = (maxHeight.value * (subtitleBottom / 100f)).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.86f)
                            .padding(bottom = bottomPad.coerceAtLeast(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = subtitleSize.sp,
                            lineHeight = (subtitleSize * 1.3f).sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.62f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (hud.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 42.dp)
                ) {
                    Text(
                        hud,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerIconButton(Icons.Filled.ArrowBack, "Back") { onBack() }
                        Spacer(Modifier.weight(1f))
                        if (hasSite) {
                            PlayerIconButton(Icons.Filled.HighQuality, "Servers") { panel = PlayerPanel.Servers }
                        }
                        PlayerIconButton(Icons.Filled.Subtitles, "Subtitles") { panel = PlayerPanel.Subtitles }
                        PlayerIconButton(Icons.Filled.Speed, "Speed") { panel = PlayerPanel.Speed }
                        PlayerIconButton(Icons.Filled.Language, "Audio") { panel = PlayerPanel.Audio }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerIconButton(Icons.Filled.FastRewind, "Back 10s", big = true) { seekBy(-10_000) }
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(PotatoAccent)
                                .focusRequester(playFocus)
                                .onFocusChanged { }
                                .clickable { togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play or pause",
                                tint = Color(0xFF00131C),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        PlayerIconButton(Icons.Filled.FastForward, "Forward 10s", big = true) { seekBy(10_000) }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        if (!isLive) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = PotatoAccent,
                                trackColor = Color.White.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isLive) "LIVE STREAM" else "${formatTime(position)}  /  ${formatTime(duration)}",
                                color = PotatoTextDim,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.weight(1f))
                            TextButtonFlat("Quality") { panel = PlayerPanel.Quality }
                            TextButtonFlat("Zoom · ${zoom.label}") { panel = PlayerPanel.Zoom }
                            if (hasSite) TextButtonFlat("Episodes") { panel = PlayerPanel.Episodes }
                        }
                    }
                }
            }

            if (panel != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { panel = null }
                )
            }

            AnimatedVisibility(
                visible = panel != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val currentPanel = panel ?: PlayerPanel.Quality
                when (currentPanel) {
                    PlayerPanel.Quality -> ChoicePanel(
                        title = "Video quality",
                        options = qualityChoices(tracks),
                        selected = { false },
                        label = { it.label },
                        onSelect = { choice ->
                            val builder = player.trackSelectionParameters.buildUpon()
                            if (choice.override == null) {
                                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            } else {
                                builder.setOverrideForType(choice.override)
                            }
                            player.trackSelectionParameters = builder.build()
                            hud = choice.label
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Audio -> ChoicePanel(
                        title = "Audio track",
                        options = audioChoices(tracks),
                        selected = { false },
                        label = { it.label },
                        onSelect = { choice ->
                            val builder = player.trackSelectionParameters.buildUpon()
                            if (choice.override == null) builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            else builder.setOverrideForType(choice.override)
                            player.trackSelectionParameters = builder.build()
                            hud = choice.label
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Text -> ChoicePanel(
                        title = "Embedded subtitles",
                        options = textChoices(tracks),
                        selected = { false },
                        label = { it.label },
                        onSelect = { choice ->
                            val builder = player.trackSelectionParameters.buildUpon()
                            if (choice.override == null) {
                                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            } else {
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                builder.setOverrideForType(choice.override)
                            }
                            player.trackSelectionParameters = builder.build()
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Speed -> ChoicePanel(
                        title = "Playback speed",
                        options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f),
                        selected = { it == playbackSpeed },
                        label = { if (it == 1f) "Normal" else "${it}x" },
                        onSelect = { value ->
                            playbackSpeed = value
                            player.setPlaybackSpeed(value)
                            hud = if (value == 1f) "Speed normal" else "Speed ${value}x"
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Zoom -> ChoicePanel(
                        title = "Picture size",
                        options = ZoomMode.entries,
                        selected = { it == zoom },
                        label = { it.label },
                        onSelect = { mode ->
                            zoom = mode
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Servers -> MenuChoicePanel(
                        title = "Site servers",
                        entries = menus.servers,
                        onSelect = { entry ->
                            BrowserSession.clickServer(entry.id)
                            hud = "Switching to ${entry.label}…"
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Episodes -> MenuChoicePanel(
                        title = "Episodes",
                        entries = menus.episodes,
                        onSelect = { entry ->
                            BrowserSession.clickEpisode(entry.id)
                            hud = "Choosing ${entry.label}…"
                            panel = null
                        },
                        onDismiss = { panel = null }
                    )
                    PlayerPanel.Subtitles -> SubtitlePanel(
                        title = title,
                        query = subtitleQuery,
                        onQuery = { subtitleQuery = it },
                        language = subtitleLanguage,
                        onLanguage = { subtitleLanguage = it },
                        url = subtitleUrl,
                        onUrl = { subtitleUrl = it },
                        state = subtitleState,
                        results = subtitleResults,
                        cuesLoaded = subtitleCues.size,
                        trackName = subtitleName,
                        delay = subtitleDelay,
                        size = subtitleSize,
                        bottom = subtitleBottom,
                        enabled = subtitlesOn,
                        onSearch = {
                            scope.launch {
                                subtitleState = SubtitleUiState.Loading
                                runCatching {
                                    ServiceLocator.subtitles.search(
                                        settings.openSubtitlesKey,
                                        subtitleQuery,
                                        subtitleLanguage
                                    )
                                }.onSuccess {
                                    subtitleResults = it
                                    subtitleState = if (it.isEmpty()) SubtitleUiState.Error("No subtitles found.")
                                    else SubtitleUiState.Idle
                                }.onFailure {
                                    subtitleState = SubtitleUiState.Error(it.message ?: "Search failed")
                                }
                            }
                        },
                        onPick = { result ->
                            scope.launch {
                                subtitleState = SubtitleUiState.Loading
                                runCatching {
                                    ServiceLocator.subtitles.download(settings.openSubtitlesKey, result.fileId)
                                }.onSuccess { raw ->
                                    val cues = ServiceLocator.subtitles.parse(raw)
                                    if (cues.isEmpty()) {
                                        subtitleState = SubtitleUiState.Error("That file had no readable cues.")
                                    } else {
                                        subtitleCues = cues
                                        subtitleName = result.release
                                        subtitlesOn = true
                                        subtitleState = SubtitleUiState.Idle
                                        hud = "Subtitles loaded"
                                    }
                                }.onFailure {
                                    subtitleState = SubtitleUiState.Error(it.message ?: "Download failed")
                                }
                            }
                        },
                        onLoadUrl = {
                            scope.launch {
                                subtitleState = SubtitleUiState.Loading
                                runCatching { ServiceLocator.subtitles.fetchDirect(subtitleUrl.trim()) }
                                    .onSuccess { raw ->
                                        val cues = ServiceLocator.subtitles.parse(raw)
                                        if (cues.isEmpty()) {
                                            subtitleState = SubtitleUiState.Error("That link had no readable cues.")
                                        } else {
                                            subtitleCues = cues
                                            subtitleName = subtitleUrl.substringAfterLast('/')
                                            subtitlesOn = true
                                            subtitleState = SubtitleUiState.Idle
                                            hud = "Subtitles loaded"
                                        }
                                    }
                                    .onFailure {
                                        subtitleState = SubtitleUiState.Error(it.message ?: "Could not load subtitle")
                                    }
                            }
                        },
                        onDelay = {
                            subtitleDelay = it
                            scope.launch { ServiceLocator.prefs.setSetting("subtitleDelayMs", it) }
                        },
                        onSize = {
                            subtitleSize = it
                            scope.launch { ServiceLocator.prefs.setSetting("subtitleSize", it) }
                        },
                        onBottom = {
                            subtitleBottom = it
                            scope.launch { ServiceLocator.prefs.setSetting("subtitleBottom", it) }
                        },
                        onToggle = { subtitlesOn = it },
                        onOff = {
                            subtitleCues = emptyList()
                            subtitleName = ""
                            subtitleState = SubtitleUiState.Idle
                            subtitlesOn = false
                        },
                        onDismiss = { panel = null }
                    )
                }
            }
        }
    }
}

private sealed interface SubtitleUiState {
    data object Idle : SubtitleUiState
    data object Loading : SubtitleUiState
    data class Error(val message: String) : SubtitleUiState
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    big: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (big) 60.dp else 46.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, CircleShape)
            .scale(if (focused) 1.06f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(if (big) 34.dp else 24.dp)
        )
    }
}

@Composable
private fun TextButtonFlat(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) PotatoAccent else PotatoTextDim,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun <T> ChoicePanel(
    title: String,
    options: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Surface(
        color = PotatoBgElevated,
        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
    ) {
        Column(Modifier.padding(vertical = 26.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = PotatoText)
                Spacer(Modifier.weight(1f))
                PlayerIconButton(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(options.size) { index ->
                    val item = options[index]
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) PotatoSurface else Color.Transparent)
                            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(10.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .clickable { onSelect(item) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label(item),
                            color = if (selected(item)) PotatoAccent else PotatoText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selected(item)) {
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PotatoAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChoicePanel(
    title: String,
    entries: List<MenuEntry>,
    onSelect: (MenuEntry) -> Unit,
    onDismiss: () -> Unit
) {
    if (entries.isEmpty()) {
        ChoicePanel(
            title = title,
            options = listOf("Nothing detected on this page"),
            selected = { false },
            label = { it },
            onSelect = { },
            onDismiss = onDismiss
        )
    } else {
        ChoicePanel(
            title = title,
            options = entries,
            selected = { false },
            label = { it.label },
            onSelect = onSelect,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun SubtitlePanel(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    url: String,
    onUrl: (String) -> Unit,
    state: SubtitleUiState,
    results: List<SubtitleResult>,
    cuesLoaded: Int,
    trackName: String,
    delay: Int,
    size: Float,
    bottom: Float,
    enabled: Boolean,
    onSearch: () -> Unit,
    onPick: (SubtitleResult) -> Unit,
    onLoadUrl: () -> Unit,
    onDelay: (Int) -> Unit,
    onSize: (Float) -> Unit,
    onBottom: (Float) -> Unit,
    onToggle: (Boolean) -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = PotatoBgElevated,
        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
        modifier = Modifier
            .width(460.dp)
            .fillMaxHeight()
    ) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtitles", style = MaterialTheme.typography.headlineMedium, color = PotatoText)
                    Spacer(Modifier.weight(1f))
                    PlayerIconButton(Icons.Filled.Close, "Close") { onDismiss() }
                }
            }
            if (cuesLoaded > 0) {
                item {
                    Text(
                        "Loaded: $trackName · $cuesLoaded cues",
                        color = PotatoRating,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButtonFlat(if (enabled) "Hide" else "Show") { onToggle(!enabled) }
                        TextButtonFlat("Remove") { onOff() }
                    }
                }
                item { StepperRow("Timing", "${"%.1f".format(delay / 1000f)}s", -100, 100, { onDelay(delay - 100) }, { onDelay(delay + 100) }) }
                item { StepperRow("Size", "${size.roundToInt()}", -2, 2, { onSize((size - 2f).coerceAtLeast(10f)) }, { onSize((size + 2f).coerceAtMost(42f)) }) }
                item { StepperRow("Position", "${bottom.roundToInt()}%", -1, 1, { onBottom((bottom - 1f).coerceAtLeast(1f)) }, { onBottom((bottom + 1f).coerceAtMost(30f)) }) }
            }
            item {
                Text("Search OpenSubtitles", color = PotatoText, style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = { Text("Title or episode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = onLanguage,
                        label = { Text("Lang") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                    TextButtonFlat("Search") { onSearch() }
                }
            }
            when (state) {
                is SubtitleUiState.Loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PotatoAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Working…", color = PotatoTextDim)
                    }
                }
                is SubtitleUiState.Error -> item {
                    Text(state.message, color = PotatoDanger, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Unit
            }
            items(results.take(12)) { result ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) PotatoSurface else Color.Transparent)
                        .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(10.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onPick(result) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(result.release.ifBlank { result.fileName }, color = PotatoText, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${result.language.uppercase()} · ${result.downloads} downloads",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Load from a subtitle URL", color = PotatoText, style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrl,
                    label = { Text("https://… .srt / .vtt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { TextButtonFlat("Load subtitle URL") { onLoadUrl() } }
            item {
                Text(
                    "OpenSubtitles needs a free API key in Settings. Subtitle timing, size and position are saved for the next episode.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    min: Int,
    max: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButtonFlat("−") { onMinus() }
        Text(value, color = PotatoText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        TextButtonFlat("+") { onPlus() }
    }
}

private fun qualityChoices(tracks: Tracks): List<TrackChoice> {
    val list = mutableListOf(TrackChoice("Auto", null))
    tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                list += TrackChoice(formatLabel(format), TrackSelectionOverride(group.mediaTrackGroup, index), group)
            }
        }
    return list.distinctBy { it.label }
}

private fun audioChoices(tracks: Tracks): List<TrackChoice> {
    val list = mutableListOf(TrackChoice("Auto", null))
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        .forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val language = format.language?.let { " ${it.uppercase()}" }.orEmpty()
                val label = (format.label ?: "Audio") + language +
                    if (format.channelCount > 2) " · ${format.channelCount}ch" else ""
                list += TrackChoice(label, TrackSelectionOverride(group.mediaTrackGroup, index), group)
            }
        }
    return list.distinctBy { it.label }
}

private fun textChoices(tracks: Tracks): List<TrackChoice> {
    val list = mutableListOf(TrackChoice("Off", null))
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val label = (format.label ?: format.language?.uppercase() ?: "Subtitle")
                list += TrackChoice(label, TrackSelectionOverride(group.mediaTrackGroup, index), group)
            }
        }
    return list.distinctBy { it.label }
}

private fun formatLabel(format: Format): String {
    val height = format.height
    val base = if (height > 0) "${height}p" else (format.label ?: "Video")
    val fps = if (format.frameRate > 0) " · ${format.frameRate.roundToInt()}fps" else ""
    val bitrate = if (format.bitrate > 0) " · ${"%.1f".format(format.bitrate / 1_000_000f)} Mbps" else ""
    return base + fps + bitrate
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
