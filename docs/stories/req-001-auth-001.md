# Story: REQ-001 관리자 로그인

Status: Draft

## Story

관리자로서 인가된 운영 시스템 접근을 위해 관리자 계정으로 로그인할 수 있다.

- 우선순위: P0
- 주 도메인: 관리자 인증·권한
- 에픽: 관리자 인증·권한 (v.0.2) — CC-567
- Jira 스토리: CC-582
- 원문 근거: INTAKE-001 US-ADM-AUTH-001
- 기존 관계: 신규 (v.0.2)
- UI: [로그인](../ux/wireframes/pages/00-login/index.html)

## Acceptance Criteria (원문 전사)

1. AC-01 정상 로그인: 활성 관리자 계정이 존재할 때 올바른 아이디·비밀번호로 로그인하면 인증 세션을 발급하고 허용된 초기 화면 정보를 반환한다.
2. AC-02 인증 실패: 아이디가 없거나 비밀번호가 불일치하면 구체적 실패 원인을 노출하지 않고 동일한 실패 응답을 반환한다.
3. AC-03 반복 실패 잠금: 동일 계정 인증 실패가 정책 횟수에 도달하면 계정을 일정 시간 잠그고 시도 내역을 기록한다.

## Tasks

- [ ] AC-01 정상 로그인 — 구현·검증 (CC-702)
- [ ] AC-02 인증 실패 — 구현·검증 (CC-703)
- [ ] AC-03 반복 실패 잠금 — 구현·검증 (CC-704)
- [ ] 관리 화면 구현 (와이어프레임 기준) (CC-705)

## Dev Notes

- Architecture: [architecture.md](../architecture.md) — React+TS admin-frontend / Java Spring Boot admin-backend / 운영 MySQL 공유 / MFA=TOTP.
- 불변 규칙: 인증·RBAC 통과 없이는 반환·변경 금지, 자산 변경은 원장 필수, 변경은 감사와 원자적 커밋.
- DB 테이블·컬럼·API 스키마는 구현(4단계)에서 확정한다.
