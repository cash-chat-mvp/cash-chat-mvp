package com.nomadclub.cashchat.feature.chat.evolution

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import com.nomadclub.cashchat.core.data.CharacterPreferenceStore
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.shop.InventoryDto
import com.nomadclub.cashchat.shared.shop.ShopApi
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val levelEmojis = mapOf(1 to "🥚", 2 to "🐣", 3 to "🐤", 4 to "🦅", 5 to "🐲")
private val levelNames = mapOf(1 to "알", 2 to "부화", 3 to "유년", 4 to "성장", 5 to "궁극")

@Composable
fun EvolutionScreen(
    onClose: () -> Unit,
    viewModel: EvolutionViewModel = koinViewModel(),
    shopApi: ShopApi = koinInject(),
    characterStore: CharacterPreferenceStore = koinInject(),
) {
    val characterName by characterStore.name.collectAsState(initial = "미래")
    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf<InventoryDto?>(null) }
    LaunchedEffect(Unit) {
        runCatching { shopApi.getInventory() }.onSuccess { inventory = it }
    }
    val state by viewModel.evolutionStore.state.collectAsState()
    val history by viewModel.evolutionStore.history.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val result by viewModel.lastResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val haptic = LocalHapticFeedback.current

    // ── 연출 값들
    val scale by animateFloatAsState(
        targetValue = when (phase) {
            EvolutionViewModel.Phase.CHARGING -> 0.92f
            EvolutionViewModel.Phase.SURGING -> 1.08f
            EvolutionViewModel.Phase.REVEAL_SUCCESS -> 1.2f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )
    val glow by animateFloatAsState(
        targetValue = if (phase == EvolutionViewModel.Phase.SURGING) 1f else 0f,
        animationSpec = tween(900), label = "glow",
    )
    // 화이트 플래시: REVEAL_SUCCESS 진입 순간 1f → 0f
    val flash = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        when (phase) {
            EvolutionViewModel.Phase.SURGING -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            EvolutionViewModel.Phase.REVEAL_SUCCESS -> {
                flash.snapTo(1f)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                flash.animateTo(0f, tween(600))
            }
            EvolutionViewModel.Phase.REVEAL_FAIL -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            else -> Unit
        }
    }
    // 실패 쉐이크
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == EvolutionViewModel.Phase.REVEAL_FAIL) {
            repeat(4) {
                shakeOffset.animateTo(12f, tween(40)); shakeOffset.animateTo(-12f, tween(40))
            }
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    val displayLevel = if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS) {
        result?.resultLevel ?: state?.level ?: 1
    } else state?.level ?: 1

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + glow * 0.25f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .clickable(enabled = phase == EvolutionViewModel.Phase.CHARGING || phase == EvolutionViewModel.Phase.SURGING) {
                if (viewModel.attemptCount >= 2) viewModel.requestSkip()
            },
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    levelEmojis[displayLevel] ?: "🐣",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .scale(scale)
                        .graphicsLayer { translationX = shakeOffset.value },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$characterName · Lv.$displayLevel ${levelNames[displayLevel] ?: ""}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        " ✏️",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable {
                            nameInput = characterName
                            showNameDialog = true
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                StepIndicator(current = displayLevel, total = 5)
            }

            Spacer(Modifier.weight(1f))

            state?.let { evolution ->
                if (evolution.isMaxLevel) {
                    AssistChip(onClick = {}, label = { Text("🏆 최고 레벨 달성!") })
                } else {
                    StatRow("다음 진화 비용", "⭐ %,d 경험치".format(evolution.nextAttemptCost ?: 0))
                    evolution.currentExp?.let { exp ->
                        Spacer(Modifier.height(6.dp))
                        StatRow("보유 경험치", "⭐ %,d".format(exp))
                    }
                    Spacer(Modifier.height(6.dp))
                    StatRow("성공 확률", "${((evolution.nextSuccessRate ?: 0.0) * 100).toInt()}%")
                    // 보유 아이템 — 효과 적용 API는 BE 미구현이라 표시 전용
                    inventory?.takeIf { it.items.isNotEmpty() }?.let { inv ->
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            inv.items.take(3).forEach { item ->
                                AssistChip(onClick = {}, label = { Text("🧿 ${item.itemCode} ×${item.qty}") })
                            }
                            Text(
                                "적용 기능 준비 중",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "성공하면 밥도 보너스 충전! ⚡",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                    val cost = evolution.nextAttemptCost ?: 0L
                    val canAfford = evolution.currentExp?.let { it >= cost } ?: true
                    Button(
                        onClick = { viewModel.attempt() },
                        enabled = phase == EvolutionViewModel.Phase.IDLE && canAfford,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            when {
                                phase != EvolutionViewModel.Phase.IDLE -> "두근두근..."
                                !canAfford -> "경험치가 부족해요"
                                else -> "🎰 진화 시도하기"
                            }
                        )
                    }

                    // 시도 기록 타임라인 (P3-1) — 플래그 활성 시에만
                    if (FeatureFlags.EVOLUTION_HISTORY && history.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 160.dp)) {
                            items(history) { record ->
                                ListItem(
                                    leadingContent = { Text(if (record.success) "✅" else "💨") },
                                    headlineContent = {
                                        Text(
                                            if (record.success) "Lv.${record.fromLevel}→${record.resultLevel} 성공!"
                                            else "Lv.${record.fromLevel} 실패",
                                        )
                                    },
                                    supportingContent = { Text("⭐${record.cost} · ${record.attemptedAt.take(10)}") },
                                )
                            }
                        }
                    }
                }
            } ?: CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        // 성공 파티클
        if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS) SuccessParticles()

        // 화이트 플래시 오버레이
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        // 결과 카드
        result?.let { attemptResult ->
            if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS || phase == EvolutionViewModel.Phase.REVEAL_FAIL) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissResult() },
                    title = {
                        Text(if (attemptResult.success) "🎉 Lv.${attemptResult.resultLevel} 달성!" else "아깝다!")
                    },
                    text = {
                        Text(
                            if (attemptResult.success) "진화 성공! 밥도 보너스로 충전됐어요 ⚡"
                            else "이번엔 실패했어요 (-%,d 경험치). 다시 도전해볼까요?".format(attemptResult.cost),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val retry = !attemptResult.success
                            viewModel.dismissResult()
                            if (retry) viewModel.attempt()
                        }) { Text(if (attemptResult.success) "좋아!" else "다시 도전") }
                    },
                    dismissButton = if (!attemptResult.success) {
                        { TextButton(onClick = { viewModel.dismissResult() }) { Text("다음에") } }
                    } else null,
                )
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("진화 실패") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("확인") } },
        )
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("이름 짓기") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 10) nameInput = it },
                    singleLine = true,
                    label = { Text("1~10자") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = nameInput
                    showNameDialog = false
                    scope.launch { characterStore.setName(newName) }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        (1..total).forEach { step ->
            val isDone = step <= current
            Box(
                Modifier.size(if (step == current) 14.dp else 10.dp).clip(CircleShape)
                    .background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
            if (step < total) {
                Box(
                    Modifier.width(22.dp).height(2.dp)
                        .background(if (step < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 성공 파티클 — 단일 progress로 N개의 입자를 방사형으로 흩뿌린다 */
@Composable
private fun SuccessParticles(count: Int = 80) {
    val particles = remember {
        List(count) {
            Triple(
                Random.nextFloat() * 2f * Math.PI.toFloat(),  // 각도
                0.4f + Random.nextFloat() * 0.6f,              // 속도 계수
                Random.nextFloat(),                            // 크기 시드
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * 0.4f)
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
