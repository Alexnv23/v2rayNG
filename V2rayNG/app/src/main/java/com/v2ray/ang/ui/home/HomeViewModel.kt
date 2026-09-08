package com.v2ray.ang.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SnAccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SuperNet: состояние оболочки (главная, друзья, устройства).
 * Владеет: именем/типом выбранного профиля, наличием профиля «Запасной канал» (OLCRTC),
 * живыми цифрами кабинета (в т.ч. устройствами) и моментом подключения для таймера.
 */
data class HomeUiState(
    val selectedName: String = "",
    val selectedIsBackup: Boolean = false,
    val backupAvailable: Boolean = false,
    val stats: SnAccountManager.Stats? = null,
    val hasToken: Boolean = false,
    val statsLoading: Boolean = false,
    val statsError: Boolean = false,
    val devicesBusy: Boolean = false,
    val connectedAtMs: Long = 0L,
    /** Настройка «Обход локальной сети» (PREF_VPN_BYPASS_LAN: "1" = вкл, "2" = выкл). */
    val bypassLan: Boolean = true,
)

/** Одноразовые сообщения для тостов (текст уже с сервера или пустой — тогда экран подставит свой). */
data class SnMessage(val ok: Boolean, val serverText: String, val removedCount: Int = 0)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<SnMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<SnMessage> = _messages.asSharedFlow()

    private var selectionJob: Job? = null
    private var statsJob: Job? = null
    private var devicesJob: Job? = null

    init {
        loadBypassLan()
    }

    /** Прочитать текущее значение «Обход локальной сети» из настроек. */
    fun loadBypassLan() {
        viewModelScope.launch(Dispatchers.IO) {
            val value = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
            withContext(Dispatchers.Main) { _uiState.update { it.copy(bypassLan = value != "2") } }
        }
    }

    /** Переключить «Обход локальной сети». Перезапуск сервиса делает Activity. */
    fun setBypassLan(enabled: Boolean) {
        _uiState.update { it.copy(bypassLan = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, if (enabled) "1" else "2")
        }
    }

    /** Пересчитать имя/тип выбранного профиля и наличие запасного канала. */
    fun onSelectionChanged(selectedGuid: String?) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch(Dispatchers.IO) {
            val profile = selectedGuid?.takeIf { it.isNotBlank() }?.let { MmkvManager.decodeServerConfig(it) }
            val backupExists = MmkvManager.decodeAllServerList().any { guid ->
                MmkvManager.decodeServerConfig(guid)?.configType == EConfigType.OLCRTC
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        selectedName = profile?.remarks.orEmpty(),
                        selectedIsBackup = profile?.configType == EConfigType.OLCRTC,
                        backupAvailable = backupExists,
                    )
                }
            }
        }
    }

    /** Таймер: запоминаем момент, когда сервис стал running. */
    fun onRunningChanged(isRunning: Boolean) {
        _uiState.update {
            val start = when {
                isRunning && it.connectedAtMs == 0L -> System.currentTimeMillis()
                !isRunning -> 0L
                else -> it.connectedAtMs
            }
            it.copy(connectedAtMs = start)
        }
    }

    /** Живые цифры ЛК по токену (если токен есть). */
    fun refreshStats() {
        val token = SnAccountManager.getToken()
        _uiState.update { it.copy(hasToken = token != null, statsError = false) }
        if (token == null) return
        statsJob?.cancel()
        _uiState.update { it.copy(statsLoading = true) }
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = SnAccountManager.fetch(token)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    if (stats != null) it.copy(stats = stats, statsLoading = false, statsError = false)
                    else it.copy(statsLoading = false, statsError = true)
                }
            }
        }
    }

    /** Удалить одно устройство в ЛК, затем перечитать статистику. */
    fun revokeDevice(deviceId: Long) {
        val token = SnAccountManager.getToken() ?: return
        devicesJob?.cancel()
        _uiState.update { it.copy(devicesBusy = true) }
        devicesJob = viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = SnAccountManager.revokeDevice(token, deviceId)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(devicesBusy = false) }
                _messages.tryEmit(SnMessage(ok, msg))
            }
            if (ok) refreshStats()
        }
    }

    /** Удалить все устройства в ЛК. */
    fun revokeAllDevices() {
        val token = SnAccountManager.getToken() ?: return
        val devices = uiState.value.stats?.devices.orEmpty()
        if (devices.isEmpty()) return
        devicesJob?.cancel()
        _uiState.update { it.copy(devicesBusy = true) }
        devicesJob = viewModelScope.launch(Dispatchers.IO) {
            var okCount = 0
            for (d in devices) {
                val (ok, _) = SnAccountManager.revokeDevice(token, d.id)
                if (ok) okCount++
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(devicesBusy = false) }
                _messages.tryEmit(SnMessage(okCount > 0, "", okCount))
            }
            refreshStats()
        }
    }
}
