package com.inkvpn.app

import com.inkvpn.app.core.DateUtils
import com.inkvpn.app.core.DeepLink
import com.inkvpn.app.core.Format
import com.inkvpn.app.core.GithubUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {

    @Test
    fun formatBytes() {
        assertEquals("0 B", Format.bytes(0))
        assertEquals("1.00 KB", Format.bytes(1024))
        assertTrue(Format.bytes(462027902985L).endsWith("GB"))
    }

    @Test
    fun expireNormalization() {
        // milliseconds -> seconds
        assertEquals(1768000000L, DateUtils.normalizeExpire(1768000000000L))
        // already seconds -> unchanged
        assertEquals(1768000000L, DateUtils.normalizeExpire(1768000000L))
    }

    @Test
    fun githubBlobToRaw() {
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/main/p.json",
            GithubUrl.toRaw("https://github.com/user/repo/blob/main/p.json")
        )
    }

    @Test
    fun deepLinkSubscriptionExtraction() {
        val url = "https://subinkerov.mooo.com/s_8Kjb5c4AKoNRU5"
        assertEquals(url, DeepLink.extractSubscriptionUrl("inkvpn://add/$url"))
        assertEquals(url, DeepLink.extractSubscriptionUrl("happ://add/$url"))
        assertEquals(url, DeepLink.extractSubscriptionUrl(url))
        assertEquals("inkvpn://add/$url", DeepLink.toStoredForm(url))
    }
}
