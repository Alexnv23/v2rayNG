package com.v2ray.ang.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.SnBlack
import com.v2ray.ang.ui.compose.SnCardBg
import com.v2ray.ang.ui.compose.SnCardBorder
import com.v2ray.ang.ui.compose.SnGold
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel
import kotlin.math.abs

/**
 * SuperNet «Локации» (портировано из owenclave ConfigurationScreen + ProfileCard).
 * Список всех профилей из всех групп; тап = выбрать. Адреса/UUID не показываем, меню правки нет.
 * Пинг: зелёная точка — работает, красная — нет, пусто — не проверялось.
 */
@Composable
fun LocationsScreen(
    mainViewModel: MainViewModel,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val selectedGuid = uiState.selectedGuid

    // Все сервера всех групп (каждая группа подписана по своему id). Если включена
    // виртуальная группа «Все» (id = ""), берём только её — иначе профили задвоятся.
    val sourceGroups = if (groups.any { it.id.isEmpty() }) groups.filter { it.id.isEmpty() } else groups
    val servers = ArrayList<ServersCache>()
    sourceGroups.forEach { g ->
        key(g.id) {
            val list by mainViewModel.serversForGroup(g.id).collectAsStateWithLifecycle()
            servers.addAll(list)
        }
    }

    val ordered = servers.distinctBy { it.guid }.sortedWith(
        compareBy<ServersCache> { it.profile.configType == EConfigType.OLCRTC }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ── Заголовок + действия (как в owenclave: тест, обновить, вставить, +) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sn_tab_locations),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isTesting) {
                IconButton(onClick = { onAction(MainAction.CancelTesting) }) {
                    Icon(painterResource(R.drawable.ic_stop_24dp), contentDescription = stringResource(R.string.sn_loc_cancel_test), tint = SnGold)
                }
            } else {
                IconButton(onClick = { onAction(MainAction.SnTestAllLocations) }) {
                    Icon(painterResource(R.drawable.ic_sn_speed_24dp), contentDescription = stringResource(R.string.sn_loc_test_all), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = { onAction(MainAction.UpdateSubscriptions) }) {
                Icon(painterResource(R.drawable.ic_sn_refresh_24dp), contentDescription = stringResource(R.string.sn_loc_refresh_sub), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { onAction(MainAction.ImportClipboard) }) {
                Icon(painterResource(R.drawable.ic_sn_paste_24dp), contentDescription = stringResource(R.string.sn_loc_import_clipboard), tint = MaterialTheme.colorScheme.onSurface)
            }
            Surface(
                onClick = { onAction(MainAction.ImportClipboard) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.sn_loc_add), tint = SnGold, modifier = Modifier.size(22.dp))
                }
            }
        }

        if (ordered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null, tint = SnGold, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.sn_loc_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            ) {
                items(ordered, key = { it.guid }) { server ->
                    LocationCard(
                        server = server,
                        selected = server.guid == selectedGuid,
                        connected = server.guid == selectedGuid && isRunning,
                        onClick = { onAction(MainAction.SelectServer(server.guid)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    server: ServersCache,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else SnCardBg,
        label = "snLocCard",
    )
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val iconContainer = if (selected) SnGold else MaterialTheme.colorScheme.secondaryContainer
    val iconContent = if (selected) SnBlack else SnGold
    val name = server.profile.remarks
    val isBackup = server.profile.configType == EConfigType.OLCRTC
    // Для «Запасного канала» скрываем реальное имя (напр. «Париж») — показываем только бренд-метку.
    val displayName = if (isBackup) stringResource(R.string.sn_loc_backup_name) else name
    val typeLabel = if (isBackup) stringResource(R.string.sn_loc_backup_sub) else server.profile.configType.name

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = if (selected) BorderStroke(1.dp, SnGold.copy(alpha = 0.6f)) else BorderStroke(1.dp, SnCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(shapeRadiusFor(name)))
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconFor(name, isBackup)),
                    contentDescription = null,
                    tint = iconContent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = onContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(typeLabel, color = onContainer.copy(alpha = 0.6f), fontSize = 12.sp)
                    if (connected) {
                        Text("·", color = onContainer.copy(alpha = 0.4f), fontSize = 12.sp)
                        Text(stringResource(R.string.sn_status_connected).lowercase(), color = onContainer.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    val delay = server.testDelayMillis
                    when {
                        delay > 0L -> Text("●", color = Color(0xFF4CAF50), fontSize = 16.sp)
                        delay < 0L -> Text("●", color = Color(0xFFF44336), fontSize = 16.sp)
                    }
                    if (delay > 0L) {
                        Text("${delay} ms", color = onContainer.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private val LOCATION_ICONS = intArrayOf(
    R.drawable.ic_sn_plane_24dp,
    R.drawable.ic_sn_cloud_24dp,
    R.drawable.ic_sn_bolt_24dp,
    R.drawable.ic_sn_shield_24dp,
    R.drawable.ic_lock_24dp,
    R.drawable.ic_sn_key_24dp,
    R.drawable.ic_sn_wifi_24dp,
)

private fun iconFor(name: String, isBackup: Boolean): Int {
    if (isBackup) return R.drawable.ic_sn_shield_24dp
    val lower = name.lowercase()
    return when {
        "wifi" in lower || "wi-fi" in lower -> R.drawable.ic_sn_wifi_24dp
        "мессендж" in lower || "telegram" in lower -> R.drawable.ic_sn_plane_24dp
        "безлимит" in lower -> R.drawable.ic_sn_bolt_24dp
        else -> LOCATION_ICONS[abs(name.hashCode()) % LOCATION_ICONS.size]
    }
}

private fun shapeRadiusFor(name: String) = (14 + abs(name.hashCode()) % 12).dp
