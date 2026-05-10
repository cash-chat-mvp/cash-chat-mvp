package com.nomadclub.cashchat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomadclub.cashchat.feature.chat.models.AdInfo
import com.nomadclub.cashchat.feature.chat.models.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// 사용자 메시지 키워드에 따라 노출할 인라인 광고 목록 (예: "맛집" → 배달의민족)
private val adDatabase = listOf(
    listOf("음식", "맛집", "먹", "배달", "요리", "점심", "저녁", "밥", "치킨", "피자") to AdInfo(
        brand = "배달의민족",
        tagline = "지금 주문하면 3,000원 즉시 할인! 오늘 뭐 먹지?",
        cta = "지금 주문하기",
        emoji = "🍔",
        bg = 0xFFFFF4E8,
        accent = 0xFFFF6B00,
        category = "음식·배달"
    ),
    listOf("여행", "휴가", "비행기", "호텔", "숙소", "관광") to AdInfo(
        brand = "야놀자",
        tagline = "특가 숙소 최대 50% 할인! 지금 예약하세요",
        cta = "특가 확인하기",
        emoji = "✈️",
        bg = 0xFFE8F4FF,
        accent = 0xFF0066CC,
        category = "여행·숙박"
    ),
    listOf("운동", "헬스", "다이어트", "건강", "요가") to AdInfo(
        brand = "나이키 트레이닝 클럽",
        tagline = "무료 운동 플랜으로 오늘부터 시작하세요!",
        cta = "무료로 시작",
        emoji = "💪",
        bg = 0xFFE8FFE8,
        accent = 0xFF00AA44,
        category = "건강·피트니스"
    )
)

// 키워드에 맞는 광고가 없을 때 보여줄 기본 광고
private val defaultAd = AdInfo(
    brand = "CashAI Premium",
    tagline = "광고 없이 AI와 무제한 대화! 월 990원부터",
    cta = "업그레이드",
    emoji = "⭐",
    bg = 0xFFFFF8E8,
    accent = 0xFFCC8800,
    category = "프리미엄"
)

// "광고 보고 답변 받기" 모달에서 돌릴 리워드 광고 목록
private val rewardAds = listOf(
    AdInfo("삼성 갤럭시 S25", "새로운 AI 폰의 시작. 사전예약 시 Galaxy Buds 증정!", "사전예약 하기", "📱", 0xFFE8F0FF, 0xFF1428A0, "전자제품"),
    AdInfo("스타벅스 코리아", "봄 시즌 신메뉴 출시! 앱 주문 시 별 2배 적립", "앱으로 주문", "☕", 0xFFE8F5E9, 0xFF00704A, "음료·카페"),
    AdInfo("현대자동차 아이오닉6", "전기차 시대의 혁신. 지금 시승 신청하세요!", "시승 신청", "🚗", 0xFFFFF3E0, 0xFF002C5F, "자동차")
)

/**
 * AI 채팅 메인 화면.
 */
