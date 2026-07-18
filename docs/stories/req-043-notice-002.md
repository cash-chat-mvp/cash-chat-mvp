# Story: REQ-043 약관 개정 관리

Status: Draft

## Story

운영 관리자로서 법적 고지와 동의 이력을 관리하기 위해 약관 버전을 등록하고 시행할 수 있다.

- 우선순위: P2
- 주 도메인: 공지·약관·고객지원
- Epic: 공지·약관·고객지원 (v.0.2) — Jira CC-502
- Jira Task: CC-549
- 원문 근거: INTAKE-001 US-ADM-NOTICE-002
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-043/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 버전 등록: 버전, 본문, 시행일, 재동의 여부를 입력해 중복 없는 버전을 생성한다.
2. AC-02 시행: 시행일 도래 시 유효 버전을 반환한다.
3. AC-03 동의 추적: 회원, 버전, 동의 시각, 근거를 기록한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
