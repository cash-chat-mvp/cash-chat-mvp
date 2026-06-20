package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import org.koin.compose.koinInject

@Composable
fun BenefitZoneScreen(
    store: AttendanceStore = koinInject(),
    pointsRepository: PointsRepository = koinInject(),
) {
    val state by store.state.collectAsState()
    val balance by pointsRepository.balance.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { store.loadMonthly() }
    LaunchedEffect(Unit) {
        store.rewardEvents.collect { ev ->
            Toast.makeText(context, "출석 완료! 🪙+${ev.awardedCoin}", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
            BenefitInfoCard(
                icon = "📺", title = "리워드 광고", badge = BenefitBadge.NEXT,
                description = "광고 1회 시청 → 🪙+40 코인 · 하루 10회까지",
                dimmed = false,
                onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
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
                icon = "🎮", title = "TNK 오퍼월", badge = BenefitBadge.SOON,
                description = "앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)",
                dimmed = true,
                onClick = { Toast.makeText(context, "곧 만나요!", Toast.LENGTH_SHORT).show() },
            )
        }
    }
}
