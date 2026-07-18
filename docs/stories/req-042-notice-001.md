# Story: REQ-042 공지사항 관리

Status: Draft

## Story

운영 관리자로서 서비스 정보를 전달하기 위해 공지사항을 작성·예약·종료할 수 있다.

- 우선순위: P1
- 주 도메인: 공지·약관·고객지원
- Epic: 공지·약관·고객지원 (v.0.2) — Jira CC-502
- Jira Task: CC-548
- 원문 근거: INTAKE-001 US-ADM-NOTICE-001
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-042/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 작성·예약: 제목, 내용, 노출 기간, 플랫폼, 중요도를 입력해 초안·예약 상태로 저장한다.
2. AC-02 사용자 노출: 노출 조건에 맞는 게시 상태 공지만 반환한다.
3. AC-03 수정 이력: 버전별 내용, 처리자, 시각을 반환한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
