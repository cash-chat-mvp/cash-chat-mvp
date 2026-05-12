# CC-161 Chat History API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/v1/chat/history/{uuid}`로 인증 사용자의 대화 기록을 조회할 수 있게 하고, Swagger와 Confluence까지 API 사용 준비를 마친다.

**Architecture:** `Conversation`에는 내부 PK `Long id`를 유지하면서 외부 공개용 `UUID uuid`를 추가한다. 조회 API는 UUID로 conversation을 찾고, 소유권을 확인한 뒤 기존 `chat_messages.conversation_id`와 `created_at` 인덱스를 통해 메시지를 시간순으로 반환한다. 구현 후 Swagger/OpenAPI 테스트와 Confluence 문서로 FE가 API 계약을 바로 확인할 수 있게 한다.

**Tech Stack:** Kotlin, Spring Boot MVC, Spring Data JPA, Hibernate, MySQL Testcontainers, Kotest, Mockito-Kotlin, springdoc-openapi, Atlassian Confluence.

---

## 파일 구조

- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/Conversation.kt`
  - conversation 외부 공개용 `uuid` 컬럼을 가진다.
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ConversationRepository.kt`
  - UUID 기반 conversation 조회 메서드를 제공한다.
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationNotFoundException.kt`
  - 존재하지 않는 conversation UUID를 404로 매핑하기 위한 도메인 예외다.
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationAccessDeniedException.kt`
  - 타인 conversation 접근을 403으로 매핑하기 위한 도메인 예외다.
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatHistory.kt`
  - 서비스 계층이 컨트롤러로 전달하는 history 조회 결과 모델이다.
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatService.kt`
  - `getHistory(userId: Long, conversationUuid: UUID)`를 추가한다.
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ChatHistoryResponse.kt`
  - API 응답 DTO다.
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/exception/ChatExceptionHandler.kt`
  - chat 도메인 예외를 JSON 에러 응답으로 변환한다.
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt`
  - `GET /api/v1/chat/history/{uuid}` 엔드포인트와 Swagger annotation을 추가한다.
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/persistence/ChatPersistenceIntegrationTest.kt`
  - conversation UUID와 unique index를 검증한다.
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceTest.kt`
  - history 조회 서비스의 성공, 403, 404 흐름을 검증한다.
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatControllerTest.kt`
  - history API의 JSON 응답, 403/404, 잘못된 UUID 400을 검증한다.
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/OpenApiDocumentationTest.kt`
  - OpenAPI 문서에 history path와 summary가 노출되는지 검증한다.
- Create or update Confluence page: `[DOCS] CC-161 · 대화 기록 조회 API`
  - 구현 요약, DB 변경점, API 계약, Swagger 확인 방법, 테스트 결과, FE 연동 포인트를 기록한다.

---

### Task 1: Conversation UUID 영속성 추가

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/Conversation.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ConversationRepository.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/persistence/ChatPersistenceIntegrationTest.kt`

- [ ] **Step 1: 실패하는 persistence 테스트를 먼저 작성한다**

`ChatPersistenceIntegrationTest.kt`에 `java.util.UUID` import를 추가한다.

```kotlin
import java.util.UUID
```

기존 테스트 `conversation owner and ordered messages are persisted in mysql with the history index` 끝부분에 아래 검증을 추가한다.

```kotlin
            persistedConversation.uuid shouldBe conversation.uuid
            conversationRepository.findByUuid(conversation.uuid)?.id shouldBe conversationId

            val conversationIndexes = JdbcTemplate(dataSource).queryForList("SHOW INDEX FROM conversations")
            val hasUniqueUuidIndex = conversationIndexes.any {
                it["Column_name"] == "uuid" && (it["Non_unique"] as Number).toInt() == 0
            }
            hasUniqueUuidIndex shouldBe true
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatPersistenceIntegrationTest"
```

Expected: compile fail. `Conversation.uuid` 또는 `ConversationRepository.findByUuid`가 아직 없다는 오류가 나와야 한다.

- [ ] **Step 3: `Conversation`과 repository를 구현한다**

`Conversation.kt`를 아래 형태로 수정한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "conversations")
class Conversation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    var title: String? = null,
) : BaseEntity()
```

`ConversationRepository.kt`를 아래처럼 수정한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, Long> {
    fun findByUuid(uuid: UUID): Conversation?
}
```

