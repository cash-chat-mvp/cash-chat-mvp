# Chat Streaming Review Fixes Design

## Context

The current chat streaming implementation introduces a new LLM provider flow, SSE controller, persistence entities, and backend tests. Code review identified a mix of runtime correctness issues, repository guideline mismatches, and missing API documentation.

This design covers the minimum cohesive changes needed to make the feature safer to merge:

- fix streaming lifecycle and history handling bugs
- align backend tests with project conventions
- restore the development Gemini model to the documented baseline
- add OpenAPI metadata and Swagger documentation for the chat streaming endpoint
- raise docstring coverage by documenting the newly added chat flow types and methods

## Goals

- Prevent incomplete or failed assistant messages from polluting later prompts.
- Ensure assistant messages do not remain stuck in `STREAMING` after client disconnects.
- Preserve whitespace chunks from provider streaming responses.
- Cancel upstream subscriptions when SSE connections terminate.
- Keep internal exception details out of SSE error payloads.
- Add a database index that supports the conversation history hot path.
- Make the chat streaming API readable in Swagger UI.
- Align backend tests with the repository's Kotest and Testcontainers guidance.

## Non-Goals

- Re-document every controller in the backend.
- Redesign the chat domain model.
- Change authentication behavior beyond what is needed for Swagger access and controller tests.
- Replace the current provider abstraction or persistence schema beyond the required index.

## Proposed Changes

### 1. Persistence and Entity Mapping

`ChatMessage` will declare an index on `conversation_id` using JPA table metadata. This targets the existing repository method that loads ordered history by conversation. If the ORM mapping allows, the design will prefer a composite index on `conversation_id` and `created_at`; otherwise it will at minimum add an index on `conversation_id` without introducing schema ambiguity around inherited audit fields.

### 2. Chat Service Streaming Lifecycle

`ChatService.stream` will be updated to:

- persist the user message first
- build provider history from prior messages that are in `COMPLETED` status only
- always include the just-persisted user message in the provider prompt
- create the assistant placeholder in `STREAMING` state before provider subscription
- buffer streamed chunks as they arrive
- finalize assistant state from a single termination path that handles `complete`, `error`, and `cancel`

Termination behavior will be:

- `complete` -> save buffered content and mark assistant `COMPLETED`
- `error` -> save buffered content and mark assistant `FAILED`
- `cancel` -> save buffered content and mark assistant `COMPLETED` when any content was emitted, otherwise `FAILED`

The cancel behavior intentionally avoids leaving orphaned `STREAMING` rows while preserving partial output when the client disconnects after receiving content.

### 3. Gemini Provider Chunk Handling

`GeminiLlmProvider.stream` will preserve whitespace-only chunks. It will only filter out empty strings so that `" "` and `"\n"` remain part of the assembled assistant message.

### 4. SSE Controller Lifecycle

`ChatController.stream` will:

- keep an explicit subscription handle
- dispose of the subscription on emitter completion, timeout, and error callbacks
- send SSE `message` events for streamed chunks
- send a generic SSE `error` event payload such as `"stream failed"` instead of forwarding internal exception messages

The request validation contract remains enforced with `@Valid`, but the test setup will move away from `standaloneSetup` so validation and MVC infrastructure are actually exercised.

### 5. Swagger / OpenAPI

Backend Swagger support already exists through the `springdoc` dependency and security allowlist. This change will add:

- an `OpenAPI` bean with title, description, and version metadata for the backend API
- `@Tag` and `@Operation` annotations for the chat streaming controller
- response documentation that explains the SSE behavior
- schema descriptions for `ChatStreamRequest`

The documentation scope is intentionally limited to the new chat streaming surface instead of retrofitting every endpoint in the project.

### 6. Configuration Alignment

`application-dev.yaml` uses `gemini-3.1-flash-lite-preview`, and tests plus fixtures that assert or embed the development model string should stay aligned with that value.

## Testing Strategy

### Unit and Slice Tests

The following tests will be rewritten in Kotest style:

- `ChatServiceTest`
- `GeminiLlmProviderTest`
- `ChatControllerTest`
- `DevGeminiProfileTest`

Coverage targets:

- provider history excludes non-`COMPLETED` messages
- assistant messages are finalized correctly on complete, error, and cancel
- whitespace chunks are preserved
- controller validation rejects invalid requests through MVC infrastructure
- controller tears down subscriptions when the emitter lifecycle ends
- dev profile exposes the expected Gemini endpoint and model

### Persistence Integration Test

`ChatPersistenceIntegrationTest` will move from `@DataJpaTest` and in-memory defaults to a MySQL Testcontainers-backed integration test using Kotest. The test will validate:

- conversation/message persistence works against MySQL
- ordered retrieval by conversation still behaves correctly
- fixture model strings match the configured development baseline

If asserting the physical database index is practical through JDBC metadata, that verification will be added; otherwise the test will focus on the mapped schema functioning under MySQL without over-coupling to vendor-specific index names.

## Documentation / Docstrings

KDoc will be added to the newly introduced or modified chat-flow classes and methods that currently lack documentation, with emphasis on public service, provider, controller, and request types involved in this feature. The goal is to satisfy the repository's docstring coverage gate without adding low-value commentary.

## Risks and Mitigations

- Cancelling a stream can occur after partial output has already been sent.
  The service will persist buffered content and avoid leaving `STREAMING` rows behind.
- SSE tests can become brittle if tied to servlet implementation details.
  The controller tests will focus on observable behavior and subscription disposal rather than internal container specifics.
- Testcontainers can slow the backend suite.
  Only persistence integration coverage will use MySQL containers; pure service/provider/controller tests will remain isolated.
- Inherited audit fields may complicate composite index declarations.
  The implementation will prefer the simplest index declaration that is valid for the mapped entity hierarchy.

## Implementation Plan Preview

Implementation should proceed in this order:

1. Add or rewrite tests that expose the current runtime and configuration issues.
2. Update runtime chat streaming code to satisfy those failing tests.
3. Add OpenAPI configuration and chat endpoint annotations.
4. Replace the persistence integration test with a MySQL Testcontainers variant.
5. Add focused KDoc and run backend verification commands.
