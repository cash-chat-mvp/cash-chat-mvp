package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 채팅 완료 보상 연출(충실도 B): tick 이 바뀔 때마다 화면 하단(응답 버블 영역)에서
 * 별/코인 입자가 위쪽(HUD 방향)으로 흘러오르며 페이드한다. HUD 좌표 추적 없이 동작.
 */
@Composable
fun RewardBurstOverlay(tick: Int, modifier: Modifier = Modifier) {
    var seedParticles by remember { mutableStateOf<List<Triple<Float, Float, Float>>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(tick) {
        if (tick <= 0) return@LaunchedEffect
        seedParticles = List(8) {
            Triple(0.25f + Random.nextFloat() * 0.5f, (Random.nextFloat() - 0.5f) * 0.2f, Random.nextFloat())
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
    }

    if (seedParticles.isNotEmpty() && progress.value < 1f) {
        Box(modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val p = progress.value
                seedParticles.forEach { (startXRatio, drift, seed) ->
                    val x = (startXRatio + drift * p) * size.width
                    val y = size.height * (0.8f - 0.45f * p)
                    drawCircle(
                        color = (if (seed > 0.5f) Color(0xFFFFC107) else Color(0xFF7C4DFF))
                            .copy(alpha = (1f - p).coerceIn(0f, 1f)),
                        radius = 4.dp.toPx() + seed * 4.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}
