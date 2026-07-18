# Story: REQ-031 AI 토큰·비용 기록

Status: Draft

## Story

비용 관리자로서 정확한 AI 비용을 계산하기 위해 채팅 요청별 토큰 사용량과 비용 기준을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 채팅·AI 운영
- 에픽: 채팅·AI 운영 (v.0.2) — CC-573
- Jira 스토리: CC-612
- 원문 근거: INTAKE-001 US-ADM-CHAT-003
- 기존 관계: 신규 (v.0.2)
- UI: [AI 비용 지표](../ux/wireframes/pages/04-dashboard-ai-cost/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 사용량 저장: 채팅 요청 종료 시 모델, 입력·출력·합계 토큰, 요청 상태를 저장한다.
2. AC-02 비용 단가: 요청 시점에 유효한 단가를 적용한다.
3. AC-03 미확정 비용: usage·단가 정보가 없으면 0으로 오인하지 않도록 미집계 상태로 표시한다.

## Tasks

- [ ] AC-01 사용량 저장 — 구현·검증 (CC-822)
- [ ] AC-02 비용 단가 — 구현·검증 (CC-823)
- [ ] AC-03 미확정 비용 — 구현·검증 (CC-824)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-825)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