- [ ] **Step 4: persistence 테스트 통과를 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatPersistenceIntegrationTest"
```

Expected: PASS. MySQL Testcontainers에서 `conversations.uuid`가 생성되고 unique index가 잡혀야 한다.

- [ ] **Step 5: 커밋한다**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/Conversation.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ConversationRepository.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/persistence/ChatPersistenceIntegrationTest.kt
git commit -m "feat: add conversation uuid for chat history"
```

---

### Task 2: ChatService history 조회 로직 추가

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationNotFoundException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationAccessDeniedException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatHistory.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceTest.kt`

- [ ] **Step 1: 실패하는 서비스 테스트를 작성한다**

`ChatServiceTest.kt`에 import를 추가한다.

```kotlin
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import java.util.UUID
```

`init` 블록 안에 아래 테스트 3개를 추가한다.

```kotlin
        test("getHistory returns owned conversation messages ordered by creation time") {
            val conversationUuid = UUID.fromString("0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
            val conversation = conversation(ownerId = 1L, uuid = conversationUuid)
            val first = ChatMessage(
                id = 10L,
                conversation = conversation,
                role = MessageRole.USER,
                content = "hello",
                status = MessageStatus.COMPLETED
            )
            val second = ChatMessage(
                id = 11L,
                conversation = conversation,
                role = MessageRole.ASSISTANT,
                content = "hi there",
                status = MessageStatus.COMPLETED,
                model = "gpt-4o-mini"
            )

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(conversation)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(listOf(first, second))

            val history = chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)

            history.conversationUuid shouldBe conversationUuid
            history.messages shouldBe listOf(first, second)
            verify(chatMessageRepository).findAllByConversationIdOrderByCreatedAtAsc(1L)
        }

        test("getHistory rejects conversations owned by another user") {
            val conversationUuid = UUID.fromString("7a4e58c0-e8dc-4f26-9b86-fdc50d03d49f")
            val conversation = conversation(ownerId = 2L, uuid = conversationUuid)

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(conversation)

            shouldThrow<ConversationAccessDeniedException> {
                chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)
            }

            verify(chatMessageRepository, never()).findAllByConversationIdOrderByCreatedAtAsc(any())
        }

        test("getHistory rejects unknown conversation uuid") {
            val conversationUuid = UUID.fromString("bd1d0ebf-599c-4a11-a582-5a8fbb716a5c")

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(null)

            shouldThrow<ConversationNotFoundException> {
                chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)
            }

            verify(chatMessageRepository, never()).findAllByConversationIdOrderByCreatedAtAsc(any())
        }
```

`ChatServiceTest.kt`의 helper를 UUID를 받을 수 있게 바꾼다.

```kotlin
    private fun conversation(ownerId: Long, uuid: UUID = UUID.randomUUID()): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, uuid = uuid, user = owner, title = null)
    }
```

- [ ] **Step 2: 서비스 테스트가 실패하는지 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatServiceTest"
```

Expected: compile fail. `ConversationNotFoundException`, `ConversationAccessDeniedException`, `ChatService.getHistory`, `ChatHistory`가 아직 없다는 오류가 나와야 한다.

- [ ] **Step 3: 예외, 서비스 결과 모델, 서비스 메서드를 구현한다**

`ConversationNotFoundException.kt`를 생성한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.exception

import java.util.UUID

class ConversationNotFoundException(
    val conversationUuid: UUID,
) : RuntimeException("Conversation not found: $conversationUuid")
```

`ConversationAccessDeniedException.kt`를 생성한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.exception

import java.util.UUID

class ConversationAccessDeniedException(
    val conversationUuid: UUID,
) : RuntimeException("Conversation does not belong to user: $conversationUuid")
```

`ChatHistory.kt`를 생성한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import java.util.UUID

