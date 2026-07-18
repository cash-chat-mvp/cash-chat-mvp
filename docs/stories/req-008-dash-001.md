# Story: REQ-008 핵심 운영 현황

Status: Draft

## Story

운영 관리자로서 서비스 이상과 운영량을 빠르게 파악하기 위해 핵심 지표를 한 화면에서 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 대시보드
- Epic: 대시보드 (v.0.2) — Jira CC-495
- Jira Task: CC-514
- 원문 근거: INTAKE-001 US-ADM-DASH-001
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-008/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 기간 지표: 오늘·주간·월간 대시보드 조회 시 신규·탈퇴 회원, 채팅, 광고 보상, 포인트·Energy, 구매 지표를 같은 기간 기준으로 반환한다.
2. AC-02 성공·실패 구분: 채팅·광고·오퍼월의 요청 수와 성공·실패·거절 수를 구분한다.
3. AC-03 상세 이동: 지표 카드 선택 시 동일 기간·상태 필터가 적용된 상세 목록으로 이동한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
