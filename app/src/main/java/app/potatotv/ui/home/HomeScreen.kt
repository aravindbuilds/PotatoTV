package app.potatotv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.model.Progress
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.HeroBanner
import app.potatotv.ui.components.LoadingState
import app.potatotv.ui.components.Mascot
import app.potatotv.ui.components.PosterCard
import app.potatotv.ui.components.SectionRow
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavController) {
    val vm: HomeViewModel = viewModel()
    val state = vm.state
    val myList by ServiceLocator.prefs.myList.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by ServiceLocator.prefs.progress.collectAsStateWithLifecycle(initialValue = emptyMap())
    val scope = rememberCoroutineScope()

    if (state.loading) {
        LoadingState("Loading your catalogue…")
        return
    }
    if (state.error != null) {
        EmptyState(
            title = "Can't reach the catalogue",
            message = state.error ?: "Check your connection.",
            actionLabel = "Try again"
        ) { vm.load(force = true) }
        return
    }

    val rows = state.rows
    val continuing = progress.values
        .filter { it.durationMs > 0 && it.positionMs > 10_000 }
        .sortedByDescending { it.updatedAt }
        .take(12)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = if (maxWidth < 720.dp) 20.dp else 48.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            item {
                val hero = state.hero
                if (hero != null) {
                    HeroBanner(
                        item = hero,
                        progress = progress[hero.id],
                        inMyList = myList.any { it.id == hero.id },
                        onPlay = { nav.navigate("title/${hero.type}/${hero.id}") },
                        onToggleList = {
                            scope.launch { ServiceLocator.prefs.toggleMyList(hero) }
                        }
                    )
                }
            }

            if (continuing.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 26.dp)) {
                        Text(
                            "Continue Watching",
                            color = PotatoText,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(start = horizontal, end = horizontal, bottom = 14.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontal),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(continuing, key = { it.key }) { entry ->
                                ContinueCard(entry) {
                                    nav.navigate("title/${entry.type}/${entry.imdbId ?: entry.key}")
                                }
                            }
                        }
                    }
                }
            }

            items(rows, key = { it.key }) { row ->
                Column(Modifier.padding(top = 28.dp)) {
                    SectionRowWithPadding(
                        title = row.title,
                        items = row.items,
                        horizontal = horizontal,
                        onSelect = { nav.navigate("title/${it.type}/${it.id}") }
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontal)
                        .padding(top = 56.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(38)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "PotatoTV 1.0",
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A browser and a player. It does not host, index or distribute any video. " +
                            "Sites, playlists and the content they serve belong to third parties — only watch what you have the right to watch.",
                        color = Color(0xFF777777),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRowWithPadding(
    title: String,
    items: List<MetaPreview>,
    horizontal: androidx.compose.ui.unit.Dp,
    onSelect: (MetaPreview) -> Unit
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = PotatoText,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = horizontal, end = horizontal, bottom = 14.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontal),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(item, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun ContinueCard(progress: Progress, onClick: () -> Unit) {
    Row(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161616))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(86.dp)
                .height(122.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = progress.poster,
                contentDescription = progress.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                progress.title,
                color = PotatoText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${((progress.durationMs - progress.positionMs) / 60_000).coerceAtLeast(0)} min left",
                color = PotatoTextDim,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.fraction)
                        .height(4.dp)
                        .background(PotatoAccent)
                )
            }
        }
    }
}
