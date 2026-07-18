# Story: REQ-033 품질 풀 운영

Status: Draft

## Story

운영 관리자로서 프리미엄 AI 사용 재원을 통제하기 위해 공용 품질 풀과 회원별 사용량을 조회할 수 있다.

- 우선순위: P2
- 주 도메인: 채팅·AI 운영
- Epic: 채팅·AI 운영 (v.0.2) — Jira CC-499
- Jira Task: CC-539
- 원문 근거: INTAKE-001 US-ADM-CHAT-005
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-033/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 풀 잔액: 현재 잔액, 기간별 적립·소비, 프리미엄 요청 수를 반환한다.
2. AC-02 회원별 cap: 사용 횟수, cap, 강등 여부를 반환한다.
3. AC-03 이상 경고: 잔액·소비량 임계치 초과 시 부족·급증 경고를 표시한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
