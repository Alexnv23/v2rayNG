package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * SuperNet: сетевой путь собственных запросов приложения к ЛК (статистика, устройства, сезонная тема).
 *
 * Зачем отдельно: приложение исключает само себя из своего VPN (CoreVpnService →
 * addDisallowedApplication(self)), поэтому его прямые запросы идут мимо туннеля. При ограничениях
 * мобильного интернета (режим белых списков у операторов) прямой путь до ЛК закрыт → пропадали фон,
 * дни подписки и белый лимит. Пока подключение поднято — идём через локальный HTTP-вход ядра
 * (тот же порт, что использует обновление подписки в AngConfigManager), а при ошибке — напрямую.
 *
 * Состояние «подключено» знает экран (сервис живёт в процессе :daemon), поэтому его передаёт вызывающий.
 * Если в настройках задан логин/пароль локального прокси — идём напрямую: HttpURLConnection не шлёт
 * Proxy-Authorization в CONNECT для https.
 */
object SnNet {
    private const val LOOPBACK = "127.0.0.1"

    /**
     * Открыть соединение к [url], выполнить [use] и закрыть. [viaCore] = подключение поднято.
     * Сначала через ядро (если можно), при IOException — повтор напрямую. Синхронно — только с Dispatchers.IO.
     */
    fun <T> request(url: String, viaCore: Boolean, use: (HttpURLConnection) -> T): T {
        val coreProxy = if (viaCore) coreProxyOrNull() else null
        if (coreProxy != null) {
            try {
                return attempt(url, coreProxy, use)
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "SnNet: via core proxy failed (${e.javaClass.simpleName}), retry direct")
            }
        }
        return attempt(url, Proxy.NO_PROXY, use)
    }

    private fun coreProxyOrNull(): Proxy? {
        if (SettingsManager.getSocksUsername() != null || SettingsManager.getSocksPassword() != null) return null
        val port = SettingsManager.getHttpPort()
        if (port <= 0) return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(LOOPBACK, port))
    }

    private fun <T> attempt(url: String, proxy: Proxy, use: (HttpURLConnection) -> T): T {
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        try {
            return use(conn)
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
