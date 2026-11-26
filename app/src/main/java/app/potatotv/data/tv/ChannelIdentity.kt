package app.potatotv.data.tv

import java.text.Normalizer
import java.util.Locale

/** Name normalisation and conservative quality/feed detection. */
object ChannelIdentity {

    private val qualityPattern = Regex(
        """(?<!\p{L})(fhd|uhd|4k|hd|sd|full\s*hd)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )
    private val prefixPattern = Regex(
        """^\s*(?:in|india|us|usa|uk|ind|en|eng|english|hindi|tamil|telugu|malayalam|kannada|bengali|marathi)\s*[-:|]\s*""",
        RegexOption.IGNORE_CASE
    )
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private val languages = listOf(
        "malayalam", "tamil", "telugu", "kannada", "hindi", "english", "bengali",
        "marathi", "punjabi", "gujarati", "urdu", "odia", "assamese", "nepali",
        "sinhala", "arabic", "french", "german", "spanish", "portuguese", "japanese"
    )
    private val languageAliases = mapOf(
        "mal" to "malayalam", "tam" to "tamil", "tel" to "telugu", "kan" to "kannada",
        "hin" to "hindi", "eng" to "english", "ben" to "bengali", "mar" to "marathi"
    )
    private val regions = setOf(
        "kerala", "tamilnadu", "tamil nadu", "andhra", "telangana", "karnataka",
        "bengal", "maharashtra", "punjab", "gujarat", "delhi", "mumbai", "north", "south"
    )

    /** Lowercase, de-accent, strip punctuation and quality/feed prefixes. */
    fun normalize(input: String): String {
        var value = input.trim()
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}"""), "")
        value = value.lowercase(Locale.ROOT)
            .replace('&', ' ')
            .replace("24x7", "24 7")
        value = value.replace(qualityPattern, " ")
        value = value.replace(prefixPattern, " ")
        value = value.replace(nonWord, " ")
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    /** A claim only. It is never used as proof of the stream's real quality. */
    fun qualityClaims(input: String): Set<String> = qualityPattern.findAll(input)
        .map { it.groupValues[1].lowercase(Locale.ROOT).replace("full hd", "fhd") }
        .toSet()

    fun feedHints(input: String): Set<String> {
        val normalized = normalize(input)
        val tokens = normalized.split(' ').toSet()
        val hints = mutableSetOf<String>()
        languages.forEach { if (tokens.contains(it)) hints += it }
        languageAliases.forEach { (alias, full) -> if (tokens.contains(alias)) hints += full }
        regions.forEach { if (normalized.contains(it)) hints += it }
        return hints
    }

    fun tokens(input: String): Set<String> = normalize(input).split(' ')
        .filter { it.length > 1 }
        .toSet()

    fun similarity(a: String, b: String): Float {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val intersection = ta.intersect(tb).size
        val union = (ta + tb).size
        val jaccard = intersection.toFloat() / union
        val distance = levenshtein(normalize(a), normalize(b))
        val longest = maxOf(a.length, b.length).coerceAtLeast(1)
        val edit = 1f - (distance.toFloat() / longest)
        return (0.72f * jaccard + 0.28f * edit).coerceIn(0f, 1f)
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
