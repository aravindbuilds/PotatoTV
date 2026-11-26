package app.potatotv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.model.Progress
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.HeroBanner
import app.potatotv.ui.components.LoadingState
import app.potatotv.ui.components.Mascot
import app.potatotv.ui.components.SectionHeader
import app.potatotv.ui.components.SectionRow
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoDimens
import app.potatotv.ui.theme.PotatoGoldSoft
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoSurfaceSoft
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import app.potatotv.ui.theme.PotatoTextFaint
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
        val compact = maxWidth < 600.dp
        val horizontal = when {
            compact -> PotatoDimens.screenPaddingCompact
            maxWidth < 900.dp -> PotatoDimens.screenPaddingMedium
            else -> PotatoDimens.screenPadding
        }
        // On very wide displays the rails stop stretching and stay optically
        // centred, with identical empty margins on both sides.
        val railPadding = if (maxWidth > PotatoDimens.maxContentWidth + horizontal + horizontal) {
            (maxWidth - PotatoDimens.maxContentWidth) / 2
        } else {
            horizontal
        }
        val cardGap = if (compact) PotatoDimens.compactCardGap else PotatoDimens.cardGap
        val posterWidth = if (compact) 132 else 176

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item(key = "hero") {
                val hero = state.hero
                if (hero != null) {
                    HeroBanner(
                        item = hero,
                        progress = progress[hero.id],
                        inMyList = myList.any { it.id == hero.id },
                        onPlay = { nav.navigate("title/${hero.type}/${hero.id}") },
                        onToggleList = {
                            scope.launch { ServiceLocator.prefs.toggleMyList(hero) }
                        },
                        horizontal = railPadding
                    )
                }
            }

            if (continuing.isNotEmpty()) {
                item(key = "continue") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = PotatoDimens.sectionGap)
                    ) {
                        SectionHeader(
                            title = "Continue Watching",
                            subtitle = "Pick up where you left off",
                            horizontal = railPadding
                        )
                        Spacer(Modifier.height(16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = railPadding),
                            horizontalArrangement = Arrangement.spacedBy(cardGap)
                        ) {
                            items(continuing, key = { it.key }) { entry ->
                                ContinueCard(progress = entry) {
                                    nav.navigate("title/${entry.type}/${entry.imdbId ?: entry.key}")
                                }
                            }
                        }
                    }
                }
            }

            items(rows, key = { it.key }) { row ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = PotatoDimens.sectionGap)
                ) {
                    SectionRow(
                        title = row.title,
                        items = row.items,
                        onSelect = { nav.navigate("title/${it.type}/${it.id}") },
                        horizontal = railPadding,
                        cardSpacing = cardGap,
                        posterWidth = posterWidth
                    )
                }
            }

            item(key = "footer") {
                HomeFooter(horizontal = railPadding)
            }
        }
    }
}

@Composable
private fun ContinueCard(progress: Progress, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "continue-scale")
    val shape = RoundedCornerShape(PotatoDimens.cardRadius)

    Row(
        Modifier
            .width(320.dp)
            .scale(scale)
            .clip(shape)
            .background(PotatoSurface)
            .border(
                1.dp,
                if (focused) PotatoAccent else PotatoLine.copy(alpha = 0.6f),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(88.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PotatoSurfaceSoft)
        ) {
            AsyncImage(
                model = progress.poster,
                contentDescription = progress.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = PotatoGoldSoft,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
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
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.fraction)
                        .fillMaxSize()
                        .background(PotatoAccent)
                )
            }
        }
    }
}

@Composable
private fun HomeFooter(horizontal: Dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal)
            .padding(top = 64.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PotatoLine.copy(alpha = 0.7f))
        )
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(30)
            Spacer(Modifier.width(8.dp))
            Text(
                "POTATO",
                color = PotatoText,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp)
            )
            Text(
                "TV",
                color = PotatoAccent,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "A browser and a player. It does not host, index or distribute any video. " +
                "Sites, playlists and the content they serve belong to third parties — only watch what you have the right to watch.",
            color = PotatoTextFaint,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp)
        )
    }
}
