package com.v2ray.ang.handler

import android.os.Build
import android.provider.Settings
import com.v2ray.ang.AngApplication
import okhttp3.HttpUrl
import okhttp3.Request

/** Device metadata for SuperNet's subscription device limit. */
internal object SnDeviceInfo {
    internal fun accepts(destination: HttpUrl): Boolean =
        destination.isHttps && destination.host.endsWith(".supernet-tech.ru")

    internal fun headerValue(raw: String?): String? =
        raw?.filter { it in ' '..'~' }?.trim()?.take(100)?.takeIf { it.isNotEmpty() }

    fun addSubscriptionHeaders(destination: HttpUrl, builder: Request.Builder) {
        if (!accepts(destination)) return

        val resolver = AngApplication.application.contentResolver
        val androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
        headerValue(androidId)?.let { builder.header("x-hwid", it) }
        headerValue(Build.MODEL)?.let { builder.header("x-device-model", it) }
        builder.header("x-device-os", "Android")
        headerValue(Build.VERSION.RELEASE)?.let { builder.header("x-ver-os", it) }
    }
}
