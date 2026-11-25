package com.v2ray.ang.ui.compose

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

internal sealed interface SeasonalArtwork {
    data class Still(val bitmap: ImageBitmap) : SeasonalArtwork
    data class Native(val drawable: Drawable) : SeasonalArtwork
    data class Legacy(val file: File, val mime: String) : SeasonalArtwork
}

internal fun animatedThemeMime(header: ByteArray): String? {
    val signature = header.toString(Charsets.ISO_8859_1)
    if (signature.startsWith("GIF89a") || signature.startsWith("GIF87a")) return "image/gif"
    // RIFF size occupies bytes 4..7. The VP8X animation feature is bit 1.
    return if (header.size >= 21 && signature.startsWith("RIFF") &&
        signature.startsWith("WEBPVP8X", 8) && (header[20].toInt() and 2) != 0
    ) "image/webp" else null
}

internal fun loadSeasonalArtwork(path: String): SeasonalArtwork? {
    val file = File(path)
    return try {
        val header = ByteArray(21)
        val size = file.inputStream().use { it.read(header) }
        val mime = animatedThemeMime(header.copyOf(size.coerceAtLeast(0)))
        when {
            mime == null -> BitmapFactory.decodeFile(path)?.let { SeasonalArtwork.Still(it.asImageBitmap()) }
            Build.VERSION.SDK_INT < 28 -> SeasonalArtwork.Legacy(file, mime)
            else -> when (val image = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))) {
                is BitmapDrawable -> SeasonalArtwork.Still(image.bitmap.asImageBitmap())
                else -> SeasonalArtwork.Native(image)
            }
        }
    } catch (e: IOException) {
        LogUtil.w(AppConfig.TAG, "Seasonal background could not be decoded: ${e.javaClass.simpleName}")
        null
    }
}

@Composable
internal fun LiveSeasonalBackground(artwork: SeasonalArtwork) {
    val owner = LocalLifecycleOwner.current
    when (artwork) {
        is SeasonalArtwork.Native -> {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setImageDrawable(artwork.drawable)
                    }
                },
            )
            DisposableEffect(artwork, owner) {
                val animation = artwork.drawable as? Animatable
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) { animation?.start() }
                    override fun onPause(owner: LifecycleOwner) { animation?.stop() }
                }
                owner.lifecycle.addObserver(observer)
                onDispose {
                    owner.lifecycle.removeObserver(observer)
                    animation?.stop()
                }
            }
        }
        is SeasonalArtwork.Legacy -> key(owner, artwork) {
            // Android 7/8 lack ImageDecoder. Remove this local-only image viewer when minSdk reaches 28.
            val observer = remember { LegacyThemeObserver(artwork) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        webViewClient = observer.client
                        observer.view = this
                        owner.lifecycle.addObserver(observer)
                    }
                },
                onRelease = { view ->
                    owner.lifecycle.removeObserver(observer)
                    observer.view = null
                    view.destroy()
                },
                update = {},
            )
        }
        is SeasonalArtwork.Still -> Unit
    }
}

private class LegacyThemeObserver(private val artwork: SeasonalArtwork.Legacy) : DefaultLifecycleObserver {
    var view: WebView? = null
    private val origin = "https://appassets.androidplatform.net/"
    val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            if (request.url.toString() == origin + "frame") {
                try {
                    return WebResourceResponse(artwork.mime, null, artwork.file.inputStream())
                } catch (e: IOException) {
                    LogUtil.w(AppConfig.TAG, "Seasonal background unavailable: ${e.javaClass.simpleName}")
                }
            }
            return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        val html = """<meta name="viewport" content="width=device-width,initial-scale=1">
            <style>*{margin:0}img{width:100vw;height:100vh;object-fit:cover;display:block}</style><img src="frame">"""
        view?.loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
    }

    override fun onPause(owner: LifecycleOwner) {
        view?.loadUrl("about:blank")
    }
}
