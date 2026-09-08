package com.v2ray.ang.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SnAccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SuperNet: состояние главного экрана (оболочка поверх v2rayNG).
 * Владеет: именем/типом выбранного профиля, наличием профиля «Запасной канал» (OLCRTC),
 * живыми цифрами кабинета и моментом подключения для таймера.
 */
data class HomeUiState(
    val selectedName: String = "",
    val selectedIsBackup: Boolean = false,
    val backupAvailable: Boolean = false,
    val stats: SnAccountManager.Stats? = null,
    val hasToken: Boolean = false,
    val connectedAtMs: Long = 0L,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var selectionJob: Job? = null
    private var statsJob: Job? = null

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
        _uiState.update { it.copy(hasToken = token != null) }
        if (token == null) return
        statsJob?.cancel()
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = SnAccountManager.fetch(token)
            withContext(Dispatchers.Main) {
                if (stats != null) _uiState.update { it.copy(stats = stats) }
            }
        }
    }
}
