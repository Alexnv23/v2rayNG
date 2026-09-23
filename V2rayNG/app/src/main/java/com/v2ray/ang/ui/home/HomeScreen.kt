package com.v2ray.ang.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SnBlack
import com.v2ray.ang.ui.compose.SnGold
import com.v2ray.ang.ui.compose.SnGoldDim
import com.v2ray.ang.ui.compose.SnCardBg
import com.v2ray.ang.ui.compose.SnCardBorder
import com.v2ray.ang.ui.compose.SnGreenOk
import kotlinx.coroutines.delay

/**
 * SuperNet: главный экран (оболочка). Портирован из owenclave HomeScreen.kt.
 * Вся логика — в HomeViewModel и MainViewModel; здесь только отрисовка и колбэки.
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    selectedGuid: String?,
    isRunning: Boolean,
    onToggleService: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleBackupChannel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenTelegram: (String) -> Unit,
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedGuid) { homeViewModel.onSelectionChanged(selectedGuid) }
    LaunchedEffect(isRunning) {
        homeViewModel.onRunningChanged(isRunning)
        homeViewModel.refreshStats()
        // Обновление проверяем при первом заходе и повторно, когда тоннель поднялся
        // (на белых списках без тоннеля до ЛК не достучаться).
        homeViewModel.checkUpdate(force = isRunning)
        // Сезонное оформление тянем с сервера тем же образом (тихо, в фоне).
        homeViewModel.checkTheme(force = isRunning)
    }

    val statusText = stringResource(if (isRunning) R.string.sn_status_connected else R.string.sn_status_disconnected)
    val statusColor = if (isRunning) SnGreenOk else MaterialTheme.colorScheme.onSurfaceVariant
    // Для «Запасного канала» (olcRTC) не показываем реальное имя из подписки — только бренд-метку.
    val displayName = if (state.selectedIsBackup) stringResource(R.string.sn_loc_backup_name) else state.selectedName
    val statusSub = if (isRunning) {
        (displayName.ifEmpty { "SuperNet" }) + " · " + stringResource(R.string.sn_status_protected)
    } else {
        stringResource(R.string.sn_status_tap_to_connect)
    }

    // SuperNet: сезонное оформление рисуется на уровне оболочки (MainActivity), под всеми вкладками.
    // Здесь только делаем фон Column прозрачным, чтобы сцена/анимация просвечивали, и вешаем тени
    // тексту (на светлом фоне без карточки иначе не читается).
    val decorActive = state.theme.enabled && state.decorEnabled && state.theme.hasBg
    val onBgShadow = if (decorActive) androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.9f),
            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
            blurRadius = 12f,
        )
    ) else androidx.compose.ui.text.TextStyle.Default
    // При активном оформлении обводим карточки тонкой золотой каёмкой (как круг подключения),
    // чтобы они читались поверх сцены; на обычном чёрном фоне — прежняя мягкая граница.
    val cardBorder = if (decorActive) SnGold.copy(alpha = 0.6f) else SnCardBorder
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (decorActive) Color.Transparent else MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 20.dp, bottom = 120.dp),
    ) {
        // ── Шапка ──
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, SnGold, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("SN", color = SnGold, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SuperNet",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    style = onBgShadow,
                )
                Text(
                    text = (state.stats?.displayName ?: state.cachedDisplayName)?.let { stringResource(R.string.sn_hello_name, it) }
                        ?: stringResource(R.string.sn_premium_access),
                    color = if (decorActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    style = onBgShadow,
                )
            }
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = SnCardBg,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_settings_24dp),
                        contentDescription = stringResource(R.string.title_settings),
                        tint = SnGold,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ── Баннер обновления (SuperNet 1.3.6) ──
        state.update?.let { upd ->
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x1AD9B95C),
                border = BorderStroke(1.dp, SnGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🆕", fontSize = 22.sp)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sn_update_available, upd.version),
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        Text(
                            stringResource(R.string.sn_update_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Surface(
                        onClick = { homeViewModel.showUpdateDialog() },
                        shape = RoundedCornerShape(12.dp), color = SnGold,
                    ) {
                        Text(
                            stringResource(R.string.sn_update_button),
                            color = Color(0xFF0A0908), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp,
                        modifier = Modifier.clip(CircleShape).clickable { homeViewModel.dismissUpdate() }.padding(6.dp),
                    )
                }
            }
        }

        // ── Карточка обновления (SuperNet 1.3.7): версия/размер/дата/что нового → скачать → установить ──
        val updInfo = state.update
        if (state.updateDialog && updInfo != null) {
            val ctx = LocalContext.current
            SnUpdateDialog(
                info = updInfo,
                downloading = state.updateDownloading,
                progress = state.updateProgress,
                error = state.updateError,
                onUpdate = { homeViewModel.downloadAndInstallUpdate(ctx) },
                onLater = { homeViewModel.hideUpdateDialog() },
                onOpenBrowser = {
                    homeViewModel.hideUpdateDialog()
                    onOpenUrl(updInfo.downloadUrl)
                },
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Кнопка-«сердце» ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HeartbeatButton(
                connected = isRunning,
                connecting = false,
                gold = SnGold,
                onClick = onToggleService,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            statusText,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            style = onBgShadow,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            statusSub,
            color = if (decorActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            style = onBgShadow,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // ── Таймер подключения ──
        if (isRunning && state.connectedAtMs > 0L) {
            var uptime by remember { mutableLongStateOf(0L) }
            LaunchedEffect(state.connectedAtMs) {
                while (true) {
                    uptime = (System.currentTimeMillis() - state.connectedAtMs) / 1000
                    delay(1000)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                fmtUptime(uptime),
                color = SnGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                style = onBgShadow,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Текущая локация ──
        Surface(
            onClick = onOpenLocations,
            shape = RoundedCornerShape(18.dp),
            color = SnCardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName.ifEmpty { stringResource(R.string.sn_choose_location) },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        stringResource(if (state.selectedIsBackup) R.string.sn_location_backup_hint else R.string.sn_location_tap_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                Icon(painterResource(R.drawable.ic_sn_chevron_right_24dp), contentDescription = null, tint = SnGold, modifier = Modifier.size(26.dp))
            }
        }

        // ── Цифры: дни + белый лимит (живые из ЛК по токену) ──
        state.stats?.let { a ->
            val whiteOk = a.hasWhiteLimit && a.whiteLimitGb > 0
            if (a.days != null || whiteOk) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (a.days != null) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.sn_stat_access),
                            big = a.days.toString(),
                            unit = stringResource(R.string.sn_stat_days),
                            borderColor = cardBorder,
                        )
                    }
                    if (whiteOk) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.sn_stat_white_limit),
                            big = fmtGb(a.whiteUsedGb),
                            unit = stringResource(R.string.sn_stat_of_gb, fmtGb(a.whiteLimitGb)),
                            progress = (a.whitePercent / 100.0).toFloat().coerceIn(0f, 1f),
                            borderColor = cardBorder,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Быстрые кнопки ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickButton("👤", stringResource(R.string.sn_quick_cabinet), Modifier.weight(1f), cardBorder) { onOpenUrl(URL_LK) }
            QuickButton("✈️", stringResource(R.string.sn_quick_telegram), Modifier.weight(1f), cardBorder) { onOpenTelegram(TG_CHANNEL) }
            QuickButton("❓", stringResource(R.string.sn_quick_faq), Modifier.weight(1f), cardBorder) { onOpenUrl(URL_FAQ) }
            QuickButton("💬", stringResource(R.string.sn_quick_support), Modifier.weight(1f), cardBorder) { onOpenTelegram(TG_SUPPORT) }
        }

        Spacer(Modifier.height(14.dp))

        // ── Запасной канал ──
        val backupActive = state.selectedIsBackup
        Surface(
            onClick = onToggleBackupChannel,
            shape = RoundedCornerShape(16.dp),
            color = SnCardBg,
            border = BorderStroke(1.dp, if (backupActive) SnGold else cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Канал активен — вместо чёрного фона течёт «матрица» (как на кнопке, но без пульса).
                if (backupActive) {
                    SnMatrixStrip(modifier = Modifier.matchParentSize())
                }
                Row(
                    modifier = Modifier.padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            when {
                                backupActive -> R.string.sn_backup_channel_active
                                state.backupAvailable -> R.string.sn_backup_channel
                                else -> R.string.sn_backup_channel_unavailable
                            }
                        ),
                        color = if (backupActive) SnGold else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    big: String,
    unit: String,
    progress: Float = -1f,
    borderColor: Color = SnCardBorder,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SnCardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(big, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Spacer(Modifier.size(6.dp))
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (progress >= 0f) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SnGold),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickButton(emoji: String, label: String, modifier: Modifier = Modifier, borderColor: Color = SnCardBorder, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SnCardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

private fun fmtGb(v: Double): String {
    if (v < 0) return "0"
    return if (v >= 10) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}

private fun fmtUptime(sec: Long): String {
    val s = if (sec < 0) 0L else sec
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, ss)
}

// ── Ссылки (боевые) ──
private const val URL_LK = "https://lk.supernet-tech.ru"
private const val URL_FAQ = "https://lk.supernet-tech.ru/?open=faq"
private const val TG_CHANNEL = "supernet_vpn_access"
private const val TG_SUPPORT = "SuperNetConnect_bot"

/**
 * SuperNet 1.3.7: карточка обновления в стиле магазина приложений —
 * версия, размер, дата, «что нового», кнопки «Обновить» / «Не сейчас».
 * Во время скачивания — прогресс; при ошибке — предложить скачать в браузере.
 */
