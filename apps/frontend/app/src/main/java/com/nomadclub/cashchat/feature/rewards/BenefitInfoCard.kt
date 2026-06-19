package com.nomadclub.cashchat.feature.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BenefitBadge(val label: String, val bg: Color, val fg: Color) {
    NEXT("곧 출시", Color(0xFFE3F0FF), Color(0xFF2D6FE0)),
    SOON("준비중", Color(0xFFF0EEF8), Color(0xFF9A95AD)),
}

/** 미구현 혜택 섹션의 소개 카드(가짜 데이터 없음). */
@Composable
fun BenefitInfoCard(
    icon: String,
    title: String,
    badge: BenefitBadge,
    description: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (dimmed) 0.72f else 1f))
            .border(1.dp, Color(0xFFF0EEF8), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B1B2A))
            Spacer(Modifier.width(8.dp))
            Text(
                badge.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badge.fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(badge.bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(description, fontSize = 13.sp, color = Color(0xFF6B6979))
    }
}
