# CC-161 대화 기록 조회 API 설계

## 배경

- Jira: `CC-161`
- 요약: `[BE] 대화 기록`
- 브랜치: `feature/cc-161-chat-history-api`
- 대상: Backend API, Swagger/OpenAPI, Confluence 인수인계 문서

현재 백엔드에는 이미 `chat_messages` 저장 구조와 스트리밍 중 메시지 저장 흐름이 있습니다. CC-161에서는 이 저장된 메시지를 클라이언트가 조회할 수 있도록, conversation의 외부 공개용 UUID와 대화 기록 조회 API를 추가합니다.

## 목표

- 클라이언트가 내부 숫자 PK를 몰라도 대화 기록을 조회할 수 있도록 `Conversation`에 공개용 UUID를 추가합니다.
- 인증 사용자가 `GET /api/v1/chat/history/{uuid}`로 대화 기록을 조회할 수 있게 합니다.
- 선택한 conversation의 메시지를 생성 시간 오름차순으로 반환합니다.
- 대화 소유권을 확인한 뒤 본인 대화만 반환합니다.
- Swagger에서 요청, 응답, 에러 케이스를 확인할 수 있게 문서화합니다.
- 구현과 검증 후 기존 `[DOCS] CC-xxx` 형식에 맞춰 Confluence 문서를 작성합니다.

## 제외 범위

- 이번 티켓에서는 기존 `POST /api/v1/chat/stream` 요청 계약을 변경하지 않습니다.
- 기존 `chat_messages.conversation_id` FK를 UUID 기반으로 바꾸지 않습니다.
- 페이지네이션은 이번 범위에 넣지 않습니다. 리뷰나 실제 사용 중 필요성이 확인되면 후속 작업으로 분리합니다.
- 포인트 차감, LLM Provider 선택, Android 클라이언트 동작은 변경하지 않습니다.

## 데이터 모델

`Conversation`은 내부 PK인 `Long id`를 유지하고, 외부 조회용 `uuid` 컬럼을 추가합니다.

- 타입: `java.util.UUID`
- nullable: false
- unique: true
- updatable: false
- 생성 방식: `Conversation` 엔티티 생성 시 애플리케이션에서 UUID 자동 생성

`chat_messages`는 계속 `conversation_id`로 `conversations.id`를 참조합니다. 메시지 조회는 기존 `chat_messages(conversation_id, created_at)` 인덱스를 그대로 사용합니다.

현재 프로젝트에는 Flyway나 Liquibase 같은 마이그레이션 도구가 없으므로, 구현은 JPA 매핑으로 DDL 형태를 표현하고 기존 MySQL Testcontainers 통합 테스트로 검증합니다. 운영 DB에 수동 DDL 적용이 필요하면 Confluence 문서에 `conversations.uuid` 컬럼과 unique index 형태를 함께 적어둡니다.

## API 계약

```http
GET /api/v1/chat/history/{uuid}
Authorization: Bearer {accessToken}
Accept: application/json
```

정상 응답 예시:

```json
{
  "conversationUuid": "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8",
  "messages": [
    {
      "id": 1,
      "role": "USER",
      "content": "hello",
      "status": "COMPLETED",
      "model": null,
      "createdAt": "2026-05-10T12:34:56Z"
    }
  ]
}
```

메시지는 `createdAt` 오름차순으로 반환합니다. 저장된 상태를 그대로 내려주므로 클라이언트는 `COMPLETED`, `FAILED`, `STREAMING` 같은 상태를 구분할 수 있습니다.

## 인증과 에러

- `401 Unauthorized`: 인증이 없거나 유효하지 않은 경우
- `403 Forbidden`: conversation은 존재하지만 현재 사용자의 대화가 아닌 경우
- `404 Not Found`: 전달된 UUID에 해당하는 conversation이 없는 경우
- `400 Bad Request`: path variable을 UUID로 파싱할 수 없는 경우

conversation 조회와 소유권 검증은 서비스 계층에서 담당합니다. 컨트롤러는 기존 채팅 스트림 API와 동일하게 `Authentication.principal`에서 인증된 사용자 ID를 꺼냅니다.

## 백엔드 구성 요소

- `Conversation`: `uuid` 컬럼 추가
- `ConversationRepository`: `findByUuid(uuid: UUID)` 추가
- `ChatMessageRepository`: 기존 `findAllByConversationIdOrderByCreatedAtAsc` 재사용
- `ChatService`: `getHistory(userId: Long, conversationUuid: UUID)` 추가
- 응답 DTO: chat web response 패키지에 history 응답과 message 응답 타입 추가
- `ChatController`: `GET /history/{uuid}` 엔드포인트와 Swagger annotation 추가
- Chat 예외 처리: conversation 없음과 소유권 불일치를 명확히 반환할 수 있도록 필요한 경우 전용 예외와 controller advice 추가

## Swagger와 API 인수인계

`/v3/api-docs`와 Swagger UI에서 다음 내용을 확인할 수 있어야 합니다.

- path: `/api/v1/chat/history/{uuid}`
- method: `GET`
- 인증 필요
- history 응답 schema
- `200`, `400`, `401`, `403`, `404` 응답 설명

구현 후 Confluence에는 `[DOCS] CC-161 · 대화 기록 조회 API` 페이지를 작성합니다. 내용은 API 계약, 요청/응답 예시, DB 변경점, Swagger 확인 방법, FE 연동 포인트를 포함하고, 기존 CC-160 문서 형식을 따릅니다.

## 테스트

집중 테스트 항목:

- 서비스가 인증된 사용자의 conversation history만 반환합니다.
- 서비스가 타인 conversation 조회를 거부합니다.
- 서비스가 존재하지 않는 UUID에 대해 not found를 반환합니다.
- Repository/JPA 통합 테스트에서 conversation UUID가 null이 아니고 unique 컬럼으로 생성되는지 확인합니다.
- 컨트롤러가 `GET /api/v1/chat/history/{uuid}` 요청을 `ChatService.getHistory`로 전달합니다.
- 컨트롤러 응답 JSON에 `conversationUuid`와 정렬된 메시지 목록이 포함됩니다.
- OpenAPI 문서에 `/api/v1/chat/history/{uuid}`가 노출됩니다.

검증 명령:

```powershell
.\gradlew.bat test --tests "*ChatServiceTest" --tests "*ChatControllerWebMvcTest" --tests "*ChatPersistenceIntegrationTest" --tests "*OpenApiDocumentationTest"
.\gradlew.bat test
```

## Confluence 완료 기준

코드 검증 후 `FCTC` 스페이스에 새 문서를 만들거나 기존 문서를 갱신합니다.

- 제목: `[DOCS] CC-161 · 대화 기록 조회 API`
- 포함 내용: Jira, 브랜치, 구현 요약, DB 변경점, API 계약, 에러 케이스, Swagger 사용 방법, 테스트 결과, FE 연동 포인트
- Jira `CC-161` 링크를 포함합니다.
- conversation 생성 API나 페이지네이션처럼 이번 범위를 벗어난 항목은 후속 작업으로 명시합니다.
