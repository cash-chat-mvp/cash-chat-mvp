package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.ChatResourceFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ResourceDeltaBadgeModel(
    val eventId: Long,
    val label: String,
)

internal fun ChatResourceFeedback.toResourceDeltaBadge(messageId: String): ResourceDeltaBadgeModel? =
    (this as? ChatResourceFeedback.EnergySpent)
        ?.takeIf { it.messageId == messageId }
        ?.let { ResourceDeltaBadgeModel(eventId = it.eventId, label = "⚡ ${it.amount}") }

@Composable
fun ResourceDeltaBadge(
    eventId: Long,
    label: String,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(6f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        visible = true
        alpha.snapTo(0f)
        offsetY.snapTo(6f)
        launch { alpha.animateTo(1f, animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing)) }
        offsetY.animateTo(0f, animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing))
        delay(520)
        launch { offsetY.animateTo(-4f, animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing)) }
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing))
        visible = false
    }

    if (!visible && alpha.value <= 0f) return

    Surface(
        modifier = modifier
            .alpha(alpha.value)
            .offset(y = offsetY.value.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .requiredHeightIn(min = 30.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
