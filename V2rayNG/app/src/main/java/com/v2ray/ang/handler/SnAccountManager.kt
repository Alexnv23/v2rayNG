package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SuperNet: «кабинет в приложении» — живые цифры юзера с ЛК по app-токену.
 *   GET  https://lk.supernet-tech.ru/api/app/stats/{token}               — статистика + устройства (read-only)
 *   POST https://lk.supernet-tech.ru/api/app/devices/{token}/{id}/revoke  — удалить устройство
 * Токен приходит из deeplink `supernet://subscription?url=…&token=…` (кнопка «Добавить в приложение» в ЛК)
 * и хранится в настройках MMKV. Владелец токена и сетевых вызовов — этот менеджер; UI сюда не ходит напрямую.
 * Портировано из owenclave AccountApi.kt.
 */
object SnAccountManager {
    private const val BASE = "https://lk.supernet-tech.ru"
    private const val KEY_TOKEN = "sn_account_token"
    private const val TIMEOUT_MS = 8000

    fun saveToken(token: String) {
        if (token.isBlank()) return
        MmkvManager.encodeSettings(KEY_TOKEN, token)
    }

    fun getToken(): String? = MmkvManager.decodeSettingsString(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    /** Забыть токен кабинета (при удалении подписки). */
    fun clearToken() {
        MmkvManager.encodeSettings(KEY_TOKEN, "")
    }

    /** Устройство из учёта ЛК (marzban_devices, сгруппировано по hwid на сервере). */
    data class Device(
        val id: Long,
        val name: String,
        val os: String,
        val lastSeen: String?,
    )

    data class Stats(
        val displayName: String?,
        val days: Int?,
        val whiteUsedGb: Double,
        val whiteLimitGb: Double,
        val whitePercent: Double,
        val whiteBlocked: Boolean,
        val hasWhiteLimit: Boolean,
        val friendsInvited: Int,
        val friendsPaid: Int,
        val balance: Int,
        val refLink: String?,
        val devicesCount: Int,
        val devicesLimit: Int,
        val devices: List<Device> = emptyList(),
    )

    /** Синхронный сетевой вызов — только с Dispatchers.IO. null при любой ошибке. */
    fun fetch(token: String): Stats? {
        if (token.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/api/app/stats/$token").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) {
                LogUtil.w(AppConfig.TAG, "SnAccount: stats HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseStats(JSONObject(body))
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnAccount: stats fetch failed: ${e.javaClass.simpleName}")
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseStats(j: JSONObject): Stats {
        val devs = ArrayList<Device>()
        try {
            val arr = j.optJSONArray("devices")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    devs.add(
                        Device(
                            id = d.optLong("id", 0L),
                            name = d.optString("name", "").ifBlank { "Device" },
                            os = d.optString("os", ""),
                            lastSeen = if (d.isNull("last_seen")) null else d.optString("last_seen").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return Stats(
            displayName = if (j.isNull("display_name")) null else j.optString("display_name").takeIf { it.isNotBlank() },
            days = if (j.isNull("days_remaining")) null else j.optInt("days_remaining"),
            whiteUsedGb = j.optDouble("white_used_gb", 0.0),
            whiteLimitGb = j.optDouble("white_limit_gb", 0.0),
            whitePercent = j.optDouble("white_percent", 0.0),
            whiteBlocked = j.optBoolean("white_blocked", false),
            hasWhiteLimit = j.optBoolean("has_white_limit", false),
            friendsInvited = j.optInt("friends_invited", 0),
            friendsPaid = j.optInt("friends_paid", 0),
            balance = j.optInt("balance", 0),
            refLink = if (j.isNull("ref_link")) null else j.optString("ref_link"),
            devicesCount = j.optInt("devices_count", devs.size),
            devicesLimit = j.optInt("devices_limit", 2),
            devices = devs,
        )
    }

    /** Удалить устройство. Синхронно — только с Dispatchers.IO. Pair(успех, сообщение сервера/ошибки). */
    fun revokeDevice(token: String, deviceId: Long): Pair<Boolean, String> {
        if (token.isBlank() || deviceId <= 0L) return false to ""
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/api/app/devices/$token/$deviceId/revoke").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Length", "0")
            }
            conn.outputStream.use { }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = try {
                stream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) {
                ""
            }
            val msg = try {
                JSONObject(body).let { it.optString("message", it.optString("detail", "")) }
            } catch (_: Exception) {
                ""
            }
            (code in 200..299) to msg
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SnAccount: revoke failed: ${e.javaClass.simpleName}")
            false to (e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
