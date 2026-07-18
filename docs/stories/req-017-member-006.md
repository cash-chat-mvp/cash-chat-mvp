# Story: REQ-017 로그인·기기 이력

Status: Draft

## Story

운영 관리자로서 계정 탈취와 다계정 사용을 조사하기 위해 회원의 로그인·기기 이력을 조회할 수 있다.

- 우선순위: P1
- 주 도메인: 회원·제재
- Epic: 회원·제재 (v.0.2) — Jira CC-496
- Jira Task: CC-523
- 원문 근거: INTAKE-001 US-ADM-MEMBER-006
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-017/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 로그인 기록: 로그인·토큰 갱신 종료 시 회원, 제공자, 성공 여부, 시각, IP, 기기 식별 정보를 기록한다.
2. AC-02 회원별 조회: 최신 기록부터 페이징 반환한다.
3. AC-03 기기별 탐색: 기기 기준 검색 시 연결 회원과 최근 이용 시각을 반환한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
