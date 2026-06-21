package com.nomadclub.cashchat.feature.rewards

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.shared.roulette.RouletteSpinResult
import com.nomadclub.cashchat.shared.roulette.RouletteStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.coroutines.resume

class RouletteViewModel(private val store: RouletteStore) : ViewModel() {
    enum class Phase { IDLE, SPINNING, AD }
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()
    private val _result = MutableSharedFlow<RouletteSpinResult>(extraBufferCapacity = 1)
    val result: SharedFlow<RouletteSpinResult> = _result.asSharedFlow()
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()
    val status = store.status

    fun load() { viewModelScope.launch { runCatching { store.refresh() } } }

    /** 무료 첫 스핀. */
    fun spin() {
        if (_phase.value != Phase.IDLE) return
        val s = store.status.value ?: return
        if (!s.freeSpinAvailable) return
        viewModelScope.launch {
            _phase.value = Phase.SPINNING
            val result = runCatching { store.spin() }.getOrNull()
            if (result != null) _result.tryEmit(result) else _toast.tryEmit("스핀에 실패했어요")
            _phase.value = Phase.IDLE
        }
    }

    /** 광고 게이트 스핀: 광고 시청 → 끝까지 봤으면 즉시 스핀. */
    fun spinWithAd(showAd: suspend (nonce: String) -> Boolean) {
        if (_phase.value != Phase.IDLE) return
        val s = store.status.value ?: return
        if (s.remaining <= 0) { _toast.tryEmit("오늘 룰렛을 다 돌렸어요. 자정에 리셋돼요"); return }
        viewModelScope.launch {
            _phase.value = Phase.AD
            val nonce = runCatching { store.prepareAdSpin() }.getOrNull()
            if (nonce == null) { _phase.value = Phase.IDLE; return@launch }
            val watched = runCatching { showAd(nonce) }.getOrDefault(false)
            if (!watched) { _phase.value = Phase.IDLE; return@launch }
            _phase.value = Phase.SPINNING
            val result = runCatching { store.spinWithAd() }.getOrNull()
            if (result != null) _result.tryEmit(result) else _toast.tryEmit("스핀에 실패했어요")
            _phase.value = Phase.IDLE
        }
    }
}

private fun resultText(r: RouletteSpinResult): String =
    if (r.awardedEnergy > 0) "⚡${r.awardedEnergy} 에너지 획득!" else "아쉽지만 꽝! 다시 도전해요"

@Composable
fun RouletteDialog(
    onDismiss: () -> Unit,
    vm: RouletteViewModel = koinViewModel(),
    adManager: RewardedAdManager = koinInject(),
) {
    val status by vm.status.collectAsState()
    val phase by vm.phase.collectAsState()
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }
    var lastResultText by remember { mutableStateOf<String?>(null) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { adManager.preload(context); vm.load() }
    LaunchedEffect(vm) { vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(vm) {
        vm.result.collect { result ->
            val n = status?.segments?.size ?: 8
            val sweep = 360f / n
            // 칸 segmentIndex 중심이 상단 포인터에 오도록: 5바퀴 + 목표 각도(시계방향 누적).
            val current = rotation.value
            val normalized = current - (current % 360f)
            val target = normalized + 360f * 5 - result.segmentIndex * sweep
            isAnimating = true
            rotation.animateTo(target, tween(2600))
            lastResultText = resultText(result)
            isAnimating = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("행운 룰렛", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B2A))
            status?.let {
                Text(
                    if (it.freeSpinAvailable) "오늘 ${it.remaining}회 · 첫 회 무료!"
                    else "오늘 ${it.remaining}회 남음 · 광고 보고 돌리기",
                    fontSize = 12.sp, color = Color(0xFF6B6979),
                )
            }

            Box(contentAlignment = Alignment.TopCenter) {
                status?.let { RouletteWheel(segments = it.segments, rotationDeg = rotation.value) }
                Text("▼", color = Color(0xFF5B5BD6), fontSize = 22.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopCenter))
                Box(
                    Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color(0xFF5B5BD6)),
                    contentAlignment = Alignment.Center,
                ) { Text("GO", color = Color.White, fontWeight = FontWeight.Black) }
            }

            lastResultText?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5BD6)) }

            val remaining = status?.remaining ?: 0
            val freeAvailable = status?.freeSpinAvailable ?: false
            val idle = phase == RouletteViewModel.Phase.IDLE && !isAnimating
            when {
                remaining <= 0 -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("내일 다시 · 자정 리셋")
                    }
                }
                freeAvailable -> {
                    Button(onClick = { vm.spin() }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                        Text("돌리기 (무료)")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            val activity = context as? Activity ?: return@Button
                            vm.spinWithAd { nonce ->
                                suspendCancellableCoroutine { cont ->
                                    var rewarded = false
                                    adManager.show(
                                        activity = activity,
                                        nonce = nonce,
                                        onRewarded = { rewarded = true },
                                        onDismissed = { if (cont.isActive) cont.resume(rewarded) },
                                        onNotReady = {
                                            if (cont.isActive) {
                                                Toast.makeText(context, "광고를 준비 중이에요. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                                cont.resume(false)
                                            }
                                        },
                                    )
                                }
                            }
                        },
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("광고 보고 돌리기") }
                }
            }

            Text("닫기", color = Color(0xFF9A95AD), fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(8.dp))
        }
    }
}
