package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * SuperNet: сезонное оформление Главной, управляется С СЕРВЕРА (одна точка правды).
 *   GET https://lk.supernet-tech.ru/static/themes/theme.json
 *   {
 *     "enabled": true,        // выключить всё оформление разом
 *     "season": "sep",        // ид сезона (информативно)
 *     "bg": "sep.webp",       // имя файла фона в /static/themes/
 *     "anim": "leaves",       // тип анимации: leaves | light | lake | off
 *     "v": 1                  // версия ассета: изменилась → перекачать фон
 *   }
 * Фон-картинки лежат на сервере (в APK их нет) → добавить/сменить сезон с готовым типом
 * анимации = залить картинку + поправить json, БЕЗ обновления приложения.
 * Тип анимации (leaves/light/lake/…) реализован в коде (SnSeasonal). Новый тип = обновление аппы.
 *
 * Все сетевые/файловые вызовы синхронные — вызывать только с Dispatchers.IO.
 */
object SnThemeManager {
    private const val THEME_URL = "https://lk.supernet-tech.ru/static/themes/theme.json"
    private const val BG_BASE = "https://lk.supernet-tech.ru/static/themes/"
    private const val TIMEOUT_MS = 8000
    private const val DOWNLOAD_TIMEOUT_MS = 20000
    private const val CACHE_DIR = "sn_theme"

    /** Типы анимации, которые умеет рисовать SnSeasonal. Незнакомый тип → NONE (только фон). */
    enum class Anim { LEAVES, LIGHT, LAKE, NONE }

    data class Theme(
        val enabled: Boolean,
        val season: String,
        val anim: Anim,
        /** Абсолютный путь к скачанному фону в кэше, либо null (фон не задан/не скачался). */
        val bgPath: String?,
    ) {
        val hasBg get() = bgPath != null
    }

    /** Пусто/выключено — вернуть тему без оформления (Главная как обычно). */
    val OFF = Theme(enabled = false, season = "", anim = Anim.NONE, bgPath = null)

    private fun parseAnim(s: String): Anim = when (s.trim().lowercase()) {
        "leaves" -> Anim.LEAVES
        "light" -> Anim.LIGHT
        "lake" -> Anim.LAKE
        else -> Anim.NONE
    }

    /**
     * Прочитать theme.json и, если нужно, скачать фон в кэш. Возвращает готовую Theme.
     * При любой сетевой ошибке — OFF (тихо, не мешаем работе аппы). Фон перекачиваем только когда
     * сменилось имя файла или "v" — иначе берём уже лежащий в кэше (экономим трафик).
     */
    fun load(context: Context): Theme {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(THEME_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) {
                LogUtil.i(AppConfig.TAG, "SnTheme: HTTP ${conn.responseCode}")
                return OFF
            }
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (!j.optBoolean("enabled", false)) return OFF
            val season = j.optString("season", "")
            val anim = parseAnim(j.optString("anim", ""))
            val bgFile = j.optString("bg", "").trim()
            val ver = j.optInt("v", 1)
            val bgPath = if (bgFile.isNotEmpty()) ensureBg(context, bgFile, ver) else null
            Theme(enabled = true, season = season, anim = anim, bgPath = bgPath)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnTheme: load failed: ${e.javaClass.simpleName}")
            OFF
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Гарантировать, что фон <bgFile> версии <ver> лежит в кэше; вернуть путь или null.
     * Ключ кэша = "<ver>_<bgFile>": сменилась версия или имя → качаем заново, старые чистим.
     */
    private fun ensureBg(context: Context, bgFile: String, ver: Int): String? {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val safeName = bgFile.substringAfterLast('/').ifBlank { "bg" }
        val target = File(dir, "${ver}_$safeName")
        if (target.exists() && target.length() > 0) return target.absolutePath
        // Новый фон — чистим прошлые и качаем.
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val tmp = File(dir, target.name + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BG_BASE + bgFile).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = DOWNLOAD_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) {
                LogUtil.w(AppConfig.TAG, "SnTheme: bg HTTP ${conn.responseCode}")
                return null
            }
            val total = conn.contentLength.toLong()
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                    }
                }
            }
            if (total > 0 && done != total) {
                LogUtil.w(AppConfig.TAG, "SnTheme: bg truncated $done/$total")
                return null
            }
            if (!tmp.renameTo(target)) return null
            LogUtil.i(AppConfig.TAG, "SnTheme: bg cached $safeName v$ver ($done bytes)")
            target.absolutePath
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnTheme: bg download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            runCatching { tmp.delete() }
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
