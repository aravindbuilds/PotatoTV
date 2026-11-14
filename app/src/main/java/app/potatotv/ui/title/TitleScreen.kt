package app.potatotv.ui.title

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.model.MetaDetail
import app.potatotv.data.model.MetaVideo
import app.potatotv.data.model.Site
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.LoadingState
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.components.Tag
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun TitleScreen(nav: NavController, type: String, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var meta by remember { mutableStateOf<MetaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    var showSites by remember { mutableStateOf(false) }
    var pendingEpisode by remember { mutableStateOf<MetaVideo?>(null) }

    val sites by ServiceLocator.prefs.sites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by ServiceLocator.prefs.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val myList by ServiceLocator.prefs.myList.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by ServiceLocator.prefs.settings.collectAsStateWithLifecycle(initialValue = app.potatotv.data.model.AppSettings())
    val progress by ServiceLocator.prefs.progress.collectAsStateWithLifecycle(initialValue = emptyMap())

    LaunchedEffect(type, id) {
        loading = true
        error = null
        runCatching { ServiceLocator.cinemeta.meta(type, id) }
            .onSuccess {
                meta = it
                season = it?.seasons()?.firstOrNull()
                if (it == null) error = "This title could not be loaded."
            }
            .onFailure { error = it.message }
        loading = false
    }

    if (loading) {
        LoadingState("Fetching details…")
        return
    }
    val detail = meta
    if (detail == null || error != null) {
        EmptyState("Title unavailable", error ?: "Try another title.") {
            nav.popBackStack()
        }
        return
    }

    val inList = myList.any { it.id == detail.id }
    val saved = progress[detail.id]
    val seasons = detail.seasons()
    val episodes = detail.videos.filter { it.season == season }.sortedBy { it.episode ?: 0 }

    fun openSite(site: Site, episode: MetaVideo?) {
        val suffix = if (episode != null && episode.season != null && episode.episode != null) {
            " S%02dE%02d".format(episode.season, episode.episode)
        } else ""
        val query = detail.name + suffix
        val url = buildSiteUrl(site, query, detail.id, detail.moviedbId, detail.type)
        showSites = false
        pendingEpisode = null
        nav.navigate("browser?url=${URLEncoder.encode(url, "UTF-8")}&name=${URLEncoder.encode(site.name, "UTF-8")}&siteId=${URLEncoder.encode(site.id, "UTF-8")}")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 78.dp, bottom = 70.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                AsyncImage(
                    model = detail.background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x220E0E0E), Color(0x660E0E0E), Color(0xE60E0E0E), PotatoBg)
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xAA0E0E0E), Color(0x330E0E0E), Color.Transparent)
                            )
                        )
                )

                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        Modifier
                            .width(156.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PotatoSurface)
                            .border(1.dp, PotatoLine, RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = detail.poster,
                            contentDescription = detail.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail.name,
                            color = PotatoText,
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!detail.imdbRating.isNullOrBlank()) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, PotatoRating, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(detail.imdbRating, color = PotatoRating, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(11.dp))
                            }
                            Text(
                                listOfNotNull(
                                    detail.releaseInfo,
                                    if (detail.type == "series") "Series" else "Movie",
                                    detail.runtime
                                ).joinToString("  ·  "),
                                color = PotatoTextDim,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (detail.genres.isNotEmpty()) {
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                detail.genres.take(4).forEach { Tag(it, accent = true) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PotatoButton(
                                label = if (saved != null && saved.positionMs > 10_000) "Resume" else "Play",
                                icon = Icons.Filled.PlayArrow,
                                primary = true
                            ) {
                                pendingEpisode = null
                                showSites = true
                            }
                            PotatoButton(
                                label = if (inList) "In My List" else "My List",
                                icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                                primary = false
                            ) {
                                scope.launch { ServiceLocator.prefs.toggleMyList(detail.toPreview()) }
                            }
                            if (settings.simklToken.isNotBlank() && detail.id.startsWith("tt")) {
                                PotatoButton(
                                    label = "Mark watched",
                                    icon = Icons.Filled.Cloud,
                                    primary = false
                                ) {
                                    scope.launch {
                                        val ok = runCatching {
                                            ServiceLocator.simkl.markWatched(
                                                clientId = settings.simklClientId,
                                                token = settings.simklToken,
                                                imdbId = detail.id,
                                                type = detail.type,
                                                title = detail.name,
                                                season = season,
                                                episode = episodes.firstOrNull()?.episode
                                            )
                                        }.getOrDefault(false)
                                        Toast.makeText(
                                            context,
                                            if (ok) "Sent to Simkl" else "Simkl rejected the request",
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 48.dp)) {
                if (!detail.description.isNullOrBlank()) {
                    Text(
                        "Overview",
                        color = PotatoText,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail.description,
                        color = Color(0xFFD7D7D7),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    )
                    Spacer(Modifier.height(20.dp))
                }
                if (detail.cast.isNotEmpty()) {
                    Text("Cast", color = PotatoText, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail.cast.take(10).joinToString("  ·  "),
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        if (detail.type == "series" && seasons.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 48.dp)) {
                    Text("Episodes", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s ->
                            val selected = s == season
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) PotatoAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, if (selected) PotatoAccent else Color.Transparent, RoundedCornerShape(20.dp))
                                    .clickable { season = s }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    "Season $s",
                                    color = if (selected) PotatoAccent else PotatoTextDim,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(episode) {
                    pendingEpisode = episode
                    showSites = true
                }
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(top = 34.dp)
            ) {
                Text(
                    "Open on a site",
                    color = PotatoText,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pick a source. PotatoTV opens it in the built-in browser and hands any HLS/DASH stream to the native player.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sites.sortedByDescending { it.id in favorites }) { site ->
                        SiteChip(site, site.id in favorites) {
                            pendingEpisode = null
                            showSites = true
                        }
                    }
                }
            }
        }
    }

    if (showSites) {
        SitePickerDialog(
            sites = sites,
            favorites = favorites,
            title = detail.name,
            episode = pendingEpisode,
            onOpen = { openSite(it, pendingEpisode) },
            onAddSite = {
                showSites = false
                nav.navigate("sites")
            },
            onDismiss = {
                showSites = false
                pendingEpisode = null
            }
        )
    }
}

@Composable
private fun EpisodeRow(episode: MetaVideo, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PotatoSurface else Color.Transparent)
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(150.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = episode.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(
                    episode.season?.let { "S%02d".format(it) },
                    episode.episode?.let { "E%02d".format(it) }
                ).joinToString("") + "  " + (episode.name ?: "Episode"),
                color = PotatoText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            episode.overview?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!episode.released.isNullOrBlank()) {
            Text(episode.released, color = PotatoTextDim, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PotatoAccent)
    }
}

@Composable
private fun SiteChip(site: Site, favorite: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) PotatoSurface else PotatoBgElevated)
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favorite) {
            Text("★", color = PotatoRating)
            Spacer(Modifier.width(6.dp))
        }
        Text(site.name, color = PotatoText, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = PotatoTextDim, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SitePickerDialog(
    sites: List<Site>,
    favorites: Set<String>,
    title: String,
    episode: MetaVideo?,
    onOpen: (Site) -> Unit,
    onAddSite: () -> Unit,
    onDismiss: () -> Unit
) {
    val sorted = sites.sortedWith(compareByDescending<Site> { it.id in favorites }.thenBy { it.name })
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Open “$title”", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                if (episode != null) {
                    Text(
                        "S%02dE%02d · %s".format(episode.season ?: 0, episode.episode ?: 0, episode.name.orEmpty()),
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (sorted.isEmpty()) {
                    Text(
                        "You haven't added any sites yet.",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(14.dp))
                    PotatoButton("Add a site", primary = true, onClick = onAddSite)
                } else {
                    LazyColumn(
                        Modifier
                            .height(360.dp)
                            .width(470.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sorted, key = { it.id }) { site ->
                            var focused by remember { mutableStateOf(false) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (focused) PotatoSurface else Color.Transparent)
                                    .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(9.dp))
                                    .onFocusChanged { focused = it.isFocused }
                                    .clickable { onOpen(site) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(site.name, color = PotatoText, style = MaterialTheme.typography.titleMedium)
                                if (site.id in favorites) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("★", color = PotatoRating, fontSize = 12.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    site.url.substringAfter("://").substringBefore('/'),
                                    color = PotatoTextDim,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Tip: hold OK on a site card to mark it as a favourite.", color = PotatoTextDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun buildSiteUrl(
    site: Site,
    query: String,
    imdb: String,
    tmdb: Int?,
    type: String
): String {
    val q = URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    // FMHY deeplink sites address a title page directly by TMDB id. Prefer the
    // template that matches the media type, and fall back to the site root when
    // Cinemeta has no moviedb_id for this title.
    val deeplink = when {
        type == "series" || type == "tv" -> site.tvTemplate.ifBlank { site.movieTemplate }
        else -> site.movieTemplate.ifBlank { site.tvTemplate }
    }
    if (deeplink.isNotBlank()) {
        if (tmdb == null) return site.url
        return deeplink
            .replace("{tmdb}", tmdb.toString())
            .replace("{q}", q)
            .replace("{title}", q)
    }

    val template = site.searchTemplate
    if (template.isBlank()) return site.url
    // A template that still needs a TMDB id cannot be resolved without one.
    if (template.contains("{tmdb}")) {
        return if (tmdb == null) site.url else template.replace("{tmdb}", tmdb.toString())
    }
    return template
        .replace("{q}", q)
        .replace("{imdb}", imdb)
        .replace("{title}", q)
}

