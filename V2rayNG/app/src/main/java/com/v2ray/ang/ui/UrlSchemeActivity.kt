package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SnAccountManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Deeplink-вход. SuperNet: ЛК шлёт `supernet://subscription?url=<sub>&token=<app-token>` —
 * импортируем подписку, включаем ей авто-обновление раз в сутки и сохраняем токен кабинета.
 * Родные `install-config` / `install-sub` v2rayNG сохранены.
 */
class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            var handled = false
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            parseUri(it, null)
                            handled = true
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    val uri: Uri? = intent.data
                    when (data?.host) {
                        "install-config", "install-sub" -> {
                            parseUri(uri?.getQueryParameter("url").orEmpty(), uri?.fragment)
                            handled = true
                        }

                        "subscription" -> {
                            uri?.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let { SnAccountManager.saveToken(it) }
                            parseUri(uri?.getQueryParameter("url").orEmpty(), uri?.fragment)
                            handled = true
                        }

                        else -> toastError(R.string.toast_failure)
                    }
                }
            }
            if (!handled) openMainAndFinish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            openMainAndFinish()
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            openMainAndFinish()
            return
        }

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri == null) {
            openMainAndFinish()
            return
        }
        if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
            if (countSub > 0) enableDailyAutoUpdate(decodedUrl)
            withContext(Dispatchers.Main) {
                if (count + countSub > 0) {
                    toast(R.string.import_subscription_success)
                } else {
                    toast(R.string.import_subscription_failure)
                }
                openMainAndFinish()
            }
        }
    }

    /** SuperNet-дефолт: подписка из ЛК обновляется сама раз в сутки. */
    private fun enableDailyAutoUpdate(url: String) {
        try {
            val cleanUrl = url.substringBefore('#')
            MmkvManager.decodeSubscriptions().forEach { cache ->
                val id = cache.guid
                val sub = cache.subscription
                if (sub.url == cleanUrl || sub.url == url) {
                    if (!sub.autoUpdate) {
                        sub.autoUpdate = true
                        sub.updateInterval = 1440
                        MmkvManager.encodeSubscription(id, sub)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "UrlScheme: enable auto-update failed", e)
        }
    }
}
