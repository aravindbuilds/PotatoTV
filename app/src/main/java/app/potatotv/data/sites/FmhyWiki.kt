package app.potatotv.data.sites

import app.potatotv.data.model.Site
import app.potatotv.data.net.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import app.potatotv.data.sources.BakedSources

/**
 * Turns the public FMHY wiki markdown into browseable [Site] entries.
 *
 * FMHYPlay ships these pages as bundled assets (fmhy-video.md /
 * fmhy-nonenglish.md). PotatoTV does NOT bundle a third-party site list:
 * it fetches the same public wiki pages on demand, only after the user
 * switches to "FMHY sources" or presses Refresh. Nothing is hosted here.
 */
object FmhyWiki {

    const val STREAMING_URL =
        "https://raw.githubusercontent.com/wiki/fmhy/FMHY/Streaming.md"

    /** Wiki page titles differ by capitalisation/mirror; try each in turn. */
    private val EXTRA_URLS = listOf(
        "https://raw.githubusercontent.com/wiki/fmhy/FMHY/Non-English.md",
        "https://raw.githubusercontent.com/wiki/fmhy/FMHY/Non_English.md",
        "https://raw.githubusercontent.com/wiki/fmhy/FMHY/Non-English-1.md"
    )

    private val LINK = Regex("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)")
    private val JUNK_LABELS = setOf(
        "2", "3", "4", "5", "6", "7", "mirror", "mirrors", "status", "note", "notes",
        "download", "downloader", "guide", "wiki", "index", "subreddit", "discord",
        "telegram", "status page", "backups", "archive", "more", "alternate"
    )
    private val JUNK_HOSTS = listOf(
        "reddit.com", "discord.gg", "discord.com", "t.me", "telegram.me", "github.com",
        "x.com", "twitter.com", "rentry.co", "linktr.ee", "beacons.ai", "patreon.com",
        "buymeacoffee.com", "facebook.com", "instagram.com", "youtube.com", "cse.google.com"
    )

    /** Pull deeplinks + the full wiki, merge templates by host, return all sites. */
    suspend fun refresh(): List<Site> = kotlinx.coroutines.coroutineScope {
        // All directories and grading are fetched concurrently; a slow mirror
        // can no longer stall the whole import.
        val fmhyDef = async {
            runCatching {
                SiteDirectory.parse(Http.getText(BakedSources.DEEPLINKS_URL)).titleSites
            }.getOrDefault(emptyList())
        }
        val anantDef = async {
            runCatching {
                SiteDirectory.parse(Http.getText(BakedSources.ANANT_DEEPLINKS_URL)).titleSites
            }.getOrDefault(emptyList())
        }
        val gradesDef = async { FmhyGrading.load() }
        val wikiDef = async {
            buildList {
                runCatching { Http.getText(STREAMING_URL) }
                    .onSuccess { addAll(parse(it)) }
                for (url in EXTRA_URLS) {
                    runCatching { Http.getText(url) }
                        .onSuccess { addAll(parse(it)) }
                }
            }
        }
        val fmhyTemplates = fmhyDef.await()
        val anantTemplates = anantDef.await()
        val anantHosts = anantTemplates.mapNotNull { it.hostOf() }.toSet()
        val templateSites = anantTemplates +
            fmhyTemplates.filterNot { it.hostOf() in anantHosts }
        val grades = gradesDef.await()
        val wikiSites = wikiDef.await()

        val templatesByHost = templateSites.associateBy { it.hostOf() }
        val merged = wikiSites.map { site ->
            val t = templatesByHost[site.hostOf()]
            val hasTemplate = t != null && listOf(
                t.movieTemplate, t.tvTemplate, t.episodeTemplate, t.animeTemplate
            ).any { it.isNotBlank() }
            if (t != null && hasTemplate) {
                site.copy(
                    movieTemplate = t.movieTemplate.ifBlank { site.movieTemplate },
                    tvTemplate = t.tvTemplate.ifBlank { site.tvTemplate },
                    episodeTemplate = t.episodeTemplate.ifBlank { site.episodeTemplate },
                    animeTemplate = t.animeTemplate.ifBlank { site.animeTemplate },
                    searchTemplate = t.movieTemplate.ifBlank { t.tvTemplate }
                        .ifBlank { site.searchTemplate },
                    rating = FmhyGrading.scoreFor(site.name, grades)
                )
            } else site.copy(rating = FmhyGrading.scoreFor(site.name, grades))
        }
        val wikiHosts = merged.map { it.hostOf() }.toSet()
        merged + templateSites
            .filterNot { it.hostOf() in wikiHosts }
            .map { it.copy(rating = FmhyGrading.scoreFor(it.name, grades)) }
    }

    fun parse(markdown: String): List<Site> {
        var category = "Streaming"
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Site>()
        markdown.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            when {
                line.startsWith("## ") && !line.startsWith("***") -> {
                    category = cleanHeading(line.removePrefix("## "))
                }
                line.startsWith("# ") -> {
                    val heading = cleanHeading(line.removePrefix("# "))
                    if (heading.contains("live", true)) category = heading
                }
                (line.startsWith("* ") || line.startsWith("- ")) && !line.startsWith("***") -> {
                    val starred = line.contains("🌟") || line.contains("⭐")
                    for ((label, url) in LINK.findAll(line).map { it.destructured }) {
                        val host = hostOf(url) ?: continue
                        if (JUNK_HOSTS.any { host.endsWith(it) }) continue
                        val name = label.trim()
                        if (name.length < 2 || name.lowercase() in JUNK_LABELS) continue
                        if (!seen.add(host)) continue
                        out += Site(
                            id = "fmhy-" + host.hashCode(),
                            name = name,
                            url = url.substringBefore('?').trimEnd('/'),
                            category = categoryFor(category, line),
                            searchTemplate = "",
                            starred = starred,
                            order = out.size
                        )
                    }
                }
            }
        }
        return out
    }

    private fun cleanHeading(raw: String): String = raw
        .trim()
        .trimStart('►', '▷', ' ', '️', '\uFE0F')
        .trim()
        .ifBlank { "Streaming" }

    private fun categoryFor(heading: String, line: String): String {
        val text = "$heading ${line.substringBefore(" - ")}".lowercase()
        return when {
            text.contains("live sport") -> "Live Sports"
            text.contains("live tv") || text.contains("iptv") -> "Live TV"
            text.contains("anime") -> "Anime"
            text.contains("drama") -> "Drama"
            text.contains("tv streaming") || text.contains("tv /") -> "TV Series"
            text.contains("cartoon") -> "Cartoons"
            text.contains("sport") -> "Live Sports"
            else -> heading.ifBlank { "Streaming" }
        }
    }

    private fun hostOf(url: String): String? = runCatching {
        java.net.URI(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private fun Site.hostOf(): String? = hostOf(url)
}
