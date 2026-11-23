package app.potatotv.data.sites

import app.potatotv.data.net.Http

/**
 * FMHY's public Stream Site Grading (score out of 18) as a name -> score map.
 * Used to rate every site imported from the wiki so the best sources sort first.
 */
object FmhyGrading {

    const val GRADING_URL =
        "https://raw.githubusercontent.com/wiki/fmhy/FMHY/Stream-Site-Grading.md"

    private val SCORE = Regex("Score\\s*:\\s*(\\d+)\\s*/\\s*18", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("^#{2,3}\\s+(.+?)\\s*$")

    suspend fun load(): Map<String, Int> = runCatching {
        parse(Http.getText(GRADING_URL))
    }.getOrDefault(emptyMap())

    fun parse(markdown: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        var pendingNames = emptyList<String>()
        markdown.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                val title = heading.groupValues[1].trim()
                if (title.startsWith("Score", ignoreCase = true)) {
                    SCORE.find(title)?.let { score ->
                        pendingNames.forEach { name -> out[name] = score.groupValues[1].toInt() }
                    }
                    pendingNames = emptyList()
                } else {
                    // Flush any previous section that never printed a score.
                    pendingNames = title.split('/').map { normalize(it) }.filter { it.length > 1 }
                }
            } else {
                SCORE.find(line)?.let { score ->
                    pendingNames.forEach { name -> out[name] = score.groupValues[1].toInt() }
                    pendingNames = emptyList()
                }
            }
        }
        return out
    }

    /** Loose key so "Freekz / ZXCSTREAM" also matches "Freekz" or "zxcstream". */
    private fun normalize(name: String): String = name
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    fun scoreFor(name: String, grades: Map<String, Int>): Int =
        grades[normalize(name)] ?: 0
}
