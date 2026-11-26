package app.potatotv.tv

import app.potatotv.data.tv.EpgQueries
import app.potatotv.data.tv.EpgXmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZoneOffset

class XmlTvParserTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv source-info-name="Test">
          <channel id="Asianet.in">
            <display-name>Asianet</display-name>
            <display-name>ASIANET HD</display-name>
            <icon src="https://logo.test/a.png" />
          </channel>
          <programme start="20261127193000 +0530" stop="20261127203000 +0530" channel="Asianet.in">
            <title>News &amp; Updates</title>
            <sub-title>Evening edition</sub-title>
            <desc><![CDATA[Headlines & <b>analysis</b> from Kerala]]></desc>
            <category>News</category>
            <language>mal</language>
            <credits><presenter>Anchor One</presenter></credits>
            <rating><value>U</value></rating>
            <previously-shown />
          </programme>
          <programme start="20261127203000 +0530" stop="20261127210000 +0530" channel="Asianet.in">
            <title>Movie</title>
            <category>Film</category>
            <episode-num system="xmltv_ns">0.2.0/0</episode-num>
            <live />
          </programme>
          <programme start="not-a-time" stop="20261127210000 +0530" channel="Asianet.in">
            <title>Broken</title>
          </programme>
          <programme start="20261127210000 +0530" stop="20261127203000 +0530" channel="Asianet.in">
            <title>Negative</title>
          </programme>
          <programme start="20261127203000 +0530" stop="20261127210000 +0530" channel="Asianet.in">
            <title>Movie</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parsesChannelsAndProgrammeFields() {
        val result = EpgXmlParser.parse(StringReader(sample), "test")
        assertEquals(1, result.channels.size)
        assertEquals("Asianet.in", result.channels.first().id)
        assertEquals(listOf("Asianet", "ASIANET HD"), result.channels.first().names)
        assertEquals("https://logo.test/a.png", result.channels.first().icon)
        assertEquals(2, result.programmes.size)
        val news = result.programmes.first()
        assertEquals("News & Updates", news.title)
        assertEquals("Evening edition", news.subtitle)
        assertTrue(news.description!!.contains("Headlines & <b>analysis</b>"))
        assertEquals(listOf("News"), news.categories)
        assertEquals("mal", news.language)
        assertEquals(listOf("Anchor One"), news.credits["presenter"])
        assertEquals("U", news.rating)
        assertTrue(news.previouslyShown)
        assertFalse(news.live)
        assertEquals("+0530", news.sourceOffset)
        assertEquals("Movie", result.programmes[1].title)
        assertEquals(1, result.programmes[1].season)
        assertEquals(3, result.programmes[1].episode)
        assertTrue(result.programmes[1].live)
    }

    @Test
    fun convertsOffsetsToUtcAndCrossesMidnight() {
        val parsed = EpgXmlParser.parseTime("20261127233000 +0530")
        assertNotNull(parsed)
        val expected = "2026-11-27T18:00:00Z"
        assertEquals(expected, java.time.Instant.ofEpochMilli(parsed!!).atZone(ZoneOffset.UTC).toInstant().toString())
        val iso = EpgXmlParser.parseTime("2026-11-28T00:30:00+05:30")
        assertEquals("2026-11-27T19:00:00Z", java.time.Instant.ofEpochMilli(iso!!).toString())
    }

    @Test
    fun reportsInvalidNegativeAndDuplicateProgrammes() {
        val result = EpgXmlParser.parse(StringReader(sample), "test")
        assertEquals(1, result.quality.invalidTimestamps)
        assertEquals(1, result.quality.negativeDurations)
        assertEquals(1, result.quality.duplicates)
    }

    @Test
    fun rejectsXmlExternalEntities() {
        val malicious = """
            <?xml version="1.0"?>
            <!DOCTYPE tv [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <tv><channel id="x"><display-name>&xxe;</display-name></channel></tv>
        """.trimIndent()
        try {
            EpgXmlParser.parse(StringReader(malicious), "evil")
            org.junit.Assert.fail("DTD should have been rejected")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("DTD"))
        }
    }

    @Test
    fun nowNextUsesNormalisedUtcTimes() {
        val result = EpgXmlParser.parse(StringReader(sample), "test")
        val sorted = result.programmes.sortedBy { it.startUtc }
        val duringNews = sorted.first().startUtc + 10 * 60_000L
        val info = EpgQueries.nowNext("Asianet.in", listOf(sorted), duringNews)
        assertEquals("News & Updates", info.current!!.title)
        assertEquals("Movie", info.next!!.title)
        assertTrue(info.progress in 0.16f..0.17f)
        assertEquals(50 * 60L, info.secondsLeft)

        val afterAll = sorted.last().endUtc + 60_000L
        val none = EpgQueries.nowNext("Asianet.in", listOf(sorted), afterAll)
        assertNullSafe(none.current)
    }

    private fun assertNullSafe(value: Any?) {
        assertTrue(value == null)
    }

    @Test
    fun classificationIsBasedOnProgrammeMetadataOnly() {
        val result = EpgXmlParser.parse(StringReader(sample), "test")
        assertEquals("news", EpgQueries.kind(result.programmes.first()))
        assertEquals("movie", EpgQueries.kind(result.programmes[1]))
    }
}
