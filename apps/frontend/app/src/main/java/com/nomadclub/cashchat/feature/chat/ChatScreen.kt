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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomadclub.cashchat.ads.RewardedAdManager
import com.nomadclub.cashchat.core.data.CharacterPreferenceStore
import com.nomadclub.cashchat.feature.chat.components.AdGateCard
import com.nomadclub.cashchat.feature.chat.components.CharacterAvatar
import com.nomadclub.cashchat.feature.chat.components.EnergyGauge
import com.nomadclub.cashchat.feature.chat.components.MessageBubble
import com.nomadclub.cashchat.feature.chat.components.StatChip
import com.nomadclub.cashchat.feature.chat.components.TypingIndicator
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
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
    characterStore: CharacterPreferenceStore = koinInject(),
) {
    val context = LocalContext.current
    val characterName by characterStore.name.collectAsStateWithLifecycle(initialValue = "미래")
    val gateInfo by viewModel.chatStore.gateInfo.collectAsStateWithLifecycle()
    val items by viewModel.chatStore.items.collectAsStateWithLifecycle()
    val isStreaming by viewModel.chatStore.isStreaming.collectAsStateWithLifecycle()
    val gateVisible by viewModel.chatStore.energyGateVisible.collectAsStateWithLifecycle()
    val hud by viewModel.hudStore.state.collectAsStateWithLifecycle()
    val attendance by viewModel.attendance.collectAsStateWithLifecycle()
    val checkInResult by viewModel.checkInResult.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showAttendance by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 사용자가 위로 스크롤해 과거 메시지를 읽는 중인지 판단(맨 아래 근처면 자동 추적 유지).
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }

    // 새 메시지가 추가되면 맨 아래로 스크롤. 단, 사용자가 과거 메시지를 보려고 위로 올린 경우
    // 강제로 끌어내리지 않는다. (내가 방금 보낸 메시지는 항상 보이도록 예외 처리)
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val sentByMe = items.lastOrNull() is ChatItem.UserMessage
        if (!sentByMe && !isAtBottom) return@LaunchedEffect
        if (isStreaming) listState.scrollToItem(items.lastIndex)
        else listState.animateScrollToItem(items.lastIndex)
    }
    // 스트리밍 중 토큰으로 본문이 길어질 때는, 사용자가 위로 올려둔 경우 강제 스크롤하지 않는다.
    // animateScrollToItem 을 매번 재시작하면 jank 가 생기므로 즉시 스크롤로 따라간다.
    val lastAssistantLen = (items.lastOrNull() as? ChatItem.AssistantMessage)?.text?.length
    LaunchedEffect(lastAssistantLen) {
        if (items.isEmpty() || !isStreaming || !isAtBottom) return@LaunchedEffect
        listState.scrollToItem(items.lastIndex)
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
            ) {
                // 탭 시 통통 반응 후 진화 화면 이동 (스펙 §6.3)
                CharacterAvatar(
                    level = hud.level,
                    energyRatio = if (hud.maxEnergy > 0) hud.energy.toFloat() / hud.maxEnergy else 1f,
                    modifier = Modifier.padding(6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    onTap = onOpenEvolution,
                )
            }
            Column(Modifier.clickable(onClick = onOpenEvolution)) {
                Text(characterName, style = MaterialTheme.typography.titleSmall)
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
            Box {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("대화 내보내기") },
                        enabled = items.isNotEmpty(),
                        onClick = {
                            showMenu = false
                            shareConversation(context, items, characterName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("공유 링크 만들기 (준비 중)") },
                        enabled = FeatureFlags.SHARE_LINK,
                        onClick = { showMenu = false },
                    )
                }
            }
            // 포인트 잔액 API 부재(BE 의존성) — points가 null이면 칩 숨김
            hud.points?.let { StatChip("🪙", "%,d".format(it)) }
            if (hud.isLoaded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatChip("⚡", "${hud.energy}/${hud.maxEnergy}", warning = hud.energy == 0)
                    EnergyGauge(hud.energy, hud.maxEnergy, Modifier.width(56.dp))
                    if (FeatureFlags.ENERGY_RECOVERY) {
                        hud.nextRecoverAt?.let { iso ->
                            RecoveryCountdown(
                                nextRecoverAtIso = iso,
                                onFinished = { viewModel.refreshEnergy() },
                            )
                        }
                    }
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
                    CharacterAvatar(
                        level = hud.level,
                        energyRatio = if (hud.maxEnergy > 0) hud.energy.toFloat() / hud.maxEnergy else 1f,
                        style = MaterialTheme.typography.displayMedium,
                    )
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
                                            // 보상은 onRewarded(=리워드 적립)에서만 확정한다.
                                            // 광고를 끝까지 보지 않고 닫으면 unlock 하지 않는다.
                                            var rewarded = false
                                            adManager.show(
                                                activity = activity,
                                                nonce = nonce,
                                                onRewarded = { rewarded = true },
                                                onDismissed = {
                                                    if (continuation.isActive) continuation.resume(rewarded)
                                                },
                                                onNotReady = {
                                                    if (continuation.isActive) continuation.resume(false)
                                                },
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

/** 대화 전체를 텍스트로 OS 공유 시트에 전달 (FE 단독). */
private fun shareConversation(context: android.content.Context, items: List<ChatItem>, characterName: String) {
    val text = items.joinToString("\n") { item ->
        when (item) {
            is ChatItem.UserMessage -> "나: ${item.text}"
            is ChatItem.AssistantMessage -> "$characterName: ${item.text}"
            else -> ""
        }
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "대화 공유"))
}

/** 다음 밥 회복까지 카운트다운 (P1-3). 0 도달 시 [onFinished]로 에너지 재조회. */
@Composable
private fun RecoveryCountdown(nextRecoverAtIso: String, onFinished: () -> Unit) {
    var remainText by remember { mutableStateOf("") }
    LaunchedEffect(nextRecoverAtIso) {
        // 서버 타임스탬프 포맷이 예상과 다르면 parse 가 던지며 화면이 크래시하므로 방어한다.
        // java.time 미사용(desugaring 미설정으로 구버전 기기 NoClassDefFoundError 회피) — SimpleDateFormat 사용.
        val target = runCatching { parseIsoInstantMillis(nextRecoverAtIso) }
            .getOrElse { return@LaunchedEffect }
        while (true) {
            val remainSec = ((target - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            remainText = "%d:%02d 후 ⚡회복".format(remainSec / 60, remainSec % 60)
            if (remainSec == 0L) break
            delay(1000)
        }
        onFinished()
    }
    Text(
        remainText, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * ISO-8601 instant 문자열을 epoch millis로 파싱. (java.time desugaring 미설정 → SimpleDateFormat)
 * "2026-06-20T12:34:56Z", "...56.789Z", "...+09:00" 형태를 허용한다. 실패 시 예외를 던진다.
 */
private fun parseIsoInstantMillis(iso: String): Long {
    val normalized = iso
        .replace(Regex("\\.\\d+"), "")                       // 소수 초 제거
        .replace("Z", "+0000")                                 // UTC 표기 보정
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")     // +09:00 -> +0900
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
    return fmt.parse(normalized)?.time
        ?: throw IllegalArgumentException("Unparseable timestamp: $iso")
}
