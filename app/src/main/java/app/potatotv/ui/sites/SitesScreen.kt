package app.potatotv.ui.sites

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.Site
import app.potatotv.data.net.Http
import app.potatotv.data.sites.SiteDirectory
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun SitesScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sites by ServiceLocator.prefs.sites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by ServiceLocator.prefs.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val settings by ServiceLocator.prefs.settings.collectAsStateWithLifecycle(initialValue = app.potatotv.data.model.AppSettings())
    var showAdd by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Sort sites: favorites first, then by usage count descending, then alphabetically
    val sorted = sites.sortedWith(compareByDescending { it.id in favorites }.thenByDescending { it.usageCount }.thenBy { it.name })

    // FMHY-sources mode: pull deeplinks from the configured directory and merge
    // them into the local site list once per URL change.
    LaunchedEffect(settings.catalogProvider, settings.siteDirectoryUrl) {
        if (settings.catalogProvider != "fmhy-sources") return@LaunchedEffect
        val url = settings.siteDirectoryUrl.trim()
        if (url.isBlank()) return@LaunchedEffect
        runCatching { SiteDirectory.parse(Http.getText(url)) }
            .onSuccess { parsed ->
                val existing = sites.map { it.id }.toSet()
                parsed.sites
                    .filterNot { it.id in existing }
                    .forEach { ServiceLocator.prefs.addSite(it) }
                parsed.titleSites
                    .filterNot { it.id in existing }
                    .forEach { ServiceLocator.prefs.addSite(it) }
                parsed.deeplinks
                    .mapNotNull { SiteDirectory.deeplinkToSite(it) }
                    .filterNot { it.id in existing }
                    .forEach { ServiceLocator.prefs.addSite(it) }
            }
            .onFailure { message = "Deeplink refresh failed: ${it.message}" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 88.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sites", color = PotatoText, style = MaterialTheme.typography.displayMedium)
                Text(
                    "Any site becomes a source. Add your own, hold OK to favourite, and PotatoTV will find the player inside.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PotatoButton(
                label = if (refreshing) "Refreshing…" else "Refresh directory",
                icon = Icons.Filled.Refresh,
                primary = false
            ) {
                if (settings.siteDirectoryUrl.isBlank()) {
                    Toast.makeText(context, "Add a directory JSON URL in Settings first.", Toast.LENGTH_LONG).show()
                    return@PotatoButton
                }
                scope.launch {
                    refreshing = true
                    runCatching {
                        val parsed = SiteDirectory.parse(Http.getText(settings.siteDirectoryUrl.trim()))
                        parsed.sites + parsed.titleSites
                    }.onSuccess { incoming ->
                        if (incoming.isEmpty()) {
                            message = "The directory returned no sites."
                        } else {
                            val merged = (sites + incoming)
                                .filter { it.url.startsWith("http") }
                                .distinctBy { it.url.trimEnd('/').lowercase() }
                            ServiceLocator.prefs.saveSites(merged)
                            message = "Added ${incoming.size} site(s) from the directory."
                        }
                    }.onFailure {
                        message = "Directory fetch failed: ${it.message}"
                    }
                    refreshing = false
                }
            }
            Spacer(Modifier.width(12.dp))
            PotatoButton("Add site", icon = Icons.Filled.Add, primary = true) { showAdd = true }
        }

        message?.let {
            Text(
                it,
                color = PotatoAccent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (sorted.isEmpty()) {
            EmptyState(
                title = "No sites yet",
                message = "Add the address of any streaming site. It will open in the built-in browser with ad blocking, cursor mode and the native player.",
                actionLabel = "Add your first site"
            ) { showAdd = true }
        } else {
            // Group by category and render small favicon cards
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Group sites by category
                val byCategory = sorted.groupBy { it.category }

                // Render cards grouped by category
                for ((category, categorySites) in byCategory) {
                    // Category header
                    if (categorySites.isNotEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(PotatoBgElevated)
                                    .border(1.dp, PotatoLine, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    category.toUpperCase(),
                                    color = PotatoTextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                        // Cards in this category
                        items(categorySites, key = { it.id }) { site ->
                            SiteCardSmall(
                                site = site,
                                favorite = site.id in favorites,
                                onClick = {
                                    // Increment usage when clicked
                                    scope.launch { ServiceLocator.prefs.incrementSiteUsage(site.id) }
                                    nav.navigate(
                                        "browser?url=${URLEncoder.encode(site.url, "UTF-8")}" +
                                            "&name=${URLEncoder.encode(site.name, "UTF-8")}" +
                                            "&siteId=${URLEncoder.encode(site.id, "UTF-8")}"
                                    )
                                },
                                onLongClick = {
                                    scope.launch { ServiceLocator.prefs.toggleFavorite(site.id) }
                                },
                                onFavorite = {
                                    scope.launch { ServiceLocator.prefs.toggleFavorite(site.id) }
                                },
                                onDelete = {
                                    scope.launch { ServiceLocator.prefs.removeSite(site.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSiteDialog(
            onDismiss = { showAdd = false },
            onSave = { site ->
                scope.launch {
                    ServiceLocator.prefs.addSite(site)
                    showAdd = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCardSmall(
    site: Site,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val initials = site.name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase().toString() }
        .takeIf { it.isNotEmpty() } ?: site.id.substringBefore('-').uppercase()

    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PotatoSurface else PotatoBgElevated)
            .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        // Favicon placeholder (initials) with colored background
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PotatoSurface.copy(alpha = 0.85f))
        ) {
            Text(
                initials,
                color = PotatoText,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                site.name,
                color = PotatoText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "×${site.usageCount}",
                color = PotatoTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            if (site.searchTemplate.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🔍", color = PotatoTextDim, style = MaterialTheme.typography.labelSmall)
                    Text("Search", color = PotatoTextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favourite",
                    tint = if (favorite) PotatoRating else PotatoTextDim
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = PotatoTextDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddSiteDialog(onDismiss: () -> Unit, onSave: (Site) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PotatoBgElevated,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PotatoLine)
        ) {
            Column(Modifier.padding(24.dp).width(480.dp)) {
                Text("Add a site", color = PotatoText, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Site URL (https://…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Search URL template (optional)") },
                    placeholder = { Text("https://site/search?q={q}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use {q} for the title text and {imdb} for the IMDb id. Without it, PotatoTV opens the site home.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    categoryChip("General", category == "general") { category = "general" }
                    categoryChip("Movie", category == "movie") { category = "movie" }
                    categoryChip("Series", category == "series") { category = "series" }
                    categoryChip("Sports", category == "sports") { category = "sports" }
                    categoryChip("Documentary", category == "documentary") { category = "documentary" }
                    categoryChip("Live TV", category == "livetv") { category = "livetv" }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = PotatoDanger, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PotatoButton("Save site", primary = true) {
                        val cleanName = name.trim()
                        val cleanUrl = url.trim()
                        if (cleanName.isBlank()) {
                            error = "Give the site a name."
                            return@PotatoButton
                        }
                        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                            error = "The URL must start with http:// or https://."
                            return@PotatoButton
                        }
                        onSave(
                            Site(
                                id = "user-" + java.util.UUID.randomUUID(),
                                name = cleanName,
                                url = cleanUrl,
                                searchTemplate = template.trim(),
                                category = category,
                                favicon = null,
                                usageCount = 0
                            )
                        )
                    }
                    PotatoButton("Cancel", primary = false) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun categoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected || focused) PotatoAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (selected || focused) PotatoAccent.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected || focused) PotatoAccent else PotatoTextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}