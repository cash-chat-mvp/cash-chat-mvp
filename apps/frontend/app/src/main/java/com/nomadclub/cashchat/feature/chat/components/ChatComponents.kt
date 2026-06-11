package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.model.ChatItem

/** 코인/밥 공용 칩 */
@Composable
fun StatChip(emoji: String, text: String, warning: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.labelMedium)
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 밥 게이지 — 잔량 비율 따라 색 전환, 변화 시 부드럽게 차오름 */
@Composable
fun EnergyGauge(energy: Int, maxEnergy: Int, modifier: Modifier = Modifier) {
    val ratio = if (maxEnergy > 0) energy.toFloat() / maxEnergy else 0f
    val animated by animateFloatAsState(ratio, animationSpec = tween(600), label = "energy")
    val barColor = if (ratio <= 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier.fillMaxWidth(animated).height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(barColor),
        )
    }
}

/** 메시지 버블 */
@Composable
fun MessageBubble(item: ChatItem) {
    when (item) {
        is ChatItem.UserMessage -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = if (item.status == ChatItem.SendStatus.PENDING || item.status == ChatItem.SendStatus.BLOCKED) 0.55f else 1f,
                ),
            ) {
                Text(
                    item.text,
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 280.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        is ChatItem.ProductCards -> ProductCardList(item)
        is ChatItem.AssistantMessage -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 300.dp).animateContentSize()) {
                    if (item.text.isNotEmpty()) {
                        Text(
                            item.text + if (item.isStreaming) " ▍" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (item.isError) {
                        Text(
                            "응답이 끊겼어요. 다시 시도해주세요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 타이핑 인디케이터 (점 3개) */
@Composable
fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(8.dp)) {
        repeat(3) { index ->
            val alpha by rememberInfiniteTransition(label = "dots")
                .animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = index * 150),
                        repeatMode = RepeatMode.Reverse,
                    ), label = "dot$index",
                )
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}
