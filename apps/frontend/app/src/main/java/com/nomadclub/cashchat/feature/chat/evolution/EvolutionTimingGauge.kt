package com.nomadclub.cashchat.feature.chat.evolution

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.evolution.TimingGrade
import com.nomadclub.cashchat.shared.evolution.TimingWindow

/**
 * 타이밍 게이지 — 누르는 동안 마커가 좌우로 순환하며 PERFECT/GREAT 구간 위에서 떼면 보너스.
 * 순수 표시 컴포넌트로, position/predictedGrade는 ViewModel이 계산해 내려준다.
 */
@Composable
fun EvolutionTimingGauge(
    window: TimingWindow,
    position: Float,
    predictedGrade: TimingGrade?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val markerColor by animateColorAsState(
        targetValue = if (active) gradeColor(predictedGrade) else Color(0xFF7C8698),
        label = "markerColor",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val greatColor = Color(0xFF9B6BFF).copy(alpha = 0.35f)
    val perfectColor = Color(0xFFFFC53D).copy(alpha = 0.55f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val h = size.height
            val trackH = 12.dp.toPx()
            val top = (h - trackH) / 2f
            val radius = trackH / 2f
            // 트랙
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            // GREAT 구간
            drawRect(
                color = greatColor,
                topLeft = Offset(window.greatStart * size.width, top),
                size = Size((window.greatEnd - window.greatStart) * size.width, trackH),
            )
            // PERFECT 구간
            drawRect(
                color = perfectColor,
                topLeft = Offset(window.perfectStart * size.width, top),
                size = Size((window.perfectEnd - window.perfectStart) * size.width, trackH),
            )
            // 마커
            if (active) {
                val x = position.coerceIn(0f, 1f) * size.width
                drawCircle(color = markerColor, radius = h / 2f, center = Offset(x, h / 2f))
                drawCircle(color = Color.White, radius = h / 2f - 4.dp.toPx(), center = Offset(x, h / 2f))
                drawCircle(color = markerColor, radius = h / 2f - 7.dp.toPx(), center = Offset(x, h / 2f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (active && predictedGrade != null) gradeLabel(predictedGrade) else "꾹 누르고 중앙에서 떼면 보너스!",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) gradeColor(predictedGrade) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
