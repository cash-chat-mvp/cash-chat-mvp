package com.nomadclub.cashchat.feature.rewards

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.ads.RewardOutcome
import com.nomadclub.cashchat.shared.hud.HudStore
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

/** 혜택존 리워드 광고 카드 상태 홀더. 채팅 경로와 무관하게 독립 동작한다. */
class BenefitRewardViewModel(
    private val adRewardStore: AdRewardStore,
    private val hudStore: HudStore,
) : ViewModel() {

    enum class Phase { IDLE, BUSY }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val quota = adRewardStore.quota

    fun loadQuota() {
        viewModelScope.launch { runCatching { adRewardStore.refreshQuota() } }
    }

    /** showAd: nonce 를 받아 광고를 표시하고 끝까지 시청했으면 true 를 반환. */
    fun watchAd(showAd: suspend (nonce: String) -> Boolean) {
        // BUSY 전환을 launch 밖(메인 스레드)에서 동기 처리 — 빠른 연속 탭이 둘 다 IDLE 을
        // 보고 광고 플로우를 중복 시작하는 경쟁을 막는다.
        if (_phase.value != Phase.IDLE) return
        _phase.value = Phase.BUSY
        viewModelScope.launch {
            try {
                val outcome = runCatching { adRewardStore.runRewardFlow(showAd) }
                    .getOrElse {
                        // quota/nonce/폴링 예외를 무음 처리하지 않고 사용자에게 알린다.
                        _toast.tryEmit("잠시 후 다시 시도해주세요.")
                        return@launch
                    }
                runCatching { hudStore.refreshEnergyOnly() }
                runCatching { adRewardStore.refreshQuota() }
                when (outcome) {
                    RewardOutcome.APPLIED -> _toast.tryEmit("에너지를 충전했어요!")
                    RewardOutcome.PENDING -> _toast.tryEmit("보상 확인 중이에요. 잠시 후 다시 확인해주세요")
                    RewardOutcome.NOT_WATCHED -> {}
                }
            } finally {
                _phase.value = Phase.IDLE
            }
        }
    }
}

@Composable
fun RewardAdCard(
    modifier: Modifier = Modifier,
    vm: BenefitRewardViewModel = koinViewModel(),
    adManager: RewardedAdManager = koinInject(),
) {
    val quota by vm.quota.collectAsState()
    val phase by vm.phase.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        adManager.preload(context)
        vm.loadQuota()
    }
    LaunchedEffect(vm) {
        vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val remaining = quota?.remaining
    val limitReached = remaining == 0
    val busy = phase == BenefitRewardViewModel.Phase.BUSY

    val accentColor = Color(0xFFFF5E8A)
    val gradient = if (limitReached) {
        Brush.linearGradient(listOf(Color(0xFFBFA9A0), Color(0xFFA89AA0)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFF8A4C), accentColor))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .clickable(enabled = !limitReached && !busy) {
                val activity = context.findActivity() ?: return@clickable
                vm.watchAd { nonce ->
                    suspendCancellableCoroutine { cont ->
                        var rewarded = false
                        adManager.show(
                            activity = activity,
                            nonce = nonce,
                            onRewarded = { rewarded = true },
                            onDismissed = { if (cont.isActive) cont.resume(rewarded) },
                            onNotReady = {
                                if (cont.isActive) {
                                    Toast.makeText(
                                        context,
                                        "광고를 준비 중이에요. 잠시 후 다시 시도해주세요.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    cont.resume(false)
                                }
                            },
                        )
                    }
                }
            }
            .padding(16.dp),
    ) {
        Text(
            text = when {
                remaining == null -> "불러오는 중…"
                limitReached -> "오늘 한도 도달 · 자정 리셋"
                else -> "오늘 ${remaining}회 남음"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )

        Column {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Text("⚡", fontSize = 20.sp) }

            Spacer(Modifier.height(10.dp))
            Text("리워드 광고", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(3.dp))
            Text(
                "광고 보고 에너지 충전하기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
            )

            Spacer(Modifier.height(13.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = if (limitReached) 0.5f else 1f))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                        Text("보상 확인 중...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                } else {
                    Text(
                        if (limitReached) "내일 다시 만나요" else "▶  광고 보기",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
            }
        }
    }
}