data class ChatHistory(
    val conversationUuid: UUID,
    val messages: List<ChatMessage>,
)
```

`ChatService.kt` import에 아래를 추가한다.

```kotlin
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import java.util.UUID
```

`ChatService` 클래스 안에 `stream` 메서드 아래에 아래 메서드를 추가한다.

```kotlin
    /**
     * Returns persisted messages for an owned conversation.
     */
    fun getHistory(userId: Long, conversationUuid: UUID): ChatHistory {
        return transactionTemplate.execute {
            val conversation = conversationRepository.findByUuid(conversationUuid)
                ?: throw ConversationNotFoundException(conversationUuid)

            if (conversation.user.id != userId) {
                throw ConversationAccessDeniedException(conversationUuid)
            }

            ChatHistory(
                conversationUuid = conversation.uuid,
                messages = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id),
            )
        } ?: error("Failed to load chat history")
    }
```

- [ ] **Step 4: 서비스 테스트 통과를 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatServiceTest"
```

Expected: PASS. 기존 stream 테스트와 새 history 테스트가 모두 통과해야 한다.

- [ ] **Step 5: 커밋한다**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationNotFoundException.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/ConversationAccessDeniedException.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatHistory.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatService.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceTest.kt
git commit -m "feat: add chat history service"
```

---

### Task 3: History API 응답 DTO와 컨트롤러 추가

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ChatHistoryResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/exception/ChatExceptionHandler.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatControllerTest.kt`

- [ ] **Step 1: 실패하는 컨트롤러 테스트를 작성한다**

`ChatControllerTest.kt` import를 추가한다.

```kotlin
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.service.ChatHistory
import com.wnl.cashchat.api.domain.chat.web.exception.ChatExceptionHandler
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.mockito.kotlin.any
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.time.Instant
import java.util.UUID
```

`@Import(PointExceptionHandler::class)`를 아래처럼 바꾼다.

```kotlin
@Import(PointExceptionHandler::class, ChatExceptionHandler::class)
```

`init` 블록 안에 아래 테스트 4개를 추가한다.

```kotlin
        test("chat history endpoint returns ordered messages for authenticated user") {
            val conversationUuid = UUID.fromString("0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
            val conversation = conversation(ownerId = 1L, uuid = conversationUuid)
            val createdAt = Instant.parse("2026-05-10T12:34:56Z")
            val message = ChatMessage(
                id = 10L,
                conversation = conversation,
                role = MessageRole.USER,
                content = "hello",
                status = MessageStatus.COMPLETED
            ).apply {
                this.createdAt = createdAt
            }

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenReturn(ChatHistory(conversationUuid = conversationUuid, messages = listOf(message)))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.conversationUuid").value(conversationUuid.toString()))
                .andExpect(jsonPath("$.messages[0].id").value(10))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("hello"))
                .andExpect(jsonPath("$.messages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.messages[0].createdAt").value("2026-05-10T12:34:56Z"))

            verify(chatService).getHistory(eq(1L), eq(conversationUuid))
        }

        test("chat history endpoint returns not found for unknown conversation") {
            val conversationUuid = UUID.fromString("bd1d0ebf-599c-4a11-a582-5a8fbb716a5c")

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenThrow(ConversationNotFoundException(conversationUuid))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
        }

        test("chat history endpoint returns forbidden for another user's conversation") {
            val conversationUuid = UUID.fromString("7a4e58c0-e8dc-4f26-9b86-fdc50d03d49f")

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenThrow(ConversationAccessDeniedException(conversationUuid))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("CONVERSATION_ACCESS_DENIED"))
        }

        test("chat history endpoint rejects malformed uuid") {
            mockMvc.perform(
                get("/api/v1/chat/history/not-a-uuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isBadRequest)

            verify(chatService, never()).getHistory(any(), any())
        }
```

`ChatControllerTest.kt` 클래스 안에 helper를 추가한다.

```kotlin
    private fun conversation(ownerId: Long, uuid: UUID): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, uuid = uuid, user = owner, title = null)
    }
```

