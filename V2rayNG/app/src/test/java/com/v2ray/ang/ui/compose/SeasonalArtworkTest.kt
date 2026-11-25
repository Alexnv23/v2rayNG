package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonalArtworkTest {
    @Test
    fun recognizesGifAndAnimatedWebpFromCacheContents() {
        assertEquals("image/gif", animatedThemeMime("GIF89a".toByteArray()))
        assertEquals("image/gif", animatedThemeMime("GIF87a".toByteArray()))
        val extended = "RIFF0000WEBPVP8X0000 ".toByteArray()
        extended[20] = 0x12
        assertEquals("image/webp", animatedThemeMime(extended))
        extended[20] = 0x10
        assertNull(animatedThemeMime(extended))
    }

    @Test
    fun preservesTheStillImagePathForSimpleWebpAndUnknownFiles() {
        for (signature in listOf("", "GIF89", "RIFF", "RIFF0000WEBPVP8X0000", "RIFF0000WEBPVP8 ", "RIFF0000WEBPVP8L", "invalid")) {
            assertNull(animatedThemeMime(signature.toByteArray()))
        }
    }
}
