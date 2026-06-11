package com.nomadclub.cashchat.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.feature.chat.components.AdGateCard
import com.nomadclub.cashchat.feature.chat.components.EnergyGauge
import com.nomadclub.cashchat.feature.chat.components.MessageBubble
import com.nomadclub.cashchat.feature.chat.components.StatChip
import com.nomadclub.cashchat.feature.chat.components.TypingIndicator
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val suggestedQuestions = listOf("오늘 저녁 뭐 먹을까?", "가성비 이어폰 추천해줘", "영어 공부 팁 알려줘")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenConversations: () -> Unit,
    onOpenEvolution: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
    adManager: RewardedAdManager = koinInject(),
) {
    val context = LocalContext.current
    val gateInfo by viewModel.chatStore.gateInfo.collectAsState()
    val items by viewModel.chatStore.items.collectAsState()
    val isStreaming by viewModel.chatStore.isStreaming.collectAsState()
    val gateVisible by viewModel.chatStore.energyGateVisible.collectAsState()
    val hud by viewModel.hudStore.state.collectAsState()
    val attendance by viewModel.attendance.collectAsState()
    val checkInResult by viewModel.checkInResult.collectAsState()
    var input by remember { mutableStateOf("") }
    var showAttendance by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, (items.lastOrNull() as? ChatItem.AssistantMessage)?.text?.length) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        // ── 슬림 톱바
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onOpenConversations) {
                Icon(Icons.Filled.Forum, contentDescription = "대화 목록")
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onOpenEvolution),
            ) {
                Text("🐣", Modifier.padding(6.dp))
            }
            Column(Modifier.clickable(onClick = onOpenEvolution)) {
                Text("미래", style = MaterialTheme.typography.titleSmall)
                if (hud.isLoaded) {
                    Text(
                        "Lv.${hud.level}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (attendance != null) {
                IconButton(onClick = { showAttendance = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "출석 캘린더")
                }
            }
            // 포인트 잔액 API 부재(BE 의존성) — points가 null이면 칩 숨김
            hud.points?.let { StatChip("🪙", "%,d".format(it)) }
            if (hud.isLoaded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatChip("⚡", "${hud.energy}/${hud.maxEnergy}", warning = hud.energy == 0)
                    EnergyGauge(hud.energy, hud.maxEnergy, Modifier.width(56.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // ── 메시지 리스트
        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🐣", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("안녕! 뭐든 물어봐요", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    suggestedQuestions.forEach { question ->
                        SuggestionChip(
                            onClick = { viewModel.send(question) },
                            label = { Text(question) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        if (item is ChatItem.AssistantMessage && item.gated && !item.isStreaming) {
                            AdGateCard(
                                fullText = item.text,
                                teaserChars = gateInfo?.teaserChars ?: 80,
                                rewardCoin = gateInfo?.rewardCoin ?: 30,
                                onWatchAd = {
                                    val activity = context as? Activity ?: return@AdGateCard
                                    viewModel.startGateUnlock(item.id) { nonce ->
                                        suspendCancellableCoroutine { continuation ->
                                            adManager.show(
                                                activity = activity,
                                                nonce = nonce,
                                                onRewarded = { },
                                                onDismissed = { continuation.resume(true) },
                                                onNotReady = { continuation.resume(false) },
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            MessageBubble(item)
                        }
                        if (item is ChatItem.AssistantMessage && item.isError) {
                            TextButton(onClick = { viewModel.chatStore.retryLastMessage() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                    if (isStreaming && items.lastOrNull() !is ChatItem.AssistantMessage) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        // ── 입력바
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("메시지를 입력하세요...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            FilledIconButton(
                onClick = { viewModel.send(input); input = "" },
                enabled = input.isNotBlank() && !isStreaming,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
            }
        }
    }

    if (gateVisible) {
        EnergyGateBottomSheet(viewModel = viewModel)
    }

    checkInResult?.let { result ->
        CheckInRewardDialog(result = result, onDismiss = { viewModel.dismissCheckIn() })
    }

    if (showAttendance) {
        attendance?.let { monthly ->
            ModalBottomSheet(onDismissRequest = { showAttendance = false }) {
                AttendanceCalendar(monthly)
            }
        }
    }
}
