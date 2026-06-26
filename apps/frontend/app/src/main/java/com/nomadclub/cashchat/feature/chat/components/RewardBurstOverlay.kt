package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.nomadclub.cashchat.shared.chat.ChatResourceFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private val pointAccent = Color(0xFFFFC53D)  // 금색
private val expAccent = Color(0xFF8E7BFF)    // 보라

/**
 * 완료 보상 토큰 오버레이 — 마지막 AI 답변 버블에서 큰 🪙/⭐ 토큰이 곡선 경로로 상단 HUD 칩까지
 * 약 1.4초 이동하며 흡수된다. 두 토큰은 0.12초 간격으로 출발한다(스펙 §3.2).
 * 좌표는 모두 root 기준 px. 측정 불가하거나 Reduce Motion 이면 이동 없이 버블 위치에서 페이드한다.
 */
@Composable
fun RewardTokenOverlay(
    event: ChatResourceFeedback.RewardEarned?,
    originOf: (String) -> Offset?,
    fallbackOrigin: Offset,
    pointTarget: Offset?,
    expTarget: Offset?,
    reducedMotion: Boolean,
    onPointArrived: () -> Unit,
    onExpArrived: () -> Unit,
) {
    val coinProgress = remember { Animatable(0f) }
    val expProgress = remember { Animatable(0f) }
    var coinVisible by remember { mutableStateOf(false) }
    var expVisible by remember { mutableStateOf(false) }
    var coinSize by remember { mutableStateOf(IntSize.Zero) }
    var expSize by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(event?.eventId) {
        val e = event
        if (e == null) {
            // 이벤트가 비워지면(소비 완료) 토큰을 확실히 감춘다.
            coinVisible = false; expVisible = false
            return@LaunchedEffect
        }
        origin = originOf(e.messageId) ?: fallbackOrigin
        if (reducedMotion) {
            // 이동 대신 버블 위치 토큰 + HUD 크로스페이드(펄스)
            try {
                coinProgress.snapTo(0f); expProgress.snapTo(0f)
                coinVisible = true; expVisible = true
                onPointArrived()
                delay(120); onExpArrived()
                delay(700)
            } finally {
                // 취소돼도 토큰이 화면에 영구히 남지 않도록 상태를 되돌린다.
                coinVisible = false; expVisible = false
            }
            return@LaunchedEffect
        }
        launch {
            try {
                coinVisible = true
                coinProgress.snapTo(0f)
                coinProgress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
                onPointArrived()
            } finally {
                coinVisible = false
            }
        }
        delay(120)
        launch {
            try {
                expVisible = true
                expProgress.snapTo(0f)
                expProgress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
                onExpArrived()
            } finally {
                expVisible = false
            }
        }
    }

    if (!coinVisible && !expVisible) return
    val lift = with(LocalDensity.current) { 90.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        if (coinVisible) {
            val target = pointTarget ?: Offset(origin.x, origin.y - lift * 3f)
            TravelingToken(origin, target, coinProgress.value, lift, +1, coinSize, { coinSize = it }) {
                ChatRewardToken("🪙", "+${event?.pointDelta ?: 1}", pointAccent)
            }
        }
        if (expVisible) {
            val target = expTarget ?: Offset(origin.x, origin.y - lift * 3f)
            TravelingToken(origin, target, expProgress.value, lift, -1, expSize, { expSize = it }) {
                ChatRewardToken("⭐", "+${event?.expDelta ?: 1}", expAccent)
            }
        }
    }
}

@Composable
private fun TravelingToken(
    origin: Offset,
    target: Offset,
    p: Float,
    lift: Float,
    curveDir: Int,
    size: IntSize,
    onSized: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    val controlX = (origin.x + target.x) / 2f + curveDir * lift * 0.4f
    val controlY = min(origin.y, target.y) - lift
    val oneMinus = 1f - p
    val x = oneMinus * oneMinus * origin.x + 2f * oneMinus * p * controlX + p * p * target.x
    val y = oneMinus * oneMinus * origin.y + 2f * oneMinus * p * controlY + p * p * target.y
    val scale = if (p < 0.18f) lerp(0.6f, 1.1f, p / 0.18f) else 1.1f - 0.25f * ((p - 0.18f) / 0.82f)
    val tokenAlpha = if (p > 0.85f) (1f - (p - 0.85f) / 0.15f).coerceIn(0f, 1f) else 1f
    Box(
        Modifier
            .offset { IntOffset((x - size.width / 2f).roundToInt(), (y - size.height / 2f).roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = tokenAlpha }
            .onGloballyPositioned { onSized(it.size) },
    ) { content() }
}
