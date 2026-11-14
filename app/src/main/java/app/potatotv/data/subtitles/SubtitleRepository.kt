package app.potatotv.data.subtitles

import app.potatotv.data.model.SubtitleCue
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.Charset

data class SubtitleResult(
    val fileId: Int,
    val language: String,
    val release: String,
    val downloads: Int,
    val fileName: String,
    val pageUrl: String
)

@Serializable
private data class OsSearchResponse(val data: List<OsItem> = emptyList())

@Serializable
private data class OsItem(val id: String = "", val attributes: OsAttributes = OsAttributes())

@Serializable
private data class OsAttributes(
    val subtitle_id: String = "",
    val language: String = "",
    val download_count: Int = 0,
    val release: String = "",
    val url: String = "",
    val files: List<OsFile> = emptyList()
)

@Serializable
private data class OsFile(val file_id: Int = 0, val file_name: String = "")

@Serializable
private data class OsDownloadResponse(
    val link: String = "",
    val file_name: String = "",
    val message: String = ""
)

class SubtitleRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(apiKey: String, query: String, language: String = "en"): List<SubtitleResult> {
        if (apiKey.isBlank()) error("Add your OpenSubtitles API key in Settings first.")
        if (query.isBlank()) return emptyList()
        val enc = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "https://api.opensubtitles.com/api/v1/subtitles?query=$enc&languages=$language&order_by=download_count&per_page=25"
        val body = Http.getText(url, headers = openSubtitlesHeaders(apiKey))
        return json.decodeFromString(OsSearchResponse.serializer(), body).data.mapNotNull { item ->
            val file = item.attributes.files.firstOrNull() ?: return@mapNotNull null
            if (file.file_id <= 0) return@mapNotNull null
            SubtitleResult(
                fileId = file.file_id,
                language = item.attributes.language.ifBlank { language },
                release = item.attributes.release.ifBlank { file.file_name },
                downloads = item.attributes.download_count,
                fileName = file.file_name,
                pageUrl = item.attributes.url
            )
        }
    }

    /** Returns the raw subtitle text (SRT/WebVTT). */
    suspend fun download(apiKey: String, fileId: Int): String {
        if (apiKey.isBlank()) error("Add your OpenSubtitles API key in Settings first.")
        val body = Http.postJson(
            "https://api.opensubtitles.com/api/v1/download",
            body = """{"file_id":$fileId}""",
            headers = openSubtitlesHeaders(apiKey)
        )
        val parsed = json.decodeFromString(OsDownloadResponse.serializer(), body)
        if (parsed.link.isBlank()) error(parsed.message.ifBlank { "OpenSubtitles did not return a download link." })
        val bytes = Http.getBytes(parsed.link, headers = mapOf("User-Agent" to USER_AGENT))
        return decodeText(bytes)
    }

    suspend fun fetchDirect(url: String): String {
        val bytes = Http.getBytes(url)
        return decodeText(bytes)
    }

    fun parse(raw: String): List<SubtitleCue> = SubtitleParser.parse(raw)

    private fun openSubtitlesHeaders(apiKey: String) = mapOf(
        "Api-Key" to apiKey,
        "Accept" to "application/json",
        "User-Agent" to USER_AGENT
    )

    private suspend fun decodeText(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        val utf8 = runCatching { Charset.forName("UTF-8").decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charsets.UTF_8) }
        if (utf8.contains('\uFFFD')) runCatching { String(bytes, Charset.forName("windows-1252")) }.getOrDefault(utf8) else utf8
    }

    companion object {
        const val USER_AGENT = "PotatoTV v1.0.0"
    }
}

object SubtitleParser {

    private val timeRegex = Regex(
        """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})"""
    )
    private val tagRegex = Regex("""<[^>]+>""")

    fun parse(raw: String): List<SubtitleCue> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        val cues = mutableListOf<SubtitleCue>()
        text.split(Regex("\n\\s*\n")).forEach { block ->
            val lines = block.lines()
            val timeIndex = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeIndex < 0) return@forEach
            val match = timeRegex.find(lines[timeIndex]) ?: return@forEach
            val start = toMillis(
                match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4]
            )
            val end = toMillis(
                match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8]
            )
            if (end <= start) return@forEach
            val content = lines.drop(timeIndex + 1)
                .joinToString("\n")
                .replace(tagRegex, "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
            if (content.isNotBlank()) cues += SubtitleCue(start, end, content)
        }
        return cues.sortedBy { it.startMs }
    }

    private fun toMillis(hours: String?, minutes: String?, seconds: String, millis: String): Long {
        val h = hours?.toLongOrNull() ?: 0L
        val m = minutes?.toLongOrNull() ?: 0L
        val s = seconds.toLongOrNull() ?: 0L
        val ms = when (millis.length) {
            1 -> millis.toLong() * 100
            2 -> millis.toLong() * 10
            else -> millis.toLong()
        }
        return ((h * 60 + m) * 60 + s) * 1000 + ms
    }
}
