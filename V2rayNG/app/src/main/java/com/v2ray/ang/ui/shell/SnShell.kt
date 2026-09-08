package com.v2ray.ang.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SnBlack
import com.v2ray.ang.ui.compose.SnGold

/**
 * SuperNet: оболочка (портирована из owenclave ComposeMainActivity).
 * Вкладки нижней панели + экраны без вкладки (Настройки, Устройства, Расширенный).
 */
enum class SnTab(val labelRes: Int, val iconRes: Int, val inBar: Boolean) {
    HOME(R.string.sn_tab_home, R.drawable.ic_sn_home_24dp, true),
    LOCATIONS(R.string.sn_tab_locations, R.drawable.ic_description_24dp, true),
    FRIENDS(R.string.sn_tab_friends, R.drawable.ic_sn_group_24dp, true),
    SETTINGS(R.string.title_settings, R.drawable.ic_settings_24dp, false),
    DEVICES(R.string.sn_devices_title, R.drawable.ic_per_apps_24dp, false),
    ADVANCED(R.string.sn_settings_advanced, R.drawable.ic_settings_24dp, false),
}

/** Нижняя панель: статус-пульс | вкладки | кнопка питания. */
@Composable
fun SnBottomBar(
    selected: SnTab,
    onSelect: (SnTab) -> Unit,
    connected: Boolean,
    testProgress: String?,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testing = testProgress != null
    val containerColor by animateColorAsState(
        targetValue = if (testing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "snBarContainer",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(32.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(start = 10.dp, end = 8.dp), contentAlignment = Alignment.Center) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = testProgress.orEmpty(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                } else {
                    StatusPulse(connected = connected)
                }
            }

            BarDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                SnTab.entries.filter { it.inBar }.forEach { tab ->
                    val isSelected = tab == selected
                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "snNavItem",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "snNavIcon",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "snNavScale",
                    )
                    Surface(
                        onClick = { onSelect(tab) },
                        shape = RoundedCornerShape(if (isSelected) 16.dp else 24.dp),
                        color = itemColor,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.labelRes),
                                tint = iconColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            BarDivider()

            PowerButton(connected = connected, onClick = onPowerClick, modifier = Modifier.padding(start = 4.dp, end = 6.dp))
        }
    }
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 32.dp)
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
    )
}

@Composable
private fun PowerButton(connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (connected) SnGold else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (connected) SnBlack else MaterialTheme.colorScheme.onPrimaryContainer
    val size by animateFloatAsState(
        targetValue = if (connected) 52f else 44f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "snPowerSize",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(24.dp),
        color = color,
        tonalElevation = if (connected) 6.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (connected) R.drawable.ic_sn_bolt_24dp else R.drawable.ic_sn_power_24dp),
                contentDescription = stringResource(if (connected) R.string.sn_disconnect else R.string.sn_connect),
                tint = contentColor,
                modifier = Modifier.size(if (connected) 26.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun StatusPulse(connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "snPulse")
    val scale by transition.animateFloat(
        initialValue = if (connected) 0.7f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (connected) 900 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "snPulseScale",
    )
    val color = if (connected) SnGold else MaterialTheme.colorScheme.outlineVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (connected) 1f - (scale - 0.7f) / 0.3f * 0.6f else 0.4f
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Общие карточки в стиле бренда (используются экранами оболочки). */
@Composable
fun SnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = com.v2ray.ang.ui.compose.SnCardBorder,
    containerColor: Color = com.v2ray.ang.ui.compose.SnCardBg,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = modifier,
        ) { content() }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = modifier,
        ) { content() }
    }
}
