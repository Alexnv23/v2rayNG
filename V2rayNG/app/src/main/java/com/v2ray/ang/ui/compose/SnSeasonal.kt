package com.v2ray.ang.ui.compose

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.v2ray.ang.R
import com.v2ray.ang.handler.SnThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * SuperNet: сезонный фон Главной (управляется с сервера, см. SnThemeManager).
 * Слои: фон-фото (из кэша) → затемнитель → анимация. Кладётся СЗАДИ контента Главной,
 * поэтому анимацию гоним только в открытых просветах (вверху вокруг круга / по бокам),
 * не под карточками. enabled=false / decorOn=false / нет фона → не рисуем ничего.
 *
 * Типы анимации: LEAVES (падающий кленовый лист), LIGHT (пятна света как от веток),
 * LAKE (дорожка от солнца по воде + капли). NONE → только статичный фон.
 */
@Composable
fun SnSeasonalBackground(
    enabled: Boolean,
    anim: SnThemeManager.Anim,
    bgPath: String?,
    modifier: Modifier = Modifier,
) {
    if (!enabled || bgPath.isNullOrEmpty()) return

    // Декод фона — вне главного потока.
    val bg by produceState<ImageBitmap?>(initialValue = null, bgPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(bgPath)?.asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = bg ?: return

    val ctx = LocalContext.current
    val leaf: ImageBitmap? = if (anim == SnThemeManager.Anim.LEAVES) {
        remember {
            runCatching {
                android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.sn_leaf)?.asImageBitmap()
            }.getOrNull()
        }
    } else null

    // Тик кадра: гоним ~60 fps, Canvas перерисовывается на каждое изменение tick.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(anim) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            tick++
        }
    }

    val leaves = remember { ArrayList<Leaf>() }
    val ripples = remember { ArrayList<Ripple>() }
    val spots = remember { ArrayList<Spot>() }
    val seeded = remember { booleanArrayOf(false) }

    Box(modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Затемнитель — чтобы карточки и текст читались.
        Box(Modifier.fillMaxSize().background(scrimBrush))
        if (anim != SnThemeManager.Anim.NONE) {
            Canvas(Modifier.fillMaxSize()) {
                val t = tick // читаем — привязка к перерисовке
                val w = size.width
                val h = size.height
                if (w < 1f || h < 1f) return@Canvas
                if (!seeded[0]) {
                    seed(anim, w, h, leaves, spots)
                    seeded[0] = true
                }
                when (anim) {
                    SnThemeManager.Anim.LEAVES -> drawLeaves(t, w, h, leaf, leaves)
                    SnThemeManager.Anim.LIGHT -> drawLight(t, w, h, spots)
                    SnThemeManager.Anim.LAKE -> drawLake(t, w, h, ripples)
                    else -> {}
                }
            }
        }
    }
}

// ── затемнитель ──
private val scrimBrush: Brush
    get() = Brush.verticalGradient(
        0.0f to Color(0x8C08060A.toInt()),
        0.22f to Color(0x1408060A.toInt()),
        0.55f to Color(0x2608060A.toInt()),
        1.0f to Color(0x8008060A.toInt()),
    )

// ── частицы ──
private class Leaf(
    var x: Float, var y: Float, val sc: Float, var rot: Float, val vr: Float,
    val vy: Float, val amp: Float, var ph: Float, val sw: Float, val op: Float,
)

private class Ripple(val x: Float, val y: Float, var r: Float, var life: Int)

private class Spot(
    var x: Float, var y: Float, val r: Float, var ph: Float, val sp: Float, val dx: Float, val dy: Float,
)

private fun newLeaf(w: Float, h: Float, atTop: Boolean): Leaf {
    val sc = 0.24f + Random.nextFloat() * 0.30f
    return Leaf(
        x = Random.nextFloat() * w,
        y = if (atTop) -70f else Random.nextFloat() * h,
        sc = sc,
        rot = Random.nextFloat() * 6.28f,
        vr = (Random.nextFloat() - 0.5f) * 0.03f,
        vy = 0.35f + Random.nextFloat() * 0.55f,
        amp = 14f + Random.nextFloat() * 28f,
        ph = Random.nextFloat() * 6.28f,
        sw = 0.006f + Random.nextFloat() * 0.009f,
        op = 0.82f + Random.nextFloat() * 0.16f,
    )
}

private fun seed(anim: SnThemeManager.Anim, w: Float, h: Float, leaves: ArrayList<Leaf>, spots: ArrayList<Spot>) {
    when (anim) {
        SnThemeManager.Anim.LEAVES -> {
            leaves.clear()
            repeat(9) { leaves.add(newLeaf(w, h, false)) }
        }
        SnThemeManager.Anim.LIGHT -> {
            spots.clear()
            val z = arrayOf(
                0.16f to 0.16f, 0.84f to 0.14f, 0.12f to 0.34f, 0.88f to 0.36f,
                0.30f to 0.10f, 0.70f to 0.09f, 0.90f to 0.24f, 0.10f to 0.22f,
            )
            for (q in z) spots.add(
                Spot(
                    x = q.first * w, y = q.second * h, r = 28f + Random.nextFloat() * 40f,
                    ph = Random.nextFloat() * 6.28f, sp = 0.018f + Random.nextFloat() * 0.03f,
                    dx = (Random.nextFloat() - 0.5f) * 0.5f, dy = (Random.nextFloat() - 0.5f) * 0.25f,
                )
            )
        }
        else -> {}
    }
}

