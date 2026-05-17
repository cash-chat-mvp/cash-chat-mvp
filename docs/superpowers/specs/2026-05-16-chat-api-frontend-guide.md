# Chat API Frontend Integration Guide

작성일: 2026-05-16

이 문서는 프론트에서 CashChat 채팅 기능을 연동할 때 필요한 전체 API 계약을 정리합니다. CC-161에서 추가된 GPT 스타일 대화방 API와 기존 채팅 스트리밍/히스토리 API를 함께 다룹니다.

## 핵심 흐름

프론트는 이제 채팅을 `대화방 목록 -> 대화방 메시지 -> 같은 대화방으로 이어서 전송` 흐름으로 사용합니다.

- 새 채팅 첫 전송 전: 대화방 생성
- 채팅 내역 화면: 대화방 목록 조회
- 방 진입: 해당 방 메시지 조회
- 메시지 전송: `conversationId`를 포함해서 스트리밍 호출

## API 목록

| 구분 | Method | Endpoint | 프론트 사용 위치 |
| --- | --- | --- | --- |
| 대화방 생성 | `POST` | `/api/v1/chat/conversations` | 새 채팅 첫 메시지 전송 직전 |
| 대화방 목록 조회 | `GET` | `/api/v1/chat/conversations` | 채팅 내역 사이드 메뉴 |
| 대화방 메시지 조회 | `GET` | `/api/v1/chat/conversations/{conversationId}/messages` | 기존 대화방 진입 시 |
| 채팅 스트리밍 | `POST` | `/api/v1/chat/stream` | 메시지 전송 시 |
| 기존 히스토리 조회 | `GET` | `/api/v1/chat/history/{uuid}` | legacy UUID 기반 히스토리 조회 |

모든 API는 인증이 필요합니다.

```http
Authorization: Bearer {accessToken}
```

## 권장 프론트 플로우

### 채팅 탭 첫 진입

첫 진입 화면에서는 API 호출이 필수는 아닙니다.

1. 빈 채팅 시작 화면을 보여준다.
2. 추천 질문 버튼과 입력창을 보여준다.
3. 사용자가 실제로 메시지를 보내기 전까지는 대화방을 만들지 않는다.

### 새 채팅에서 첫 메시지 전송

1. `POST /api/v1/chat/conversations` 호출
2. 응답으로 받은 `conversationId`를 현재 채팅 상태에 저장
3. 같은 `conversationId`로 `POST /api/v1/chat/stream` 호출
4. SSE 응답을 화면에 실시간 출력
5. 스트리밍 종료 후에도 같은 `conversationId`를 유지

### 채팅 내역 화면 열기

1. `GET /api/v1/chat/conversations`
2. 응답 배열을 그대로 렌더링
3. `title`은 굵게, `lastMessage`는 미리보기로 표시
4. 응답은 최신 대화순입니다.

### 기존 대화방 진입

1. 선택한 아이템의 `conversationId`를 저장
2. `GET /api/v1/chat/conversations/{conversationId}/messages`
3. 응답 메시지를 위에서 아래로 렌더링
4. 이후 입력창에서 보내는 메시지는 같은 `conversationId`로 `/stream` 호출

## 1. 대화방 생성