@Composable
fun ChatScreen(
    points: Int,
    messageCount: Int,
    addPoints: (Int) -> Unit,
    onNavigateTab: (String) -> Unit,
    incrementMessageCount: () -> Unit
) {
    var messages by remember {
        mutableStateOf<List<ChatMessage>>(
            listOf(
                ChatMessage.Text(
                    id = "1",
                    text = "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!",
                    isUser = false
                )
            )
        )
    }
    var inputValue by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var chatIdle by remember { mutableStateOf(true) }
    var showMissionReward by remember { mutableStateOf(false) }
    var showRewardAdModal by remember { mutableStateOf(false) }
    var pendingAIResponse by remember { mutableStateOf("") }
    var rewardAdCount by remember { mutableIntStateOf(0) }
    var currentRewardAd by remember { mutableStateOf(rewardAds[0]) }
    var sentCount by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val suggestions = remember {
        listOf("오늘 점심 추천해줘", "여행 계획 짜줘", "영어 공부 방법", "다이어트 팁")
    }

    // --- 플로팅 네비게이션 바 노출 제어 로직 ---
    var isScrollingUp by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    // 현재 리스트가 스크롤 가능한 상태인지 확인 (아이템이 화면을 꽉 채웠는지)
    val isScrollable by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.size < listState.layoutInfo.totalItemsCount ||
            listState.canScrollBackward || listState.canScrollForward
        }
    }

    // 스크롤 방향 감지
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index > previousIndex) {
                    isScrollingUp = false
                } else if (index < previousIndex) {
                    isScrollingUp = true
                } else {
                    if (offset > previousScrollOffset + 10) {
                        isScrollingUp = false
                    } else if (offset < previousScrollOffset - 10) {
                        isScrollingUp = true
                    }
                }
                previousIndex = index
                previousScrollOffset = offset
            }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.lastIndex + if (isLoading) 1 else 0)
            // 새 메시지가 추가될 때 스크롤이 끝에 도달하면 잠시 네비바를 숨김 (자연스러운 연출)
            if (isScrollable) isScrollingUp = false
        }
    }

    LaunchedEffect(messageCount) {
        if (messageCount > 0 && messageCount % 10 == 0) {
            showMissionReward = true
        }
    }

    suspend fun appendAiWithAd(text: String, response: String) {
        delay(2000)
        messages = messages + ChatMessage.Text(
            id = "${System.currentTimeMillis()}-ai",
            text = response,
            isUser = false
        )
        isLoading = false
        delay(400)
        messages = messages + ChatMessage.InlineAd(
            id = "${System.currentTimeMillis()}-ad",
            ad = matchAd(text)
        )
    }

    fun submitMessage(override: String? = null) {
        val text = (override ?: inputValue).trim()
        if (text.isEmpty() || isLoading) return

        chatIdle = false
        sentCount += 1
        messages = messages + ChatMessage.Text(
            id = System.currentTimeMillis().toString(),
            text = text,
            isUser = true
        )
        inputValue = ""
        isLoading = true
        incrementMessageCount()

        val isRewardTurn = sentCount % 3 == 0
        if (isRewardTurn) {
            pendingAIResponse = generateAiResponse(text)
        }

        scope.launch {
            if (isRewardTurn) {
                delay(1800)
                isLoading = false
                messages = messages + ChatMessage.RewardPrompt(id = "${System.currentTimeMillis()}-reward")
            } else {
                appendAiWithAd(text, generateAiResponse(text))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 바
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Menu, contentDescription = "menu")
                }
                Text("CashAI 비서", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFF6B00))
                        .clickable { onNavigateTab("shop") }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🪙 ${points} P",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 메시지 목록
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    when (message) {
                        is ChatMessage.Text -> TextBubble(message = message)
                        is ChatMessage.InlineAd -> InlineAdCard(ad = message.ad)
                        is ChatMessage.RewardPrompt -> RewardPromptCard(
                            onWatchAd = {
                                currentRewardAd = rewardAds[rewardAdCount % rewardAds.size]
                                showRewardAdModal = true
                            }
                        )
                    }
                }
                if (isLoading) {
                    item { LoadingBubble() }
                }
            }

            // 하단 입력 영역
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    placeholder = { Text("메시지를 입력하세요...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    shape = RoundedCornerShape(999.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { submitMessage() },
                    enabled = inputValue.isNotBlank() && !isLoading,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "send")
                }
            }
        }

        // --- 채팅 중 나타나는 플로팅 네비게이션 바 ---
        // 조건: 아이들 상태가 아니어야 하며 (대화 중), (스크롤이 불가능한 상태이거나 OR 위로 스크롤 중이어야 함)
        AnimatedVisibility(
            visible = !chatIdle && (!isScrollable || isScrollingUp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IdleNavItem(icon = Icons.AutoMirrored.Filled.Chat, label = "채팅", active = true, onClick = {})
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.2f)))
                    IdleNavItem(icon = Icons.Default.CardGiftcard, label = "리워드", active = false, onClick = { onNavigateTab("rewards") })
                    IdleNavItem(icon = Icons.Default.Store, label = "상점", active = false, onClick = { onNavigateTab("shop") })
                    IdleNavItem(icon = Icons.Default.Person, label = "MY", active = false, onClick = { onNavigateTab("mypage") })
                }
            }
        }

        // 대화 시작 전 오버레이
        if (chatIdle) {
            IdleOverlay(
                points = points,
                inputValue = inputValue,
                onInputChange = { inputValue = it },
                onSubmit = { submitMessage() },
                onSuggestionClick = { submitMessage(it) },
                onNavigateTab = onNavigateTab,
                suggestions = suggestions
            )
        }

        // 리워드 광고 모달
        if (showRewardAdModal) {
            RewardAdModal(
                ad = currentRewardAd,
                onClose = {
                    showRewardAdModal = false
                    scope.launch {
                        delay(300)
                        messages = messages + ChatMessage.Text(
                            id = "${System.currentTimeMillis()}-ai",
                            text = pendingAIResponse,
                            isUser = false
                        )
                    }
                },
                onComplete = {
                    addPoints(30)
                    rewardAdCount += 1
                    showRewardAdModal = false
                    scope.launch {
                        delay(300)
                        messages = messages + ChatMessage.Text(
                            id = "${System.currentTimeMillis()}-ai",
                            text = pendingAIResponse,
                            isUser = false
                        )
                    }
                }
            )
        }
    }
}

