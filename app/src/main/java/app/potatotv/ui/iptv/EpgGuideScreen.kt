package app.potatotv.ui.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.potatotv.data.model.Channel
import app.potatotv.data.tv.CanonicalChannel
import app.potatotv.data.tv.NowNext
import app.potatotv.data.tv.Programme
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SLOT_MINUTES = 30L
private const val SLOT_COUNT = 12

private fun timeLabel(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun dayLabel(ms: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

/** Full-screen TV guide; phone portrait gets a compact now/next list instead. */
@Composable
fun GuidePane(
    vm: IptvViewModel,
    compact: Boolean,
    hPad: Dp,
    onPlay: (Channel) -> Unit,
    onProgramme: (Programme) -> Unit,
    onChannel: (CanonicalChannel) -> Unit
) {
    val channels = remember(vm.tick, vm.intelligenceReady, vm.language) { vm.guideChannels().take(80) }
    if (channels.isEmpty()) {
        EmptyState(
            title = "No schedules yet",
            message = "The guide is downloading in the background. Channels stay playable without it.",
            actionLabel = if (vm.guideRefreshing) "Refreshing…" else "Refresh guide"
        ) { vm.refreshGuide() }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = hPad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TV Guide",
                color = PotatoText,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${channels.size} channels with schedules",
                color = PotatoTextDim,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.width(12.dp))
            PotatoButton(
                label = if (vm.guideRefreshing) "Refreshing…" else "Refresh",
                icon = Icons.Filled.Refresh,
                primary = false
            ) { vm.refreshGuide() }
        }
        Spacer(Modifier.height(10.dp))
        var programmeQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = programmeQuery,
            onValueChange = { programmeQuery = it },
            label = { Text("Search programmes, films, sport…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        if (programmeQuery.trim().length >= 2) {
            val results = remember(programmeQuery, vm.tick) { vm.searchProgrammes(programmeQuery) }
            if (results.isEmpty()) {
                Text("No scheduled programmes match “${programmeQuery}”.", color = PotatoTextDim)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
                    items(results, key = { it.id }) { programme ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PotatoBgElevated)
                                .clickable { onProgramme(programme) }
                                .padding(12.dp)
                        ) {
                            Text(programme.title, color = PotatoText, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${dayLabel(programme.startUtc)} · ${programme.categories.joinToString(", ")}",
                                color = PotatoAccent,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        } else if (compact) {
            GuideList(vm, channels, onPlay, onProgramme, onChannel)
        } else {
            GuideGrid(vm, channels, onPlay, onProgramme, onChannel)
        }
    }
}

@Composable
private fun GuideList(
    vm: IptvViewModel,
    channels: List<CanonicalChannel>,
    onPlay: (Channel) -> Unit,
    onProgramme: (Programme) -> Unit,
    onChannel: (CanonicalChannel) -> Unit
) {
    val now = vm.tick
    val nowNext = vm.nowNext
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            val info = nowNext[channel.id] ?: NowNext(channel.id)
            GuideListRow(
                channel = channel,
                info = info,
                onPlay = {
                    val stream = channel.streams.firstOrNull()
                    if (stream != null) {
                        onPlay(
                            Channel(
                                id = channel.id,
                                name = channel.name,
                                url = stream.url,
                                logo = channel.logo,
                                canonicalId = channel.id,
                                referrer = stream.referrer,
                                userAgent = stream.userAgent
                            )
                        )
                    }
                },
                onDetails = { onChannel(channel) },
                onProgramme = { programme ->
                    if (programme != null) onProgramme(programme) else {
                        val epgId = channel.epgIds.firstOrNull().orEmpty()
                        val next = vm.schedule(epgId, now, now + 6 * 60 * 60 * 1000L).firstOrNull()
                        if (next != null) onProgramme(next)
                    }
                }
            )
        }
    }
}

@Composable
private fun GuideListRow(
    channel: CanonicalChannel,
    info: NowNext,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onProgramme: (Programme?) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PotatoBgElevated)
            .border(1.dp, PotatoLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onDetails)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel.logo, channel.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = PotatoText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    listOfNotNull(channel.languages.firstOrNull(), channel.categories.firstOrNull()).joinToString(" · "),
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            PotatoButton("Play", primary = true) { onPlay() }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (info.current != null) PotatoAccent.copy(alpha = 0.12f) else PotatoSurface)
                .border(1.dp, if (info.current != null) PotatoAccent.copy(alpha = 0.5f) else PotatoLine, RoundedCornerShape(8.dp))
                .clickable { onProgramme(info.current) }
                .padding(10.dp)
        ) {
            Column {
                Text(
                    info.current?.title ?: "No programme right now",
                    color = PotatoText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                info.current?.let { current ->
                    Text(
                        "${timeLabel(current.startUtc)} – ${timeLabel(current.endUtc)}  ·  ${info.secondsLeft / 60} min left",
                        color = PotatoAccent,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(Modifier.fillMaxWidth(info.progress).fillMaxSize().background(PotatoAccent))
                    }
                }
                info.next?.let { next ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Next  ${timeLabel(next.startUtc)}  ${next.title}",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideGrid(
    vm: IptvViewModel,
    channels: List<CanonicalChannel>,
    onPlay: (Channel) -> Unit,
    onProgramme: (Programme) -> Unit,
    onChannel: (CanonicalChannel) -> Unit
) {
    val slotDuration = SLOT_MINUTES * 60_000L
    val windowStart = (vm.tick / slotDuration) * slotDuration
    val windowEnd = windowStart + slotDuration * SLOT_COUNT
    val slotWidth = 150.dp
    val channelWidth = 190.dp
    val totalWidth = slotWidth * SLOT_COUNT
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(PotatoSurface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(channelWidth).padding(start = 8.dp)) {
                Text("CHANNEL", color = PotatoTextDim, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.horizontalScroll(horizontal)) {
                repeat(SLOT_COUNT) { index ->
                    Box(Modifier.width(slotWidth)) {
                        Text(
                            timeLabel(windowStart + index * slotDuration),
                            color = if (index == 0) PotatoAccent else PotatoTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxSize().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.width(channelWidth)) {
                items(channels, key = { it.id }) { channel ->
                    Box(
                        Modifier
                            .height(76.dp)
                            .fillMaxWidth()
                            .background(if (listState.layoutInfo.visibleItemsInfo.any { it.key == channel.id }) PotatoSurface else PotatoBgElevated)
                            .border(1.dp, PotatoLine)
                            .clickable {
                                val stream = channel.streams.firstOrNull()
                                if (stream != null) {
                                    onPlay(
                                        Channel(
                                            id = channel.id, name = channel.name, url = stream.url,
                                            logo = channel.logo, canonicalId = channel.id,
                                            referrer = stream.referrer, userAgent = stream.userAgent
                                        )
                                    )
                                }
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(channel.logo, channel.name)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                channel.name,
                                color = PotatoText,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Box(Modifier.horizontalScroll(horizontal).fillMaxSize()) {
                Column {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(channels, key = { it.id }) { channel ->
                            val programmes = vm.schedule(
                                channel.epgIds.firstOrNull().orEmpty(), windowStart, windowEnd
                            )
                            Box(
                                Modifier
                                    .height(76.dp)
                                    .width(totalWidth)
                                    .border(1.dp, PotatoLine)
                                    .background(PotatoBgElevated)
                            ) {
                                programmes.forEach { programme ->
                                    val start = programme.startUtc.coerceAtLeast(windowStart)
                                    val end = programme.endUtc.coerceAtMost(windowEnd)
                                    if (end <= start) return@forEach
                                    val offset = ((start - windowStart).toFloat() / slotDuration) * 150f
                                    val widthDp = (((end - start).toFloat() / slotDuration) * 150f)
                                        .coerceAtLeast(72f).dp
                                    val current = vm.tick in programme.startUtc..programme.endUtc
                                    Box(
                                        Modifier
                                            .offset(x = offset.dp)
                                            .width(widthDp)
                                            .fillMaxSize()
                                            .padding(horizontal = 2.dp, vertical = 3.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (current) PotatoAccent.copy(alpha = 0.22f) else PotatoSurface
                                            )
                                            .border(
                                                1.dp,
                                                if (current) PotatoAccent else PotatoLine,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onProgramme(programme) }
                                            .padding(7.dp)
                                    ) {
                                        Column {
                                            Text(
                                                programme.title,
                                                color = if (current) PotatoAccent else PotatoText,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                timeLabel(programme.startUtc),
                                                color = PotatoTextDim,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(url: String?, name: String) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(PotatoSurface),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Text(name.take(1), color = PotatoAccent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChannelDetailDialog(
    channel: CanonicalChannel,
    nowNext: NowNext?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    schedule: (Long, Long) -> List<Programme>,
    onProgramme: (Programme) -> Unit,
    onToggleStar: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val upcoming = remember(channel.id, nowNext?.current?.startUtc) {
        schedule(now, now + 8 * 60 * 60 * 1000L).take(8)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(
                Modifier
                    .padding(22.dp)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(channel.logo, channel.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            listOfNotNull(
                                channel.languages.joinToString(", ").ifBlank { null },
                                channel.categories.firstOrNull(),
                                channel.countryName ?: channel.country,
                                channel.network
                            ).joinToString(" · "),
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                channel.description?.let {
                    Text(it, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                nowNext?.current?.let { current ->
                    Text(
                        "On now: ${current.title}",
                        color = PotatoAccent,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${dayLabel(current.startUtc)} → ${timeLabel(current.endUtc)}",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (channel.feeds.isNotEmpty()) {
                    Text("Feeds", color = PotatoAccent, style = MaterialTheme.typography.labelLarge)
                    channel.feeds.take(6).forEach { feed ->
                        Text(
                            "• ${feed.name} · ${feed.languages.joinToString(", ")} · ${feed.format ?: "format unknown"}",
                            color = PotatoTextDim,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (channel.qualityClaims.isNotEmpty()) {
                    Text("Quality claims", color = PotatoAccent, style = MaterialTheme.typography.labelLarge)
                    Text(
                        channel.qualityClaims.joinToString(", ") + " (reported by provider or name; not independently verified)",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (upcoming.isNotEmpty()) {
                    Text("Upcoming", color = PotatoAccent, style = MaterialTheme.typography.labelLarge)
                    upcoming.forEach { programme ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onProgramme(programme) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                timeLabel(programme.startUtc),
                                color = PotatoAccent,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp)
                            )
                            Text(
                                programme.title,
                                color = PotatoText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        "No schedule available for this channel.",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    PotatoButton("Play", primary = true) { onPlay() }
                    Spacer(Modifier.width(10.dp))
                    PotatoButton("Favourite", primary = false) { onToggleStar() }
                }
            }
        }
    }
}

@Composable
fun ProgrammeDetailDialog(
    programme: Programme,
    onDismiss: () -> Unit,
    onPlayChannel: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!programme.image.isNullOrBlank()) {
                    AsyncImage(
                        model = programme.image,
                        contentDescription = programme.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(programme.title, color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${dayLabel(programme.startUtc)} – ${timeLabel(programme.endUtc)}  ·  ${programme.durationMs / 60000} min",
                    color = PotatoAccent,
                    style = MaterialTheme.typography.labelLarge
                )
                val meta = listOfNotNull(
                    programme.categories.joinToString(", ").ifBlank { null },
                    programme.language,
                    programme.rating,
                    if (programme.live) "Live" else null,
                    if (programme.season != null && programme.episode != null)
                        "S${programme.season} E${programme.episode}" else null
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(meta, color = PotatoTextDim, style = MaterialTheme.typography.labelMedium)
                }
                programme.description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
                }
                programme.previouslyShown?.let { if (it) {
                    Spacer(Modifier.height(6.dp))
                    Text("Previously shown", color = PotatoTextDim, style = MaterialTheme.typography.labelMedium)
                } }
                Spacer(Modifier.height(18.dp))
                PotatoButton("Watch channel", primary = true) { onPlayChannel(programme.channelId) }
            }
        }
    }
}
