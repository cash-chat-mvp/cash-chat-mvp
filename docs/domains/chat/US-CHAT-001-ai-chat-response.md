---
id: US-CHAT-001
domain: chat
slug: ai-chat-response
status: draft
jira: CC-391
source: apps/frontend (feature/chat), docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md
related-domains: [chat, energy]
---

# AI 채팅 응답 수신

## 스토리

사용자로서, 나는 채팅 입력창에 메시지를 보내면 AI의 응답을 화면에서 받고 싶다.
응답 스트림이 실패하면, 잘못된 응답이 정상처럼 표시되지 않기를 바란다.

## FE 관통 인수 기준 (Acceptance Criteria)

- [ ] **AC-FE-01 정상 응답 수신**
  Given mock 플레이버 앱이 로그인(MEMBER) 상태로 채팅 화면에 있다
  When 입력창에 메시지를 입력하고 전송한다
  Then 인앱 Fake 백엔드의 SSE 응답(`목킹응답: 반갑습니다 👋`)이 대화에 렌더된다.

- [ ] **AC-FE-02 스트림 에러**
  Given `chat_error` 시나리오다
  When 메시지를 전송한다
  Then 정상 응답(`목킹응답`)이 렌더되지 않는다(에러 경로로 분기).

## 검증 매핑 (Verification)

- FE(Maestro): `apps/frontend/maestro/flows/chat/ai-response.yaml`, `stream-error.yaml`
- 기술 상세: `docs/superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md`

## 관련

- 용어: [_glossary.md](./_glossary.md)
