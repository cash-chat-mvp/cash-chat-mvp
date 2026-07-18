# Story: REQ-022 채널별 보상 조회

Status: Draft

## Story

운영 관리자로서 보상 채널의 정상 동작을 확인하기 위해 채널별 지급·거절 내역을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 보상·경제
- Epic: 보상·경제 (v.0.2) — Jira CC-497
- Jira Task: CC-528
- 원문 근거: INTAKE-001 US-ADM-REWARD-004
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-022/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 채널 구분: 출석, 광고, 오퍼월, 채팅, 초대, 룰렛을 구분한다.
2. AC-02 상태 검색: 지급, 거절, 처리 중, 실패 상태와 기간으로 검색한다.
3. AC-03 원본 추적: 원본 이벤트부터 자산 원장까지 처리 단계를 연결한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
