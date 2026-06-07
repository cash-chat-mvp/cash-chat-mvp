package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import java.util.Calendar

private val HeroStart = Color(0xFF5C6BFA)
private val HeroEnd = Color(0xFF8466FA)
private val Accent = Color(0xFFFFB800)
private val White = Color(0xFFFFFFFF)
private val DayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun AttendanceWidget(
    state: AttendanceUiState,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cal = Calendar.getInstance()
    val ty = cal.get(Calendar.YEAR)
    val tm = cal.get(Calendar.MONTH) + 1
    val td = cal.get(Calendar.DAY_OF_MONTH)
    val dispYear = if (state.year > 0) state.year else ty
    val dispMonth = if (state.month in 1..12) state.month else tm
    val cells = weeklyAttendanceCells(dispYear, dispMonth, ty, tm, td, state.checkedDays.toSet())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥 ${state.currentStreak}일 연속 출석", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text("${dispMonth}월", color = White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            cells.forEachIndexed { idx, cell ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        DayLabels[idx],
                        color = if (cell.isToday) Accent else White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = if (cell.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    val dotColor = when {
                        cell.checked -> White
                        cell.isToday -> Accent
                        else -> White.copy(alpha = 0.18f)
                    }
                    val contentColor = when {
                        cell.checked -> HeroStart
                        cell.isToday -> Color(0xFF1B1B2A)
                        else -> White
                    }
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(dotColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (cell.checked) "✓" else "${cell.dayOfMonth}",
                            color = contentColor,
                            fontSize = if (cell.checked) 14.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        state.nextReward?.let { r ->
            val bonus = r.bonusItems.joinToString(" ") { "📦 ${it.itemCode} ${it.quantity}개" }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("🎁 오늘 보상 🪙+${r.coin}  $bonus", color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = onCheckIn,
            enabled = !state.todayChecked && !state.isCheckingIn,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF1B1B2A),
                disabledContainerColor = White.copy(alpha = 0.25f),
                disabledContentColor = White.copy(alpha = 0.7f),
            ),
        ) {
            Text(if (state.todayChecked) "오늘 출석 완료" else "출석 도장 찍기", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
