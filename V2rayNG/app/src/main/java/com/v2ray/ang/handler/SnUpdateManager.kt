package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * SuperNet: обновление приложения по нашему ЛК.
 *   GET https://lk.supernet-tech.ru/static/version.json
 *   {
 *     "version":"1.3.7", "versionCode":11, "file":"SuperNet_1.3.7.apk", "date":"22.09.2026",
 *     "size_mb":39.7,                       // опционально — показываем в карточке
 *     "changelog":["строка","строка"]       // опционально — строка или массив строк
 *   }
 * Тот же файл читает кнопка «Скачать» в кабинете — одна точка правды. Новая версия = выложить APK
 * в static + поправить json. Сравниваем по versionCode (целое), не по строке версии.
 * Старые поля обязательны, новые (size_mb/changelog) — нет: старые сборки их просто не читают.
 *
 * Полный флоу (с 1.3.7): проверка → карточка (версия/размер/дата/что нового) → скачать APK
 * в кэш приложения → открыть системный установщик поверх текущей версии.
 * Все сетевые/файловые вызовы синхронные — только с Dispatchers.IO.
 */
object SnUpdateManager {
    private const val VERSION_URL = "https://lk.supernet-tech.ru/static/version.json"
    private const val DOWNLOAD_BASE = "https://lk.supernet-tech.ru/static/"
    private const val TIMEOUT_MS = 8000
    private const val DOWNLOAD_TIMEOUT_MS = 30000
    private const val CACHE_DIR = "sn_updates"
    private const val APK_MIME = "application/vnd.android.package-archive"

    data class Info(
        val version: String,
        val versionCode: Int,
        val date: String?,
        val downloadUrl: String,
        /** Размер APK в мегабайтах (size_mb в json); null — не указан. */
        val sizeMb: Double? = null,
        /** «Что нового» — уже готовые строки для показа; пусто — не указано. */
        val changelog: List<String> = emptyList(),
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
                sizeMb = j.optDouble("size_mb", -1.0).takeIf { it > 0.0 },
                changelog = parseChangelog(j.opt("changelog")),
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

    /** changelog может быть строкой (переносы = пункты) или массивом строк. Пустые пункты выкидываем. */
    private fun parseChangelog(raw: Any?): List<String> = when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
        is String -> raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    /**
     * Скачать APK в приватный кэш приложения. Возвращает файл или null при любой ошибке.
     * onProgress — проценты 0..100 (или -1, если сервер не отдал Content-Length).
     * Прошлые скачанные APK удаляем, чтобы кэш не рос.
     */
    fun download(context: Context, info: Info, onProgress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(dir, "SuperNet_${info.version}.apk")
        val tmp = File(dir, target.name + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = DOWNLOAD_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) {
                LogUtil.w(AppConfig.TAG, "SnUpdate: download HTTP ${conn.responseCode}")
                return null
            }
            val total = conn.contentLength.toLong()
            var done = 0L
            var lastPct = -2
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) ((done * 100) / total).toInt() else -1
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            if (total > 0 && done != total) {
                LogUtil.w(AppConfig.TAG, "SnUpdate: download truncated $done/$total")
                return null
            }
            if (!tmp.renameTo(target)) {
                LogUtil.w(AppConfig.TAG, "SnUpdate: rename failed")
                return null
            }
            LogUtil.i(AppConfig.TAG, "SnUpdate: downloaded ${info.version} ($done bytes)")
            target
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnUpdate: download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            runCatching { tmp.delete() }
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Открыть системный установщик для скачанного APK (ставится поверх — та же подпись).
     * Android сам покажет «Установить?» и, если надо, попросит разрешить установку из этого
     * источника (REQUEST_INSTALL_PACKAGES в манифесте). Молча поставить без Play/MDM нельзя.
     * true — установщик запущен; false — не удалось (тогда экран предложит скачать в браузере).
     */
    fun install(context: Context, apk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnUpdate: install intent failed: ${e.javaClass.simpleName}")
            false
        }
    }
}