// ── LEAVES: падающий кленовый лист (реальный спрайт) ──
private fun DrawScope.drawLeaves(t: Long, w: Float, h: Float, leaf: ImageBitmap?, leaves: ArrayList<Leaf>) {
    leaf ?: return
    val iw = leaf.width
    val ih = leaf.height
    for (l in leaves) {
        l.y += l.vy
        l.ph += l.sw
        l.rot += l.vr
        val x = l.x + sin(l.ph) * l.amp
        val dw = iw * l.sc
        val dh = ih * l.sc
        withTransform({
            translate(x, l.y)
            rotate(l.rot * 57.2958f, pivot = Offset.Zero)
        }) {
            drawImage(
                image = leaf,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(iw, ih),
                dstOffset = IntOffset((-dw / 2f).toInt(), (-dh / 2f).toInt()),
                dstSize = IntSize(dw.toInt(), dh.toInt()),
                alpha = l.op,
            )
        }
        if (l.y > h + 70f) {
            val nl = newLeaf(w, h, true)
            l.x = nl.x; l.y = nl.y; l.rot = nl.rot; l.ph = nl.ph
        }
    }
}

// ── LIGHT: пятна света «как от качающихся веток» + мягкие лучи ──
private fun DrawScope.drawLight(t: Long, w: Float, h: Float, spots: ArrayList<Spot>) {
    val ft = t.toFloat()
    val p = 0.5f + 0.5f * sin(ft * 0.035f)
    // лучи из верхне-правого угла
    val sx = w * 0.80f
    val sy = -h * 0.06f
    for (i in 0 until 4) {
        withTransform({
            translate(sx, sy)
            rotate((-0.05f + i * 0.12f + sin(ft * 0.025f + i) * 0.03f) * 57.2958f, pivot = Offset.Zero)
        }) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(-26f, h * 0.62f); lineTo(54f, h * 0.62f); close()
            }
            drawPath(path, Color(0xFFFFE496).copy(alpha = 0.05f + p * 0.05f), blendMode = BlendMode.Plus)
        }
    }
    for (s in spots) {
        s.ph += s.sp
        s.x += s.dx * sin(s.ph * 0.5f)
        s.y += s.dy * sin(s.ph * 0.7f)
        val a = 0.09f + 0.15f * (0.5f + 0.5f * sin(s.ph))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE498).copy(alpha = a), Color(0x00FFD278)),
                center = Offset(s.x, s.y),
                radius = s.r,
            ),
            radius = s.r,
            center = Offset(s.x, s.y),
            blendMode = BlendMode.Plus,
        )
    }
}

// ── LAKE: дорожка от солнца по воде + капли (строго в полосе воды) ──
private fun DrawScope.drawLake(t: Long, w: Float, h: Float, ripples: ArrayList<Ripple>) {
    val ft = t.toFloat()
    val wy0 = h * 0.40f
    val wy1 = h * 0.52f
    val sx = w * 0.80f
    // дорожка от солнца — узкая, справа, мягкое мерцание
    var y = wy0
    while (y < wy1) {
        val depth = (y - wy0) / (wy1 - wy0)
        val tw = 0.5f + 0.5f * sin(ft * 0.045f + y * 0.14f)
        val ww = 6f + depth * 30f
        val a = (0.14f + 0.26f * tw) * (0.55f + depth * 0.5f)
        drawRect(
            brush = Brush.horizontalGradient(
                0.0f to Color(0x00FFC46E),
                0.5f to Color(0xFFFFDC96).copy(alpha = a),
                1.0f to Color(0x00FFC46E),
                startX = sx - ww,
                endX = sx + ww,
            ),
            topLeft = Offset(0f, y),
            size = Size(w, 2.2f),
            blendMode = BlendMode.Plus,
        )
        if (Random.nextFloat() < 0.17f) {
            val bx = sx + (Random.nextFloat() - 0.5f) * ww * 1.5f
            drawRect(
                color = Color(0xFFFFF5C8).copy(alpha = a * 1.3f),
                topLeft = Offset(bx, y),
                size = Size(3f + Random.nextFloat() * 4f, 1.6f),
                blendMode = BlendMode.Plus,
            )
        }
        y += 6f
    }
    // капли — только в полосе воды, слева-центр (мимо мостика справа)
    if (t % 42L == 0L) {
        val rx = w * (0.08f + Random.nextFloat() * 0.58f)
        ripples.add(Ripple(rx, wy0 + Random.nextFloat() * (wy1 - wy0), 2f, 0))
    }
    val it = ripples.iterator()
    while (it.hasNext()) {
        val r = it.next()
        r.r += 0.42f
        r.life++
        val a = (0.34f - r.life * 0.005f).coerceAtLeast(0f)
        if (a > 0f) {
            drawOval(
                color = Color(0xFFFFE2AA).copy(alpha = a),
                topLeft = Offset(r.x - r.r, r.y - r.r * 0.32f),
                size = Size(r.r * 2f, r.r * 0.64f),
                style = Stroke(width = 1.1f),
            )
        }
        if (r.life >= 70) it.remove()
    }
}