```http
POST /api/v1/chat/conversations
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### Request

```json
{
  "title": "영어 공부 방법"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | 아니오 | 대화방 제목. 보통 첫 사용자 메시지를 사용 |

`title`이 없거나 빈 문자열이면 서버 기본 제목이 사용됩니다.

### Response `200 OK`

```json
{
  "conversationId": 7,
  "title": "영어 공부 방법",
  "createdAt": "2026-05-16T01:00:00Z",
  "updatedAt": "2026-05-16T01:00:00Z"
}
```

## 2. 대화방 목록 조회

```http
GET /api/v1/chat/conversations
Authorization: Bearer {accessToken}
```

### Response `200 OK`

```json
[
  {
    "conversationId": 7,
    "title": "영어 공부 방법",
    "lastMessage": "영어 공부는 매일 조금씩 하는 게 좋아요.",
    "createdAt": "2026-05-16T01:00:00Z",
    "updatedAt": "2026-05-16T01:03:00Z"
  }
]
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `conversationId` | number | 대화방 ID |
| `title` | string | 대화방 제목 |
| `lastMessage` | string \| null | 가장 최근 메시지 미리보기. 메시지가 없으면 `null` |
| `createdAt` | string | 생성 시각 |
| `updatedAt` | string | 마지막 대화/업데이트 시각 |

정렬은 `updatedAt` 기준 내림차순입니다.

## 3. 대화방 메시지 조회

```http
GET /api/v1/chat/conversations/7/messages
Authorization: Bearer {accessToken}
```

### Response `200 OK`

```json
[
  {
    "messageId": 10,
    "role": "USER",
    "content": "영어 공부 방법",
    "status": "COMPLETED",
    "createdAt": "2026-05-16T01:00:01Z"
  },
  {
    "messageId": 11,
    "role": "ASSISTANT",
    "content": "영어 공부는 매일 짧게라도 반복하는 것이 좋아요.",
    "status": "COMPLETED",
    "createdAt": "2026-05-16T01:00:05Z"
  }
]
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `messageId` | number | 메시지 ID |
| `role` | string | `USER`, `ASSISTANT`, `SYSTEM` |
| `content` | string | 메시지 내용 |
| `status` | string | `COMPLETED`, `STREAMING`, `FAILED` |
| `createdAt` | string | 메시지 생성 시각 |

정렬은 `createdAt` 기준 오름차순입니다. 프론트는 응답 순서 그대로 렌더링하면 됩니다.

## 4. 채팅 스트리밍

CC-161 이후 `conversationId`는 필수입니다.

```http
POST /api/v1/chat/stream
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: text/event-stream
```

### Request

```json
{
  "conversationId": 7,
  "message": "그럼 하루에 얼마나 공부하면 돼?"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `conversationId` | number | 예 | 이어서 대화할 방 ID |
| `message` | string | 예 | 사용자 입력 메시지. 공백 불가 |

### Response `200 OK`

SSE 형식으로 내려옵니다.

```text
event: message
data: 하루 20분 정도라도 매일 반복하는 것이 좋아요.

```

오류 이벤트 예시:

```text
event: error
data: stream failed

```

프론트 처리 추천:

1. 사용자 메시지를 화면에 추가한다.
2. assistant 말풍선을 빈 상태로 생성한다.
3. `event: message`의 `data`를 assistant 말풍선에 append한다.
4. `event: error` 수신 시 assistant 말풍선을 실패 상태로 표시한다.
5. 스트림 종료 후 대화방 목록을 다시 조회하거나 현재 방의 `lastMessage`/`updatedAt`을 갱신한다.

## 5. 기존 히스토리 조회 Legacy

기존 UUID 기반 대화 히스토리 조회 API입니다. GPT 스타일 UI에서는 신규 conversation API 사용을 권장합니다.

```http
GET /api/v1/chat/history/{uuid}
Authorization: Bearer {accessToken}
```

### Response `200 OK`

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

## 에러 응답 공통 형식

```json
{
  "code": "CONVERSATION_NOT_FOUND",
  "message": "Conversation not found"
}
```

| 상황 | HTTP Status | code | 설명 |
| --- | --- | --- | --- |
| 인증 없음 | `401 Unauthorized` | 환경별 인증 에러 | 로그인 필요 |
| `conversationId` 누락 | `400 Bad Request` | validation error | `/stream` 요청 필수값 누락 |
| `message` 공백 | `400 Bad Request` | validation error | `/stream` 메시지 공백 |
| 포인트 부족 | `402 Payment Required` | `INSUFFICIENT_POINTS` | 채팅에 필요한 포인트 부족 |
| 없는 대화방 | `404 Not Found` | `CONVERSATION_NOT_FOUND` | 존재하지 않는 `conversationId` |
| 다른 사용자의 대화방 | `404 Not Found` | `CONVERSATION_NOT_FOUND` | 방 존재 여부 노출 방지를 위해 404 처리 |
| legacy UUID 방 접근 권한 없음 | `403 Forbidden` | `CONVERSATION_ACCESS_DENIED` | 기존 `/history/{uuid}` 정책 |

## 보안/소유권 정책

- 모든 conversation API는 인증된 사용자 기준으로만 조회합니다.
- 사용자는 자신의 대화방만 목록 조회, 메시지 조회, 스트리밍 전송할 수 있습니다.
- 다른 사용자의 `conversationId`는 존재하지 않는 방처럼 `404 Not Found`로 처리합니다.
- 이 정책은 대화방 존재 여부를 외부에 노출하지 않기 위한 것입니다.

## 프론트 상태 타입 예시

```ts
type ConversationSummary = {
  conversationId: number;
  title: string;
  lastMessage: string | null;
  createdAt: string;
  updatedAt: string;
};

type ChatMessage = {
  messageId: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  status: 'COMPLETED' | 'STREAMING' | 'FAILED';
  createdAt: string;
};
```

추천 상태:

```ts
const [conversations, setConversations] = useState<ConversationSummary[]>([]);
const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
const [messages, setMessages] = useState<ChatMessage[]>([]);
const [isStreaming, setIsStreaming] = useState(false);
```

## 프론트 구현 체크리스트

- [ ] 채팅 탭 첫 진입 시 빈 화면 표시
- [ ] 첫 메시지 전송 시 `POST /api/v1/chat/conversations` 먼저 호출
- [ ] 생성된 `conversationId` 저장
- [ ] `POST /api/v1/chat/stream` 요청에 `conversationId` 포함
- [ ] 채팅 내역 메뉴에서 `GET /api/v1/chat/conversations` 호출
- [ ] 대화방 선택 시 `GET /api/v1/chat/conversations/{id}/messages` 호출
- [ ] 스트리밍 중 중복 전송 방지
- [ ] `402` 수신 시 포인트 부족 UI 표시
- [ ] `404` 수신 시 삭제되었거나 접근 불가한 방으로 안내하고 목록 새로고침

## Swagger

Swagger에서도 확인할 수 있습니다.

```text
/swagger-ui.html
/v3/api-docs
```

Swagger 포함 항목:

- `POST /api/v1/chat/conversations`
- `GET /api/v1/chat/conversations`
- `GET /api/v1/chat/conversations/{conversationId}/messages`
- `POST /api/v1/chat/stream`
- `GET /api/v1/chat/history/{uuid}`

## 참고

- 대화방 목록 API는 최신 메시지를 배치 조회하도록 구현되어 있어 N+1 쿼리를 피합니다.
- 새 채팅 화면에 진입했다는 이유만으로 대화방을 만들 필요는 없습니다.
- 현재 구조에서는 대화방 생성 후 스트리밍 요청이 실패하면 빈 방이 남을 수 있습니다. 엄격한 빈 방 방지가 필요하면 이후 create-and-send 원자 API 또는 빈 방 cleanup 정책을 추가할 수 있습니다.