/** 사용자/AI 텍스트 메시지 말풍선. isUser에 따라 오른쪽/왼쪽 정렬, 색상 구분 */
@Composable
private fun TextBubble(message: ChatMessage.Text) {
    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(1f, animationSpec = tween(300))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF5C6BFA))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .fillMaxWidth(0.85f) // Max width constraint
                    .clip(
                        if (message.isUser) RoundedCornerShape(18.dp, 2.dp, 18.dp, 18.dp)
                        else RoundedCornerShape(2.dp, 18.dp, 18.dp, 18.dp)
                    )
                    .background(if (message.isUser) Color(0xFF5C6BFA) else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (message.isUser) Color.Transparent else Color.Black.copy(alpha = 0.03f),
                        shape = if (message.isUser) RoundedCornerShape(18.dp, 2.dp, 18.dp, 18.dp)
                                else RoundedCornerShape(2.dp, 18.dp, 18.dp, 18.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isUser) Color.White else Color(0xFF111827),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.sp
                    )
                )
            }
            // Timestamp
            Text(
                text = SimpleDateFormat("a h:mm", Locale.getDefault()).format(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

/** 채팅 흐름 안에 끼워 넣는 맞춤 광고 카드. X 버튼으로 닫기 가능 */
@Composable
private fun InlineAdCard(ad: AdInfo) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    AnimatedVisibility(
        visible = !dismissed,
        enter = fadeIn() + slideInVertically { 50 },
        exit = fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(ad.bg)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 맞춤 광고 · ${ad.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { dismissed = true }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "dismiss", tint = Color.Black.copy(alpha = 0.3f))
                    }
                }

                // Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(ad.accent).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ad.emoji, style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ad.brand, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            ad.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4B5563),
                            maxLines = 2
                        )
                    }
                }

                // CTA Button
                Box(modifier = Modifier.padding(14.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(ad.accent),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("${ad.cta} →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** "광고 보고 바로 답변 받기" 카드. 클릭 시 리워드 광고 모달 오픈 */
@Composable
private fun RewardPromptCard(onWatchAd: () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { 50 }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp, 2.dp, 20.dp, 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x335C6BFA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤔", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI가 생각을 오래하네요...", fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "복잡한 질문이라 분석에 시간이 걸리고 있어요.\n짧은 광고를 보고 오시면 바로 답변 드릴게요! 🎁",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onWatchAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("광고 보고 바로 답변 받기", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            "+30P",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** AI 답변 대기 중일 때 보여주는 "답변 생성 중..." 말풍선 */
@Composable
private fun LoadingBubble() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp, top = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF5C6BFA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
        
        Card(
            shape = RoundedCornerShape(2.dp, 18.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Simple dot pulsing animation
                val dotAlpha = remember { Animatable(0.3f) }
                LaunchedEffect(Unit) {
                    dotAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        )
                    )
                }
                
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF5C6BFA).copy(alpha = dotAlpha.value)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF5C6BFA).copy(alpha = dotAlpha.value)).graphicsLayer { translationY = -2f })
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF5C6BFA).copy(alpha = dotAlpha.value)))
            }
        }
    }
}

/** 채팅 시작 전 첫 화면: 그라데이션 배경, 입력창, 추천 문구, 하단 탭 미리보기 */
@Composable
private fun IdleOverlay(
    points: Int,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onNavigateTab: (String) -> Unit,
    suggestions: List<String>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFEEF0FF), Color(0xFFF8F4FF), Color(0xFFFFF4EE)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        // Points Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFFF6B00))
                .clickable { onNavigateTab("shop") }
        ) {
            Text(
                "🪙 ${points} P",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Center Content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Logo
            val offsetY = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                offsetY.animateTo(
                    targetValue = -10f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = tween(2000, easing = androidx.compose.animation.core.EaseInOutSine),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    )
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = offsetY.value }
                    .size(88.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF5C6BFA)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome, 
                    contentDescription = null, 
                    tint = Color.White, 
                    modifier = Modifier.size(44.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "CashAI 비서", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF111827)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "궁금한 것은 무엇이든 물어보세요.\n대화할수록 포인트가 쌓여요!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Input Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color.White, RoundedCornerShape(28.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    placeholder = { Text("질문을 입력해보세요...", color = Color.Gray.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    maxLines = 1,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Button(
                    onClick = onSubmit,
                    enabled = inputValue.isNotBlank(),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.size(56.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5C6BFA),
                        disabledContainerColor = Color(0xFF5C6BFA).copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Suggestion Chips
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Just showing first 3 for better layout
                suggestions.take(3).forEach { suggestion ->
                    Surface(
                        onClick = { onSuggestionClick(suggestion) },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f)),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4B5563),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Bottom Nav (Static Preview)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.8f))
                .border(1.dp, Color.White, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IdleNavItem(icon = Icons.AutoMirrored.Filled.Chat, label = "채팅", active = true, onClick = {})
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.2f)).align(Alignment.CenterVertically))
            IdleNavItem(icon = Icons.Default.CardGiftcard, label = "리워드", active = false, onClick = { onNavigateTab("rewards") })
            IdleNavItem(icon = Icons.Default.Store, label = "상점", active = false, onClick = { onNavigateTab("shop") })
            IdleNavItem(icon = Icons.Default.Person, label = "MY", active = false, onClick = { onNavigateTab("mypage") })
        }
    }
}