- [ ] **Step 2: 컨트롤러 테스트가 실패하는지 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatControllerWebMvcTest"
```

Expected: compile fail. `ChatHistoryResponse`, `ChatExceptionHandler`, `GET /history/{uuid}`가 아직 없다는 오류 또는 404가 나와야 한다.

- [ ] **Step 3: 응답 DTO, 예외 핸들러, 컨트롤러 엔드포인트를 구현한다**

`ChatHistoryResponse.kt`를 생성한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.web.response

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.service.ChatHistory
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Persisted chat history for a conversation.")
data class ChatHistoryResponse(
    @field:Schema(description = "Public conversation identifier.", example = "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
    val conversationUuid: UUID,

    @field:Schema(description = "Messages ordered by creation time.")
    val messages: List<ChatHistoryMessageResponse>,
) {
    companion object {
        fun from(history: ChatHistory): ChatHistoryResponse =
            ChatHistoryResponse(
                conversationUuid = history.conversationUuid,
                messages = history.messages.map(ChatHistoryMessageResponse::from),
            )
    }
}

@Schema(description = "Persisted chat message.")
data class ChatHistoryMessageResponse(
    @field:Schema(description = "Internal message identifier.", example = "10")
    val id: Long,

    @field:Schema(description = "Message role.", example = "USER")
    val role: MessageRole,

    @field:Schema(description = "Message content.", example = "hello")
    val content: String,

    @field:Schema(description = "Message persistence status.", example = "COMPLETED")
    val status: MessageStatus,

    @field:Schema(description = "Model used for assistant messages.", example = "gpt-4o-mini", nullable = true)
    val model: String?,

    @field:Schema(description = "Message creation time in UTC.", example = "2026-05-10T12:34:56Z")
    val createdAt: Instant,
) {
    companion object {
        fun from(message: ChatMessage): ChatHistoryMessageResponse =
            ChatHistoryMessageResponse(
                id = message.id,
                role = message.role,
                content = message.content,
                status = message.status,
                model = message.model,
                createdAt = message.createdAt,
            )
    }
}
```

`ChatExceptionHandler.kt`를 생성한다.

```kotlin
package com.wnl.cashchat.api.domain.chat.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.chat"])
class ChatExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleConversationNotFoundException(e: ConversationNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("CONVERSATION_NOT_FOUND", "Conversation not found"))

    @ExceptionHandler(ConversationAccessDeniedException::class)
    fun handleConversationAccessDeniedException(e: ConversationAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("CONVERSATION_ACCESS_DENIED", "Conversation does not belong to user"))
}
```

`ChatController.kt` import를 추가한다.

```kotlin
import com.wnl.cashchat.api.domain.chat.web.response.ChatHistoryResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.UUID
```

`ChatController` 클래스의 `stream` 메서드 위에 아래 메서드를 추가한다.

```kotlin
    /**
     * Returns persisted chat history for the authenticated user's conversation.
     */
    @GetMapping("/history/{uuid}")
    fun history(
        authentication: Authentication,
        @PathVariable uuid: UUID,
    ): ResponseEntity<ChatHistoryResponse> {
        val userId = authentication.principal as? Long
            ?: throw IllegalArgumentException("Invalid authenticated principal")

        return ResponseEntity.ok(ChatHistoryResponse.from(chatService.getHistory(userId, uuid)))
    }
```

- [ ] **Step 4: 컨트롤러 테스트 통과를 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatControllerWebMvcTest"
```

Expected: PASS. 기존 stream 테스트와 새 history 테스트가 모두 통과해야 한다.

- [ ] **Step 5: 커밋한다**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ChatHistoryResponse.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/exception/ChatExceptionHandler.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatControllerTest.kt
git commit -m "feat: add chat history api"
```

---

### Task 4: Swagger/OpenAPI 문서 노출 추가

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/OpenApiDocumentationTest.kt`

- [ ] **Step 1: 실패하는 OpenAPI 테스트를 작성한다**

`OpenApiDocumentationTest.kt`의 기존 테스트 이름을 아래처럼 바꾸고, history path와 summary 검증을 추가한다.

```kotlin
        test("openapi docs expose chat streaming and history metadata") {
            val response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

            response.contains("\"title\":\"Cash Chat API\"") shouldBe true
            response.contains("\"version\":\"v1\"") shouldBe true
            response.contains("/api/v1/chat/stream") shouldBe true
            response.contains("Stream a chat completion") shouldBe true
            response.contains("/api/v1/chat/history/{uuid}") shouldBe true
            response.contains("Get chat history") shouldBe true
        }
```

- [ ] **Step 2: OpenAPI 테스트가 실패하는지 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*OpenApiDocumentationTest"
```

Expected: FAIL. path는 보일 수 있지만 `"Get chat history"` summary가 아직 없어 실패해야 한다.

- [ ] **Step 3: history API Swagger annotation을 추가한다**

`ChatController.kt` import를 추가한다.

