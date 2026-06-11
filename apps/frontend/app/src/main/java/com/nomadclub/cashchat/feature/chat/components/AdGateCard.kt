package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 게이트된 응답: teaser는 노출, 본문은 blur. 광고 시청 완료 시 호출측이 gated를 해제한다. */
@Composable
fun AdGateCard(
    fullText: String,
    teaserChars: Int,
    rewardCoin: Int,
    onWatchAd: () -> Unit,
) {
    val teaser = fullText.take(teaserChars)
    val hidden = fullText.drop(teaserChars)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Text(teaser, style = MaterialTheme.typography.bodyMedium)
            if (hidden.isNotEmpty()) {
                Box {
                    Text(hidden, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.blur(16.dp))
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔓 답변 전체 보기", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "광고 시청 후 🪙+$rewardCoin", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Button(onClick = onWatchAd, shape = RoundedCornerShape(20.dp)) { Text("▶ 광고 보기") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdGatePreview() {
    AdGateCard(
        fullText = "7만원이면 QCY T13 ANC가 최고의 선택이에요! 노이즈캔슬링 성능이 가격 대비 뛰어나고 배터리도 30시간으로 넉넉합니다. 통화 품질도 이 가격대 최상위권이라...",
        teaserChars = 40, rewardCoin = 30, onWatchAd = {},
    )
}
