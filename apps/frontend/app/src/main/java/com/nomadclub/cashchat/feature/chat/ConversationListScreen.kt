package com.nomadclub.cashchat.feature.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.ChatApi
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    chatApi: ChatApi = koinInject(),
) {
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ConversationSummaryDto?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationSummaryDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationSummaryDto?>(null) }
    var renameInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { chatApi.listConversations() }
            .onSuccess { conversations = it }
            .onFailure { loadFailed = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("대화") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewConversation) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("새 대화")
            }
        },
    ) { padding ->
        // 강제 언래핑(!!) 대신 불변 지역값으로 캡처해 스마트캐스트를 활용한다.
        val currentConversations = conversations
        when {
            loadFailed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("목록을 불러오지 못했어요")
            }
            currentConversations == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            currentConversations.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🐣", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("첫 대화를 시작해보세요")
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(currentConversations, key = { it.conversationId }) { conversation ->
                    Box {
                        ListItem(
                            headlineContent = { Text(conversation.title) },
                            supportingContent = {
                                conversation.lastMessage?.let { Text(it, maxLines = 1) }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpenConversation(conversation.conversationId) },
                                onLongClick = { menuFor = conversation },
                            ),
                        )
                        DropdownMenu(
                            expanded = menuFor?.conversationId == conversation.conversationId,
                            onDismissRequest = { menuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (FeatureFlags.CONVERSATION_EDIT) "이름 변경" else "이름 변경 (준비 중)") },
                                enabled = FeatureFlags.CONVERSATION_EDIT,
                                onClick = {
                                    renameInput = conversation.title
                                    renameTarget = conversation
                                    menuFor = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (FeatureFlags.CONVERSATION_EDIT) "삭제" else "삭제 (준비 중)") },
                                enabled = FeatureFlags.CONVERSATION_EDIT,
                                onClick = {
                                    deleteTarget = conversation
                                    menuFor = null
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("이름 변경") },
            text = {
                OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = renameInput.trim()
                    renameTarget = null
                    if (title.isEmpty()) return@TextButton
                    scope.launch {
                        runCatching { chatApi.renameConversation(target.conversationId, title) }
                            .onSuccess {
                                conversations = conversations?.map {
                                    if (it.conversationId == target.conversationId) it.copy(title = title) else it
                                }
                            }
                            .onFailure { Toast.makeText(context, "이름 변경에 실패했어요", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("취소") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("대화 삭제") },
            text = { Text("\"${target.title}\" 대화를 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    // optimistic 제거 — 실패 시 복원
                    val before = conversations
                    conversations = conversations?.filterNot { it.conversationId == target.conversationId }
                    scope.launch {
                        runCatching { chatApi.deleteConversation(target.conversationId) }
                            .onFailure {
                                conversations = before
                                Toast.makeText(context, "삭제에 실패했어요", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
        )
    }
}
