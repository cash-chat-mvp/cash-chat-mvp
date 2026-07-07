# chat 도메인 — 유저 스토리 인덱스

AI 채팅(서버 SSE 스트리밍) 여정의 유저 스토리 카탈로그. 각 파일은 **직군 중립 계약**(스토리 + 인수 조건)이며, 완료 판정·검증의 기준선이다.
기술 구현 상세(API 계약·데이터 모델·시퀀스)는 각 파일이 링크하는 원본 spec에 있다.

> 파일명 = 불변 ID(`US-CHAT-NNN`) + slug. 번호는 **생성 순서일 뿐 의미 없음**(우선순위/순서 아님, 재사용·재배치 안 함). 사람이 보는 순서·우선순위는 아래 표에서 관리한다.

| ID | 스토리 | 상태 | Jira | 원본 spec |
| -- | ------ | ---- | ---- | --------- |
| [US-CHAT-001](./US-CHAT-001-ai-chat-response.md) | AI 채팅 응답 수신 | draft | CC-391 | [../../superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md](../../superpowers/specs/2026-07-07-maestro-fe-acceptance-spike-design.md) |

- **상태**는 스펙 합의 기준의 응결값이다. 실제 배포/진척 추적은 Jira를 참조.
- 공유 용어: [_glossary.md](./_glossary.md)
