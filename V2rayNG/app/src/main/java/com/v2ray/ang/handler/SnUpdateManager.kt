package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SuperNet: проверка обновления приложения по нашему ЛК.
 *   GET https://lk.supernet-tech.ru/static/version.json
 *   {"version":"1.3.6","file":"SuperNet_1.3.6.apk","date":"09.09.2026","versionCode":10}
 * Тот же файл читает кнопка «Скачать» в кабинете — одна точка правды. Новая версия = выложить APK
 * в static + поправить json. Сравниваем по versionCode (целое), не по строке версии.
 * Синхронный сетевой вызов — только с Dispatchers.IO.
 */
object SnUpdateManager {
    private const val VERSION_URL = "https://lk.supernet-tech.ru/static/version.json"
    private const val DOWNLOAD_BASE = "https://lk.supernet-tech.ru/static/"
    private const val TIMEOUT_MS = 8000

    data class Info(
        val version: String,
        val versionCode: Int,
        val date: String?,
        val downloadUrl: String,
    )

    /** null — обновления нет или не удалось проверить (тихо: это не ошибка для юзера). */
    fun check(): Info? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) {
                LogUtil.w(AppConfig.TAG, "SnUpdate: HTTP ${conn.responseCode}")
                return null
            }
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = j.optInt("versionCode", 0)
            val version = j.optString("version", "")
            if (code <= 0 || version.isBlank()) return null
            if (code <= BuildConfig.VERSION_CODE) {
                LogUtil.i(AppConfig.TAG, "SnUpdate: up to date (server $code, app ${BuildConfig.VERSION_CODE})")
                return null
            }
            val file = j.optString("file", "").ifBlank { "SuperNet_$version.apk" }
            LogUtil.i(AppConfig.TAG, "SnUpdate: new version $version ($code) available")
            Info(
                version = version,
                versionCode = code,
                date = j.optString("date", "").takeIf { it.isNotBlank() },
                downloadUrl = DOWNLOAD_BASE + file,
            )
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnUpdate: check failed: ${e.javaClass.simpleName}")
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
