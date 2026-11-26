package app.potatotv.data.tv

import java.util.Locale

/**
 * Confidence-based matching. Exact external ids win; names and aliases are
 * considered only when country, language and feed hints remain compatible.
 * Logo similarity can support a match but never creates one on its own.
 */
class ChannelMatcher(
    private val catalogue: List<CanonicalChannel>,
    private val config: MatchConfig = MatchConfig()
) {
    private val byId: Map<String, CanonicalChannel> = catalogue.associateBy { it.id.lowercase(Locale.ROOT) }
    private val byExternalId: Map<String, CanonicalChannel> = buildMap {
        catalogue.forEach { channel ->
            channel.provenance.filter { it.field == "externalId" }.forEach { provenance ->
                provenance.sourceId?.let { put(it.lowercase(Locale.ROOT), channel) }
            }
            channel.epgIds.forEach { put("epg:$it".lowercase(Locale.ROOT), channel) }
        }
    }
    private val byName: Map<String, List<CanonicalChannel>> = catalogue.groupBy {
        ChannelIdentity.normalize(it.name)
    }
    private val byAlias: Map<String, List<CanonicalChannel>> = buildMap {
        catalogue.forEach { channel ->
            (listOf(channel.name) + channel.aliases).forEach { alias ->
                val key = ChannelIdentity.normalize(alias)
                put(key, (get(key).orEmpty() + channel).distinctBy { it.id })
            }
        }
    }
    /** Token index avoids an O(entries x catalogue) scan for fuzzy candidates. */
    private val byToken: Map<String, List<CanonicalChannel>> = buildMap {
        catalogue.forEach { channel ->
            ChannelIdentity.tokens(channel.name).forEach { token ->
                val existing = get(token).orEmpty()
                if (existing.size < 250 && channel !in existing) put(token, existing + channel)
            }
        }
    }

    fun candidatePool(entry: PlaylistEntry): List<CanonicalChannel> {
        val matched = mutableListOf<CanonicalChannel>()
        entry.tvgId?.let { id ->
            byId[id.lowercase(Locale.ROOT)]?.let { matched += it }
            byExternalId[id.lowercase(Locale.ROOT)]?.let { matched += it }
            byExternalId["epg:$id".lowercase(Locale.ROOT)]?.let { matched += it }
        }
        val name = ChannelIdentity.normalize(entry.originalName)
        matched += byName[name].orEmpty()
        matched += byAlias[name].orEmpty()
        ChannelIdentity.tokens(entry.originalName).forEach { token ->
            byToken[token].orEmpty().forEach { channel ->
                if (channel !in matched) matched += channel
            }
        }
        return matched.distinctBy { it.id }
    }

    fun match(
        entry: PlaylistEntry,
        overrides: ChannelOverrides = ChannelOverrides(),
        configOverride: MatchConfig = config
    ): List<MatchCandidate> {
        val manual = listOf(entry.id, entry.originalName, entry.tvgId.orEmpty())
            .firstNotNullOfOrNull { key -> overrides.mappings[key]?.let { byId[it.lowercase(Locale.ROOT)] } }
        if (manual != null) {
            return listOf(MatchCandidate(manual, 1f, MatchConfidence.HIGH, listOf("Manual override")))
        }

        val results = candidatePool(entry).mapNotNull { channel ->
            val separatorKey = "${channel.id}|${entry.tvgId.orEmpty()}"
            if (separatorKey in overrides.separated) return@mapNotNull null
            score(entry, channel, configOverride)
        }
        return results.sortedWith(
            compareByDescending<MatchCandidate> { it.score }
                .thenBy { it.channel.name.lowercase(Locale.ROOT) }
        )
    }

    /** Best automatic association, or null when the evidence is insufficient. */
    fun bestAutomatic(
        entry: PlaylistEntry,
        overrides: ChannelOverrides = ChannelOverrides(),
        configOverride: MatchConfig = config
    ): MatchCandidate? {
        val ranked = match(entry, overrides, configOverride)
        val top = ranked.firstOrNull() ?: return null
        // A manual override or an exact external id is decisive even when a
        // name-only candidate also exists (e.g. "Asianet" vs "Asianet HD").
        if (top.score >= 0.99f && ranked.count { kotlin.math.abs(it.score - top.score) < 0.001f } == 1) {
            return top
        }
        val high = ranked.filter { it.confidence == MatchConfidence.HIGH }
        // Two unrelated high-confidence channels are ambiguous - leave them for review.
        if (high.size > 1 && high.distinctBy { it.channel.id }.size > 1) return null
        return high.firstOrNull()
    }

    private fun score(entry: PlaylistEntry, channel: CanonicalChannel, cfg: MatchConfig): MatchCandidate? {
        val reasons = mutableListOf<String>()
        var score = 0f
        val id = entry.tvgId?.lowercase(Locale.ROOT)
        when {
            id != null && id == channel.id.lowercase(Locale.ROOT) -> {
                score = cfg.exactIdWeight; reasons += "Exact EPG/channel id"
            }
            id != null && channel.epgIds.any { it.lowercase(Locale.ROOT) == id } -> {
                score = cfg.exactIdWeight; reasons += "Exact EPG id on channel"
            }
            id != null && id == channel.provenance.firstOrNull { it.field == "externalId" }?.sourceId?.lowercase(
                Locale.ROOT
            ) -> {
                score = cfg.exactIdWeight; reasons += "Exact external id"
            }
            else -> {
                val normalized = ChannelIdentity.normalize(entry.originalName)
                val exactName = ChannelIdentity.normalize(channel.name) == normalized
                val exactAlias = (listOf(channel.name) + channel.aliases)
                    .any { ChannelIdentity.normalize(it) == normalized }
                val similarity = ChannelIdentity.similarity(entry.originalName, channel.name)
                when {
                    exactName -> { score = cfg.nameWeight; reasons += "Exact normalised name" }
                    exactAlias -> { score = cfg.aliasWeight; reasons += "Known alternative name" }
                    similarity >= 0.55f -> {
                        score = 0.55f + 0.28f * similarity
                        reasons += "Similar name (${(similarity * 100).toInt()}%)"
                    }
                    else -> return null
                }
                if (channel.network != null && channel.network!!.contains(
                        ChannelIdentity.tokens(entry.originalName).firstOrNull().orEmpty(), true
                    )
                ) {
                    score += 0.05f; reasons += "Broadcaster agrees"
                }
            }
        }

        val entryHints = ChannelIdentity.feedHints(entry.originalName) +
            ChannelIdentity.feedHints(entry.groupTitle.orEmpty()) +
            listOfNotNull(entry.language?.lowercase(Locale.ROOT))
        val channelHints = ChannelIdentity.feedHints(channel.name) +
            channel.languages.map { it.lowercase(Locale.ROOT) }
        val conflictingHints = entryHints - channelHints
        if (entryHints.isNotEmpty() && channelHints.isNotEmpty() && conflictingHints.isNotEmpty()) {
            score -= 0.18f * minOf(conflictingHints.size, 2)
            reasons += "Language/feed mismatch: ${conflictingHints.joinToString()}"
        }
        if (!entry.country.isNullOrBlank() && !channel.country.isNullOrBlank() &&
            !entry.country.equals(channel.country, true)
        ) {
            score -= 0.12f
            reasons += "Country mismatch"
        }

        val entryQuality = ChannelIdentity.qualityClaims(entry.originalName) +
            listOfNotNull(entry.qualityClaim?.lowercase(Locale.ROOT))
        val channelQuality = channel.qualityClaims.toSet() +
            channel.feeds.flatMap { ChannelIdentity.qualityClaims(it.format.orEmpty()) }
        if (entryQuality.isNotEmpty() && channelQuality.isNotEmpty() && entryQuality.none { it in channelQuality }) {
            score -= 0.1f
            reasons += "Quality claim mismatch"
        }

        // Logo is supporting evidence only, capped below automatic threshold.
        if (score < cfg.exactIdWeight && logoSimilar(entry.logoUrl, channel.logo)) {
            score += 0.04f
            reasons += "Logo agrees"
            score = minOf(score, cfg.highThreshold - 0.01f)
        }
        if (score <= 0f) return null
        return MatchCandidate(
            channel = channel,
            score = score.coerceIn(0f, 1f),
            confidence = when {
                score >= cfg.highThreshold -> MatchConfidence.HIGH
                score >= cfg.mediumThreshold -> MatchConfidence.MEDIUM
                else -> MatchConfidence.LOW
            },
            reasons = reasons
        )
    }

    private fun logoSimilar(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val first = a.substringAfterLast('/').substringBefore('?').lowercase(Locale.ROOT)
        val second = b.substringAfterLast('/').substringBefore('?').lowercase(Locale.ROOT)
        return first == second || (first.length > 5 && second.length > 5 && (first in second || second in first))
    }
}
