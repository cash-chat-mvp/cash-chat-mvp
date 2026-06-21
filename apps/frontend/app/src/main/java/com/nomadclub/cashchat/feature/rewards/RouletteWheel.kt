package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.roulette.RoulettePrize
import com.nomadclub.cashchat.shared.roulette.RouletteSegment
import kotlin.math.cos
import kotlin.math.sin

private val CREAM = Color(0xFFFFF6DF)
private val WHITEISH = Color(0xFFFFFFFF)
private val DIVIDER = Color(0xFFECEAF5)
private val GOLD = Color(0xFFFFB02E)

private fun labelFor(prize: RoulettePrize): String = when (prize) {
    RoulettePrize.JACKPOT_100 -> "⚡100"
    RoulettePrize.E10 -> "⚡10"
    RoulettePrize.E3 -> "⚡3"
    RoulettePrize.MISS -> "꽝"
}

private fun labelColor(prize: RoulettePrize): Int = when (prize) {
    RoulettePrize.JACKPOT_100 -> 0xFFB07C00.toInt()
    RoulettePrize.MISS -> 0xFF9A95AD.toInt()
    else -> 0xFF1B1B2A.toInt()
}

/**
 * 8칸 룰렛 휠. rotationDeg 만큼 회전해 그린다(상위에서 Animatable 로 제어).
 * 칸 0 의 중심이 회전 0 일 때 12시(상단 포인터)에 오도록 그린다.
 */
@Composable
fun RouletteWheel(
    segments: List<RouletteSegment>,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val n = segments.size
            if (n == 0) return@Canvas
            val sweep = 360f / n
            val d = size.minDimension
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = d / 2f

            rotate(rotationDeg, pivot = Offset(cx, cy)) {
                segments.forEachIndexed { i, seg ->
                    val start = -90f - sweep / 2f + i * sweep
                    drawArc(
                        color = if (seg.prize == RoulettePrize.JACKPOT_100) CREAM
                                else if (i % 2 == 0) CREAM else WHITEISH,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    val a = Math.toRadians(start.toDouble())
                    drawLine(
                        color = DIVIDER,
                        start = Offset(cx, cy),
                        end = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()),
                        strokeWidth = 2f,
                    )
                    val midDeg = start + sweep / 2f
                    val mid = Math.toRadians(midDeg.toDouble())
                    val lx = cx + (r * 0.62f) * cos(mid).toFloat()
                    val ly = cy + (r * 0.62f) * sin(mid).toFloat()
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = labelColor(seg.prize)
                            textSize = 34f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        save()
                        rotate(midDeg + 90f, lx, ly)
                        drawText(labelFor(seg.prize), lx, ly + 12f, paint)
                        restore()
                    }
                }
                val jStart = -90f - sweep / 2f
                drawArc(
                    color = GOLD,
                    startAngle = jStart,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 6f),
                )
            }
        }
    }
}
