package app.potatotv.tv

import app.potatotv.data.tv.M3uPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun parsesAttributesInAnyOrderAndKeepsProviderExtras() {
        val raw = """
            #EXTM3U x-tvg-url="https://example.com/epg.xml.gz"
            #EXTINF:-1 tvg-chno="101" group-title="Malayalam" tvg-name="Asianet HD" tvg-country="IN" tvg-id="Asianet.in" tvg-language="Malayalam" tvg-logo="https://logo.test/a.png" tvg-url="https://example.com/per-channel.xml" provider-tag="premium",Asianet Original
            #EXTVLCOPT:http-user-agent=Agent/1.0
            #EXTVLCOPT:http-referrer=https://provider.test/
            https://cdn.test/live/a%20b.m3u8?token=secret
        """.trimIndent()
        val result = M3uPlaylistParser.parse(raw, "p1", "Provider A", 1234L)
        assertEquals(1, result.entries.size)
        assertEquals("https://example.com/epg.xml.gz", result.epgUrl)
        val entry = result.entries.first()
        assertEquals("Asianet HD", entry.originalName)
        assertEquals("asianet", entry.normalizedName)
        assertEquals("Asianet.in", entry.tvgId)
        assertEquals("IN", entry.country)
        assertEquals("Malayalam", entry.language)
        assertEquals("101", entry.channelNumber)
        assertEquals("premium", entry.attributes["provider-tag"])
        assertEquals("https://example.com/per-channel.xml", entry.epgUrl)
        assertEquals("Agent/1.0", entry.headers["User-Agent"])
        assertEquals("https://provider.test/", entry.headers["Referer"])
        assertEquals(1234L, entry.importedAt)
    }

    @Test
    fun missingAttributesAndPlainEntriesAreStillImported() {
        val raw = """
            #EXTM3U
            #EXTINF:-1,Bare Channel
            https://cdn.test/one.m3u8
            https://cdn.test/two.ts
        """.trimIndent()
        val result = M3uPlaylistParser.parse(raw, "p2", "Provider B")
        assertEquals(2, result.entries.size)
        assertEquals("Bare Channel", result.entries[0].originalName)
        assertEquals("two.ts", result.entries[1].originalName)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun duplicateStreamUrlsAreRemovedButDifferentStreamsRemain() {
        val raw = """
            #EXTM3U
            #EXTINF:-1,Duplicate
            https://cdn.test/same.m3u8
            #EXTINF:-1,Duplicate again
            https://cdn.test/same.m3u8
            #EXTINF:-1,Alternative
            https://cdn.test/alt.m3u8
        """.trimIndent()
        val result = M3uPlaylistParser.parse(raw, "p3", "Provider C")
        assertEquals(2, result.entries.size)
        assertTrue(result.warnings.any { it.startsWith("Duplicate stream URL") })
    }

    @Test
    fun malformedAndUnsafeEntriesAreSkippedWithoutLosingGoodOnes() {
        val raw = """
            this is not an m3u
            #EXTINF:-1,Bad
            javascript:alert(1)
            #EXTINF:-1,Good
            https://cdn.test/good.m3u8
        """.trimIndent()
        val result = M3uPlaylistParser.parse(raw, "p4", "Provider D")
        assertEquals(1, result.entries.size)
        assertEquals("Good", result.entries.first().originalName)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun handlesLargePlaylists() {
        val raw = buildString {
            append("#EXTM3U\n")
            repeat(5_000) { index ->
                append("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"News\",Channel $index\n")
                append("https://cdn.test/stream/$index.m3u8\n")
            }
        }
        val result = M3uPlaylistParser.parse(raw, "p5", "Provider E")
        assertEquals(5_000, result.entries.size)
        assertEquals("ch4999", result.entries.last().tvgId)
    }
}
