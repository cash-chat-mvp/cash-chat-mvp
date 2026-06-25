package com.nomadclub.cashchat.feature.chat.evolution

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.core.data.CharacterPreferenceStore
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.evolution.TimingCapability
import com.nomadclub.cashchat.shared.evolution.TimingGrade
import com.nomadclub.cashchat.shared.evolution.TimingWindow
import com.nomadclub.cashchat.shared.shop.InventoryDto
import com.nomadclub.cashchat.shared.shop.ShopApi
import kotlinx.coroutines.launch
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
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.evolutionStore.history.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val haptic = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()

    val content = uiState as? EvolutionViewModel.UiState.Content
    val phase = content?.phase ?: EvolutionViewModel.Phase.IDLE
    val result = content?.result
    val glowActive = phase == EvolutionViewModel.Phase.CHARGING || phase == EvolutionViewModel.Phase.RESOLVING
    val glow by animateFloatAsState(if (glowActive) 1f else 0f, tween(600), label = "glow")

    // 성공 화이트 플래시 + 햅틱
    val flash = remember { Animatable(0f) }
    LaunchedEffect(result) {
        if (result != null) {
            if (result.success) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!reducedMotion) { flash.snapTo(1f); flash.animateTo(0f, tween(600)) }
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    // 등급 향상 시 미세 햅틱
    LaunchedEffect(content?.predictedGrade) {
        if (phase == EvolutionViewModel.Phase.CHARGING && content?.predictedGrade != null &&
            content.predictedGrade != TimingGrade.NORMAL
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + glow * 0.25f),
                    MaterialTheme.colorScheme.background,
                ),
            ),
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기") }
                Spacer(Modifier.weight(1f))
            }

            // ── 스크롤 본문
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = uiState) {
                    is EvolutionViewModel.UiState.Loading ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    is EvolutionViewModel.UiState.LoadError ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(s.message, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.retryLoad() }) { Text("다시 시도") }
                        }

                    is EvolutionViewModel.UiState.Content -> {
                        val evolution = s.evolution
                        val displayLevel =
                            if (s.result?.success == true) s.result.resultLevel else evolution.level
                        val charging = phase == EvolutionViewModel.Phase.CHARGING
                        val orbScale by animateFloatAsState(
                            targetValue = if (charging) 1.06f else if (s.result?.success == true) 1.18f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "orbScale",
                        )
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                levelEmojis[displayLevel] ?: "🐣",
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.scale(orbScale),
                            )
                            Spacer(Modifier.height(8.dp))
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
                            Spacer(Modifier.height(12.dp))
                            StepIndicator(current = displayLevel, total = 5)
                            Spacer(Modifier.height(20.dp))

                            if (!evolution.isMaxLevel) {
                                // 경험치 진행도
                                val cost = evolution.nextAttemptCost ?: 0L
                                evolution.currentExp?.let { exp ->
                                    val progress = if (cost > 0) (exp.toFloat() / cost).coerceIn(0f, 1f) else 0f
                                    Column(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Text("진화 충전", style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.weight(1f))
                                            Text("⭐ %,d / %,d".format(exp, cost), style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard("성공 확률", "${((evolution.nextSuccessRate ?: 0.0) * 100).toInt()}%", Modifier.weight(1f))
                                    StatCard("진화 비용", "⭐ %,d".format(cost), Modifier.weight(1f))
                                }
                                inventory?.takeIf { it.items.isNotEmpty() }?.let { inv ->
                                    Spacer(Modifier.height(10.dp))
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
                                if (FeatureFlags.EVOLUTION_HISTORY && history.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                                        items(history) { record ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Text(if (record.success) "✅ " else "💨 ")
                                                Text(
                                                    if (record.success) "Lv.${record.fromLevel}→${record.resultLevel} 성공"
                                                    else "Lv.${record.fromLevel} 실패",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Text("⭐${record.cost}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            // ── 하단 고정 CTA
            content?.let { c ->
                Surface(tonalElevation = 3.dp) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        when {
                            c.evolution.isMaxLevel ->
                                AssistChip(onClick = {}, label = { Text("🏆 최고 레벨 달성!") })

                            c.capability == TimingCapability.SUPPORTED ->
                                TimingEvolveControls(c, viewModel)

                            else -> {
                                val cost = c.evolution.nextAttemptCost ?: 0L
                                val canAfford = c.evolution.currentExp?.let { it >= cost } ?: true
                                Button(
                                    onClick = { viewModel.attemptLegacy() },
                                    enabled = phase == EvolutionViewModel.Phase.IDLE && canAfford,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(
                                        when {
                                            phase != EvolutionViewModel.Phase.IDLE -> "분석 중..."
                                            !canAfford -> "경험치가 부족해요"
                                            else -> "🎰 진화 시도하기"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 성공 파티클 + 플래시
        if (!reducedMotion && result?.success == true && phase == EvolutionViewModel.Phase.RESULT) {
            SuccessParticles(color = gradeColor(result.timingGrade ?: TimingGrade.PERFECT))
        }
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        // 결과 카드
        if (result != null && phase == EvolutionViewModel.Phase.RESULT) {
            ResultDialog(result, onRetry = { viewModel.dismissResult(); viewModel.attemptLegacy() }, onDismiss = { viewModel.dismissResult() })
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
                androidx.compose.material3.OutlinedTextField(
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
private fun TimingEvolveControls(
    content: EvolutionViewModel.UiState.Content,
    viewModel: EvolutionViewModel,
) {
    val session = viewModel.evolutionStore.timingSession.collectAsState().value
    val window = remember(session) {
        session?.let { TimingWindow(minimumHoldMs = it.minimumHoldMs, cycleDurationMs = it.cycleDurationMs) }
    } ?: TimingWindow(minimumHoldMs = 600, cycleDurationMs = 1800)
    val charging = content.phase == EvolutionViewModel.Phase.CHARGING
    val cost = content.evolution.nextAttemptCost ?: 0L
    val canAfford = content.evolution.currentExp?.let { it >= cost } ?: true
    val idle = content.phase == EvolutionViewModel.Phase.IDLE
    val buttonScale by animateFloatAsState(if (charging) 0.96f else 1f, label = "holdScale")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EvolutionTimingGauge(
            window = window,
            position = content.timingPosition,
            predictedGrade = content.predictedGrade,
            active = charging,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        val enabled = (idle || charging) && canAfford
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (charging) MaterialTheme.colorScheme.primary
            else if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(56.dp).scale(buttonScale).then(
                if (enabled) Modifier.pointerInput(content.capability) {
                    awaitEachGesture {
                        awaitFirstDown()
                        viewModel.beginHold()
                        val up = waitForUpOrCancellation()
                        if (up == null) viewModel.cancelHold() else viewModel.releaseHold()
                    }
                } else Modifier,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    when {
                        content.phase == EvolutionViewModel.Phase.RESOLVING -> "분석 중..."
                        charging -> "꾹 — 중앙에서 떼세요!"
                        !canAfford -> "경험치가 부족해요"
                        else -> "🔋 꾹 눌러 진화 충전"
                    },
                    color = if (enabled || charging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ResultDialog(
    result: com.nomadclub.cashchat.shared.evolution.EvolutionAttemptDto,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.success) "🎉 Lv.${result.resultLevel} 달성!" else "아깝다!") },
        text = {
            Column {
                if (result.success) {
                    Text("진화 성공! 밥도 보너스로 충전됐어요 ⚡")
                } else {
                    Text("이번엔 실패했어요 (-%,d 경험치). 다시 도전해볼까요?".format(result.cost))
                }
                // 서버가 내려준 등급·확률을 예상값보다 우선해 노출
                result.timingGrade?.takeIf { it != TimingGrade.NORMAL }?.let { grade ->
                    Spacer(Modifier.height(6.dp))
                    Text(gradeLabel(grade), color = gradeColor(grade), fontWeight = FontWeight.SemiBold)
                }
                result.finalSuccessRate?.let { rate ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "적용 확률 ${(rate * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (result.success) onDismiss() else onRetry() }) {
                Text(if (result.success) "좋아!" else "다시 도전")
            }
        },
        dismissButton = if (!result.success) {
            { TextButton(onClick = onDismiss) { Text("다음에") } }
        } else null,
    )
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
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
