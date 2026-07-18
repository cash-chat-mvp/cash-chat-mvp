# Story: REQ-029 채팅 이력 조회

Status: Draft

## Story

운영·CS 관리자로서 채팅 문의의 원인을 조사하기 위해 회원의 대화와 메시지 이력을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 채팅·AI 운영
- 에픽: 채팅·AI 운영 (v.0.2) — CC-573
- Jira 스토리: CC-610
- 원문 근거: INTAKE-001 US-ADM-CHAT-001
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 대화 검색: 회원, 대화 ID, 메시지 상태, 모델, 기간으로 검색해 페이징 반환한다.
2. AC-02 메시지 상태: 역할, 상태, 모델, 발생 시각을 순서대로 반환한다.
3. AC-03 원문 보호: 채팅 원문 권한이 없으면 내용은 제외하고 운영 메타데이터만 반환한다.

## Tasks

- [ ] AC-01 대화 검색 — 구현·검증 (CC-814)
- [ ] AC-02 메시지 상태 — 구현·검증 (CC-815)
- [ ] AC-03 원문 보호 — 구현·검증 (CC-816)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-817)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
