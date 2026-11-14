package app.potatotv.data.adblock

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

/**
 * Small, fast domain blocker. Wildcards are implicit: adding example.com also
 * blocks ads.example.com. The ruleset ships in assets/blocklist.txt and can be
 * refreshed from the settings screen later on.
 */
class AdBlocker(private val context: Context) {

    @Volatile
    private var domains: Set<String> = emptySet()

    @Volatile
    private var loaded = false

    val blockedCount = AtomicLong(0)

    val blockedPathHints = listOf(
        "/adserver", "/ads/", "/advert", "/popunder", "/prebid", "/vast?", "/vpaid",
        "/banners/", "/tracking/", "/sponsor/", "/popup/", "/click/", "gampad", "doubleclick"
    )

    fun load() {
        if (loaded) return
        domains = runCatching {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toSet()
            }
        }.getOrDefault(emptySet())
        loaded = true
    }

    /** Returns true if the given host matches any rule. The host may include subdomain —
     *  we match against the full domain OR any label suffix. */
    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        load()
        val clean = host.lowercase().trimEnd('.')
        // Direct match against full domain
        if (clean in domains) return true
        // Match against subdomains: e.g. great.googleadservices.com → googleadservices.com
        var depth = clean.split('.').size - 1 // remaining labels to peel back
        while (depth >= 1) {
            val suffix = clean.substringAfterLast('.', "")
            if (suffix in domains) return true
            clean = clean.substringBeforeLast('.')
            depth--
        }
        // Path hints
        val lower = host.lowercase()
        return blockedPathHints.any { lower.contains(it) }
    }

    /** Exposed to injected JS for redirect-blocking comparisons. */
    fun allHosts(): Set<String> = domains

    fun size(): Int {
        load()
        return domains.size
    }
}
