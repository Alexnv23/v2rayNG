package com.v2ray.ang.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

// Форма ЭКГ (x 0..1, y 0..1, 0.5 = базовая линия)
private val ECG: List<Pair<Float, Float>> = listOf(
    0f to 0.5f, 0.12f to 0.5f, 0.18f to 0.44f, 0.24f to 0.56f, 0.30f to 0.5f,
    0.40f to 0.5f, 0.44f to 0.60f, 0.47f to 0.14f, 0.50f to 0.88f, 0.54f to 0.5f,
    0.62f to 0.5f, 0.70f to 0.44f, 0.77f to 0.56f, 0.84f to 0.5f, 1f to 0.5f,
)

private fun ecgY(x: Float): Float {
    for (i in 0 until ECG.size - 1) {
        val x0 = ECG[i].first; val y0 = ECG[i].second
        val x1 = ECG[i + 1].first; val y1 = ECG[i + 1].second
        if (x in x0..x1) {
            val t = if (x1 > x0) (x - x0) / (x1 - x0) else 0f
            return y0 + (y1 - y0) * t
        }
    }
    return 0.5f
}

private const val COLS = 13
private val GLYPHS = charArrayOf('0', '1', '0', '1', 'S', 'N', '7', '<', '>', '+', '=', '*', '1')

@Composable
fun HeartbeatButton(
    connected: Boolean,
    connecting: Boolean,
    gold: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onState = rememberUpdatedState(connected)
    var pulse by remember { mutableFloatStateOf(0f) }
    val drops = remember { FloatArray(COLS) { Random.nextFloat() } }
    val gi = remember { IntArray(COLS) { Random.nextInt(GLYPHS.size) } }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                    val on = onState.value
                    pulse = (pulse + (if (on) 0.50f else 0.17f) * dt) % 1f
                    val fall = if (on) 0.15f else 0.07f
                    for (i in 0 until COLS) {
                        var y = drops[i] + fall * dt
                        if (y > 1.12f) {
                            y = -0.12f - Random.nextFloat() * 0.25f
                            gi[i] = Random.nextInt(GLYPHS.size)
                        }
                        drops[i] = y
                    }
                }
                last = now
            }
        }
    }

    val ph = pulse // читаем состояние — этим драйвим перерисовку каждый кадр

    Box(
        modifier = modifier
            .size(210.dp)
            .clickable(enabled = !connecting) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(210.dp)) {
            val w = size.width
            val cx = w / 2f
            val cy = size.height / 2f
            val r = w / 2f - 6.dp.toPx()
            val beats = if (connected) 2 else 1

            // свечение на ударе
            var glowMax = 0f
            run {
                var d = abs(ph - 0.5f); d = minOf(d, 1f - d)
                glowMax = if (connected) max(0f, 1f - d / 0.10f) else 0f
            }
            if (glowMax > 0.04f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(gold.copy(alpha = 0.30f * glowMax), Color.Transparent),
                        center = Offset(cx, cy), radius = r * 1.15f,
                    ),
                    radius = r * 1.15f, center = Offset(cx, cy),
                )
            }

            drawCircle(color = Color(0xFF0E0C09), radius = r, center = Offset(cx, cy))

            val circle = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
            clipPath(circle) {
                // ── матрица (золотой код течёт) ──
                paint.textSize = w / 11f
                val spanW = r * 1.72f
                val colW = spanW / COLS
                val startX = cx - spanW / 2f + colW / 2f
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    for (i in 0 until COLS) {
                        val x = startX + i * colW
                        val y = (cy - r) + drops[i] * (r * 2f)
                        paint.color = if (connected)
                            android.graphics.Color.argb(150, 242, 220, 147)
                        else
                            android.graphics.Color.argb(78, 217, 185, 92)
                        nc.drawText(GLYPHS[gi[i]].toString(), x, y, paint)
                        paint.color = android.graphics.Color.argb(34, 217, 185, 92)
                        nc.drawText(GLYPHS[(gi[i] + 1) % GLYPHS.size].toString(), x, y - paint.textSize, paint)
                    }
                }

                // ── пульс (ЭКГ поверх) ──
                val left = cx - r * 0.82f
                val band = r * 1.64f
                val top = cy - r * 0.40f
                val bh = r * 0.80f
                val baseA = if (connected) 0.32f else 0.18f
                val addA = if (connected) 0.68f else 0.5f
                var px = left
                var py = top + ecgY(0f) * bh
                var nx = 0f
                while (nx <= 1f) {
                    val seg = (nx * beats) % 1f
                    val X = left + nx * band
                    val Y = top + ecgY(seg) * bh
                    var d = abs(nx - ph); d = minOf(d, 1f - d)
                    val bright = max(0f, 1f - d / 0.09f)
                    val a = (baseA + bright * addA).coerceIn(0f, 1f)
                    drawLine(
                        color = Color(0xFFF2DC93).copy(alpha = a),
                        start = Offset(px, py), end = Offset(X, Y),
                        strokeWidth = (1.7f + bright * 2.4f).dp.toPx(),
                    )
                    px = X; py = Y
                    nx += 0.006f
                }
            }

            // золотое кольцо
            drawCircle(
                color = gold.copy(alpha = if (connected) 1f else 0.5f),
                radius = r, center = Offset(cx, cy),
                style = Stroke(width = (if (connected) 4f else 2f).dp.toPx()),
            )
        }

        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = stringResource(if (connected) R.string.sn_disconnect else R.string.sn_connect),
            tint = gold.copy(alpha = if (connected) 0.92f else 0.78f),
            modifier = Modifier.size(if (connected) 30.dp else 52.dp),
        )
    }
}
