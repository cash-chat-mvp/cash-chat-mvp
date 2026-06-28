package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val LocalChatHistoryJson = Json {
    ignoreUnknownKeys = true
}

interface LocalChatHistory {
    suspend fun load(): List<ChatItem>

    suspend fun save(items: List<ChatItem>)

    suspend fun clear()
}

@Serializable
private data class StoredLocalChatRow(
    val role: String? = null,
    val text: String? = null,
)

class JsonFileLocalChatHistory(
    private val filePath: String = joinPath(localModelsDir(), "local-chat-history.json"),
) : LocalChatHistory {

    override suspend fun load(): List<ChatItem> {
        val content = readTextFile(filePath) ?: return emptyList()
        val rows = runCatching {
            LocalChatHistoryJson.decodeFromString<List<StoredLocalChatRow>>(content)
        }.getOrDefault(emptyList())

        return rows.mapIndexedNotNull { index, row ->
            val text = row.text ?: return@mapIndexedNotNull null
            when (row.role) {
                ROLE_USER -> ChatItem.UserMessage(
                    id = "history-user-$index",
                    text = text,
                    status = ChatItem.SendStatus.CONFIRMED,
                )

                ROLE_ASSISTANT -> ChatItem.AssistantMessage(
                    id = "history-assistant-$index",
                    text = text,
                    isStreaming = false,
                )

                else -> null
            }
        }
    }

    override suspend fun save(items: List<ChatItem>) {
        val rows = items.mapNotNull { item ->
            when (item) {
                is ChatItem.UserMessage -> StoredLocalChatRow(ROLE_USER, item.text)
                is ChatItem.AssistantMessage -> StoredLocalChatRow(ROLE_ASSISTANT, item.text)
                else -> null
            }
        }
        parentPath(filePath)?.let(::ensureDirectory)
        writeTextFile(filePath, LocalChatHistoryJson.encodeToString(rows))
    }

    override suspend fun clear() {
        deleteFile(filePath)
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
