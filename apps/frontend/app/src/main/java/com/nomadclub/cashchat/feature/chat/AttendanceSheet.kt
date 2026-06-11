package com.nomadclub.cashchat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.attendance.CheckInDto
import com.nomadclub.cashchat.shared.attendance.MonthlyAttendanceDto

/** 출석 보상 다이얼로그 — 채팅 진입 시 미출석이면 자동 표시 */
@Composable
fun CheckInRewardDialog(result: CheckInDto, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📅 ${result.streakDayCount}일째 출석!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🪙 +%,d 코인".format(result.awardedCoin), style = MaterialTheme.typography.titleLarge)
                if (result.bonusItems.isNotEmpty()) {
                    Text("보너스: " + result.bonusItems.joinToString { "${it.itemCode} ×${it.quantity}" })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "다음 보상: ${result.nextRewardPreview.dayCount}일차 🪙${result.nextRewardPreview.coin}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("좋아!") } },
    )
}

/** 월 캘린더 — 톱바 캘린더 아이콘으로 진입하는 바텀시트 내용물 */
@Composable
fun AttendanceCalendar(monthly: MonthlyAttendanceDto) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${monthly.year}년 ${monthly.month}월 출석", style = MaterialTheme.typography.titleMedium)
        Text(
            "🔥 연속 ${monthly.currentStreak}일", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(220.dp)) {
            items((1..31).toList()) { day ->
                val checked = day in monthly.checkedDays
                Box(
                    Modifier.padding(3.dp).size(34.dp).clip(CircleShape)
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$day",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
