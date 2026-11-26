package app.potatotv.tv

import app.potatotv.data.tv.SafeUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeUrlsTest {

    @Test
    fun blocksLoopbackPrivateAndMalformedDestinations() {
        assertFalse(SafeUrls.checkFetchable("http://127.0.0.1/epg.xml").ok)
        assertFalse(SafeUrls.checkFetchable("http://localhost/epg.xml").ok)
        assertFalse(SafeUrls.checkFetchable("http://10.0.0.5/epg.xml").ok)
        assertFalse(SafeUrls.checkFetchable("http://192.168.1.1/epg.xml").ok)
        assertFalse(SafeUrls.checkFetchable("file:///etc/passwd").ok)
        assertFalse(SafeUrls.checkFetchable("not a url").ok)
    }

    @Test
    fun allowsPublicAddresses() {
        assertTrue(SafeUrls.checkFetchable("https://8.8.8.8/epg.xml.gz").ok)
    }

    @Test
    fun streamSchemesAreSeparateFromFetchValidation() {
        assertTrue(SafeUrls.isPlayable("rtmp://cdn.example/live/stream"))
        assertFalse(SafeUrls.isPlayable("javascript:alert(1)"))
    }

    @Test
    fun redactsCredentialsAndTokensFromLogs() {
        val redacted = SafeUrls.redact("https://user:pass@example.com/live/user/pass/123.m3u8?token=abc&name=x")
        assertFalse(redacted.contains("pass@"))
        assertFalse(redacted.contains("abc"))
        assertTrue(redacted.contains("token=***"))
        assertTrue(redacted.contains("name=x"))
        assertEquals("<invalid url>", SafeUrls.redact("http://[bad"))
    }
}
