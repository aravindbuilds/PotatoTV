package app.potatotv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.model.Site
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.Mascot
import app.potatotv.ui.components.PosterCard
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import kotlinx.coroutines.delay
import java.net.URLEncoder

@Composable
fun SearchScreen(nav: NavController) {
    val context = LocalContext.current
    val settings by ServiceLocator.prefs.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val sites by ServiceLocator.prefs.sites
        .collectAsStateWithLifecycle(initialValue = emptyList<Site>())

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) query = text
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search PotatoTV")
        }
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No speech recogniser on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length < 2) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(420)
        loading = true
        error = null
        runCatching { ServiceLocator.cinemeta.search(clean) }
            .onSuccess {
                results = it
                searched = true
            }
            .onFailure {
                error = it.message ?: "Search failed."
                searched = true
            }
        loading = false
    }

    // In FMHY-sources mode, also surface matching saved sites so the user can jump
    // straight into the built-in browser with their search template.
    val siteMatches = remember(query, sites, settings.catalogProvider) {
        val clean = query.trim()
        if (settings.catalogProvider != "fmhy-sources" || clean.length < 2) {
            emptyList()
        } else {
            sites.filter { it.name.contains(clean, ignoreCase = true) }.take(8)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = if (app.potatotv.ui.theme.isCompactScreen()) 8.dp else 94.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mascot(58)
            Spacer(Modifier.width(18.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movies, series, anime…", color = PotatoTextDim) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = PotatoTextDim)
                },
                trailingIcon = {
                    IconButton(onClick = { startVoice() }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = PotatoAccent)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PotatoAccent,
                    unfocusedBorderColor = PotatoLine,
                    focusedTextColor = PotatoText,
                    unfocusedTextColor = PotatoText
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (siteMatches.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(siteMatches, key = { it.id }) { site ->
                    SourceChip(site) {
                        val template = site.searchTemplate
                        // Title-page templates need a TMDB id, which a text search
                        // does not have. Fall back to the site root instead of
                        // opening a URL with a literal "{tmdb}" in it.
                        val target = if (template.isNotBlank() && !template.contains("{tmdb}")) {
                            template
                                .replace("{q}", URLEncoder.encode(query.trim(), "UTF-8"))
                                .replace("{imdb}", "")
                        } else site.url
                        nav.navigate(
                            "browser?url=${URLEncoder.encode(target, "UTF-8")}" +
                                "&name=${URLEncoder.encode(site.name, "UTF-8")}" +
                                "&siteId=${URLEncoder.encode(site.id, "UTF-8")}"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(
                    color = PotatoAccent,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                )
                error != null -> EmptyState(
                    title = "Search failed",
                    message = error ?: "Try again."
                )
                searched && results.isEmpty() -> EmptyState(
                    title = "Nothing found",
                    message = "No title matches \"${query.trim()}\". Try another spelling or a shorter name."
                )
                query.trim().length < 2 -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(70.dp))
                    Mascot(110)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Search the catalogue",
                        color = PotatoText,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Type a title, or press the mic and say it. The catalogue comes from Cinemeta.",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(0.54f)
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(158.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { item ->
                        PosterCard(item, onClick = {
                            nav.navigate("title/${item.type}/${item.id}")
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(site: Site, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PotatoBgElevated)
            .border(1.dp, PotatoLine, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            site.name,
            color = PotatoText,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(7.dp))
        Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = PotatoAccent, modifier = Modifier.width(15.dp))
    }
}
