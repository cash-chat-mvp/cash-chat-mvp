package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 완료 보상 토큰 — 최소 44dp 높이, 26sp 이모지 + 16sp 굵은 +N.
 * 반투명한 어두운 배경에 밝은 외곽선/글로우로 가독성을 확보한다(스펙 §3.1).
 */
@Composable
fun ChatRewardToken(
    emoji: String,
    delta: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xCC15151F),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.9f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(emoji, fontSize = 26.sp)
            Text(delta, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