```kotlin
import com.wnl.cashchat.api.common.web.response.ErrorResponse
```

Task 3에서 추가한 `history` 메서드의 `@GetMapping` 아래에 아래 annotation을 추가한다.

```kotlin
    @Operation(
        summary = "Get chat history",
        description = "Returns persisted messages for an authenticated user's conversation."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Chat history returned successfully.",
                content = [Content(schema = Schema(implementation = ChatHistoryResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "The supplied conversation UUID is malformed.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "The conversation belongs to another user.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Conversation not found.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
```

메서드 파라미터 `uuid`에 Swagger 설명을 추가한다.

```kotlin
        @PathVariable
        @io.swagger.v3.oas.annotations.Parameter(
            description = "Public conversation UUID.",
            example = "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8"
        )
        uuid: UUID,
```

- [ ] **Step 4: OpenAPI 테스트 통과를 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*OpenApiDocumentationTest"
```

Expected: PASS. `/api/v1/chat/history/{uuid}`와 `"Get chat history"`가 `/v3/api-docs` 응답에 포함되어야 한다.

- [ ] **Step 5: 컨트롤러와 OpenAPI 테스트를 함께 확인한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatControllerWebMvcTest" --tests "*OpenApiDocumentationTest"
```

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/OpenApiDocumentationTest.kt
git commit -m "docs: expose chat history api in swagger"
```

---

### Task 5: 전체 백엔드 검증

**Files:**
- Verify: `apps/backend/**`

- [ ] **Step 1: CC-161 집중 테스트를 실행한다**

Run:

```powershell
.\gradlew.bat test --tests "*ChatServiceTest" --tests "*ChatControllerWebMvcTest" --tests "*ChatPersistenceIntegrationTest" --tests "*OpenApiDocumentationTest"
```

Expected: PASS.

- [ ] **Step 2: 전체 백엔드 테스트를 실행한다**

Run:

```powershell
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 3: 변경 파일에 Android 또는 unrelated workflow 변경이 없는지 확인한다**

Run:

```powershell
git diff --name-only upstream/dev...HEAD
```

Expected: 출력은 CC-161 backend, spec, plan 파일만 포함한다. `apps/frontend/**`, Android workflow, nginx workflow는 포함되면 안 된다.

- [ ] **Step 4: diff whitespace를 확인한다**

Run:

```powershell
git diff --check upstream/dev...HEAD
```

Expected: PASS. whitespace error가 없어야 한다.

---

### Task 6: Confluence API 문서 작성

**Files:**
- Create page in Confluence space: `FCTC`
- Title: `[DOCS] CC-161 · 대화 기록 조회 API`

- [ ] **Step 1: Confluence에 올릴 문서 본문을 준비한다**

아래 Markdown을 Confluence page body로 사용한다. 이 문서는 Task 5의 집중 테스트와 전체 테스트가 모두 PASS인 상태에서 생성한다.

````markdown
# [DOCS] CC-161 · 대화 기록 조회 API

* Jira: `CC-161`
* Branch: `feature/cc-161-chat-history-api`
* 작성일: `2026-05-10`
* 대상: Backend / Frontend 협업

## 1. 이번 작업에서 반영한 내용

### 1-1. Conversation 공개 UUID 추가

* 내부 PK `conversations.id`는 유지했습니다.
* 외부 API 조회용 `conversations.uuid`를 추가했습니다.
* `uuid`는 `null`이 아니며 unique 값입니다.
* `chat_messages`는 기존처럼 `conversation_id`로 conversation을 참조합니다.

### 1-2. 대화 기록 조회 API 추가

* `GET /api/v1/chat/history/{uuid}` 엔드포인트를 추가했습니다.
* 인증된 사용자만 호출할 수 있습니다.
* 요청한 conversation이 현재 사용자 소유일 때만 메시지를 반환합니다.
* 메시지는 `createdAt` 오름차순으로 반환합니다.
* 저장된 메시지의 `role`, `content`, `status`, `model`, `createdAt`을 내려줍니다.

### 1-3. Swagger 문서화

* Swagger UI와 `/v3/api-docs`에 history API가 노출됩니다.
* `200`, `400`, `401`, `403`, `404` 응답을 문서화했습니다.

## 2. API 계약

### 요청

```http
GET /api/v1/chat/history/{uuid}
Authorization: Bearer {accessToken}
Accept: application/json
```

### 정상 응답

```json
{
  "conversationUuid": "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8",
  "messages": [
    {
      "id": 10,
      "role": "USER",
      "content": "hello",
      "status": "COMPLETED",
      "model": null,
      "createdAt": "2026-05-10T12:34:56Z"
    }
  ]
}
```

### 에러

* `400 Bad Request`: UUID 형식이 잘못된 경우
* `401 Unauthorized`: 인증이 없거나 실패한 경우
* `403 Forbidden`: 타인 conversation을 조회한 경우
* `404 Not Found`: conversation UUID가 존재하지 않는 경우

## 3. Swagger 확인 방법

1. 백엔드 서버를 실행합니다.
2. Swagger UI에 접속합니다.
   * 로컬: `http://localhost:8080/swagger-ui.html`
   * 배포: `https://cashchat.duckdns.org/swagger-ui.html`
3. `Chat` 섹션에서 `GET /api/v1/chat/history/{uuid}`를 확인합니다.
4. Authorize에 Bearer token을 넣고 UUID를 입력해 호출합니다.

## 4. 검증한 테스트

```powershell
.\gradlew.bat test --tests "*ChatServiceTest" --tests "*ChatControllerWebMvcTest" --tests "*ChatPersistenceIntegrationTest" --tests "*OpenApiDocumentationTest"
.\gradlew.bat test
```

검증 결과:

* 집중 테스트: PASS
* 전체 테스트: PASS

## 5. FE 연동 포인트

* history 조회에는 conversation의 public UUID가 필요합니다.
* 현재 `POST /api/v1/chat/stream`은 기존 `conversationId: Long` 계약을 유지합니다.
* conversation 생성/목록 API가 필요하면 후속 티켓으로 분리해야 합니다.
* 클라이언트는 `messages[].status`를 보고 실패 또는 스트리밍 중 저장된 메시지를 구분할 수 있습니다.

## 6. 후속 작업

* conversation 생성 API 또는 목록 API
* history pagination
* stream API의 conversation 식별자를 UUID로 전환할지 여부
````

- [ ] **Step 2: Confluence 페이지를 만든다**

Atlassian connector로 아래 값으로 페이지를 생성한다.

```text
cloudId: 3f3043a6-b325-44f8-bce7-6484d28f8761
spaceId: FCTC
contentFormat: markdown
title: [DOCS] CC-161 · 대화 기록 조회 API
body: Step 1의 Markdown
```

Expected: `moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/...` URL이 반환된다.

- [ ] **Step 3: Jira 또는 최종 응답에 Confluence URL을 남긴다**

최종 응답에 아래 형식으로 링크를 포함한다.

```text
Confluence: https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/...
```

---

### Task 7: 최종 상태 점검과 PR 준비

**Files:**
- Verify: entire branch

- [ ] **Step 1: git 상태를 확인한다**

Run:

```powershell
git status --short --branch
```

Expected: clean worktree.

- [ ] **Step 2: 커밋 로그를 확인한다**

Run:

```powershell
git log --oneline --decorate -8
```

Expected: CC-161 설계, 구현, Swagger 문서화 커밋이 `upstream/dev` 위에 쌓여 있다.

- [ ] **Step 3: PR 설명에 넣을 요약을 준비한다**

```markdown
## Summary

- Add public UUID to conversations for chat history lookup
- Add `GET /api/v1/chat/history/{uuid}` with ownership checks
- Document the endpoint in Swagger/OpenAPI
- Add service, controller, persistence, and OpenAPI tests
- Publish CC-161 API handoff notes to Confluence

## Test

- `.\gradlew.bat test --tests "*ChatServiceTest" --tests "*ChatControllerWebMvcTest" --tests "*ChatPersistenceIntegrationTest" --tests "*OpenApiDocumentationTest"`
- `.\gradlew.bat test`

## Docs

- Confluence 문서는 `[DOCS] CC-161 · 대화 기록 조회 API`로 작성했습니다.
```

- [ ] **Step 4: 필요하면 브랜치를 push한다**

Run:

```powershell
git push origin HEAD:feature/cc-161-chat-history-api
```

Expected: branch is pushed to `Jeonj95/cash-chat-mvp`.