@Composable
private fun SnUpdateDialog(
    info: com.v2ray.ang.handler.SnUpdateManager.Info,
    downloading: Boolean,
    progress: Int,
    error: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!downloading) onLater() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SnCardBg,
            border = BorderStroke(1.dp, SnGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🆕", fontSize = 26.sp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.sn_update_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // ── Факты: версия / размер / дата ──
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x14D9B95C),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        SnUpdateFactRow(stringResource(R.string.sn_update_fact_version), info.version)
                        info.sizeMb?.let {
                            Spacer(Modifier.height(6.dp))
                            SnUpdateFactRow(stringResource(R.string.sn_update_fact_size), stringResource(R.string.sn_update_size_mb, fmtMb(it)))
                        }
                        info.date?.let {
                            Spacer(Modifier.height(6.dp))
                            SnUpdateFactRow(stringResource(R.string.sn_update_fact_date), it)
                        }
                    }
                }

                // ── Что нового ──
                if (info.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.sn_update_whats_new),
                        color = SnGold, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    info.changelog.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = SnGold, fontSize = 14.sp)
                            Spacer(Modifier.size(8.dp))
                            Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.sn_update_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                )

                // ── Прогресс / ошибка ──
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    if (progress >= 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            color = SnGold,
                            trackColor = Color(0x33D9B95C),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.sn_update_downloading_pct, progress),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        )
                    } else {
                        LinearProgressIndicator(color = SnGold, trackColor = Color(0x33D9B95C), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.sn_update_downloading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        )
                    }
                } else if (error) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.sn_update_download_failed),
                        color = Color(0xFFF44336), fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ── Кнопки ──
                if (error) {
                    Surface(
                        onClick = onOpenBrowser,
                        shape = RoundedCornerShape(14.dp), color = SnGold,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.sn_update_open_browser),
                            color = SnBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 13.dp),
                        )
                    }
                } else {
                    Surface(
                        onClick = { if (!downloading) onUpdate() },
                        shape = RoundedCornerShape(14.dp),
                        color = if (downloading) SnGoldDim else SnGold,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.sn_update_button),
                            color = SnBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 13.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { if (!downloading) onLater() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, SnCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.sn_update_later),
                        color = if (downloading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SnUpdateFactRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun fmtMb(v: Double): String =
    if (v >= 100) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
