package com.v2ray.ang.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SnBlack
import com.v2ray.ang.ui.compose.SnGold

private val SnDanger = Color(0xFFE5484D)

/** SuperNet «Настройки» (портировано из owenclave AppSettingsScreen; всё лишнее из v2rayNG спрятано в «Расширенные»). */
@Composable
fun SnSettingsScreen(
    bypassLan: Boolean,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onDeleteSubscription: () -> Unit,
    onOpenPerApp: () -> Unit,
    onBypassLanChange: (Boolean) -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenAdvancedProfiles: () -> Unit,
) {
    var confirmDeleteSub by remember { mutableStateOf(false) }

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
                stringResource(R.string.title_settings),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.sn_settings_section_account))

        SettingRow(stringResource(R.string.sn_devices_title), stringResource(R.string.sn_settings_devices_sub), onOpenDevices)
        Spacer(Modifier.height(10.dp))
        SettingRow(
            stringResource(if (isRefreshing) R.string.sn_settings_refreshing_sub else R.string.sn_settings_refresh_sub),
            stringResource(R.string.sn_settings_refresh_sub_sub),
            onClick = { if (!isRefreshing) onRefreshSubscription() },
        )
        Spacer(Modifier.height(10.dp))
        DangerRow(stringResource(R.string.sn_settings_delete_sub), stringResource(R.string.sn_settings_delete_sub_sub)) { confirmDeleteSub = true }

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.sn_settings_section_connection))

        SettingRow(stringResource(R.string.sn_settings_per_app), stringResource(R.string.sn_settings_per_app_sub), onOpenPerApp)
        Spacer(Modifier.height(10.dp))
        SwitchRow(stringResource(R.string.sn_settings_bypass_lan), stringResource(R.string.sn_settings_bypass_lan_sub), bypassLan, onBypassLanChange)

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.sn_settings_section_more))

        SettingRow(stringResource(R.string.title_logcat), stringResource(R.string.sn_settings_logcat_sub), onOpenLogcat)
        Spacer(Modifier.height(10.dp))
        SettingRow(stringResource(R.string.sn_settings_advanced), stringResource(R.string.sn_settings_advanced_sub), onOpenAdvancedSettings)
        Spacer(Modifier.height(10.dp))
        SettingRow(stringResource(R.string.sn_settings_advanced_profiles), stringResource(R.string.sn_settings_advanced_profiles_sub), onOpenAdvancedProfiles)

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.sn_settings_section_about))

        SnCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SuperNet", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(stringResource(R.string.sn_premium_access), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Text("v${BuildConfig.VERSION_NAME}", color = SnGold, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (confirmDeleteSub) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSub = false },
            title = { Text(stringResource(R.string.sn_settings_delete_sub_confirm_title)) },
            text = { Text(stringResource(R.string.sn_settings_delete_sub_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteSub = false; onDeleteSubscription() }) {
                    Text(stringResource(R.string.sn_delete), color = SnDanger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSub = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = SnGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    SnCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DangerRow(title: String, subtitle: String, onClick: () -> Unit) {
    SnCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, borderColor = SnDanger.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = SnDanger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SnCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = SnBlack, checkedTrackColor = SnGold),
            )
        }
    }
}
