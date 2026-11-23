package app.potatotv.ui.iptv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.iptv.IptvCategory
import app.potatotv.data.iptv.IptvCountry
import app.potatotv.data.model.Channel
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.Playlist
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.LoadingState
import app.potatotv.ui.components.Mascot
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.player.PlayerActivity
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import app.potatotv.ui.theme.isCompactScreen
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun IptvScreen(nav: NavController) {
    val vm: IptvViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playlists by ServiceLocator.prefs.playlists.collectAsStateWithLifecycle(initialValue = emptyList<Playlist>())
    val settings by ServiceLocator.prefs.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val starred by ServiceLocator.prefs.starredChannels.collectAsStateWithLifecycle(initialValue = emptySet<String>())
    val working by ServiceLocator.prefs.workingChannels.collectAsStateWithLifecycle(initialValue = emptySet<String>())

    // 18+ playlists stay hidden until the user turns them on in Settings.
    val visiblePlaylists = remember(playlists, settings.adultSources) {
        playlists.filter { !it.adult || settings.adultSources }
    }

    LaunchedEffect(settings.adultSources) {
        if (!settings.adultSources && vm.activePlaylist?.adult == true) {
            vm.closePlaylist()
        }
    }

    var showAdd by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    val compact = isCompactScreen()
    val hPad = if (compact) 16.dp else 48.dp

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.query = text
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search live TV")
        }
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No speech recogniser on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = vm.activePlaylist != null) {
        vm.closePlaylist()
    }

    val data = vm.data
    val categories: List<IptvCategory> = data?.categories.orEmpty()
    val countries: List<IptvCountry> = data?.countries.orEmpty()

    val baseChannels: List<Channel> = if (vm.activePlaylist != null) {
        vm.playlistChannels
    } else {
        data?.channels.orEmpty()
    }

    val categoryName = categories.firstOrNull { it.id == vm.category }?.name
    val countryName = countries.firstOrNull { it.code == vm.country }?.name

    val filtered = remember(
        baseChannels, vm.query, vm.category, vm.country, vm.tab, starred, working, vm.workingOnly, categoryName
    ) {
        val q = vm.query.trim()
        baseChannels.filter { channel ->
            val matchesQuery = q.isBlank() ||
                channel.name.contains(q, ignoreCase = true) ||
                channel.group?.contains(q, ignoreCase = true) == true ||
                channel.playlistName?.contains(q, ignoreCase = true) == true
            val matchesCategory = vm.category == null ||
                vm.category in channel.categories ||
                channel.group == categoryName
            val matchesCountry = vm.country == null || channel.country == vm.country
            val matchesTab = when (vm.tab) {
                IptvTab.Starred -> channel.id in starred
                IptvTab.Working -> channel.id in working
                else -> true
            }
            val matchesWorking = !vm.workingOnly || channel.id in working
            matchesQuery && matchesCategory && matchesCountry && matchesTab && matchesWorking
        }
    }

    @Composable
    fun HeaderActions() {
        if (vm.tab != IptvTab.Playlists && filtered.isNotEmpty()) {
            PotatoButton(
                label = if (vm.checking) "Testing ${vm.checkDone}/${vm.checkTotal}" else "Check",
                icon = Icons.Filled.Check,
                primary = false
            ) {
                vm.checkWorking(if (vm.tab == IptvTab.Starred || vm.tab == IptvTab.Working) {
                    baseChannels.filter { it.id in starred || vm.tab == IptvTab.Working }
                } else {
                    filtered
                })
            }
            Spacer(Modifier.width(8.dp))
        }
        PotatoButton("Add playlist", icon = Icons.Filled.Add, primary = true) { showAdd = true }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = if (compact) 8.dp else 84.dp)
    ) {
        if (compact) {
            Column(Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(40)
                    Spacer(Modifier.width(10.dp))
                    Text("Live TV", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    when {
                        vm.activePlaylist != null -> "Playing “${vm.activePlaylist?.name}”"
                        data != null -> "${data.channels.size} channels · ${visiblePlaylists.size} playlists"
                        else -> "Free live TV and your own playlists"
                    },
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HeaderActions() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = hPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Mascot(58)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Live TV", color = PotatoText, style = MaterialTheme.typography.displayMedium)
                    Text(
                        when {
                            vm.activePlaylist != null -> "Playing the “${vm.activePlaylist?.name}” playlist"
                            data != null -> "${data.channels.size} live channels from iptv-org · ${visiblePlaylists.size} of your playlists"
                            else -> "Free live TV from iptv-org, plus your own M3U and Xtream playlists"
                        },
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                HeaderActions()
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad)
                .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IptvTab.entries.forEach { tab ->
                TabPill(
                    label = when (tab) {
                        IptvTab.Live -> "Live"
                        IptvTab.Playlists -> "Playlists (${visiblePlaylists.size})"
                        IptvTab.Starred -> "Starred (${starred.size})"
                        IptvTab.Working -> "Working (${working.size})"
                    },
                    selected = vm.tab == tab
                ) {
                    vm.tab = tab
                    vm.workingOnly = false
                    if (tab != IptvTab.Playlists) vm.closePlaylist()
                }
            }
            Spacer(Modifier.weight(1f))
            if (vm.activePlaylist != null) {
                Text(
                    "Showing ${vm.playlistChannels.size} channels · BACK to close",
                    color = PotatoAccent,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (vm.checking || vm.checkMessage != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vm.checking) {
                    LinearProgressIndicator(
                        progress = { vm.checkProgress },
                        color = PotatoAccent,
                        trackColor = PotatoSurface,
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    vm.checkMessage ?: "Testing streams…",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (vm.tab != IptvTab.Playlists) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad)
                    .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text("Search channels, groups, playlists…", color = PotatoTextDim) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = PotatoTextDim) },
                    trailingIcon = {
                        IconButton(onClick = { startVoice() }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = PotatoAccent)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = if (compact) Modifier.width(240.dp) else Modifier.weight(1f)
                )
                if (vm.activePlaylist == null) {
                    FilterPill(
                        label = "Category: ${categoryName ?: "All"}",
                        active = vm.category != null
                    ) { showCategories = true }
                    FilterPill(
                        label = "Country: ${countryName ?: "All"}",
                        active = vm.country != null
                    ) { showCountries = true }
                    FilterPill(
                        label = "Working only",
                        active = vm.workingOnly
                    ) { vm.workingOnly = !vm.workingOnly }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            vm.tab == IptvTab.Playlists && vm.activePlaylist != null -> {
                PlaylistChannelPane(
                    vm = vm,
                    starred = starred,
                    onPlay = { playChannel(context, it) },
                    onStar = { channel -> scope.launch { ServiceLocator.prefs.toggleStarChannel(channel.id) } }
                )
            }
            vm.tab == IptvTab.Playlists -> {
                PlaylistsPane(
                    playlists = visiblePlaylists,
                    onOpen = { vm.tab = IptvTab.Playlists; vm.selectPlaylist(it) },
                    onRemove = { vm.removePlaylist(it) },
                    onAdd = { showAdd = true }
                )
            }
            vm.loading && data == null -> LoadingState("Downloading the live catalogue…")
            vm.error != null && data == null -> EmptyState(
                title = "Live TV is unavailable",
                message = vm.error ?: "Check your connection and try again.",
                actionLabel = "Try again"
            ) { vm.load(force = true) }
            vm.playlistLoading -> LoadingState("Reading that playlist…")
            vm.playlistError != null -> EmptyState(
                title = "Playlist failed",
                message = vm.playlistError ?: "Check the link and login.",
                actionLabel = "Back to playlists"
            ) { vm.closePlaylist() }
            filtered.isEmpty() -> EmptyState(
                title = if (vm.tab == IptvTab.Starred) "No starred channels" else "No channels match",
                message = when {
                    vm.tab == IptvTab.Starred -> "Hold OK on any channel to star it. Starred channels show up here and on every device reset."
                    vm.tab == IptvTab.Working -> "Run “Check working” on the Live tab to fill this list with streams that answered just now."
                    else -> "Try a different search, category or country."
                }
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(218.dp),
                contentPadding = PaddingValues(start = hPad, end = hPad, top = 4.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { channel ->
                    ChannelCard(
                        channel = channel,
                        starred = channel.id in starred,
                        working = channel.id in working,
                        onClick = { playChannel(context, channel) },
                        onStar = { scope.launch { ServiceLocator.prefs.toggleStarChannel(channel.id) } }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddPlaylistDialog(
            onDismiss = { showAdd = false },
            onSave = { playlist ->
                vm.addPlaylist(playlist)
                showAdd = false
                vm.tab = IptvTab.Playlists
                vm.selectPlaylist(playlist)
            }
        )
    }

    if (showCategories) {
        FilterPickerDialog(
            title = "Category",
            options = listOf("All" to null) + categories.map { it.name to it.id },
            selected = vm.category,
            onSelect = { vm.category = it; showCategories = false },
            onDismiss = { showCategories = false }
        )
    }

    if (showCountries) {
        FilterPickerDialog(
            title = "Country",
            options = listOf("All" to null) + countries.map { "${it.flag}  ${it.name}" to it.code },
            selected = vm.country,
            onSelect = { vm.country = it; showCountries = false },
            onDismiss = { showCountries = false }
        )
    }
}

private fun playChannel(context: Context, channel: Channel) {
    val headers = buildMap {
        channel.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        channel.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
    }
    val kind = when {
        channel.url.contains(".m3u8", ignoreCase = true) -> "hls"
        channel.url.contains(".mpd", ignoreCase = true) -> "dash"
        else -> "auto"
    }
    runCatching {
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                url = channel.url,
                kind = kind,
                title = channel.name,
                resumeKey = "iptv:${channel.id}",
                isLive = true,
                headers = headers
            )
        )
    }.onFailure {
        Toast.makeText(context, "Could not open that channel: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    focused -> PotatoSurface
                    selected -> PotatoAccent.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.05f)
                }
            )
            .border(
                1.dp,
                when {
                    focused -> PotatoAccent
                    selected -> PotatoAccent.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(20.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected || focused) PotatoAccent else PotatoTextDim,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PotatoSurface else PotatoBgElevated)
            .border(1.dp, if (focused || active) PotatoAccent else PotatoLine, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (focused) PotatoAccent else if (active) PotatoAccent else PotatoText,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    starred: Boolean,
    working: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) PotatoSurface else PotatoBgElevated)
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onStar)
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF202020)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                    )
                } else {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, tint = PotatoAccent, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = PotatoText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        channel.group,
                        channel.countryName ?: channel.country,
                        channel.playlistName
                    ).distinct().joinToString(" · ").ifBlank { "Live stream" },
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (starred) {
                Icon(Icons.Filled.Star, contentDescription = "Starred", tint = PotatoRating, modifier = Modifier.size(18.dp))
            } else if (working) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PotatoRating)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    channel.quality != null -> channel.quality ?: "LIVE"
                    channel.urls.isNotEmpty() -> "${channel.allSources().size} sources"
                    else -> "LIVE"
                },
                color = PotatoAccent,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            if (focused) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PotatoAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PlaylistsPane(
    playlists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    onRemove: (Playlist) -> Unit,
    onAdd: () -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyState(
            title = "No playlists yet",
            message = "Add an M3U link, paste an M3U file, connect an Xtream Codes login, or save a single stream URL.",
            actionLabel = "Add a playlist"
        ) { onAdd() }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist, onOpen = { onOpen(playlist) }, onRemove = { onRemove(playlist) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(playlist: Playlist, onOpen: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) PotatoSurface else PotatoBgElevated)
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onOpen, onLongClick = onRemove)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PotatoSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    playlist.name.take(2).uppercase(),
                    color = PotatoAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = PotatoText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (playlist.kind) {
                        Playlist.M3U_URL -> "M3U link"
                        Playlist.M3U_TEXT -> "Pasted M3U"
                        Playlist.XTREAM -> "Xtream Codes"
                        Playlist.STREAM -> "Single stream"
                        else -> playlist.kind
                    },
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (playlist.adult) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PotatoDanger.copy(alpha = 0.16f))
                        .border(1.dp, PotatoDanger.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "18+",
                        color = PotatoDanger,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove playlist", tint = PotatoTextDim, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            playlist.url.ifBlank { playlist.fileName }.substringAfter("://").take(58),
            color = PotatoTextDim,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text("Press OK to load · hold OK to remove", color = if (focused) PotatoAccent else PotatoLine, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlaylistChannelPane(
    vm: IptvViewModel,
    starred: Set<String>,
    onPlay: (Channel) -> Unit,
    onStar: (Channel) -> Unit
) {
    val channels = vm.playlistChannels
    if (vm.playlistLoading) {
        LoadingState("Reading that playlist…")
        return
    }
    if (vm.playlistError != null) {
        EmptyState(
            title = "Playlist failed",
            message = vm.playlistError ?: "Check the link and login.",
            actionLabel = "Close playlist"
        ) { vm.closePlaylist() }
        return
    }
    if (channels.isEmpty()) {
        LoadingState("No channels in this playlist yet…")
        return
    }
    val filtered = channels.filter { channel ->
        val q = vm.query.trim()
        q.isBlank() || channel.name.contains(q, true) || channel.group?.contains(q, true) == true
    }
    if (filtered.isEmpty()) {
        EmptyState("No channels match", "Try a different search.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(218.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(filtered, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                starred = channel.id in starred,
                working = false,
                onClick = { onPlay(channel) },
                onStar = { onStar(channel) }
            )
        }
    }
}

@Composable
private fun FilterPickerDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(Modifier.padding(22.dp).width(430.dp)) {
                Text(title, color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    Modifier
                        .height(380.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(options, key = { it.first }) { option ->
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (focused) PotatoSurface else Color.Transparent)
                                .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(9.dp))
                                .onFocusChanged { focused = it.isFocused }
                                .clickable { onSelect(option.second) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option.first,
                                color = if (option.second == selected) PotatoAccent else PotatoText,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (option.second == selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = PotatoAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(onDismiss: () -> Unit, onSave: (Playlist) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("url") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .width(540.dp)
            ) {
                Text("Add a playlist", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your playlists never leave this device.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "url" to "M3U link",
                        "xtream" to "Xtream",
                        "stream" to "Single stream",
                        "text" to "Paste M3U"
                    ).forEach { (key, label) ->
                        TabPill(label = label, selected = kind == key) { kind = key }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when (kind) {
                    "url", "stream" -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(if (kind == "stream") "Stream URL" else "M3U playlist URL") },
                            placeholder = { Text("https://…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "xtream" -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://provider.tv:8080") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("M3U contents") },
                            placeholder = { Text("#EXTM3U\n#EXTINF:-1,Channel\nhttps://…") },
                            minLines = 5,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = PotatoDanger, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PotatoButton(
                        label = if (saving) "Saving…" else "Save playlist",
                        primary = true
                    ) {
                        val cleanName = name.trim().let { if (it.isBlank()) "Playlist" else it }
                        if (kind == "text" && text.trim().length < 8) {
                            error = "Paste the contents of an M3U file first."
                            return@PotatoButton
                        }
                        if (kind != "text" && !url.trim().startsWith("http://") && !url.trim().startsWith("https://")) {
                            error = "That URL must start with http:// or https://."
                            return@PotatoButton
                        }
                        if (kind == "xtream" && username.isBlank()) {
                            error = "Xtream needs a username."
                            return@PotatoButton
                        }
                        val playlist = when (kind) {
                            "url" -> Playlist(
                                id = "m3u-" + UUID.randomUUID(),
                                name = cleanName,
                                kind = Playlist.M3U_URL,
                                url = url.trim()
                            )
                            "xtream" -> Playlist(
                                id = "xtream-" + UUID.randomUUID(),
                                name = cleanName,
                                kind = Playlist.XTREAM,
                                url = url.trim(),
                                username = username.trim(),
                                password = password
                            )
                            "stream" -> Playlist(
                                id = "stream-" + UUID.randomUUID(),
                                name = cleanName,
                                kind = Playlist.STREAM,
                                url = url.trim()
                            )
                            else -> Playlist(
                                id = "paste-" + UUID.randomUUID(),
                                name = cleanName,
                                kind = Playlist.M3U_TEXT,
                                fileName = "${UUID.randomUUID()}.m3u"
                            )
                        }
                        saving = true
                        scope.launch {
                            if (kind == "text") {
                                withContext(Dispatchers.IO) {
                                    val dir = File(context.filesDir, "playlists").apply { mkdirs() }
                                    File(dir, playlist.fileName).writeText(text)
                                }
                            }
                            onSave(playlist)
                            saving = false
                        }
                    }
                    PotatoButton("Cancel", primary = false) { onDismiss() }
                }
            }
        }
    }
}
