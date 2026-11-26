package app.potatotv.data.tv

import kotlinx.serialization.Serializable

/**
 * One original record from an imported playlist. Stream URLs are never used as
 * channel identity: an entry only names where a playable feed was found.
 */
@Serializable
data class PlaylistEntry(
    val id: String,
    val playlistId: String,
    val playlistName: String,
    val originalName: String,
    val normalizedName: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val language: String? = null,
    val country: String? = null,
    val channelNumber: String? = null,
    val qualityClaim: String? = null,
    val epgUrl: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val importedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ChannelFeed(
    val id: String,
    val name: String,
    val isMain: Boolean = false,
    val languages: List<String> = emptyList(),
    val timezones: List<String> = emptyList(),
    val broadcastAreas: List<String> = emptyList(),
    val format: String? = null,
    val logo: String? = null
)

@Serializable
data class StreamHealth(
    val available: Boolean,
    val checkedAt: Long,
    val errorCategory: String? = null,
    val latencyMs: Long? = null
)

/** A playable URL belonging to a provider and (optionally) a specific feed. */
@Serializable
data class StreamSource(
    val url: String,
    val playlistId: String,
    val playlistName: String,
    val entryId: String,
    val feedId: String? = null,
    val title: String? = null,
    val qualityClaim: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
    val importedAt: Long = 0L,
    val health: StreamHealth? = null
)

/** Field-level provenance. Weaker data must never overwrite verified data. */
@Serializable
data class FieldProvenance(
    val field: String,
    val source: String,
    val sourceId: String? = null,
    val retrievedAt: Long = 0L,
    val verified: Boolean = false,
    val confidence: Float = 1f
)

/** A canonical channel. It exists independently of any stream URL. */
@Serializable
data class CanonicalChannel(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val country: String? = null,
    val countryName: String? = null,
    val languages: List<String> = emptyList(),
    val region: String? = null,
    val timezone: String? = null,
    val network: String? = null,
    val owners: List<String> = emptyList(),
    val logo: String? = null,
    val alternativeLogos: List<String> = emptyList(),
    val description: String? = null,
    val website: String? = null,
    /** Claims found in names or playlists. Not independently verified quality. */
    val qualityClaims: List<String> = emptyList(),
    val feeds: List<ChannelFeed> = emptyList(),
    val streams: List<StreamSource> = emptyList(),
    /** EPG ids that can carry this channel's schedule, best first. */
    val epgIds: List<String> = emptyList(),
    val provenance: List<FieldProvenance> = emptyList(),
    val originalNames: List<String> = emptyList(),
    val playlistIds: List<String> = emptyList(),
    val matchSources: List<String> = emptyList(),
    val status: String = "unmatched",
    val matchConfidence: Float = 0f
) {
    val allSources: List<String> get() = streams.map { it.url }.distinct()
    val isMatched get() = status != "unmatched"
}

@Serializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val country: String? = null,
    val language: String? = null,
    val refreshHours: Int = 6,
    val enabled: Boolean = true,
    val note: String = ""
)

@Serializable
data class EpgChannel(
    val id: String,
    val names: List<String> = emptyList(),
    val icon: String? = null
)

@Serializable
data class Programme(
    val id: String,
    val channelId: String,
    val sourceId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    /** UTC epoch millis. Source offsets are preserved in sourceOffset. */
    val startUtc: Long,
    val endUtc: Long,
    val sourceOffset: String? = null,
    val language: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val rating: String? = null,
    val image: String? = null,
    val previouslyShown: Boolean = false,
    val live: Boolean = false,
    val credits: Map<String, List<String>> = emptyMap()
) {
    val durationMs: Long get() = endUtc - startUtc
}

@Serializable
data class EpgQualityReport(
    val channels: Int = 0,
    val programmes: Int = 0,
    val invalidTimestamps: Int = 0,
    val negativeDurations: Int = 0,
    val duplicates: Int = 0,
    val overlaps: Int = 0,
    val gaps: Int = 0,
    val largestGapMinutes: Long = 0,
    val fetchedAt: Long = 0L
)

@Serializable
data class EpgSnapshot(
    val sourceId: String,
    val fetchedAt: Long,
    val channels: List<EpgChannel> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val quality: EpgQualityReport = EpgQualityReport()
)

@Serializable
data class SourceStatus(
    val id: String,
    val lastSuccess: Long = 0L,
    val lastAttempt: Long = 0L,
    val failureCount: Int = 0,
    val lastError: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val stale: Boolean = false
)

@Serializable
data class NowNext(
    val channelId: String,
    val epgId: String? = null,
    val current: Programme? = null,
    val next: Programme? = null,
    val progress: Float = 0f,
    val secondsLeft: Long = 0L
)

@Serializable
data class ChannelOverrides(
    /** Original entry name/key -> canonical channel id chosen by the user. */
    val mappings: Map<String, String> = emptyMap(),
    /** Pair "localId|providerId" -> true keeps channels separate. */
    val separated: Set<String> = emptySet(),
    /** Canonical id -> provider channel id. */
    val preferredProviderIds: Map<String, String> = emptyMap()
)

enum class MatchConfidence { HIGH, MEDIUM, LOW }

data class MatchCandidate(
    val channel: CanonicalChannel,
    val score: Float,
    val confidence: MatchConfidence,
    val reasons: List<String>
)

@Serializable
data class ReviewCandidate(
    val channelId: String,
    val name: String,
    val score: Float,
    val confidence: String,
    val reasons: List<String>
)

@Serializable
data class MatchReview(
    val key: String,
    val entryName: String,
    val tvgId: String? = null,
    val candidates: List<ReviewCandidate> = emptyList(),
    val resolved: Boolean = false
)

data class MatchConfig(
    val highThreshold: Float = 0.86f,
    val mediumThreshold: Float = 0.66f,
    val exactIdWeight: Float = 1f,
    val nameWeight: Float = 0.95f,
    val aliasWeight: Float = 0.9f
)

data class ParsedPlaylist(
    val entries: List<PlaylistEntry>,
    val epgUrl: String?,
    val warnings: List<String>
)

data class ProviderCatalogue(
    val providerId: String,
    val channels: List<CanonicalChannel> = emptyList(),
    val feeds: Map<String, List<ChannelFeed>> = emptyMap(),
    val logos: Map<String, List<String>> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
    val languages: Map<String, String> = emptyMap(),
    val countries: Map<String, String> = emptyMap(),
    val timezones: Map<String, String> = emptyMap(),
    val fetchedAt: Long = 0L
)

data class IntelligenceStats(
    val playlists: Int = 0,
    val entries: Int = 0,
    val channels: Int = 0,
    val streams: Int = 0,
    val matched: Int = 0,
    val unmatched: Int = 0,
    val withLogo: Int = 0,
    val withDescription: Int = 0,
    val withEpg: Int = 0,
    val withoutEpg: Int = 0,
    val reviewCandidates: Int = 0,
    val epgSources: Int = 0,
    val staleEpgSources: Int = 0
)
