package app.potatotv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import app.potatotv.R
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.model.Progress
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoDimens
import app.potatotv.ui.theme.PotatoGoldSoft
import app.potatotv.ui.theme.PotatoGlow
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoOnAccent
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import app.potatotv.ui.theme.PotatoTextFaint
import coil.compose.AsyncImage

@Composable
fun Mascot(size: Int = 36) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.logo_mascot),
        contentDescription = null,
        modifier = Modifier.size(size.dp)
    )
}

@Composable
fun Wordmark(compact: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Mascot(if (compact) 30 else 38)
        Spacer(Modifier.width(8.dp))
        Text(
            "POTATO",
            color = PotatoText,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 18.sp else 22.sp,
            letterSpacing = (-0.4).sp
        )
        Text(
            "TV",
            color = PotatoAccent,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 18.sp else 22.sp,
            letterSpacing = (-0.4).sp,
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = Shadow(color = PotatoGlow, blurRadius = 26f)
            )
        )
    }
}

@Composable
fun PotatoTopBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF70E0E0E), Color(0xD90E0E0E), Color.Transparent)
                )
            )
    ) {
        val compact = maxWidth < 720.dp
        val wide = maxWidth >= 600.dp
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 14.dp else 34.dp)
                .padding(top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark(compact = compact) { onNavigate("home") }
            if (wide) {
                Spacer(Modifier.width(26.dp))
                NavPill("Home", currentRoute == "home") { onNavigate("home") }
                NavPill("Sites", currentRoute == "sites") { onNavigate("sites") }
                NavPill("Live TV", currentRoute == "iptv") { onNavigate("iptv") }
                NavPill("My List", currentRoute == "mylist") { onNavigate("mylist") }
            }
            Spacer(Modifier.weight(1f))
            SearchPill(
                compact = compact,
                onClick = { onNavigate("search") }
            )
            Spacer(Modifier.width(8.dp))
            IconPill(Icons.Filled.Settings, "Settings") { onNavigate("settings") }
        }
    }
}

