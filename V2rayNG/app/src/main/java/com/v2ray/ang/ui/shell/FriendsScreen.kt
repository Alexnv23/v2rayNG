package com.v2ray.ang.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SnBlack
import com.v2ray.ang.ui.compose.SnCardBg
import com.v2ray.ang.ui.compose.SnCardBorder
import com.v2ray.ang.ui.compose.SnGold
import com.v2ray.ang.ui.home.HomeViewModel

private const val URL_LK_REF = "https://lk.supernet-tech.ru/?open=referral"
private const val FREE_MONTH_AT = 4 // оплативших друзей → накопил балансом на месяц (~30% с каждого)

private data class Tier(val n: String, val labelRes: Int, val rewardRes: Int)

// Реальные бонусы (bot.py): 30% на баланс с каждого + разовые за 1-го и 10-го.
private val TIERS = listOf(
    Tier("%", R.string.sn_friends_tier_each, R.string.sn_friends_tier_each_reward),
    Tier("1", R.string.sn_friends_tier_first, R.string.sn_friends_tier_first_reward),
    Tier("10", R.string.sn_friends_tier_tenth, R.string.sn_friends_tier_tenth_reward),
)

/** SuperNet «Друзья» (портировано из owenclave FriendsScreen). */
@Composable
fun FriendsScreen(
    homeViewModel: HomeViewModel,
    onOpenUrl: (String) -> Unit,
    onShareText: (String) -> Unit,
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { homeViewModel.refreshStats() }
    val a = state.stats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 120.dp),
    ) {
        Text(
            stringResource(R.string.sn_tab_friends),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            fontFamily = FontFamily.Serif,
        )
        Spacer(Modifier.height(18.dp))

        if (a != null) {
            val paid = a.friendsPaid
            val frac = (paid.toFloat() / FREE_MONTH_AT).coerceIn(0f, 1f)
            val left = (FREE_MONTH_AT - paid).coerceAtLeast(0)
            SnCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁", fontSize = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (left == 0) R.string.sn_friends_free_open else R.string.sn_friends_free_next),
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (left == 0) stringResource(R.string.sn_friends_free_done)
                        else pluralStringResource(R.plurals.sn_friends_bring_more, left, left),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x33FFFFFF)),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SnGold))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.sn_friends_invited_paid, a.friendsInvited, paid),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        )
                        Text(
                            stringResource(R.string.sn_friends_need, FREE_MONTH_AT),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val link = a.refLink
            if (!link.isNullOrBlank()) {
                val shareText = stringResource(R.string.sn_friends_share_text, link)
                SnCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.sn_friends_your_link), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(link, color = SnGold, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            onClick = { onShareText(shareText) },
                            shape = RoundedCornerShape(14.dp), color = SnGold, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.sn_friends_share), color = SnBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            SnCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenUrl(URL_LK_REF) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💰  ", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.sn_friends_balance), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("${a.balance} ₽", color = SnGold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Text("›", color = SnGold, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            SnCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁", fontSize = 34.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.sn_friends_hook_title),
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (state.hasToken) R.string.sn_friends_hook_text else R.string.sn_friends_no_token),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SnCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                TIERS.forEachIndexed { i, t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0x22D9B95C)).border(1.dp, SnGold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t.n, color = SnGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.size(14.dp))
                        Text(stringResource(t.labelRes), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(stringResource(t.rewardRes), color = SnGold, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    if (i < TIERS.size - 1) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0x14FFFFFF)))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            onClick = { onOpenUrl(URL_LK_REF) },
            shape = RoundedCornerShape(16.dp), color = Color.Transparent,
            border = BorderStroke(1.dp, SnCardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sn_friends_all_in_lk), color = SnGold, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}
