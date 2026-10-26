package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
    /** Последний удачно прочитанный theme.json (строкой) — для показа темы без сети. */
    private const val KEY_LAST = "sn_theme_last_json"

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
     * Прочитать theme.json и, если нужно, скачать фон в кэш. Возвращает готовую Theme,
     * или null — если сервер недоступен (сеть/ограничения): тогда экран берёт [cached].
     * [viaCore] = подключение поднято → запрос идёт через туннель (см. SnNet), иначе напрямую.
     * Фон перекачиваем только когда сменилось имя файла или "v" — иначе берём уже лежащий в кэше.
     * Удачно прочитанный theme.json (вместе с докачанным фоном) запоминаем в KEY_LAST — чтобы без сети
     * показать последнюю тему.
     */
    fun load(context: Context, viaCore: Boolean = false): Theme? {
        return try {
            val raw = SnNet.request(THEME_URL, viaCore) { conn ->
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Cache-Control", "no-cache")
                if (conn.responseCode != 200) {
                    LogUtil.i(AppConfig.TAG, "SnTheme: HTTP ${conn.responseCode}")
                    null
                } else {
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
            } ?: return null
            val j = JSONObject(raw)
            val theme = fromJson(context, j, download = true, viaCore = viaCore)
            val wantsBg = j.optString("bg", "").trim().isNotEmpty()
            if (theme.enabled && wantsBg && theme.bgPath == null) {
                // Новый фон не докачался — считаем загрузку неудачной: экран покажет прошлую тему
                // из [cached] (её фон ещё в кэше) и попробует снова после подключения. KEY_LAST не трогаем.
                return null
            }
            MmkvManager.encodeSettings(KEY_LAST, raw)
            theme
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnTheme: load failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Последняя удачно загруженная тема из кэша, без сети. Фон — только если он уже лежит в кэше.
     * Нет сохранённой темы → OFF. Синхронно (MMKV + файл) — с Dispatchers.IO.
     */
    fun cached(context: Context): Theme {
        val raw = MmkvManager.decodeSettingsString(KEY_LAST)?.takeIf { it.isNotBlank() } ?: return OFF
        return try {
            fromJson(context, JSONObject(raw), download = false, viaCore = false)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnTheme: cached theme unreadable: ${e.javaClass.simpleName}")
            OFF
        }
    }

    private fun fromJson(context: Context, j: JSONObject, download: Boolean, viaCore: Boolean): Theme {
        if (!j.optBoolean("enabled", false)) return OFF
        val season = j.optString("season", "")
        val anim = parseAnim(j.optString("anim", ""))
        val bgFile = j.optString("bg", "").trim()
        val ver = j.optInt("v", 1)
        val bgPath = when {
            bgFile.isEmpty() -> null
            download -> ensureBg(context, bgFile, ver, viaCore)
            else -> cachedBg(context, bgFile, ver)
        }
        return Theme(enabled = true, season = season, anim = anim, bgPath = bgPath)
    }

    private fun bgTarget(context: Context, bgFile: String, ver: Int): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val safeName = bgFile.substringAfterLast('/').ifBlank { "bg" }
        return File(dir, "${ver}_$safeName")
    }

    private fun cachedBg(context: Context, bgFile: String, ver: Int): String? {
        val target = bgTarget(context, bgFile, ver)
        return if (target.exists() && target.length() > 0) target.absolutePath else null
    }

    /**
     * Гарантировать, что фон <bgFile> версии <ver> лежит в кэше; вернуть путь или null.
     * Ключ кэша = "<ver>_<bgFile>": сменилась версия или имя → качаем заново, старые чистим
     * (только после того, как новый фон докачался целиком — иначе без сети остались бы без фона).
     */
    private fun ensureBg(context: Context, bgFile: String, ver: Int, viaCore: Boolean): String? {
        val target = bgTarget(context, bgFile, ver)
        if (target.exists() && target.length() > 0) return target.absolutePath
        val dir = target.parentFile ?: return null
        val tmp = File(dir, target.name + ".part")
        return try {
            val ok = SnNet.request(BG_BASE + bgFile, viaCore) { conn ->
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = DOWNLOAD_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Cache-Control", "no-cache")
                if (conn.responseCode != 200) {
                    LogUtil.w(AppConfig.TAG, "SnTheme: bg HTTP ${conn.responseCode}")
                    false
                } else {
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
                        false
                    } else {
                        LogUtil.i(AppConfig.TAG, "SnTheme: bg downloaded ${target.name} ($done bytes)")
                        true
                    }
                }
            }
            if (!ok) return null
            // Новый фон докачан — теперь чистим прошлые и ставим новый на место.
            dir.listFiles()?.forEach { if (it != tmp) runCatching { it.delete() } }
            if (!tmp.renameTo(target)) return null
            target.absolutePath
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnTheme: bg download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            runCatching { tmp.delete() }
        }
    }
}
