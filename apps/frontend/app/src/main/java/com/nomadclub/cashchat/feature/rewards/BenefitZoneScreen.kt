package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nomadclub.cashchat.offerwall.TnkOfferwallManager
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitZoneScreen(
    store: AttendanceStore = koinInject(),
    pointsRepository: PointsRepository = koinInject(),
    offerwallManager: TnkOfferwallManager = koinInject(),
) {
    val state by store.state.collectAsState()
    val balance by pointsRepository.balance.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showRoulette by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }

    suspend fun refreshAll() {
        runCatching { pointsRepository.refresh() }
        store.loadMonthly()
    }

    LaunchedEffect(Unit) { store.loadMonthly() }
    LaunchedEffect(Unit) {
        store.rewardEvents.collect { ev ->
            Toast.makeText(context, "출석 완료! 🪙+${ev.awardedCoin}", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshAll() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                refreshAll()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("혜택존", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "🪙 $balance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFB07C00),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFFFF7E6))
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
            item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
            item {
                com.nomadclub.cashchat.ads.BannerAd(
                    slot = com.nomadclub.cashchat.shared.ads.BannerAdSlot.BENEFIT_TOP,
                )
            }
            item { RewardAdCard() }
            item {
                BenefitInfoCard(
                    icon = "🎡", title = "행운 룰렛", badge = BenefitBadge.NEXT,
                    description = "하루 1회 무료 · 광고로 최대 5회 · 에너지 잭팟까지!",
                    dimmed = false,
                    onClick = { showRoulette = true },
                )
            }
            item {
                BenefitInfoCard(
                    icon = "🤝", title = "친구 초대", badge = BenefitBadge.NEXT,
                    description = "친구가 가입하면 나는 코인, 친구는 에너지!",
                    dimmed = false,
                    onClick = { showInvite = true },
                )
            }
            item {
                BenefitInfoCard(
                    icon = "🎯", title = "데일리 미션", badge = BenefitBadge.SOON,
                    description = "매일 바뀌는 3가지 미션을 완료하고 코인 적립",
                    dimmed = true,
                    onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                BenefitInfoCard(
                    icon = "🎮", title = "TNK 오퍼월", badge = BenefitBadge.NEXT,
                    description = "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)",
                    dimmed = false,
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null) {
                            Toast.makeText(context, "오퍼월을 열 수 없어요", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                offerwallManager.launch(activity).onFailure {
                                    Toast.makeText(context, "오퍼월 진입에 실패했어요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            }
        }
        if (showRoulette) {
            RouletteDialog(onDismiss = { showRoulette = false })
        }
        if (showInvite) {
            InviteDialog(onDismiss = { showInvite = false })
        }
    }
}
