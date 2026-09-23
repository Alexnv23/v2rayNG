package com.v2ray.ang.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val SN_MATRIX_GLYPHS = charArrayOf('0', '1', '0', '1', 'S', 'N', '7', '<', '>', '+', '=', '*', '1')

/**
 * SuperNet: «матрица» — золотой код, стекающий сверху вниз (как на кнопке подключения,
 * но без пульса). Фон карточки «Резервный канал», когда канал активен.
 *
 * Колонки инициализируются по ширине в первом кадре отрисовки; списки — обычные (не
 * snapshot-состояние), поэтому мутация внутри draw не вызывает рекомпозицию. И цикл кадров,
 * и отрисовка идут на главном потоке последовательно — гонок нет.
 */
@Composable
fun SnMatrixStrip(
    modifier: Modifier = Modifier,
    cell: Dp = 15.dp,
) {
    val drops = remember { ArrayList<Float>() }
    val gi = remember { ArrayList<Int>() }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                    for (i in drops.indices) {
                        var y = drops[i] + 0.9f * dt
                        if (y > 1.25f) {
                            y = -0.25f - Random.nextFloat() * 0.6f
                            gi[i] = Random.nextInt(SN_MATRIX_GLYPHS.size)
                        }
                        drops[i] = y
                    }
                }
                last = now
                tick = now
            }
        }
    }

    Canvas(modifier = modifier) {
        // Подписка на кадровый тик ДОЛЖНА быть внутри draw — иначе Canvas не перерисовывается
        // и глифы «висят» (счётчик кадров меняется, но draw об этом не знает).
        @Suppress("UNUSED_VARIABLE")
        val frame = tick
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cellPx = cell.toPx()
        val n = (w / cellPx).toInt().coerceAtLeast(1)
        if (drops.size != n) {
            drops.clear(); gi.clear()
            for (i in 0 until n) {
                drops.add(Random.nextFloat() * 1.5f - 0.25f)
                gi.add(Random.nextInt(SN_MATRIX_GLYPHS.size))
            }
        }
        paint.textSize = cellPx * 0.92f
        val colW = w / n
        val startX = colW / 2f
        val travel = h + cellPx
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (i in 0 until n) {
                val x = startX + i * colW
                val y = drops[i] * travel - cellPx * 0.2f
                paint.color = android.graphics.Color.argb(120, 217, 185, 92)
                nc.drawText(SN_MATRIX_GLYPHS[gi[i]].toString(), x, y, paint)
                paint.color = android.graphics.Color.argb(40, 217, 185, 92)
                nc.drawText(SN_MATRIX_GLYPHS[(gi[i] + 1) % SN_MATRIX_GLYPHS.size].toString(), x, y - paint.textSize, paint)
            }
        }
    }
}
