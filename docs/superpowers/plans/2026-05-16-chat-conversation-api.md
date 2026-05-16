# Chat Conversation API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GPT-style chat room APIs so the app can list conversations, load a selected room's messages, and continue streaming replies into that room.

**Architecture:** Keep `ChatController` as the HTTP boundary and `ChatService` as the conversation/message orchestration layer. Add small request/response DTOs for conversation list/detail APIs, repository finder methods scoped by authenticated user, and keep `POST /api/v1/chat/stream` conversation-id based.

**Tech Stack:** Kotlin, Spring Boot MVC, Spring Data JPA, Kotest, Mockito, MockMvc.

---

## Files

- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatService.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ConversationRepository.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ChatMessageRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/request/CreateConversationRequest.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ConversationResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ConversationSummaryResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/ChatMessageResponse.kt`
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatControllerTest.kt` (`ChatControllerWebMvcTest`)
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceTest.kt`
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/persistence/ChatPersistenceIntegrationTest.kt`

## Task 1: Controller API Tests

- [ ] Add MockMvc tests for `POST /api/v1/chat/conversations`, `GET /api/v1/chat/conversations`, and `GET /api/v1/chat/conversations/{id}/messages`.
- [ ] Verify each endpoint passes the authenticated user id into `ChatService`.
- [ ] Run `.\gradlew.bat test --tests "*ChatControllerWebMvcTest"` from `apps/backend`; the pattern targets the test class declared inside `ChatControllerTest.kt`.

## Task 2: Service And Repository Tests

- [ ] Add `ChatService` tests for creating a conversation, listing summaries with last message preview, loading messages with ownership validation, and rejecting foreign conversations.
- [ ] Add persistence coverage for user-scoped conversation lookup and latest-message lookup.
- [ ] Run `.\gradlew.bat test --tests "*ChatServiceTest" --tests "*ChatPersistenceIntegrationTest"` from `apps/backend`; expect new tests to fail before implementation.

## Task 3: DTOs And Repository Methods

- [ ] Add request and response DTOs under `domain/chat/web`.
- [ ] Add user-scoped repository methods:
  - `ConversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId: Long)`
  - `ConversationRepository.findByIdAndUserId(id: Long, userId: Long)`
  - `ChatMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId: Long)`
- [ ] Run targeted tests and confirm compile errors move to service/controller implementation.

## Task 4: Service Implementation

- [ ] Implement `createConversation(userId, title)`, `listConversations(userId)`, and `getMessages(userId, conversationId)`.
- [ ] In `stream`, load the conversation by both id and user id, then save the conversation after user message creation so `updatedAt` moves and list ordering reflects recent activity.
- [ ] Keep provider history limited to completed messages plus the current user message.

## Task 5: Controller Implementation

- [ ] Wire new endpoints to `ChatService`.
- [ ] Keep `conversationId` required in `ChatStreamRequest`.
- [ ] Return JSON DTOs for normal endpoints and SSE for stream.

## Task 6: Verification

- [ ] Run `.\gradlew.bat test --tests "*ChatControllerWebMvcTest" --tests "*ChatServiceTest" --tests "*ChatPersistenceIntegrationTest"` from `apps/backend`.
- [ ] If Docker is unavailable for Testcontainers, run non-container unit/web tests and report the integration-test blocker.
- [ ] Inspect the diff for unrelated changes before final response.
