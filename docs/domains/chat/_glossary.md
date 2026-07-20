# chat 용어

- **SSE 스트림**: `POST /api/v1/chat/stream` 이 `text/event-stream` 으로 토큰을 순차 방출. `event: done`/`data: [DONE]` 로 종료.
- **대화(conversation)**: 첫 메시지 전송 시 `POST /api/v1/chat/conversations` 로 lazy 생성.
