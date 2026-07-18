# Story: REQ-002 관리자 계정 관리

Status: Draft

## Story

슈퍼 관리자로서 운영 인력의 접근 통제를 위해 관리자 계정을 생성하고 비활성화할 수 있다.

- 우선순위: P0
- 주 도메인: 관리자 인증·권한
- 에픽: 관리자 인증·권한 (v.0.2) — CC-567
- Jira 스토리: CC-583
- 원문 근거: INTAKE-001 US-ADM-AUTH-002
- 기존 관계: 신규 (v.0.2)
- UI: 2단계에서 와이어프레임 작성 (docs/ux/wireframes/)

## Acceptance Criteria (원문 전사)

1. AC-01 생성: 관리자 계정 관리 권한이 있을 때 이름·로그인 ID·역할을 입력하면 중복되지 않은 계정을 생성한다.
2. AC-02 비활성화: 슈퍼 관리자가 사유를 입력해 비활성화하면 모든 세션을 만료시키고 새 로그인을 차단한다.
3. AC-03 자기 계정 보호: 자신의 계정 비활성화 요청은 거부한다.

## Tasks

- [ ] AC-01 생성 — 구현·검증 (CC-706)
- [ ] AC-02 비활성화 — 구현·검증 (CC-707)
- [ ] AC-03 자기 계정 보호 — 구현·검증 (CC-708)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-709)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Kotlin Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
