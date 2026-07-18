# Story: REQ-006 민감정보 조회 감사

Status: Draft

## Story

감사 담당자로서 개인정보 오남용 방지를 위해 민감정보 조회 이력을 확인할 수 있다.

- 우선순위: P0
- 주 도메인: 감사·보안
- Epic: 감사·보안 (v.0.2) — Jira CC-494
- Jira Task: CC-512
- 원문 근거: INTAKE-001 US-ADM-AUDIT-002
- 기존 관계: 신규 (v.0.2)
- UI: ADMIN_UI — 와이어프레임: ../ux/wireframes/req-006/index.html

## Acceptance Criteria (원문 전사)

1. AC-01 회원정보 조회: 이메일·로그인·기기 정보 조회 시 조회자, 대상 회원, 범위, 시각과 IP를 기록한다.
2. AC-02 채팅 원문 조회: 권한 보유자가 사유를 입력해 원문을 조회하면 사유와 조회 범위를 감사 로그에 기록한다.
3. AC-03 다운로드: 개인정보 포함 파일 생성 시 검색 조건, 포함 범위와 관리자를 기록한다.

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.

## 관계

- 없음

## Tasks

- [ ] 구현 착수 시 세분화 (스프린트 4단계)