/** IdleOverlay 하단의 탭 버튼 하나 (채팅/리워드/상점/MY) */
@Composable
private fun IdleNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) Color(0xFF5C6BFA) else Color.Transparent)
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Color.White else Color(0xFF9CA3AF),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) Color(0xFF5C6BFA) else Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** 리워드 광고 풀스크린 모달. 일정 시간 후 건너뛰기/시청 완료 후 포인트 지급 + AI 답변 표시 */
@Composable
private fun RewardAdModal(
    ad: AdInfo,
    onComplete: () -> Unit,
    onClose: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var canSkip by remember { mutableStateOf(false) }
    var phaseComplete by remember { mutableStateOf(false) }
    val duration = 5000L

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        while (!phaseComplete) {
            val elapsed = System.currentTimeMillis() - startedAt
            progress = (elapsed / duration.toFloat()).coerceIn(0f, 1f)
            if (elapsed >= 3000L) canSkip = true
            if (elapsed >= duration) phaseComplete = true
            delay(16) // ~60fps
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("리워드 광고", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
            
            if (canSkip && !phaseComplete) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(99.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("건너뛰기", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        AnimatedVisibility(
            visible = !phaseComplete,
            enter = scaleIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                // Ad Creative
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(ad.bg)),
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(ad.accent).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(ad.emoji, style = MaterialTheme.typography.displayLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    ad.brand, 
                                    color = Color(ad.accent), 
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                ad.tagline, 
                                textAlign = TextAlign.Center, 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(ad.accent)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text(ad.cta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("광고 시청 중...", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    Text("${((1f - progress) * (duration / 1000)).toInt() + 1}초 남음", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxSize()
                            .background(Color(0xFFFF6B00))
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = phaseComplete,
            enter = scaleIn() + fadeIn(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B00)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("시청 완료! 🎉", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("리워드 포인트가 지급되었어요", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF6B00).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 40.dp, vertical = 16.dp)
                ) {
                    Text("+30 P", color = Color(0xFFFF6B00), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Button(
                    onClick = onComplete, 
                    modifier = Modifier.fillMaxWidth().height(56.dp), 
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BFA))
                ) {
                    Text("AI 답변 확인하기 →", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 사용자 메시지 키워드에 맞는 광고를 adDatabase에서 찾아 반환. 없으면 defaultAd */
private fun matchAd(userMessage: String): AdInfo {
    val lower = userMessage.lowercase()
    return adDatabase.firstOrNull { (keywords, _) ->
        keywords.any { keyword -> lower.contains(keyword) }
    }?.second ?: defaultAd
}

/** 간단한 키워드 매칭으로 AI 답변 문자열 생성 (실제 앱에서는 API 호출로 대체) */
private fun generateAiResponse(userMessage: String): String {
    val lower = userMessage.lowercase()
    return when {
        listOf("안녕", "hello", "hi", "반가").any { lower.contains(it) } ->
            "안녕하세요! 😊 저는 CashAI 비서예요. 질문하시면 바로 답변 드릴게요. 대화하면서 포인트도 모아보세요!"
        listOf("음식", "맛집", "먹", "배달", "메뉴", "점심", "저녁", "밥").any { lower.contains(it) } ->
            "오늘 뭐 드실지 고민이시군요! 😋 요즘 인기 있는 메뉴는 마라탕, 스시 부리또, 감자탕 등이에요."
        listOf("여행", "휴가", "비행기", "해외", "여행지").any { lower.contains(it) } ->
            "여행 계획을 세우고 계시군요! ✈️ 예산과 기간을 알려주시면 맞춤 코스를 추천해 드릴게요!"
        listOf("운동", "헬스", "다이어트", "건강").any { lower.contains(it) } ->
            "건강 관리에 관심이 있으시군요! 💪 하루 30분 걷기만 해도 큰 효과가 있어요."
        else -> {
            val short = userMessage.take(20)
            "\"${if (userMessage.length > 20) "$short..." else short}\"에 대해 답변 드릴게요! 🤖 관련 정보를 분석해보면 다양한 관점에서 살펴볼 수 있어요."
        }
    }
}
