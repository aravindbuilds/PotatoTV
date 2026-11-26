package app.potatotv.tv

import app.potatotv.data.tv.ChannelMatcher
import app.potatotv.data.tv.ChannelOverrides
import app.potatotv.data.tv.CanonicalChannel
import app.potatotv.data.tv.FieldProvenance
import app.potatotv.data.tv.MatchConfidence
import app.potatotv.data.tv.MatchConfig
import app.potatotv.data.tv.PlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMatcherTest {

    private fun channel(
        id: String,
        name: String,
        aliases: List<String> = emptyList(),
        languages: List<String> = emptyList()
    ) = CanonicalChannel(
        id = id,
        name = name,
        aliases = aliases,
        languages = languages,
        provenance = listOf(FieldProvenance("externalId", "iptv-org", id, verified = true))
    )

    private fun entry(name: String, tvgId: String? = null, id: String = "entry-$name") = PlaylistEntry(
        id = id,
        playlistId = "p1",
        playlistName = "Provider",
        originalName = name,
        normalizedName = app.potatotv.data.tv.ChannelIdentity.normalize(name),
        streamUrl = "https://cdn.test/$name.m3u8",
        tvgId = tvgId
    )

    private val catalogue = listOf(
        channel("Asianet.in", "Asianet", listOf("Asianet TV"), listOf("Malayalam")),
        channel("AsianetMovies.in", "Asianet Movies", languages = listOf("Malayalam")),
        channel("AsianetNews.in", "Asianet News", languages = listOf("English")),
        channel("AsianetNewsMalayalam.in", "Asianet News Malayalam", languages = listOf("Malayalam")),
        channel("JayaTV.in", "Jaya TV", listOf("Jaya"), listOf("Tamil"))
    )

    @Test
    fun exactExternalIdWins() {
        val matcher = ChannelMatcher(catalogue)
        val result = matcher.bestAutomatic(entry("Something else entirely", "AsianetMovies.in"))
        assertNotNull(result)
        assertEquals("AsianetMovies.in", result!!.channel.id)
        assertEquals(MatchConfidence.HIGH, result.confidence)
        assertTrue(result.reasons.any { it.contains("id", true) })
    }

    @Test
    fun exactNameAndAliasMatch() {
        val matcher = ChannelMatcher(catalogue)
        assertEquals("JayaTV.in", matcher.bestAutomatic(entry("Jaya TV"))!!.channel.id)
        assertEquals("JayaTV.in", matcher.bestAutomatic(entry("Jaya"))!!.channel.id)
    }

    @Test
    fun hdVariantWithoutAnIdIsNotSilentlyMerged() {
        val catalogueWithFeed = catalogue + channel("AsianetHD.in", "Asianet HD")
        val matcher = ChannelMatcher(catalogueWithFeed)
        assertNull("Ambiguous HD/SD name must go to review", matcher.bestAutomatic(entry("Asianet HD")))
        val withId = matcher.bestAutomatic(entry("Asianet HD", "AsianetHD.in"))
        assertNotNull(withId)
        assertEquals("AsianetHD.in", withId!!.channel.id)
    }

    @Test
    fun languageFeedIsNotMergedIntoTheWrongChannel() {
        val matcher = ChannelMatcher(catalogue)
        val result = matcher.bestAutomatic(entry("Asianet News Malayalam"))
        assertNotNull(result)
        assertEquals("AsianetNewsMalayalam.in", result!!.channel.id)
    }

    @Test
    fun manualOverrideTakesPrecedence() {
        val matcher = ChannelMatcher(catalogue)
        val source = entry("Asianet", "Asianet.in", id = "my-entry")
        val overridden = matcher.bestAutomatic(
            source,
            ChannelOverrides(mappings = mapOf("my-entry" to "JayaTV.in"))
        )
        assertNotNull(overridden)
        assertEquals("JayaTV.in", overridden!!.channel.id)
        assertTrue(overridden.reasons.any { it.contains("Manual") })
    }

    @Test
    fun unrelatedChannelIsLeftUnmatched() {
        val matcher = ChannelMatcher(catalogue, MatchConfig())
        val results = matcher.match(entry("BBC World News"))
        assertTrue(results.isEmpty() || results.all { it.confidence != MatchConfidence.HIGH })
    }

    @Test
    fun normalisationHandlesUnicodePunctuationAndQualitySuffixes() {
        assertEquals("asianet", app.potatotv.data.tv.ChannelIdentity.normalize("  ASIANET 4K  "))
        assertEquals("kairali tv", app.potatotv.data.tv.ChannelIdentity.normalize("IN - Kairali.TV"))
        assertEquals("mazhavil manorama", app.potatotv.data.tv.ChannelIdentity.normalize("Mazhavil Manorama HD"))
        assertTrue(app.potatotv.data.tv.ChannelIdentity.qualityClaims("Asianet FULL HD").contains("fhd"))
    }
}
