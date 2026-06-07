package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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

private val Primary = Color(0xFF5C6BFA)
private val Accent = Color(0xFFFFB800)
private val Unchecked = Color(0xFFE0DCEF)

@Composable
fun AttendanceWidget(
    state: AttendanceUiState,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE8E1FF), Color(0xFFFAFBFF))))
            .padding(20.dp)
    ) {
        Text("${state.month}월 출석체크", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1B1B2A))
        Spacer(Modifier.height(14.dp))

        val daysInMonth = 31
        val checked = state.checkedDays.toSet()
        val todayNum = state.checkedDays.maxOrNull()?.let { if (state.todayChecked) it else it + 1 } ?: 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..daysInMonth).toList()) { day ->
                val color = when {
                    day in checked -> Primary
                    day == todayNum && !state.todayChecked -> Accent
                    else -> Unchecked
                }
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center,
                ) { Text("$day", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(12.dp))
        state.nextReward?.let { r ->
            val bonus = r.bonusItems.joinToString(" ") { "📦 ${it.itemCode} ${it.quantity}개" }
            Text("오늘 보상: 🪙+${r.coin}  $bonus", fontSize = 13.sp, color = Color(0xFF1B1B2A))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCheckIn,
            enabled = !state.todayChecked && !state.isCheckingIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, disabledContainerColor = Unchecked),
        ) {
            Text(if (state.todayChecked) "오늘 출석 완료" else "출석 도장 찍기", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}
