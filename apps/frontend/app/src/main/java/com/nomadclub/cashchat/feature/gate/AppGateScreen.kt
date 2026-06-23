package com.nomadclub.cashchat.feature.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.config.AppGateState

/**
 * 점검 모드 / 강제 업데이트 전체 차단 화면.
 * Remote Config 긴급 키로 [AppGateState]가 None 이 아닐 때 루트에서 표시된다.
 *
 * @param onUpdate 강제 업데이트 시 스토어로 이동(점검 모드에서는 호출되지 않음).
 */
@Composable
fun AppGateScreen(state: AppGateState, onUpdate: () -> Unit) {
    val (icon, title, defaultMessage, showButton) = when (state) {
        is AppGateState.Maintenance -> GateContent(
            icon = Icons.Default.Build,
            title = "서비스 점검 중이에요",
            defaultMessage = "더 나은 서비스를 위해 점검 중입니다.\n잠시 후 다시 이용해주세요.",
            showButton = false,
        )
        is AppGateState.ForceUpdate -> GateContent(
            icon = Icons.Default.SystemUpdate,
            title = "업데이트가 필요해요",
            defaultMessage = "최신 버전에서 더 안정적으로 이용할 수 있어요.\n스토어에서 업데이트해주세요.",
            showButton = true,
        )
        AppGateState.None -> return
    }

    val message = when (state) {
        is AppGateState.Maintenance -> state.message.ifBlank { defaultMessage }
        is AppGateState.ForceUpdate -> state.message.ifBlank { defaultMessage }
        AppGateState.None -> defaultMessage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (showButton) {
                Spacer(Modifier.height(32.dp))
                Button(onClick = onUpdate) {
                    Text("업데이트 하러 가기")
                }
            }
        }
    }
}

private data class GateContent(
    val icon: ImageVector,
    val title: String,
    val defaultMessage: String,
    val showButton: Boolean,
)
