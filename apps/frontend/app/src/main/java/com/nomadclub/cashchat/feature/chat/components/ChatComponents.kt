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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
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
    val ratio = if (maxEnergy > 0) (energy.toFloat() / maxEnergy).coerceIn(0f, 1f) else 0f
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
                        // LLM 응답은 마크다운(굵게/목록/제목/코드 등)으로 오므로 렌더링해 보여준다.
                        // 스트리밍 커서 ▍는 마크다운 특수문자가 아니라 그대로 인라인 표시된다.
                        AssistantMarkdown(item.text + if (item.isStreaming) " ▍" else "")
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

/**
 * 채팅 버블용 마크다운 렌더링.
 * 기본 M3 마크다운은 h1=displaySmall(~36sp) 등 헤딩이 버블에 비해 지나치게 크고 블록 간격이 거의
 * 없어 "글 덩어리"처럼 보인다. 헤딩을 title 계열로 축소하고, 본문/리스트를 bodyMedium 로 통일하며,
 * 색은 surfaceVariant 버블에 맞는 onSurfaceVariant 로, 블록·리스트 간 간격을 줘서 구조가 보이게 한다.
 */
@Composable
private fun AssistantMarkdown(content: String) {
    val onColor = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium
    Markdown(
        content = content,
        colors = markdownColor(
            text = onColor,
            linkText = MaterialTheme.colorScheme.primary,
            codeText = onColor,
            inlineCodeText = onColor,
            codeBackground = MaterialTheme.colorScheme.surface,
            inlineCodeBackground = MaterialTheme.colorScheme.surface,
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            h4 = body.copy(fontWeight = FontWeight.Bold),
            h5 = body.copy(fontWeight = FontWeight.Bold),
            h6 = body.copy(fontWeight = FontWeight.Bold),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            quote = body.copy(color = onColor.copy(alpha = 0.85f)),
            code = body.copy(fontFamily = FontFamily.Monospace),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace),
        ),
        padding = markdownPadding(block = 8.dp, list = 4.dp, listItemBottom = 4.dp),
    )
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
