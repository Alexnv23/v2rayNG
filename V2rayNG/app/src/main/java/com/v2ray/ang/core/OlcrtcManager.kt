package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import mobile.Mobile
import mobile.Runtime
import mobile.SocketProtector
import java.net.ServerSocket
import kotlin.random.Random

/**
 * SuperNet: запасной канал olcRTC (TCP-over-WebRTC под видом видеозвонка).
 * Поднимает локальный SOCKS5 на loopback; ядро xray ходит через него.
 * Портировано из venterum/veil, адаптировано под нашу базу.
 */
object OlcrtcManager {

    private const val WAIT_READY_MS = 15000L
    private const val STOP_TIMEOUT_MS = 5000L
    private const val DEFAULT_VP8_FPS = 30L
    private const val DEFAULT_VP8_BATCH_SIZE = 64L

    /** Ставится сервисом: VpnService.protect(fd), чтобы сокеты olcRTC шли мимо тоннеля. */
    @Volatile
    var socketProtector: ((Int) -> Boolean)? = null

    private val runtime: Runtime by lazy { Mobile.new_() }

    private val protector = object : SocketProtector {
        override fun protect(fd: Long): Boolean {
            return socketProtector?.invoke(fd.toInt()) ?: true
        }
    }

    val isRunning: Boolean
        get() = try {
            runtime.isRunning
        } catch (e: Exception) {
            false
        }

    fun start(context: Context, config: ProfileItem): Boolean {
        if (isRunning) {
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: already running")
            return true
        }

        val carrier = config.olcrtcCarrier?.takeIf { it.isNotBlank() } ?: "jitsi"
        val transport = config.olcrtcTransport?.takeIf { it.isNotBlank() } ?: "datachannel"
        val roomId = normalizeRoomURL(carrier, config.olcrtcRoomId, config.olcrtcServerUrl)
        val hadClientId = config.olcrtcClientId?.isNotBlank() == true
        val clientId = config.olcrtcClientId?.takeIf { it.isNotBlank() } ?: persistentDeviceId()
        val keyHex = config.olcrtcKeyHex ?: ""
        val preferredPort = (config.serverPort ?: AppConfig.PORT_OLCRTC_SOCKS).toIntOrNull()
            ?: AppConfig.PORT_OLCRTC_SOCKS.toInt()
        val socksPort = findAvailablePort(preferredPort)
        val socksHost = AppConfig.LOOPBACK
        val (fps, batchSize) = parseEngine(config.olcrtcEngine)

        if (roomId.isEmpty()) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: roomId is empty")
            return false
        }
        if (keyHex.isEmpty()) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: keyHex is empty")
            return false
        }

        if (!hadClientId) {
            config.olcrtcClientId = clientId
        }
        config.serverPort = socksPort.toString()

        try {
            runtime.setProtector(protector)
            runtime.setProvider(carrier)
            runtime.setTransport(transport)
            runtime.setRoom(roomId)
            runtime.setKey(keyHex)
            runtime.setDNS(resolveOlcrtcDns())
            runtime.setSocksListenHost(socksHost)
            runtime.setSocksPort(socksPort.toLong())
            runtime.setDeviceID(clientId)
            if (fps > 0 || batchSize > 0) {
                runtime.setVP8Options(
                    (if (fps > 0) fps.toLong() else DEFAULT_VP8_FPS),
                    (if (batchSize > 0) batchSize.toLong() else DEFAULT_VP8_BATCH_SIZE)
                )
            }

            LogUtil.d(AppConfig.TAG, "OlcrtcManager: start carrier=$carrier transport=$transport room=$roomId client=$clientId key=${keyHex.take(8)}... socks=$socksHost:$socksPort")
            runtime.start()
            runtime.waitReady(WAIT_READY_MS)
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: started on $socksHost:$socksPort")
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: start failed: ${e.javaClass.simpleName}: ${e.message}", e)
            return false
        }
    }

    fun stop() {
        try {
            runtime.stop(STOP_TIMEOUT_MS)
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: stopped")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: stop failed", e)
        }
    }

    private fun resolveOlcrtcDns(): String {
        MmkvManager.decodeSettingsString(AppConfig.PREF_OLCRTC_DNS)?.trim()?.let { dns ->
            if (dns.isNotBlank()) {
                ipWithPort(dns)?.let { return it }
            }
        }
        SettingsManager.getDomesticDnsServers().firstNotNullOfOrNull { ipWithPort(it) }?.let { return it }
        SettingsManager.getRemoteDnsServers().firstNotNullOfOrNull { ipWithPort(it) }?.let { return it }
        return AppConfig.DNS_OLCRTC_FALLBACK
    }

    private fun ipWithPort(dns: String): String? {
        val trimmed = dns.trim()
        return when {
            trimmed.contains(":") -> {
                val ip = trimmed.substringBefore(":")
                val port = trimmed.substringAfter(":", "53").toIntOrNull() ?: 53
                if (Utils.isPureIpAddress(ip)) "$ip:$port" else null
            }
            Utils.isPureIpAddress(trimmed) -> "$trimmed:53"
            else -> null
        }
    }

    private fun persistentDeviceId(): String {
        val key = "olcrtc_device_id"
        val stored = MmkvManager.decodeSettingsString(key)
        if (!stored.isNullOrBlank()) return stored
        val generated = generateInstallId()
        MmkvManager.encodeSettings(key, generated)
        return generated
    }

    private fun generateInstallId(): String {
        return "install-" + Random.nextBytes(16).joinToString("") { b ->
            (b.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun findAvailablePort(preferred: Int): Int {
        for (port in preferred..preferred + 100) {
            try {
                ServerSocket(port).use { return port }
            } catch (_: Exception) {
                // busy, next
            }
        }
        LogUtil.e(AppConfig.TAG, "OlcrtcManager: no available port in range $preferred..${preferred + 100}")
        return preferred
    }

    private fun normalizeRoomURL(carrier: String, roomId: String?, serverUrl: String?): String {
        val room = roomId ?: ""
        if (room.isEmpty()) return ""
        if (room.contains("://") || room.contains("/")) return room
        val server = serverUrl?.takeIf { it.isNotBlank() } ?: when (carrier) {
            "jitsi" -> "meet.egovm.ru"
            "telemost" -> "telemost.yandex.ru/j"
            else -> return room
        }
        return "https://$server/$room"
    }

    private fun parseEngine(engine: String?): Pair<Int, Int> {
        if (engine.isNullOrBlank()) return 0 to 0
        var fps = 0
        var batchSize = 0
        engine.split("&").forEach { pair ->
            val kv = pair.split("=", limit = 2)
            when (kv.getOrNull(0)?.trim()) {
                "fps" -> fps = kv.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                "batchSize" -> batchSize = kv.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            }
        }
        return fps to batchSize
    }
}
