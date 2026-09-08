package com.v2ray.ang.ui.shell

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.SnAccountManager
import com.v2ray.ang.ui.compose.SnGold
import com.v2ray.ang.ui.home.HomeViewModel

private val SnDanger = Color(0xFFE5484D)

/**
 * SuperNet «Мои устройства» (портировано из owenclave DevicesScreen).
 * Данные и удаление — через HomeViewModel → SnAccountManager (ЛК).
 */
@Composable
fun DevicesScreen(homeViewModel: HomeViewModel, onBack: () -> Unit) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { homeViewModel.refreshStats() }

    val devices = state.stats?.devices.orEmpty()
    val limit = state.stats?.devicesLimit ?: 2
    val loading = state.statsLoading && state.stats == null
    val busy = state.devicesBusy
    var confirmOne by remember { mutableStateOf<SnAccountManager.Device?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 120.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.sn_devices_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { homeViewModel.refreshStats() }, enabled = !loading && !busy) {
                Icon(painterResource(R.drawable.ic_sn_refresh_24dp), contentDescription = stringResource(R.string.sn_devices_refresh), tint = SnGold)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (loading) stringResource(R.string.sn_devices_loading) else stringResource(R.string.sn_devices_connected, devices.size, limit),
            color = SnGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        when {
            !state.hasToken -> InfoCard(stringResource(R.string.sn_devices_no_token_title), stringResource(R.string.sn_devices_no_token_text))
            loading -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SnGold)
            }
            state.statsError && state.stats == null -> InfoCard(stringResource(R.string.sn_devices_error_title), stringResource(R.string.sn_devices_error_text))
            devices.isEmpty() -> InfoCard(stringResource(R.string.sn_devices_empty_title), stringResource(R.string.sn_devices_empty_text))
            else -> {
                devices.forEach { dev ->
                    DeviceCard(dev, enabled = !busy) { confirmOne = dev }
                    Spacer(Modifier.height(10.dp))
                }
                if (devices.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    SnCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (!busy) confirmAll = true },
                        borderColor = SnDanger.copy(alpha = 0.5f),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = null, tint = SnDanger)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(R.string.sn_devices_delete_all), color = SnDanger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.sn_devices_delete_all_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.sn_devices_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    confirmOne?.let { dev ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text(stringResource(R.string.sn_devices_confirm_one_title)) },
            text = { Text(stringResource(R.string.sn_devices_confirm_one_text, dev.name)) },
            confirmButton = {
                TextButton(onClick = { confirmOne = null; homeViewModel.revokeDevice(dev.id) }) {
                    Text(stringResource(R.string.sn_delete), color = SnDanger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.sn_devices_confirm_all_title)) },
            text = { Text(stringResource(R.string.sn_devices_confirm_all_text, devices.size)) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; homeViewModel.revokeAllDevices() }) {
                    Text(stringResource(R.string.sn_devices_delete_all), color = SnDanger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeviceCard(dev: SnAccountManager.Device, enabled: Boolean, onDelete: () -> Unit) {
    SnCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📱", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dev.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val seen = formatSeen(dev.lastSeen)
                val sub = buildString {
                    if (dev.os.isNotBlank()) append(dev.os)
                    if (seen.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(seen)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        if (seen.isNotBlank()) stringResource(R.string.sn_devices_last_seen, sub) else sub,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.sn_delete), tint = SnDanger)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    SnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** "2026-09-04 12:34:56" -> "04.09 12:34" (сервер отдаёт строку без зоны; показываем как есть). */
private fun formatSeen(s: String?): String {
    if (s.isNullOrBlank()) return ""
    return try {
        val datePart = s.substring(0, 10)
        val timePart = if (s.length >= 16) s.substring(11, 16) else ""
        val (y, m, d) = datePart.split("-")
        if (y.length == 4) "$d.$m${if (timePart.isNotBlank()) " $timePart" else ""}" else s
    } catch (_: Exception) {
        s
    }
}
