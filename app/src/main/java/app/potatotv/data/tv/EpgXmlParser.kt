package app.potatotv.data.tv

import java.io.Reader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Streaming XMLTV reader. It uses a small pull tokenizer rather than a DOM so
 * large guides never have to be held in memory at once. DTD/ENTITY declarations
 * are rejected outright: no external entity resolution is ever performed.
 */
object EpgXmlParser {

    private val attrRegex = Regex("""([^\s=/>]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val compactTime = Regex("""^(\d{14})\s*([+-]\d{4})$""")
    private val onscreenEpisode = Regex("""(?:s|season)\s*0*(\d+)\s*(?:e|episode)\s*0*(\d+)""", RegexOption.IGNORE_CASE)
    private val xmltvEpisode = Regex("""^(\d+)\.(\d+)\.""")
    private const val MAX_TEXT = 64 * 1024

    data class Result(
        val channels: List<EpgChannel>,
        val programmes: List<Programme>,
        val quality: EpgQualityReport
    )

    fun parseTime(raw: String): Long? = runCatching {
        val value = raw.trim()
        compactTime.matchEntire(value)?.let { match ->
            val date = LocalDateTime.parse(match.groupValues[1], DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            return date.toInstant(ZoneOffset.of(match.groupValues[2])).toEpochMilli()
        }
        runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            .getOrNull()?.toInstant()?.toEpochMilli()
            ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }.getOrNull()

    fun parse(reader: Reader, sourceId: String, maxProgrammes: Int = 500_000): Result {
        val source = reader.buffered()
        val channels = mutableListOf<EpgChannel>()
        val programmes = mutableListOf<Programme>()
        val seenProgrammes = HashSet<String>()
        var invalidTimestamps = 0
        var negativeDurations = 0
        var duplicates = 0
        var truncated = false

        val stack = ArrayDeque<String>()
        var inChannel = false
        var inProgramme = false
        var inCredits = false
        var currentChild: String? = null
        var pendingEpisodeSystem: String? = null
        var text = StringBuilder()

        var channelId: String? = null
        var channelNames = mutableListOf<String>()
        var channelIcon: String? = null

        var startUtc = 0L
        var endUtc = 0L
        var offset: String? = null
        var programmeChannel: String? = null
        var title = ""
        var subtitle: String? = null
        var description: String? = null
        var language: String? = null
        var categories = mutableListOf<String>()
        var credits = mutableMapOf<String, MutableList<String>>()
        var season: Int? = null
        var episode: Int? = null
        var episodeTitle: String? = null
        var rating: String? = null
        var image: String? = null
        var previouslyShown = false
        var live = false
        var validProgramme = false

        fun decode(value: String): String {
            if ('&' !in value) return value
            return buildString(value.length) {
                var i = 0
                while (i < value.length) {
                    if (value[i] != '&') { append(value[i++]); continue }
                    val end = value.indexOf(';', i + 1)
                    if (end < 0 || end - i > 12) { append(value[i++]); continue }
                    val entity = value.substring(i + 1, end)
                    when (entity) {
                        "amp" -> append('&')
                        "lt" -> append('<')
                        "gt" -> append('>')
                        "quot" -> append('"')
                        "apos" -> append('\'')
                        else -> if (entity.startsWith("#x") || entity.startsWith("#X")) {
                            runCatching { append(entity.substring(2).toInt(16).toChar()) }
                                .getOrElse { append(value.substring(i, end + 1)) }
                        } else if (entity.startsWith("#")) {
                            runCatching { append(entity.substring(1).toInt().toChar()) }
                                .getOrElse { append(value.substring(i, end + 1)) }
                        } else append(value.substring(i, end + 1))
                    }
                    i = end + 1
                }
            }
        }

        fun attrsOf(content: String): Map<String, String> {
            val body = content.substring(content.indexOfFirst { it.isWhitespace() || it == '/' }.coerceAtLeast(0))
            return attrRegex.findAll(body).associate {
                it.groupValues[1].lowercase(Locale.ROOT) to decode(it.groupValues[2].ifBlank { it.groupValues[3] })
            }
        }

        fun applyEpisode(value: String) {
            if (pendingEpisodeSystem.equals("xmltv_ns", true)) {
                xmltvEpisode.find(value)?.let {
                    season = it.groupValues[1].toIntOrNull()?.plus(1)
                    episode = it.groupValues[2].toIntOrNull()?.plus(1)
                }
            } else {
                onscreenEpisode.find(value)?.let {
                    season = it.groupValues[1].toIntOrNull()
                    episode = it.groupValues[2].toIntOrNull()
                }
            }
        }

        fun flushText() {
            if (text.isEmpty()) return
            val raw = text.toString()
            text.setLength(0)
            val value = decode(raw).trim()
            if (value.isEmpty()) return
            when {
                inChannel && currentChild == "display-name" && channelNames.size < 8 -> channelNames += value
                inProgramme && currentChild == "title" && title.isEmpty() -> title = value
                inProgramme && currentChild == "sub-title" && subtitle == null -> subtitle = value
                inProgramme && currentChild == "desc" && description == null -> description = value
                inProgramme && currentChild == "language" && language == null -> language = value
                inProgramme && currentChild == "category" && categories.size < 12 -> categories += value
                inProgramme && currentChild == "value" && rating == null -> rating = value
                inProgramme && currentChild == "episode-num" -> applyEpisode(value)
                inProgramme && currentChild == "episode-title" && episodeTitle == null -> episodeTitle = value
                inProgramme && inCredits && currentChild != null && credits.size < 8 -> {
                    credits.getOrPut(currentChild!!) { mutableListOf() }.apply { if (size < 12) add(value) }
                }
            }
        }

        fun startElement(name: String, attrs: Map<String, String>) {
            currentChild = name
            when (name) {
                "channel" -> {
                    inChannel = true
                    channelId = attrs["id"]?.takeIf { it.isNotBlank() }
                    channelNames = mutableListOf()
                    channelIcon = null
                }
                "programme" -> {
                    inProgramme = true
                    val rawStart = attrs["start"].orEmpty()
                    val rawStop = attrs["stop"].orEmpty()
                    startUtc = parseTime(rawStart) ?: 0L
                    endUtc = parseTime(rawStop) ?: 0L
                    offset = rawStart.substringAfterLast(' ', "").takeIf { it.isNotBlank() }
                    programmeChannel = attrs["channel"]?.takeIf { it.isNotBlank() }
                    validProgramme = startUtc > 0 && endUtc > 0 && !programmeChannel.isNullOrBlank()
                    if (!validProgramme) invalidTimestamps++
                    title = ""
                    subtitle = null
                    description = null
                    language = null
                    categories = mutableListOf()
                    credits = mutableMapOf()
                    season = null
                    episode = null
                    episodeTitle = null
                    rating = null
                    image = null
                    previouslyShown = false
                    live = false
                }
                "icon" -> {
                    val src = attrs["src"]?.takeIf { it.isNotBlank() }
                    if (inChannel && channelIcon == null) channelIcon = src
                    if (inProgramme && image == null) image = src
                }
                "previously-shown" -> if (inProgramme) previouslyShown = true
                "live" -> if (inProgramme) live = true
                "episode-num" -> pendingEpisodeSystem = attrs["system"]
                "credits" -> if (inProgramme) inCredits = true
            }
            stack.addLast(name)
        }

        fun endElement(name: String) {
            flushText()
            if (name == "channel" && inChannel) {
                channelId?.takeIf { it.isNotBlank() }?.let {
                    channels += EpgChannel(it, channelNames.distinct(), channelIcon)
                }
                inChannel = false
            }
            if (name == "programme" && inProgramme) {
                if (validProgramme && title.isNotBlank()) {
                    if (endUtc <= startUtc) {
                        negativeDurations++
                    } else if (programmes.size < maxProgrammes) {
                        val key = "$programmeChannel|$startUtc|${title.lowercase(Locale.ROOT)}"
                        if (!seenProgrammes.add(key)) {
                            duplicates++
                        } else {
                            programmes += Programme(
                                id = "$sourceId|${key.hashCode()}",
                                channelId = programmeChannel.orEmpty(),
                                sourceId = sourceId,
                                title = title,
                                subtitle = subtitle,
                                description = description,
                                categories = categories.distinct(),
                                startUtc = startUtc,
                                endUtc = endUtc,
                                sourceOffset = offset,
                                language = language,
                                season = season,
                                episode = episode,
                                episodeTitle = episodeTitle,
                                rating = rating,
                                image = image,
                                previouslyShown = previouslyShown,
                                live = live,
                                credits = credits.mapValues { it.value.toList() }
                            )
                            if (programmes.size >= maxProgrammes) truncated = true
                        }
                    }
                } else if (validProgramme) {
                    invalidTimestamps++
                }
                inProgramme = false
            }
            if (name == "credits") inCredits = false
            if (stack.isNotEmpty()) stack.removeLast()
            currentChild = stack.lastOrNull()
        }

        fun skipUntil(marker: String) {
            var matched = 0
            while (matched < marker.length) {
                val c = source.read()
                if (c < 0) break
                if (c == marker[matched].code) matched++ else {
                    matched = if (c == marker[0].code) 1 else 0
                }
            }
        }

        while (true) {
            val c = source.read()
            if (c < 0) break
            if (c != '<'.code && text.length < MAX_TEXT) text.append(c.toChar())
            if (c != '<'.code) continue
            flushText()
            val next = source.read()
            if (next < 0) break
            when (next) {
                '?'.code -> skipUntil("?>")
                '!'.code -> {
                    val lead = StringBuilder()
                    var handled = false
                    while (!handled && lead.length < 512) {
                        val ch = source.read()
                        if (ch < 0) break
                        lead.append(ch.toChar())
                        val current = lead.toString()
                        when {
                            current.startsWith("--") -> { skipUntil("-->"); handled = true }
                            current == "[CDATA[" -> { skipCdata(source, text); handled = true }
                            current.endsWith('>') -> {
                                if (current.contains("DOCTYPE", true) || current.contains("ENTITY", true)) {
                                    throw SecurityException("XML DTD/entities are not allowed in EPG data")
                                }
                                handled = true
                            }
                            current.startsWith("[") && !"[CDATA[".startsWith(current) ->
                                throw SecurityException("Unsupported XML declaration in EPG data")
                        }
                    }
                }
                '/'.code -> {
                    val name = StringBuilder()
                    while (true) {
                        val ch = source.read()
                        if (ch < 0 || ch == '>'.code) break
                        name.append(ch.toChar())
                    }
                    endElement(name.toString().trim().substringAfter(':').lowercase(Locale.ROOT))
                }
                else -> {
                    val content = StringBuilder()
                    content.append(next.toChar())
                    var quote = 0
                    while (true) {
                        val ch = source.read()
                        if (ch < 0) break
                        val char = ch.toChar()
                        if (quote != 0) {
                            content.append(char)
                            if (char.code == quote) quote = 0
                        } else if (char == '"' || char == '\'') {
                            quote = char.code
                            content.append(char)
                        } else if (char == '>') break else content.append(char)
                    }
                    val raw = content.toString().trim()
                    val name = raw.takeWhile { !it.isWhitespace() && it != '/' }
                        .substringAfter(':').lowercase(Locale.ROOT)
                    if (name.isNotBlank()) {
                        startElement(name, attrsOf(raw))
                        if (raw.endsWith("/")) endElement(name)
                    }
                }
            }
        }

        val byChannel = programmes.groupBy { it.channelId }
        var overlaps = 0
        var gaps = 0
        var largestGap = 0L
        byChannel.values.forEach { list ->
            val sorted = list.sortedBy { it.startUtc }
            for (i in 1 until sorted.size) {
                val gap = sorted[i].startUtc - sorted[i - 1].endUtc
                if (gap < 0) overlaps++
                if (gap > 6 * 60 * 60 * 1000L) {
                    gaps++
                    largestGap = maxOf(largestGap, gap)
                }
            }
        }
        return Result(
            channels = channels.distinctBy { it.id },
            programmes = programmes,
            quality = EpgQualityReport(
                channels = channels.size,
                programmes = programmes.size,
                invalidTimestamps = invalidTimestamps,
                negativeDurations = negativeDurations,
                duplicates = duplicates,
                overlaps = overlaps,
                gaps = gaps,
                largestGapMinutes = largestGap / 60_000,
                fetchedAt = System.currentTimeMillis()
            )
        )
    }

    private fun skipCdata(reader: Reader, target: StringBuilder) {
        var matched = 0
        val closing = "]]>"
        while (matched < closing.length) {
            val c = reader.read()
            if (c < 0) break
            val char = c.toChar()
            if (char.code == closing[matched].code) {
                matched++
            } else {
                if (matched > 0) target.append(closing.substring(0, matched))
                matched = if (char == ']') 1 else 0
                if (matched == 0) target.append(char)
            }
        }
    }
}
