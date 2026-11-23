package com.v2ray.ang

import com.v2ray.ang.handler.SnDeviceInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnDeviceInfoTest {
    @Test
    fun onlyHttpsSuperNetSubdomainsReceiveMetadata() {
        assertTrue(SnDeviceInfo.accepts("https://PANEL.supernet-tech.ru/sub".toHttpUrl()))
        for (url in listOf("http://panel.supernet-tech.ru", "https://supernet-tech.ru.evil.example",
                "https://panel.supernet-tech.ru@external.example", "https://fakesupernet-tech.ru")) {
            assertFalse(url, SnDeviceInfo.accepts(url.toHttpUrl()))
        }
    }

    @Test
    fun valuesPreserveModelAndExcludeControlCharacters() {
        assertEquals("Infinix X6850", SnDeviceInfo.headerValue("Infinix X6850"))
        assertEquals("SM-A047F", SnDeviceInfo.headerValue("SM-\r\nA047F"))
        assertEquals("a".repeat(100), SnDeviceInfo.headerValue("a".repeat(120)))
        assertNull(SnDeviceInfo.headerValue(null))
        assertNull(SnDeviceInfo.headerValue("  \r\n"))
    }
}
