package com.v2ray.ang.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SnAccountManager
import com.v2ray.ang.handler.SnThemeManager
import com.v2ray.ang.handler.SnUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /** Имя из кэша (последняя удачная загрузка) — показываем, пока свежая статистика не пришла. */
    val cachedDisplayName: String? = null,
    /** Доступное обновление приложения (version.json на ЛК); null — нет или скрыто. */
    val update: SnUpdateManager.Info? = null,
    /** Открыта карточка обновления (версия/размер/дата/что нового). */
    val updateDialog: Boolean = false,
    /** Идёт скачивание APK. */
    val updateDownloading: Boolean = false,
    /** Прогресс скачивания 0..100; -1 — размер неизвестен (крутилка без процентов). */
    val updateProgress: Int = 0,
    /** Скачать/запустить установку не удалось — экран предложит скачать в браузере. */
    val updateError: Boolean = false,
    val hasToken: Boolean = false,
    val statsLoading: Boolean = false,
    val statsError: Boolean = false,
    val devicesBusy: Boolean = false,
    val connectedAtMs: Long = 0L,
    /** Настройка «Обход локальной сети» (PREF_VPN_BYPASS_LAN: "1" = вкл, "2" = выкл). */
    val bypassLan: Boolean = true,
    /** Сезонное оформление с сервера (theme.json). OFF — нет оформления. */
    val theme: SnThemeManager.Theme = SnThemeManager.OFF,
    /** Пользователь включил оформление (PREF_SN_DECOR_ON, по умолчанию вкл). */
    val decorEnabled: Boolean = true,
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
        loadDecor()
    }

    /** Прочитать пользовательский тумблер «Анимация оформления» (по умолчанию вкл). */
    fun loadDecor() {
        viewModelScope.launch(Dispatchers.IO) {
            val on = MmkvManager.decodeSettingsString(AppConfig.PREF_SN_DECOR_ON, "1") != "0"
            withContext(Dispatchers.Main) { _uiState.update { it.copy(decorEnabled = on) } }
        }
    }

    /** Переключить сезонное оформление (только визуальный слой, ничего не рестартит). */
    fun setDecor(enabled: Boolean) {
        _uiState.update { it.copy(decorEnabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSettings(AppConfig.PREF_SN_DECOR_ON, if (enabled) "1" else "0")
        }
    }

    private var themeChecked = false

    /** Загрузить сезонную тему с сервера — один раз за запуск, тихо в фоне. */
    fun checkTheme(force: Boolean = false) {
        if (themeChecked && !force) return
        themeChecked = true
        viewModelScope.launch(Dispatchers.IO) {
            val theme = SnThemeManager.load(AngApplication.application)
            withContext(Dispatchers.Main) { _uiState.update { it.copy(theme = theme) } }
        }
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

    /**
     * Таймер подключения. Момент старта хранится в MMKV (PREF_SN_CONNECTED_AT), а не только
     * в памяти экрана — иначе после сворачивания и убийства приложения системой таймер
     * начинал с нуля, хотя подключение не рвалось. При остановке сервиса (в т.ч. перезапуск
     * на смену локации) значение сбрасывается — отсчёт начинается заново, как и должен.
     */
    fun onRunningChanged(isRunning: Boolean) {
        if (!isRunning) {
            _uiState.update { it.copy(connectedAtMs = 0L) }
            return
        }
        // Момент старта пишет СЕРВИС (CoreServiceManager) при запуске ядра и стирает при остановке.
        // Экран только читает — поэтому перезапуск/убийство приложения отсчёт не сбивают.
        // Фолбэк на «сейчас» — только если запись отсутствует (старый сервис до этого фикса).
        val persisted = MmkvManager.decodeSettingsLong(AppConfig.PREF_SN_CONNECTED_AT, 0L)
        val start = if (persisted > 0L) persisted else System.currentTimeMillis().also {
            MmkvManager.encodeSettings(AppConfig.PREF_SN_CONNECTED_AT, it)
        }
        _uiState.update { it.copy(connectedAtMs = start) }
        // Подключение поднялось — статистика могла не загрузиться до этого (нет сети / обход
        // ещё не готов). Перечитать чуть позже, когда тоннель точно живой.
        if (uiState.value.stats == null && SnAccountManager.getToken() != null) {
            viewModelScope.launch {
                delay(3000L)
                refreshStats()
            }
        }
    }

    private var updateChecked = false

    /** Проверка обновления — один раз за запуск приложения, тихо в фоне. */
    fun checkUpdate(force: Boolean = false) {
        if (updateChecked && !force) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.IO) {
            val info = SnUpdateManager.check() ?: return@launch
            withContext(Dispatchers.Main) { _uiState.update { it.copy(update = info) } }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(update = null, updateDialog = false) }
    }

    private var updateJob: Job? = null

    /** Открыть карточку обновления (по кнопке «Обновить» на баннере). */
    fun showUpdateDialog() {
        if (uiState.value.update == null) return
        _uiState.update { it.copy(updateDialog = true, updateError = false) }
    }

    /** «Не сейчас»: закрыть карточку, баннер остаётся. Скачивание, если шло, прерывается. */
    fun hideUpdateDialog() {
        updateJob?.cancel()
        updateJob = null
        _uiState.update { it.copy(updateDialog = false, updateDownloading = false, updateProgress = 0) }
    }

    /**
     * «Обновить»: скачать APK в кэш и открыть системный установщик. Контекст — только
     * applicationContext (скачивание переживает пересоздание экрана, установщик стартует с NEW_TASK).
     * Успех → карточка закрывается сама (дальше рулит Android). Ошибка → updateError, кнопка «в браузере».
     */
    fun downloadAndInstallUpdate(context: android.content.Context) {
        val info = uiState.value.update ?: return
        if (uiState.value.updateDownloading) return
        val appContext = context.applicationContext
        updateJob?.cancel()
        _uiState.update { it.copy(updateDownloading = true, updateProgress = 0, updateError = false) }
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            val apk = SnUpdateManager.download(appContext, info) { pct ->
                _uiState.update { it.copy(updateProgress = pct) }
            }
            val launched = apk != null && SnUpdateManager.install(appContext, apk)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    if (launched) it.copy(updateDownloading = false, updateDialog = false, updateProgress = 100)
                    else it.copy(updateDownloading = false, updateError = true)
                }
            }
        }
    }

    /** Живые цифры ЛК по токену (если токен есть). */
    fun refreshStats() {
        val token = SnAccountManager.getToken()
        _uiState.update {
            it.copy(hasToken = token != null, statsError = false, cachedDisplayName = SnAccountManager.getCachedDisplayName())
        }
        if (token == null) return
        statsJob?.cancel()
        _uiState.update { it.copy(statsLoading = true) }
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = SnAccountManager.fetch(token)
            if (stats != null) SnAccountManager.cacheDisplayName(stats.displayName)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    if (stats != null) it.copy(stats = stats, statsLoading = false, statsError = false, cachedDisplayName = stats.displayName ?: it.cachedDisplayName)
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
