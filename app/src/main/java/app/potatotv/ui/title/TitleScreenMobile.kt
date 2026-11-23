package app.potatotv.ui.title

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.potatotv.data.model.MetaDetail
import app.potatotv.data.model.MetaVideo
import app.potatotv.data.model.Progress
import app.potatotv.data.model.Site
import app.potatotv.data.model.ratingOutOf10
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import app.potatotv.ui.theme.isCompactScreen
import coil.compose.AsyncImage

/** Phone-portrait title page: compact hero, clear episodes, title-matched sources. */
@Composable
fun TitleScreenMobile(
    detail: MetaDetail,
    sites: List<Site>,
    favorites: Set<String>,
    inList: Boolean,
    saved: Progress?,
    episodes: List<MetaVideo>,
    season: Int?,
    onSeason: (Int) -> Unit,
    onOpenSite: (Site, MetaVideo?) -> Unit,
    onToggleList: () -> Unit,
    onBack: () -> Unit
) {
    var selectedEpisode by remember { mutableStateOf<MetaVideo?>(null) }
    val bestSite = sites.firstOrNull()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(PotatoBg),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = detail.background ?: detail.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color(0x330E0E0E), Color(0x990E0E0E), PotatoBg)
                        )
                    )
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(10.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        Modifier
                            .width(92.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PotatoSurface)
                    ) {
                        AsyncImage(
                            model = detail.poster,
                            contentDescription = detail.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail.name,
                            color = PotatoText,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(
                                detail.imdbRating?.let { "★ $it" },
                                detail.releaseInfo,
                                if (detail.type == "series") "Series" else "Movie",
                                detail.runtime
                            ).joinToString("  ·  "),
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (detail.genres.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                detail.genres.take(4).joinToString(" · "),
                                color = PotatoAccent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PotatoButton(
                        label = if (saved != null && saved.positionMs > 10_000) "Resume" else "Play",
                        icon = Icons.Filled.PlayArrow,
                        primary = true
                    ) {
                        bestSite?.let { onOpenSite(it, selectedEpisode) }
                    }
                    PotatoButton(
                        label = if (inList) "In My List" else "My List",
                        icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                        primary = false
                    ) { onToggleList() }
                }
                if (!detail.description.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Overview", color = PotatoText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        detail.description,
                        color = Color(0xFFD0D0D0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (detail.type == "series" && episodes.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(20.dp))
                    Text("Episodes", color = PotatoText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..26).toList()) { s ->
                        if (episodes.any { it.season == s }) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (s == season) PotatoAccent.copy(alpha = 0.2f) else PotatoBgElevated)
                                    .border(1.dp, if (s == season) PotatoAccent else PotatoLine, RoundedCornerShape(18.dp))
                                    .clickable { onSeason(s) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "S$s",
                                    color = if (s == season) PotatoAccent else PotatoTextDim,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            items(episodes, key = { it.id }) { ep ->
                val active = selectedEpisode?.id == ep.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) PotatoSurface else PotatoBgElevated)
                        .border(1.dp, if (active) PotatoAccent else PotatoLine, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedEpisode = ep
                            bestSite?.let { onOpenSite(it, ep) }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(76.dp, 46.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(PotatoSurface)
                    ) {
                        AsyncImage(
                            model = ep.thumbnail,
                            contentDescription = ep.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "S${ep.season ?: 0}E${ep.episode ?: 0}  ${ep.name.orEmpty()}",
                            color = PotatoText,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            ep.released?.take(10).orEmpty(),
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PotatoAccent)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("Play on a source", color = PotatoText, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${sites.size} title-matched sources ready to play",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        items(sites, key = { it.id }) { site ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PotatoBgElevated)
                    .border(1.dp, PotatoLine, RoundedCornerShape(10.dp))
                    .clickable { onOpenSite(site, selectedEpisode) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        site.name,
                        color = PotatoText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        site.url.substringAfter("://").substringBefore('/'),
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (site.starred) Text("🌟", fontSize = 12.sp)
                if (site.rating > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("${site.ratingOutOf10}/10", color = PotatoRating, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (site.id in favorites) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, contentDescription = null, tint = PotatoRating, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
