package com.nomadclub.cashchat.feature.chat

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.energy.EnergyTopupApi
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyGateBottomSheet(
    viewModel: ChatViewModel,
    adManager: RewardedAdManager = koinInject(),
    topupApi: EnergyTopupApi = koinInject(),
) {
    val quota by viewModel.adRewardStore.quota.collectAsState()
    val phase by viewModel.rewardPhase.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showTopupConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { adManager.preload(context) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissGate() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🍚", style = MaterialTheme.typography.displayMedium)
            Text("밥이 떨어졌어요!", style = MaterialTheme.typography.titleLarge)
            Text(
                "광고 보고 밥을 채우면 바로 이어서 대화해요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quota?.let {
                Text(
                    if (it.remaining > 0) "오늘 ${it.remaining}회 남음 · 자정에 리셋"
                    else "오늘 광고 한도에 도달했어요 · 내일 다시 만나요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            when (phase) {
                ChatViewModel.RewardPhase.POLLING -> {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("보상 확인 중...", style = MaterialTheme.typography.labelMedium)
                }
                ChatViewModel.RewardPhase.FAILED -> {
                    Text("보상 확인이 지연되고 있어요", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = {
                        viewModel.startAdReward { _ -> true } // 폴링만 재시도 (광고 재시청 없이)
                    }) { Text("다시 확인") }
                }
                else -> {
                    Button(
                        onClick = {
                            val activity = context as? Activity ?: return@Button
                            viewModel.startAdReward { nonce ->
                                suspendCancellableCoroutine { continuation ->
                                    adManager.show(
                                        activity = activity,
                                        nonce = nonce,
                                        onRewarded = { },
                                        onDismissed = { continuation.resume(true) },
                                        onNotReady = { continuation.resume(false) },
                                    )
                                }
                            }
                        },
                        enabled = (quota?.remaining ?: 0) > 0 && phase == ChatViewModel.RewardPhase.IDLE,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("▶  광고 보고 밥 채우기") }

                    OutlinedButton(
                        onClick = { showTopupConfirm = true },
                        enabled = FeatureFlags.POINT_TOPUP,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (FeatureFlags.POINT_TOPUP) "🪙 포인트로 충전" else "🪙 포인트로 충전 (준비 중)") }
                }
            }
        }
    }

    if (showTopupConfirm) {
        AlertDialog(
            onDismissRequest = { showTopupConfirm = false },
            title = { Text("포인트로 충전") },
            text = { Text("포인트를 사용해 밥을 채울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showTopupConfirm = false
                    scope.launch {
                        try {
                            topupApi.topup(UUID.randomUUID().toString())
                            // 광고 경로와 동일한 마무리: HUD 갱신 + 막힌 메시지 재전송
                            runCatching { viewModel.hudStore.refreshEnergyOnly() }
                            viewModel.chatStore.retryBlocked()
                        } catch (e: ApiException) {
                            val message = if (e.code == ApiException.INSUFFICIENT_POINTS) "포인트가 부족해요" else e.message
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "충전에 실패했어요", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("충전") }
            },
            dismissButton = {
                TextButton(onClick = { showTopupConfirm = false }) { Text("취소") }
            },
        )
    }
}
