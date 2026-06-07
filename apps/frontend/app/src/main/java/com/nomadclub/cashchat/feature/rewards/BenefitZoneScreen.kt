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

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("혜택존", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("🪙 ${balance}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFB07C00))
            }
        }
        item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
        item { PhasePlaceholder("데일리 미션 (Phase 3)") }
        item { PhasePlaceholder("리워드 광고 (Phase 2)") }
        item { PhasePlaceholder("TNK Factory 오퍼월 (Phase 4)") }
    }
}

@Composable
private fun PhasePlaceholder(label: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F1F7)).padding(20.dp)
    ) { Text(label, color = Color(0xFFB0ADBE), fontWeight = FontWeight.Bold) }
}
