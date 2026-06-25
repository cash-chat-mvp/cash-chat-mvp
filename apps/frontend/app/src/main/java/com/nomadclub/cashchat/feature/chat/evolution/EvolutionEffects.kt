package com.nomadclub.cashchat.feature.chat.evolution

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.evolution.TimingGrade
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 시스템 Reduce Motion(애니메이터 지속시간 0) 여부. true면 회전·파티클·플래시를 줄인다. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/** 등급별 강조 색 — 색 외에도 항상 아이콘/텍스트를 함께 노출한다(접근성). */
fun gradeColor(grade: TimingGrade?): Color = when (grade) {
    TimingGrade.PERFECT -> Color(0xFFFFC53D) // 금색
    TimingGrade.GREAT -> Color(0xFF9B6BFF)   // 보라
    else -> Color(0xFF7C8698)                // 중립
}

fun gradeLabel(grade: TimingGrade?): String = when (grade) {
    TimingGrade.PERFECT -> "PERFECT +10%p"
    TimingGrade.GREAT -> "GREAT +5%p"
    TimingGrade.NORMAL -> "NORMAL"
    null -> ""
}

/** 성공 파티클 — 단일 progress로 N개의 입자를 방사형으로 흩뿌린다. Reduce Motion 시 비활성. */
@Composable
fun SuccessParticles(color: Color, count: Int = 64) {
    val particles = remember {
        List(count) {
            Triple(
                Random.nextFloat() * 2f * Math.PI.toFloat(),
                0.4f + Random.nextFloat() * 0.6f,
                Random.nextFloat(),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * 0.38f)
        particles.forEach { (angle, speed, seed) ->
            val distance = progress.value * speed * size.minDimension * 0.5f
            drawCircle(
                color = color.copy(alpha = (1f - progress.value).coerceIn(0f, 1f)),
                radius = 3.dp.toPx() + seed * 4.dp.toPx(),
                center = center + Offset(cos(angle) * distance, sin(angle) * distance),
            )
        }
    }
}
