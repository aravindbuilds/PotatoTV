package app.potatotv.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.potatotv.BuildConfig
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.simkl.SimklPin
import app.potatotv.data.update.UpdateInfo
import app.potatotv.ui.components.Mascot
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.theme.PotatoAccent
import app.potatotv.ui.theme.PotatoBgElevated
import app.potatotv.ui.theme.PotatoDanger
import app.potatotv.ui.theme.PotatoLine
import app.potatotv.ui.theme.PotatoRating
import app.potatotv.ui.theme.PotatoSurface
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(@Suppress("UNUSED_PARAMETER") nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var proxyEnabled by remember(settings.proxyEnabled) { mutableStateOf(settings.proxyEnabled) }
    var proxyType by remember(settings.proxyType) { mutableStateOf(settings.proxyType) }
    var proxyHost by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPort by remember(settings.proxyPort) { mutableStateOf(if (settings.proxyPort > 0) settings.proxyPort.toString() else "") }

    var warpEnabled by remember(settings.warpEnabled) { mutableStateOf(settings.warpEnabled) }
    var warpType by remember(settings.warpType) { mutableStateOf(settings.warpType) }
    var warpHost by remember(settings.warpHost) { mutableStateOf(settings.warpHost) }
    var warpPort by remember(settings.warpPort) { mutableStateOf(if (settings.warpPort > 0) settings.warpPort.toString() else "") }
    var catalogProvider by remember(settings.catalogProvider) { mutableStateOf(settings.catalogProvider) }

    var subtitleKey by remember(settings.openSubtitlesKey) { mutableStateOf(settings.openSubtitlesKey) }
    var directoryUrl by remember(settings.siteDirectoryUrl) { mutableStateOf(settings.siteDirectoryUrl) }
    var updateRepo by remember(settings.updateRepo) { mutableStateOf(settings.updateRepo) }

    var simklClient by remember(settings.simklClientId) { mutableStateOf(settings.simklClientId) }
    var simklPin by remember { mutableStateOf<SimklPin?>(null) }
    var simklBusy by remember { mutableStateOf(false) }
    var simklStatus by remember { mutableStateOf<String?>(null) }

    var updateBusy by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateFile by remember { mutableStateOf<File?>(null) }

    fun save(key: String, value: Any) {
        scope.launch { runCatching { ServiceLocator.prefs.setSetting(key, value) } }
    }

    fun connectSimkl() {
        val client = simklClient.trim()
        if (client.isBlank()) {
            simklStatus = "Add your Simkl API client id first."
            return
        }
        if (simklBusy) return
        scope.launch {
            simklBusy = true
            simklStatus = "Requesting a pairing code…"
            runCatching {
                val pin = ServiceLocator.simkl.requestPin(client)
                simklPin = pin
                ServiceLocator.prefs.setSetting("simklClientId", client)
                simklStatus = "Open ${pin.verificationUrl} and enter ${pin.userCode}."
                val deadline = System.currentTimeMillis() + pin.expiresIn * 1000L
                var token: String? = null
                while (System.currentTimeMillis() < deadline) {
                    delay(pin.interval * 1000L)
                    token = ServiceLocator.simkl.pollPin(client, pin.userCode)
                    if (token != null) break
                }
                token
            }.onSuccess { token ->
                if (token != null) {
                    ServiceLocator.prefs.setSetting("simklToken", token)
                    simklStatus = "Connected. Finished episodes will scrobble to Simkl."
                } else {
                    simklStatus = "The code expired before it was confirmed. Try again."
                }
            }.onFailure {
                simklStatus = it.message ?: "Could not reach Simkl."
            }
            simklPin = null
            simklBusy = false
        }
    }

    fun checkUpdates() {
        val repo = updateRepo.trim()
        if (repo.isBlank()) {
            updateStatus = "Add your GitHub repository as owner/name first."
            return
        }
        if (updateBusy) return
        scope.launch {
            updateBusy = true
            updateStatus = "Checking GitHub releases…"
            runCatching { ServiceLocator.updates.latest(repo) }
                .onSuccess { info ->
                    if (info == null) {
                        updateStatus = "That release has no APK asset attached."
                    } else {
                        ServiceLocator.prefs.setSetting("updateRepo", repo)
                        updateInfo = info
                        updateStatus = if (ServiceLocator.updates.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                            "Version ${info.version} is available — you have ${BuildConfig.VERSION_NAME}."
                        } else {
                            "You're on the newest version (${BuildConfig.VERSION_NAME})."
                        }
                    }
                }
                .onFailure {
                    updateStatus = it.message ?: "Update check failed."
                }
            updateBusy = false
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        if (updateBusy) return
        scope.launch {
            updateBusy = true
            updateStatus = "Downloading ${info.apkName}…"
            runCatching { ServiceLocator.updates.download(info) }
                .onSuccess { file ->
                    updateFile = file
                    updateStatus = "Downloaded ${info.apkName}. Press install to continue."
                }
                .onFailure {
                    updateStatus = it.message ?: "Download failed."
                }
            updateBusy = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (app.potatotv.ui.theme.isCompactScreen()) 16.dp else 48.dp,
            end = if (app.potatotv.ui.theme.isCompactScreen()) 16.dp else 48.dp,
            top = if (app.potatotv.ui.theme.isCompactScreen()) 8.dp else 88.dp,
            bottom = 70.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(58)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Settings", color = PotatoText, style = MaterialTheme.typography.displayMedium)
                    Text(
                        "Everything is stored on this device. Nothing is uploaded.",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "Playback & browsing",
                subtitle = "These apply to the built-in browser and the native player."
            ) {
                ToggleRow(
                    title = "Show detected stream prompts",
                    subtitle = "Offer a button when a stream is found. Turn off to never see the prompt again.",
                    checked = settings.showStreamPrompts
                ) { save("showStreamPrompts", it) }
                ToggleRow(
                    title = "Ad & popup blocking",
                    subtitle = "Blocks known ad, tracker and popunder hosts before they load.",
                    checked = settings.adBlock
                ) { save("adblock", it) }
                ToggleRow(
                    title = "Prevent redirects to ads",
                    subtitle = "Stops the page from following redirects to blocked ad domains inside the site.",
                    checked = settings.adBlockRedirectPrevent
                ) { save("adblockRedirectPrevent", it) }
                ToggleRow(
                    title = "Hide ads in-site (Brave-style)",
                    subtitle = "Hides ad, sponsor, banner and tracker elements by CSS selectors on the page itself.",
                    checked = settings.adBlockSiteHiding
                ) { save("adBlockSiteHiding", it) }
                ToggleRow(
                    title = "Dark web pages",
                    subtitle = "Asks WebView for algorithmic darkening so bright sites match PotatoTV.",
                    checked = settings.darkPages
                ) { save("darkPages", it) }
                ToggleRow(
                    title = "Desktop user agent",
                    subtitle = "Sites show their full desktop player instead of a mobile page. Turn off if a site breaks.",
                    checked = settings.desktopUserAgent
                ) { save("desktopUserAgent", it) }
                ToggleRow(
                    title = "Cursor mode by default",
                    subtitle = "D-pad moves a virtual mouse pointer inside the page; OK clicks, BACK leaves.",
                    checked = settings.cursorMode
                ) { save("cursorMode", it) }
            }
        }

        item {
            SettingsSection(
                title = "Catalog source",
                subtitle = "Where the home screen gets its rows from."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Cinemeta", catalogProvider == "cinemeta") {
                        catalogProvider = "cinemeta"
                        save("catalogProvider", "cinemeta")
                    }
                    ChoiceChip("FMHY sources", catalogProvider == "fmhy-sources") {
                        catalogProvider = "fmhy-sources"
                        save("catalogProvider", "fmhy-sources")
                    }
                }
                Text(
                    "Cinemeta gives free, key-less metadata (movies/shows). " +
                        "FMHY sources refreshes your Sites list from the directory JSON in Settings, then uses the same " +
                        "Cinemeta metadata. It never hides where a poster comes from.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Sources",
                subtitle = "Where PotatoTV finds sites and title-page links."
            ) {
                Text(
                    "Title-page templates are loaded from the built-in source list. " +
                        "Only sites that can open this exact title are offered for playback.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Site list: " + app.potatotv.data.sites.FmhyWiki.STREAMING_URL,
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "These are third-party lists. PotatoTV does not host or index their content, and it never adds " +
                        "sources you did not ask for.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Proxy & WARP",
                subtitle = "Route browser, catalogue and player requests through an HTTP or SOCKS5 proxy."
            ) {
                ToggleRow(
                    title = "Use a proxy",
                    subtitle = "Applies to OkHttp traffic. The WebView follows the device proxy settings, not this one.",
                    checked = proxyEnabled
                ) { value ->
                    proxyEnabled = value
                    save("proxyEnabled", value)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("HTTP", proxyType == "http") { proxyType = "http" }
                    ChoiceChip("SOCKS5", proxyType == "socks") { proxyType = "socks" }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Host") },
                        placeholder = { Text("127.0.0.1") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { proxyPort = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        placeholder = { Text("8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    PotatoButton("Save proxy", primary = true) {
                        val port = proxyPort.toIntOrNull() ?: 0
                        if (proxyHost.isNotBlank() && port !in 1..65535) {
                            Toast.makeText(context, "Give the proxy a valid port (1–65535).", Toast.LENGTH_SHORT).show()
                            return@PotatoButton
                        }
                        save("proxyType", proxyType)
                        save("proxyHost", proxyHost.trim())
                        save("proxyPort", port)
                        save("proxyEnabled", proxyEnabled)
                        Toast.makeText(context, "Proxy saved", Toast.LENGTH_SHORT).show()
                    }
                }
                Text(
                    "This proxy covers OkHttp traffic: the catalogue, IPTV lists, subtitles and player headers. " +
                        "The WebView uses the device's own proxy settings.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Cloudflare WARP",
                subtitle = "Route traffic through Cloudflare WARP without a VPN permission or root access."
            ) {
                ToggleRow(
                    title = "Use WARP",
                    subtitle = "Replaces the regular HTTP/SOCKS5 proxy above when enabled.",
                    checked = warpEnabled
                ) { value ->
                    warpEnabled = value
                    save("warpEnabled", value)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("SOCKS5", warpType == "socks5") { warpType = "socks5" }
                    ChoiceChip("HTTP", warpType == "http") { warpType = "http" }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = warpHost,
                        onValueChange = { warpHost = it },
                        label = { Text("Host") },
                        placeholder = { Text("localhost") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = warpPort,
                        onValueChange = { warpPort = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        placeholder = { Text("40000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    PotatoButton("Save WARP", primary = true) {
                        val port = warpPort.toIntOrNull() ?: 0
                        if (warpHost.isNotBlank() && port !in 1..65535) {
                            Toast.makeText(context, "Give WARP a valid port (1–65535).", Toast.LENGTH_SHORT).show()
                            return@PotatoButton
                        }
                        save("warpType", warpType)
                        save("warpHost", warpHost.trim())
                        save("warpPort", port)
                        save("warpEnabled", warpEnabled)
                        Toast.makeText(context, "WARP saved", Toast.LENGTH_SHORT).show()
                    }
                }
                Text(
                    "Cloudflare WARP is not bundled. PotatoTV contains no native WireGuard helper, so it cannot create a " +
                        "tunnel by itself and will not pretend to. If you already run a WARP-compatible endpoint — a local " +
                        "SOCKS5 listener (default port 40000), a travel router, or wgcf on another machine — point WARP " +
                        "at it here. Browser, catalogue and player requests will then route through that endpoint.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Subtitles",
                subtitle = "OpenSubtitles supplies searchable subtitles. A free API key takes a minute."
            ) {
                OutlinedTextField(
                    value = subtitleKey,
                    onValueChange = { subtitleKey = it },
                    label = { Text("OpenSubtitles API key") },
                    placeholder = { Text("Paste your Api-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PotatoButton("Save key", primary = true) {
                        save("openSubtitlesKey", subtitleKey.trim())
                        Toast.makeText(context, "Subtitle key saved", Toast.LENGTH_SHORT).show()
                    }
                    PotatoButton("opensubtitles.com", icon = Icons.Filled.OpenInNew, primary = false) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.opensubtitles.com/en/consumers"))
                            )
                        }
                    }
                }
                Text(
                    "Without a key you can still load any .srt or .vtt link from the player's subtitle panel, " +
                        "and embedded subtitle tracks still work.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Simkl",
                subtitle = "Optional scrobbling. Create an API app at simkl.com to get a client id."
            ) {
                OutlinedTextField(
                    value = simklClient,
                    onValueChange = { simklClient = it },
                    label = { Text("Simkl API client id") },
                    singleLine = true,
                    enabled = settings.simklToken.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.simklToken.isBlank()) {
                        PotatoButton(
                            label = if (simklBusy) "Waiting for confirmation…" else "Connect with a code",
                            icon = Icons.Filled.Cloud,
                            primary = true
                        ) { connectSimkl() }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(PotatoRating)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connected", color = PotatoRating, style = MaterialTheme.typography.labelLarge)
                        }
                        PotatoButton("Disconnect", primary = false) {
                            save("simklToken", "")
                            simklStatus = "Disconnected from Simkl."
                        }
                    }
                    if (simklBusy) {
                        CircularProgressIndicator(color = PotatoAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                simklPin?.let { pin ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Code ${pin.userCode}",
                            color = PotatoAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.width(14.dp))
                        PotatoButton("Open simkl.com/pin", icon = Icons.Filled.OpenInNew, primary = false) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pin.verificationUrl)))
                            }
                        }
                    }
                }
                simklStatus?.let {
                    Text(it, color = if (it.startsWith("Connected")) PotatoRating else PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "The pairing code expires quickly. Keep this screen open while Simkl waits for confirmation.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Site directory",
                subtitle = "A remote JSON list of sites you trust. PotatoTV merges it into your own list."
            ) {
                OutlinedTextField(
                    value = directoryUrl,
                    onValueChange = { directoryUrl = it },
                    label = { Text("Directory JSON URL") },
                    placeholder = { Text("https://example.com/potatotv-sites.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PotatoButton("Save directory URL", primary = true) {
                    save("siteDirectoryUrl", directoryUrl.trim())
                    Toast.makeText(context, "Directory URL saved", Toast.LENGTH_SHORT).show()
                }
                Text(
                    "Expected shape: {\"sites\":[{\"name\":\"Example\",\"url\":\"https://example.com\"," +
                        "\"searchTemplate\":\"https://example.com/search?q={q}\"}]} — refresh from the Sites screen.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Updates",
                subtitle = "Check GitHub Releases for a newer PotatoTV APK."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Installed version",
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(BuildConfig.VERSION_NAME, color = PotatoText, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = updateRepo,
                    onValueChange = { updateRepo = it },
                    label = { Text("GitHub repo (owner/name)") },
                    placeholder = { Text("yourname/PotatoTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PotatoButton(
                        label = if (updateBusy) "Working…" else "Check for updates",
                        icon = Icons.Filled.Refresh,
                        primary = true
                    ) { checkUpdates() }
                    updateInfo?.let { info ->
                        if (ServiceLocator.updates.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                            PotatoButton(
                                label = if (updateFile == null) "Download ${info.version}" else "Install update",
                                icon = Icons.Filled.SystemUpdate,
                                primary = false
                            ) {
                                val file = updateFile
                                if (file == null) downloadUpdate(info)
                                else {
                                    ServiceLocator.updates.install(file)
                                    updateStatus = "Follow the Android installer prompt."
                                }
                            }
                        }
                    }
                    if (updateBusy) {
                        CircularProgressIndicator(color = PotatoAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                updateStatus?.let {
                    Text(it, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
                }
                updateInfo?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes.take(700),
                        color = PotatoTextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "Android will ask you to allow installs from PotatoTV the first time. Updates are downloaded from the " +
                        "release URL you configure — always check the repository is yours.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SettingsSection(
                title = "Legal",
                subtitle = "PotatoTV is a browser, a player and a remote control for your own sources."
            ) {
                Text(
                    "PotatoTV hosts, indexes and distributes no video. It ships with legal, free sources only — " +
                        "YouTube, Tubi, Pluto TV, Plex, the Internet Archive, Crunchyroll, BBC iPlayer and FMHY — and " +
                        "you choose what else to add. Sites, IPTV playlists and the media they serve belong to third " +
                        "parties. Only watch what you have the right to watch, and follow the laws where you live.",
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(30)
                    Spacer(Modifier.width(9.dp))
                    Text("PotatoTV ${BuildConfig.VERSION_NAME} · Android TV first", color = PotatoLine, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PotatoBgElevated)
            .border(1.dp, PotatoLine, RoundedCornerShape(16.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = PotatoText, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PotatoSurface else Color.Transparent)
            .border(1.dp, if (focused) PotatoAccent else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PotatoText, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = PotatoTextDim, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .width(58.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (checked) PotatoAccent.copy(alpha = 0.9f) else PotatoSurface)
                .border(1.dp, if (focused) PotatoAccent else PotatoLine, RoundedCornerShape(15.dp)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Text(
                if (checked) "ON" else "OFF",
                color = if (checked) Color(0xFF00131C) else PotatoTextDim,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
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
                    selected -> PotatoAccent.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(9.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected || focused) PotatoAccent else PotatoTextDim,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
