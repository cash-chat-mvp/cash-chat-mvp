# [DOCS] CC-159 · Gemini 기반 채팅 Provider 및 SSE 초안

- Jira: `CC-159`
- Branch: `feature/cc-159-chat-llm-provider`
- 작성일: `2026-04-18`
- 대상: Backend / Frontend 협업

## 1. 이번 작업에서 반영한 내용

### 1-1. 개발 프로필의 Gemini 모델 고정

- `application-dev.yaml`에서 Spring AI OpenAI-compatible endpoint를 Gemini로 유지
- 개발 모델을 `gemini-3.1-flash-lite-preview`로 고정
- dev 프로필 설정을 검증하는 테스트 추가

```yaml
spring:
  ai:
    openai:
      api-key: ${GEMINI_API_KEY:your-gemini-api-key}
      base-url: https://generativelanguage.googleapis.com/v1beta/openai
      chat:
        options:
          model: gemini-3.1-flash-lite-preview
```

### 1-2. 채팅 저장 구조 추가

추가 테이블 개념은 아래와 같다.

- `conversations`
  - 사용자별 대화방
- `chat_messages`
  - 대화방별 메시지
  - `role`: `SYSTEM`, `USER`, `ASSISTANT`
  - `status`: `STREAMING`, `COMPLETED`, `FAILED`

구조를 이렇게 나눈 이유는 아래와 같다.

- LLM provider는 stateless하게 유지
- 대화 이력은 우리 DB가 source of truth가 되도록 유지
- 이후 OpenAI 구현체 추가 시 저장 구조를 그대로 재사용 가능
- `CC-160` SSE, `CC-161` 대화 기록 조회와 자연스럽게 연결 가능

### 1-3. LLM 추상화 도입

추가 구성:

- `LlmProvider`
- `LlmMessage`
- `LlmMessageRole`
- `GeminiLlmProvider`

책임 분리는 아래처럼 가져갔다.

- `ChatService`
  - 대화방 검증
  - 사용자 메시지 저장
  - 히스토리 조회
  - assistant 메시지 상태 전이
- `GeminiLlmProvider`
  - `messages -> response stream` 변환
  - 외부 Gemini API 호출 어댑터 역할

### 1-4. 공개 SSE API 초안 추가

현재 공개 API는 Jira 흐름에 맞춰 아래 경로로 맞췄다.

```http
POST /api/v1/chat/stream
Accept: text/event-stream
Content-Type: application/json
Authorization: Bearer {accessToken}
```

요청 예시:

```json
{
  "conversationId": 7,
  "message": "hello"
}
```

처리 흐름:

1. 인증 사용자 확인
2. conversation 소유권 검증
3. `USER` 메시지 저장
4. 대화 히스토리 조회 후 provider 호출
5. `ASSISTANT` 메시지를 `STREAMING`으로 생성
6. 스트리밍 완료 시 `COMPLETED`, 실패 시 `FAILED`

## 2. 로컬 실행을 위한 환경 변수

`apps/backend/.env.example` 기준으로 아래 키가 필요하다.

```properties
GEMINI_API_KEY=your-gemini-api-key
OPENAI_API_KEY=your-openai-api-key
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
JWT_SECRET=this-is-a-development-secret-key-at-least-32-bytes-long!!
```

주의:

- `.env`에서 OAuth / JWT 값이 완전히 비어 있으면 properties binding이 실패할 수 있다
- 실제 Gemini 호출 테스트를 하려면 `GEMINI_API_KEY`만 실값으로 넣으면 된다
- 로컬 테스트만 돌릴 때도 최소 placeholder 문자열은 채워두는 편이 안전하다

## 3. 검증한 테스트

아래 테스트를 추가하거나 통과 확인했다.

- `DevGeminiProfileTest`
- `ChatPersistenceIntegrationTest`
- `GeminiLlmProviderTest`
- `ChatServiceTest`
- `ChatControllerTest`

실행 예시:

```bash
cd apps/backend
./gradlew test
```

## 4. 현재 기준 결정 사항

- dev/test LLM은 Gemini 사용
- 모델은 `gemini-3.1-flash-lite-preview`
- provider는 stateless 유지
- 대화 이력은 DB 저장
- 공개 스트리밍 API는 `POST /api/v1/chat/stream`
- 내부 저장 구조는 `conversations` / `chat_messages`

## 5. 다음 작업 연결 포인트

- `CC-160`
  - SSE 이벤트 포맷 구체화
  - 프론트와 chunk/event 이름 정합성 맞추기
- `CC-161`
  - 대화 목록 및 메시지 조회 API
  - conversation 생성 / 재진입 흐름
- 이후
  - `OpenAiLlmProvider` 추가
  - 모델별 설정 분리
