# Story: REQ-012 회원 검색

Status: Draft

## Story

운영·CS 관리자로서 문의 또는 운영 대상 회원을 찾기 위해 회원 목록을 검색할 수 있다.

- 우선순위: P0
- 주 도메인: 회원·제재
- 에픽: 회원·제재 (v.0.2) — CC-570
- Jira 스토리: CC-593
- 원문 근거: INTAKE-001 US-ADM-MEMBER-001
- 기존 관계: 신규 (v.0.2)
- UI: [회원 목록·검색](../ux/wireframes/pages/05-members/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 조건 검색: ID, 이름, 이메일, 가입 유형, 상태, 가입 기간 조건을 모두 만족하는 회원을 페이징 반환한다.
2. AC-02 기본 정보: ID, 이름, 마스킹 이메일, 가입 유형, 상태, 가입일을 반환한다.
3. AC-03 개인정보 제한: 원문 권한이 없으면 이메일과 외부 식별자를 마스킹한다.

## Tasks

- [ ] AC-01 조건 검색 — 구현·검증 (CC-745)
- [ ] AC-02 기본 정보 — 구현·검증 (CC-746)
- [ ] AC-03 개인정보 제한 — 구현·검증 (CC-747)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-748)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
