package app.potatotv.data.tv

import java.util.Locale

/** Pure query helpers: shared by the repository and covered by JVM tests. */
object EpgQueries {

    fun lowerBound(list: List<Programme>, time: Long): Int {
        var low = 0
        var high = list.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (list[mid].startUtc < time) low = mid + 1 else high = mid
        }
        return low
    }

    fun nowNext(epgId: String, channelLists: Collection<List<Programme>>, at: Long): NowNext {
        var current: Programme? = null
        var next: Programme? = null
        channelLists.forEach { list ->
            if (list.isEmpty()) return@forEach
            val sorted = if (list.isSortedByStart()) list else list.sortedBy { it.startUtc }
            val upper = lowerBound(sorted, at + 1)
            for (i in (upper - 1) downTo maxOf(0, upper - 8)) {
                val candidate = sorted[i]
                if (candidate.startUtc <= at && candidate.endUtc > at &&
                    (current == null || candidate.startUtc > current!!.startUtc)
                ) {
                    current = candidate
                }
                if (candidate.startUtc <= at) break
            }
            if (upper < sorted.size && (next == null || sorted[upper].startUtc < next!!.startUtc)) {
                next = sorted[upper]
            }
        }
        val progress = if (current == null || current.durationMs <= 0) 0f else
            ((at - current.startUtc).toFloat() / current.durationMs).coerceIn(0f, 1f)
        val secondsLeft = if (current == null) 0L else maxOf(0L, (current.endUtc - at) / 1000L)
        return NowNext(epgId, epgId, current, next, progress, secondsLeft)
    }

    private fun List<Programme>.isSortedByStart(): Boolean {
        for (i in 1 until size) if (this[i].startUtc < this[i - 1].startUtc) return false
        return true
    }

    /** Classification only from actual EPG metadata; never from channel names. */
    fun kind(programme: Programme): String {
        val categories = programme.categories.map { it.lowercase(Locale.ROOT) }
        val text = (listOf(programme.title, programme.subtitle, programme.description) + categories)
            .joinToString(" ").lowercase(Locale.ROOT)
        return when {
            categories.any { it.contains("sport") || it.contains("football") || it.contains("cricket") } -> "sports"
            categories.any { it.contains("movie") || it.contains("film") } -> "movie"
            categories.any { it.contains("news") } -> "news"
            categories.any { it.contains("document") } -> "documentary"
            categories.any { it.contains("series") || it.contains("soap") } -> "series"
            Regex("""\b(football|cricket|match|tournament|league|olympic)\b""").containsMatchIn(text) -> "sports"
            Regex("""\b(news|bulletin|headlines)\b""").containsMatchIn(text) -> "news"
            else -> "other"
        }
    }
}
