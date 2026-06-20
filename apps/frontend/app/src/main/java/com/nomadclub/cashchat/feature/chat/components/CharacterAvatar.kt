package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/** 에너지 비율별 표정 + 탭 시 통통 튀는 반응 (스펙 §6.3) */
@Composable
fun CharacterAvatar(
    level: Int,
    energyRatio: Float,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    onTap: () -> Unit = {},
) {
    val base = mapOf(1 to "🥚", 2 to "🐣", 3 to "🐤", 4 to "🦅", 5 to "🐲")[level] ?: "🐣"
    val mood = when {
        energyRatio <= 0f -> "😵"
        energyRatio <= 0.2f -> "🥺"
        else -> ""
    }
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Text(
        base + mood,
        style = style,
        modifier = modifier.scale(scale.value).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                scale.animateTo(1.25f, spring(stiffness = Spring.StiffnessHigh))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            onTap()
        },
    )
}