/** Phone-portrait navigation: all destinations stay one tap away. */
@Composable
fun PotatoBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    data class NavItem(val route: String, val label: String, val icon: ImageVector)
    val items = listOf(
        NavItem("home", "Home", Icons.Filled.Home),
        NavItem("sites", "Sites", Icons.Filled.Language),
        NavItem("iptv", "Live", Icons.Filled.LiveTv),
        NavItem("mylist", "List", Icons.Filled.Bookmark),
        NavItem("search", "Search", Icons.Filled.Search),
        NavItem("settings", "Settings", Icons.Filled.Settings)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(PotatoBg)
            .border(1.dp, PotatoLine)
            .padding(vertical = 6.dp)
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) PotatoAccent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable {
                        onNavigate(item.route)
                    }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) PotatoAccent else PotatoTextDim,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    item.label,
                    color = if (selected) PotatoAccent else PotatoTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                when {
                    focused -> PotatoAccent
                    selected -> PotatoAccent.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .background(if (focused) PotatoSurface else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) PotatoAccent else PotatoTextDim,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SearchPill(compact: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .width(if (compact) 46.dp else 210.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PotatoSurface.copy(alpha = 0.9f))
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = "Search", tint = PotatoTextDim, modifier = Modifier.size(20.dp))
        if (!compact) {
            Spacer(Modifier.width(9.dp))
            Text("Search titles", color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(8.dp))
            .background(if (focused) PotatoSurface else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = PotatoTextDim, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    horizontal: Dp = PotatoDimens.screenPadding
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal)
    ) {
        Text(
            title,
            color = PotatoText,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(PotatoAccent)
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = PotatoTextFaint,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SectionRow(
    title: String,
    items: List<MetaPreview>,
    onSelect: (MetaPreview) -> Unit,
    horizontal: Dp = PotatoDimens.screenPadding,
    subtitle: String? = null,
    cardSpacing: Dp = PotatoDimens.cardGap,
    posterWidth: Int = 176
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title, subtitle, horizontal)
        Spacer(Modifier.height(16.dp))
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = horizontal),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(item, onClick = { onSelect(item) }, width = posterWidth)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: MetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 176,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "poster-scale")
    val radius = RoundedCornerShape(PotatoDimens.cardRadius)
    Column(
        modifier
            .width(width.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(radius)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) PotatoAccent else PotatoLine.copy(alpha = 0.6f),
                radius
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(radius)
                .background(
                    Brush.verticalGradient(listOf(PotatoSurface, Color(0xFF141414)))
                )
        ) {
            AsyncImage(
                model = item.posterLarge() ?: item.background,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.poster.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Mascot(52)
                }
            }
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        item.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!item.imdbRating.isNullOrBlank()) {
                            Text(
                                "★ ${item.imdbRating}",
                                color = PotatoGoldSoft,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            item.releaseInfo ?: if (item.type == "series") "Series" else "Movie",
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            if (!item.imdbRating.isNullOrBlank() && !focused) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, PotatoLine, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("★", color = PotatoGoldSoft, fontSize = 10.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(item.imdbRating, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = if (focused) PotatoText else PotatoTextDim,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HeroBanner(
    item: MetaPreview,
    progress: Progress?,
    inMyList: Boolean,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    horizontal: Dp = PotatoDimens.screenPadding
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = (maxWidth * 0.46f).coerceIn(390.dp, 620.dp)
        val compact = maxWidth < 600.dp
        val contentPadding = if (compact) PotatoDimens.screenPaddingCompact else horizontal

        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            AsyncImage(
                model = item.backdropLarge() ?: item.posterLarge(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // A deep base fade blends the artwork into the page, while the
            // symmetrical side vignette keeps type legible on any backdrop.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PotatoBg.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color(0x990E0E0E),
                                Color(0xF50E0E0E),
                                PotatoBg
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0x990E0E0E),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0x660E0E0E)
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, PotatoAccent.copy(alpha = 0.07f))
                        )
                    )
            )

            // Centred content frame: equal optical margins on both sides, with
            // the copy aligned to the same column as every rail below it.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = PotatoDimens.heroMaxWidth)
                    .padding(horizontal = contentPadding, vertical = 34.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PotatoAccent)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "FEATURED",
                        color = PotatoGoldSoft,
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (!item.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(64.dp)
                            .widthIn(max = 300.dp)
                    )
                } else {
                    Text(
                        item.name,
                        color = Color.White,
                        style = if (compact) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.displayLarge
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.imdbRating.isNullOrBlank()) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(2.dp, PotatoRating, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                item.imdbRating,
                                color = PotatoRating,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        listOfNotNull(
                            item.releaseInfo,
                            if (item.type == "series") "Series" else "Movie",
                            item.genres.take(2).joinToString(" · ").ifBlank { null }
                        ).joinToString("  ·  "),
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                item.description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PotatoButton(
                        label = if (progress != null && progress.positionMs > 10_000) "Resume" else "Play",
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = onPlay
                    )
                    PotatoButton(
                        label = if (inMyList) "In My List" else "My List",
                        icon = if (inMyList) Icons.Filled.Check else Icons.Filled.Add,
                        primary = false,
                        onClick = onToggleList
                    )
                }

                if (progress != null && progress.durationMs > 0) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.18f))
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
    }
}

@Composable
fun PotatoButton(
    label: String,
    icon: ImageVector? = null,
    primary: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "button-scale")
    val shape = RoundedCornerShape(10.dp)

    val background = when {
        primary && focused -> PotatoGoldSoft
        primary -> PotatoAccent
        focused -> Color.White.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val contentColor = if (primary) PotatoOnAccent else PotatoText
    val borderColor = when {
        focused && primary -> PotatoOnAccent.copy(alpha = 0.35f)
        focused -> PotatoAccent
        primary -> Color.Transparent
        else -> PotatoLine
    }

    Row(
        Modifier
            .scale(scale)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        val compact = maxHeight < 340.dp
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Mascot(if (compact) 54 else 110)
            Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
            Text(
                title,
                color = PotatoText,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                message,
                color = PotatoTextDim,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = if (compact) 4 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                PotatoButton(actionLabel, primary = true, onClick = onAction)
            }
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading…") {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 320.dp
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Mascot(if (compact) 48 else 84)
            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
            Text(
                message,
                color = PotatoTextDim,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun Tag(text: String, accent: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (accent) PotatoAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (accent) PotatoAccent.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, color = if (accent) PotatoAccent else PotatoTextDim, style = MaterialTheme.typography.labelMedium)
    }
}
